package com.daara.internatadmin.finance.paiementenfant;

import com.daara.internatadmin.audit.Audited;
import com.daara.internatadmin.common.AmountMismatchException;
import com.daara.internatadmin.common.PartialPaymentException;
import com.daara.internatadmin.common.PaymentAlreadyExistsException;
import com.daara.internatadmin.common.PrintActionResponse;
import com.daara.internatadmin.enfant.Enfant;
import com.daara.internatadmin.enfant.EnfantRepository;
import com.daara.internatadmin.enfant.StatutEnfant;
import com.daara.internatadmin.finance.paiementenfant.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PaiementEnfantService {

    private final PaiementEnfantRepository paiementEnfantRepository;
    private final EnfantRepository enfantRepository;
    private final PaiementLocks paiementLocks;

    @Audited(action = "CREATE", entityType = "PaiementEnfant")
    @Transactional
    public PaiementEnfantResponse create(CreatePaiementEnfantRequest dto, Long currentUserId, String currentUsername) {
        Enfant enfant = enfantRepository.findById(dto.enfantId())
                .orElseThrow(() -> new IllegalArgumentException("Enfant introuvable : " + dto.enfantId()));

        LocalDate concernedMonth = null;
        BigDecimal balanceAfter = null;
        BigDecimal expectedAmount = null;
        Boolean isPartial = null;

        if (dto.paymentType() == TypePaiementEnfant.MENSUALITE) {
            if (dto.concernedYear() == null || dto.concernedMonth() == null) {
                throw new IllegalArgumentException("L'année et le mois concernés sont requis pour une mensualité");
            }
            concernedMonth = LocalDate.of(dto.concernedYear(), dto.concernedMonth(), 1);

            // avant toute lecture : un paiement concurrent doit etre commite pour etre compte dans le deja paye
            paiementLocks.lockMensualite(enfant.getId(), concernedMonth);

            List<PaiementEnfant> existingPaiements = paiementEnfantRepository.findByPaymentTypeAndConcernedMonthAndStatus(
                    TypePaiementEnfant.MENSUALITE, concernedMonth, StatutPaiement.VALIDE
            ).stream().filter(p -> p.getEnfant().getId().equals(enfant.getId())).toList();

            BigDecimal alreadyPaid = existingPaiements.stream()
                    .map(PaiementEnfant::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            expectedAmount = existingPaiements.stream()
                    .map(PaiementEnfant::getExpectedAmount)
                    .filter(java.util.Objects::nonNull)
                    .findFirst()
                    .orElse(enfant.getMonthlyFee());


            BigDecimal remainingBefore = expectedAmount.subtract(alreadyPaid);

            if (remainingBefore.compareTo(BigDecimal.ZERO) <= 0) {
                throw new AmountMismatchException(
                        "La mensualité de " + dto.concernedMonth() + "/" + dto.concernedYear() + " est déjà entièrement payée pour cet enfant"
                );
            }

            int comparison = dto.amount().compareTo(remainingBefore);

            if (comparison > 0) {
                throw new AmountMismatchException(
                        "Le montant saisi (" + dto.amount() + ") dépasse le solde restant dû (" + remainingBefore + "). " +
                                "Aucun dépassement n'est autorisé."
                );
            }

            if (comparison < 0 && !dto.confirmPartial()) {
                throw new PartialPaymentException(
                        "Le montant saisi (" + dto.amount() + ") est inférieur au solde restant dû (" + remainingBefore + "). " +
                                "Si c'est un paiement partiel intentionnel, renvoyez avec confirmPartial=true."
                );
            }

            balanceAfter = remainingBefore.subtract(dto.amount());
            isPartial = balanceAfter.compareTo(BigDecimal.ZERO) > 0;

        } else {
            // INSCRIPTION : montant strictement fixe, aucune dérogation possible
            if (dto.amount().compareTo(enfant.getRegistrationFee()) != 0) {
                throw new AmountMismatchException(
                        "Le montant des frais d'inscription doit être exactement " + enfant.getRegistrationFee() +
                                ". Aucune dérogation n'est autorisée."
                );
            }
        }

        try {
            PaiementEnfant paiement = new PaiementEnfant();
            paiement.setEnfant(enfant);
            paiement.setRegisteredByUserId(currentUserId);
            paiement.setRegisteredByUsername(currentUsername);
            paiement.setPaymentType(dto.paymentType());
            paiement.setPaymentMethod(dto.paymentMethod());
            paiement.setAmount(dto.amount());
            paiement.setConcernedMonth(concernedMonth);
            paiement.setExpectedAmount(expectedAmount);
            paiement.setBalanceAfter(balanceAfter);
            paiement.setExcessAmount(BigDecimal.ZERO); // le surpaiement n'est plus possible
            paiement.setIsPartial(isPartial);
            if (dto.paymentDate() != null) {
                paiement.setPaymentDate(dto.paymentDate());
            }

            paiementEnfantRepository.saveAndFlush(paiement);
            return toResponse(paiement);

        } catch (DataIntegrityViolationException e) {
            throw new PaymentAlreadyExistsException("Les frais d'inscription ont déjà été enregistrés pour cet enfant");
        }
    }

    @Audited(action = "CANCEL", entityType = "PaiementEnfant")
    @Transactional
    public PaiementEnfantResponse cancel(Long id) {
        PaiementEnfant paiement = paiementEnfantRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Paiement introuvable : " + id));

        if (paiement.getStatus() == StatutPaiement.ANNULE) {
            throw new IllegalArgumentException("Ce paiement est déjà annulé");
        }

        paiement.setStatus(StatutPaiement.ANNULE);
        return toResponse(paiement);
    }

    @Audited(action = "PRINT_RECEIPT", entityType = "PaiementEnfant")
    @Transactional
    public PrintActionResponse recordReceiptPrint(Long id) {
        PaiementEnfant paiement = paiementEnfantRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Paiement introuvable : " + id));
        return new PrintActionResponse(
                paiement.getId(),
                "Impression du reçu enregistrée pour le paiement de " + paiement.getEnfant().getFullName()
        );
    }

    public List<PaiementEnfantResponse> getHistoriqueInscription(Long enfantId) {
        return paiementEnfantRepository
                .findByEnfantIdAndPaymentTypeOrderByPaymentDateDesc(enfantId, TypePaiementEnfant.INSCRIPTION)
                .stream().map(this::toResponse).toList();
    }

    public List<PaiementEnfantResponse> getHistoriqueMensualites(Long enfantId) {
        return paiementEnfantRepository
                .findByEnfantIdAndPaymentTypeOrderByPaymentDateDesc(enfantId, TypePaiementEnfant.MENSUALITE)
                .stream().map(this::toResponse).toList();
    }

    public List<SuiviMensuelResponse> getSuiviMensuel(int year, int month, String statusFilter) {
        LocalDate concernedMonth = LocalDate.of(year, month, 1);

        List<PaiementEnfant> paiements = paiementEnfantRepository.findByPaymentTypeAndConcernedMonthAndStatus(
                TypePaiementEnfant.MENSUALITE, concernedMonth, StatutPaiement.VALIDE
        );

        Map<Long, List<PaiementEnfant>> paiementsByEnfant = paiements.stream()
                .collect(Collectors.groupingBy(p -> p.getEnfant().getId()));

        List<SuiviMensuelResponse> all = enfantRepository.findByStatusIn(List.of(StatutEnfant.ACTIF, StatutEnfant.EN_VACANCES)).stream()
                .map(enfant -> {
                    List<PaiementEnfant> enfantPaiements = paiementsByEnfant.getOrDefault(enfant.getId(), List.of());

                    BigDecimal paid = enfantPaiements.stream()
                            .map(PaiementEnfant::getAmount)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

                    BigDecimal due = enfantPaiements.stream()
                            .map(PaiementEnfant::getExpectedAmount)
                            .filter(java.util.Objects::nonNull)
                            .findFirst()
                            .orElse(enfant.getMonthlyFee());

                    BigDecimal remaining = due.subtract(paid).max(BigDecimal.ZERO);
                    BigDecimal excess = paid.subtract(due).max(BigDecimal.ZERO);

                    String statut = remaining.compareTo(BigDecimal.ZERO) <= 0
                            ? "PAYE"
                            : (paid.compareTo(BigDecimal.ZERO) == 0 ? "IMPAYE" : "PARTIEL");

                    List<Long> ids = enfantPaiements.stream().map(PaiementEnfant::getId).toList();

                    return new SuiviMensuelResponse(
                            enfant.getId(), enfant.getMatricule(), enfant.getFullName(),
                            enfant.getGuardianName(), enfant.getGuardianPhone(),
                            due, paid, remaining, excess, statut, ids
                    );
                })
                .sorted(Comparator.comparing(SuiviMensuelResponse::fullName))
                .toList();

        if ("ALL".equals(statusFilter)) {
            return all;
        }
        return all.stream().filter(s -> s.statutPaiement().equals(statusFilter)).toList();
    }

    public List<InscriptionSuiviResponse> getInscriptions(String statusFilter) {
        Map<Long, PaiementEnfant> paidByEnfant = paiementEnfantRepository
                .findByPaymentTypeAndStatus(TypePaiementEnfant.INSCRIPTION, StatutPaiement.VALIDE)
                .stream()
                .collect(Collectors.toMap(p -> p.getEnfant().getId(), p -> p));

        List<InscriptionSuiviResponse> all = enfantRepository.findByStatusIn(List.of(StatutEnfant.ACTIF, StatutEnfant.EN_VACANCES)).stream()
                .map(enfant -> {
                    PaiementEnfant p = paidByEnfant.get(enfant.getId());
                    boolean paid = p != null;
                    return new InscriptionSuiviResponse(
                            enfant.getId(), enfant.getMatricule(), enfant.getFullName(),
                            enfant.getRegistrationFee(),
                            paid ? "PAYE" : "IMPAYE",
                            paid ? p.getPaymentDate() : null,
                            paid ? p.getId() : null
                    );
                })
                .toList();

        List<InscriptionSuiviResponse> filtered = "ALL".equals(statusFilter)
                ? all
                : all.stream().filter(r -> r.statut().equals(statusFilter)).toList();

        // tri par date pour les payés (plus récent d'abord), alphabétique pour les impayés
        Comparator<InscriptionSuiviResponse> comparator = "PAYE".equals(statusFilter)
                ? Comparator.comparing(InscriptionSuiviResponse::paymentDate, Comparator.nullsLast(Comparator.reverseOrder()))
                : Comparator.comparing(InscriptionSuiviResponse::fullName);

        return filtered.stream().sorted(comparator).toList();
    }


    // Méthode utilitaire pour récupérer les données d'un enfant pour un mois donné
    private Object[] findMonthData(Long enfantId, LocalDate month) {
        return paiementEnfantRepository.sumMensualitesByEnfantForMonth(month).stream()
                .filter(row -> row[0].equals(enfantId))
                .findFirst()
                .orElse(null);
    }

    private PaiementEnfantResponse toResponse(PaiementEnfant paiement) {
        return new PaiementEnfantResponse(
                paiement.getId(),
                paiement.getEnfant().getId(),
                paiement.getEnfant().getFullName(),
                paiement.getEnfant().getMatricule(),
                paiement.getPaymentType(),
                paiement.getPaymentMethod(),
                paiement.getAmount(),
                paiement.getConcernedMonth(),
                paiement.getPaymentDate(),
                paiement.getRegisteredByUsername(),
                paiement.getStatus(),
                paiement.getBalanceAfter(),
                paiement.getIsPartial(),
                paiement.getExpectedAmount(),
                paiement.getExcessAmount()
        );
    }

    public RecuPaiementResponse getReceiptData(Long paiementId) {
        PaiementEnfant paiement = paiementEnfantRepository.findById(paiementId)
                .orElseThrow(() -> new IllegalArgumentException("Paiement introuvable : " + paiementId));

        String libelle;
        if (paiement.getPaymentType() == TypePaiementEnfant.INSCRIPTION) {
            libelle = "Frais d'inscription";
        } else {
            libelle = Boolean.TRUE.equals(paiement.getIsPartial()) ? "Paiement partiel" : "Paiement complet";
        }

        return new RecuPaiementResponse(
                paiement.getId(),
                paiement.getEnfant().getMatricule(),
                paiement.getEnfant().getFullName(),
                paiement.getEnfant().getGuardianName(),
                paiement.getPaymentType(),
                paiement.getPaymentMethod(),
                paiement.getAmount(),
                paiement.getConcernedMonth(),
                paiement.getPaymentDate(),
                paiement.getBalanceAfter(),
                Boolean.TRUE.equals(paiement.getIsPartial()),
                paiement.getRegisteredByUsername(),
                libelle
        );
    }
}
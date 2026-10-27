package com.daara.internatadmin.paiement.enfant;

import com.daara.internatadmin.common.api.PrintActionResponse;
import com.daara.internatadmin.common.audit.Audited;
import com.daara.internatadmin.common.security.CurrentUser;
import com.daara.internatadmin.paiement.StatutPaiement;
import com.daara.internatadmin.paiement.client.EnfantClient;
import com.daara.internatadmin.paiement.client.EnfantView;
import com.daara.internatadmin.paiement.enfant.dto.CreatePaiementEnfantRequest;
import com.daara.internatadmin.paiement.enfant.dto.InscriptionSuiviResponse;
import com.daara.internatadmin.paiement.enfant.dto.PaiementEnfantResponse;
import com.daara.internatadmin.paiement.enfant.dto.RecuPaiementResponse;
import com.daara.internatadmin.paiement.enfant.dto.SuiviMensuelResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class PaiementEnfantService {

    /** Eleves concernes par les suivis : ACTIF et EN_VACANCES, comme le monolithe. */
    static final List<String> SUIVI_STATUSES = List.of("ACTIF", "EN_VACANCES");

    private final PaiementEnfantRepository repository;
    private final PaiementEnfantRecorder recorder;
    private final EnfantClient enfantClient;

    public PaiementEnfantService(PaiementEnfantRepository repository, PaiementEnfantRecorder recorder,
                                 EnfantClient enfantClient) {
        this.repository = repository;
        this.recorder = recorder;
        this.enfantClient = enfantClient;
    }

    /**
     * L'eleve est lu dans enfant-service avant d'ouvrir la transaction : aucune transaction ne reste ouverte
     * pendant un appel reseau. Eleve inconnu : 400 INVALID_REQUEST ; enfant-service indisponible : 503.
     */
    @Audited(action = "CREATE", entityType = "PaiementEnfant")
    public PaiementEnfantResponse create(CreatePaiementEnfantRequest dto, CurrentUser user) {
        EnfantView enfant = enfantClient.get(dto.enfantId());
        return toResponse(recorder.record(dto, enfant, user));
    }

    @Audited(action = "CANCEL", entityType = "PaiementEnfant")
    @Transactional
    public PaiementEnfantResponse cancel(Long id) {
        PaiementEnfant paiement = findPaiement(id);
        if (paiement.getStatus() == StatutPaiement.ANNULE) {
            throw new IllegalArgumentException("Ce paiement est déjà annulé");
        }
        paiement.setStatus(StatutPaiement.ANNULE);
        return toResponse(paiement);
    }

    @Audited(action = "PRINT_RECEIPT", entityType = "PaiementEnfant")
    @Transactional(readOnly = true)
    public PrintActionResponse recordReceiptPrint(Long id) {
        PaiementEnfant paiement = findPaiement(id);
        return new PrintActionResponse(paiement.getId(),
                "Impression du reçu enregistrée pour le paiement de " + paiement.getEnfantFullName());
    }

    @Transactional(readOnly = true)
    public RecuPaiementResponse getReceiptData(Long id) {
        PaiementEnfant paiement = findPaiement(id);
        boolean partial = Boolean.TRUE.equals(paiement.getIsPartial());
        String libelle = paiement.getPaymentType() == TypePaiementEnfant.INSCRIPTION
                ? "Frais d'inscription"
                : (partial ? "Paiement partiel" : "Paiement complet");

        return new RecuPaiementResponse(
                paiement.getId(),
                paiement.getEnfantMatricule(),
                paiement.getEnfantFullName(),
                paiement.getGuardianName(),
                paiement.getPaymentType(),
                paiement.getPaymentMethod(),
                paiement.getAmount(),
                paiement.getConcernedMonth(),
                paiement.getPaymentDate(),
                paiement.getBalanceAfter(),
                partial,
                paiement.getRegisteredByUsername(),
                libelle);
    }

    @Transactional(readOnly = true)
    public List<PaiementEnfantResponse> getHistorique(Long enfantId, TypePaiementEnfant type) {
        return repository.findByEnfantIdAndPaymentTypeOrderByPaymentDateDesc(enfantId, type).stream()
                .map(PaiementEnfantService::toResponse).toList();
    }

    /**
     * Suivi des mensualites d'un mois : pour chaque eleve ACTIF ou EN_VACANCES, montant du (fige au premier
     * paiement, sinon tarif actuel), paye, restant et statut PAYE, PARTIEL ou IMPAYE. Tri par nom ;
     * filtre ALL, PAYE, PARTIEL ou IMPAYE (toute autre valeur donne une liste vide, comme le monolithe).
     * Volontairement hors transaction : aucune connexion a la base n'est retenue pendant l'appel a enfant-service.
     */
    public List<SuiviMensuelResponse> getSuiviMensuel(int year, int month, String statusFilter) {
        LocalDate concernedMonth = LocalDate.of(year, month, 1);
        Map<Long, List<PaiementEnfant>> paiementsByEnfant = repository
                .findByPaymentTypeAndConcernedMonthAndStatus(TypePaiementEnfant.MENSUALITE, concernedMonth, StatutPaiement.VALIDE)
                .stream()
                .collect(Collectors.groupingBy(PaiementEnfant::getEnfantId));

        List<SuiviMensuelResponse> all = enfantClient.listByStatuses(SUIVI_STATUSES).stream()
                .map(enfant -> suiviLine(enfant, paiementsByEnfant.getOrDefault(enfant.id(), List.of())))
                .sorted(Comparator.comparing(SuiviMensuelResponse::fullName))
                .toList();

        return "ALL".equals(statusFilter)
                ? all
                : all.stream().filter(s -> s.statutPaiement().equals(statusFilter)).toList();
    }

    /** Suivi des inscriptions : tri par date de paiement decroissante si filtre PAYE, sinon par nom. Hors transaction. */
    public List<InscriptionSuiviResponse> getInscriptions(String statusFilter) {
        Map<Long, PaiementEnfant> paidByEnfant = repository
                .findByPaymentTypeAndStatus(TypePaiementEnfant.INSCRIPTION, StatutPaiement.VALIDE).stream()
                .collect(Collectors.toMap(PaiementEnfant::getEnfantId, Function.identity()));

        List<InscriptionSuiviResponse> all = enfantClient.listByStatuses(SUIVI_STATUSES).stream()
                .map(enfant -> {
                    PaiementEnfant p = paidByEnfant.get(enfant.id());
                    boolean paid = p != null;
                    return new InscriptionSuiviResponse(enfant.id(), enfant.matricule(), enfant.fullName(),
                            enfant.registrationFee(), paid ? "PAYE" : "IMPAYE",
                            paid ? p.getPaymentDate() : null, paid ? p.getId() : null);
                })
                .toList();

        List<InscriptionSuiviResponse> filtered = "ALL".equals(statusFilter)
                ? all
                : all.stream().filter(r -> r.statut().equals(statusFilter)).toList();

        Comparator<InscriptionSuiviResponse> comparator = "PAYE".equals(statusFilter)
                ? Comparator.comparing(InscriptionSuiviResponse::paymentDate, Comparator.nullsLast(Comparator.reverseOrder()))
                : Comparator.comparing(InscriptionSuiviResponse::fullName);
        return filtered.stream().sorted(comparator).toList();
    }

    private static SuiviMensuelResponse suiviLine(EnfantView enfant, List<PaiementEnfant> paiements) {
        BigDecimal paid = paiements.stream().map(PaiementEnfant::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal due = paiements.stream()
                .map(PaiementEnfant::getExpectedAmount)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(enfant.monthlyFee());

        BigDecimal remaining = due.subtract(paid).max(BigDecimal.ZERO);
        BigDecimal excess = paid.subtract(due).max(BigDecimal.ZERO);
        String statut = remaining.compareTo(BigDecimal.ZERO) <= 0
                ? "PAYE"
                : (paid.compareTo(BigDecimal.ZERO) == 0 ? "IMPAYE" : "PARTIEL");

        return new SuiviMensuelResponse(enfant.id(), enfant.matricule(), enfant.fullName(),
                enfant.guardianName(), enfant.guardianPhone(), due, paid, remaining, excess, statut,
                paiements.stream().map(PaiementEnfant::getId).toList());
    }

    private PaiementEnfant findPaiement(Long id) {
        return repository.findById(id).orElseThrow(() -> new IllegalArgumentException("Paiement introuvable : " + id));
    }

    static PaiementEnfantResponse toResponse(PaiementEnfant p) {
        return new PaiementEnfantResponse(
                p.getId(),
                p.getEnfantId(),
                p.getEnfantFullName(),
                p.getEnfantMatricule(),
                p.getPaymentType(),
                p.getPaymentMethod(),
                p.getAmount(),
                p.getConcernedMonth(),
                p.getPaymentDate(),
                p.getRegisteredByUsername(),
                p.getStatus(),
                p.getBalanceAfter(),
                p.getIsPartial(),
                p.getExpectedAmount(),
                p.getExcessAmount());
    }
}

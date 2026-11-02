package com.daara.internatadmin.finance.paiementsalaire;

import com.daara.internatadmin.audit.Audited;
import com.daara.internatadmin.common.PaymentAlreadyExistsException;
import com.daara.internatadmin.common.PrintActionResponse;
import com.daara.internatadmin.finance.paiementsalaire.dto.*;
import com.daara.internatadmin.personnel.Employe;
import com.daara.internatadmin.personnel.EmployeRepository;
import com.daara.internatadmin.personnel.StatutEmploye;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PaiementSalaireService {

    private final PaiementSalaireRepository paiementSalaireRepository;
    private final EmployeRepository employeRepository;

    @Audited(action = "CREATE", entityType = "PaiementSalaire")
    @Transactional
    public PaiementSalaireResponse create(CreatePaiementSalaireRequest dto, Long currentUserId, String currentUsername) {
        Employe employe = employeRepository.findById(dto.employeId())
                .orElseThrow(() -> new IllegalArgumentException("Employé introuvable : " + dto.employeId()));

        LocalDate concernedMonth = LocalDate.of(dto.concernedYear(), dto.concernedMonth(), 1);

        try {
            PaiementSalaire paiement = new PaiementSalaire();
            paiement.setEmploye(employe);
            paiement.setRegisteredByUserId(currentUserId);
            paiement.setRegisteredByUsername(currentUsername);
            paiement.setPaymentMethod(dto.paymentMethod());
            paiement.setAmount(employe.getBaseSalary());
            paiement.setExpectedAmount(employe.getBaseSalary());
            paiement.setConcernedMonth(concernedMonth);
            if (dto.paymentDate() != null) {
                paiement.setPaymentDate(dto.paymentDate());
            }

            paiementSalaireRepository.saveAndFlush(paiement);
            return toResponse(paiement);

        } catch (DataIntegrityViolationException e) {
            throw new PaymentAlreadyExistsException(
                    "Le salaire de " + dto.concernedMonth() + "/" + dto.concernedYear() + " a déjà été enregistré pour cet employé"
            );
        }
    }

    @Audited(action = "CANCEL", entityType = "PaiementSalaire")
    @Transactional
    public PaiementSalaireResponse cancel(Long id) {
        PaiementSalaire paiement = paiementSalaireRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Paiement introuvable : " + id));

        if (paiement.getStatus() == StatutPaiementSalaire.ANNULE) {
            throw new IllegalArgumentException("Ce paiement est déjà annulé");
        }

        paiement.setStatus(StatutPaiementSalaire.ANNULE);
        return toResponse(paiement);
    }

    @Audited(action = "PRINT_RECEIPT", entityType = "PaiementSalaire")
    @Transactional
    public PrintActionResponse recordReceiptPrint(Long id) {
        PaiementSalaire paiement = paiementSalaireRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Paiement introuvable : " + id));
        return new PrintActionResponse(
                paiement.getId(),
                "Impression du reçu enregistrée pour le salaire de " + paiement.getEmploye().getFullName()
        );
    }

    public RecuSalaireResponse getReceiptData(Long id) {
        PaiementSalaire paiement = paiementSalaireRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Paiement introuvable : " + id));

        return new RecuSalaireResponse(
                paiement.getId(),
                paiement.getEmploye().getMatricule(),
                paiement.getEmploye().getFullName(),
                paiement.getEmploye().getFunction().name(),
                paiement.getPaymentMethod(),
                paiement.getAmount(),
                paiement.getConcernedMonth(),
                paiement.getPaymentDate(),
                paiement.getRegisteredByUsername()
        );
    }

    public List<PaiementSalaireResponse> getHistorique(Long employeId) {
        return paiementSalaireRepository.findByEmployeIdOrderByPaymentDateDesc(employeId).stream()
                .map(this::toResponse)
                .toList();
    }

    public List<SalaireSuiviResponse> getSuiviMensuel(int year, int month, String statusFilter) {
        LocalDate concernedMonth = LocalDate.of(year, month, 1);

        Map<Long, PaiementSalaire> paidByEmploye = paiementSalaireRepository
                .findByConcernedMonthAndStatus(concernedMonth, StatutPaiementSalaire.VALIDE)
                .stream()
                .collect(Collectors.toMap(p -> p.getEmploye().getId(), p -> p));

        List<SalaireSuiviResponse> all = employeRepository.findByStatusIn(List.of(StatutEmploye.ACTIF, StatutEmploye.EN_CONGE)).stream()
                .map(employe -> {
                    PaiementSalaire p = paidByEmploye.get(employe.getId());
                    boolean paid = p != null;
                    return new SalaireSuiviResponse(
                            employe.getId(), employe.getMatricule(), employe.getFullName(),
                            employe.getBaseSalary(),
                            paid ? "PAYE" : "IMPAYE",
                            paid ? p.getPaymentDate() : null,
                            paid ? p.getId() : null
                    );
                })
                .sorted(Comparator.comparing(SalaireSuiviResponse::fullName))
                .toList();

        if ("ALL".equals(statusFilter)) {
            return all;
        }
        return all.stream().filter(r -> r.statut().equals(statusFilter)).toList();
    }

    private PaiementSalaireResponse toResponse(PaiementSalaire paiement) {
        return new PaiementSalaireResponse(
                paiement.getId(),
                paiement.getEmploye().getId(),
                paiement.getEmploye().getFullName(),
                paiement.getEmploye().getMatricule(),
                paiement.getPaymentMethod(),
                paiement.getAmount(),
                paiement.getConcernedMonth(),
                paiement.getPaymentDate(),
                paiement.getRegisteredByUsername(),
                paiement.getStatus()
        );
    }
}
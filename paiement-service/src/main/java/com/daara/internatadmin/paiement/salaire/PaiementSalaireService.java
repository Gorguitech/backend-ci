package com.daara.internatadmin.paiement.salaire;

import com.daara.internatadmin.common.api.PrintActionResponse;
import com.daara.internatadmin.common.audit.Audited;
import com.daara.internatadmin.common.error.PaymentAlreadyExistsException;
import com.daara.internatadmin.common.security.CurrentUser;
import com.daara.internatadmin.paiement.StatutPaiement;
import com.daara.internatadmin.paiement.client.EmployeClient;
import com.daara.internatadmin.paiement.client.EmployeView;
import com.daara.internatadmin.paiement.salaire.dto.CreatePaiementSalaireRequest;
import com.daara.internatadmin.paiement.salaire.dto.PaiementSalaireResponse;
import com.daara.internatadmin.paiement.salaire.dto.RecuSalaireResponse;
import com.daara.internatadmin.paiement.salaire.dto.SalaireSuiviResponse;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class PaiementSalaireService {

    /** Employes concernes par le suivi : ACTIF et EN_CONGE, comme le monolithe. */
    static final List<String> SUIVI_STATUSES = List.of("ACTIF", "EN_CONGE");

    private final PaiementSalaireRepository repository;
    private final EmployeClient employeClient;

    public PaiementSalaireService(PaiementSalaireRepository repository, EmployeClient employeClient) {
        this.repository = repository;
        this.employeClient = employeClient;
    }

    /**
     * Le montant est le salaire de base lu dans personnel-service, fige dans amount et expectedAmount.
     * Un seul salaire valide par employe et par mois (index unique) : sinon 409 PAYMENT_ALREADY_EXISTS.
     * L'employe est lu avant toute transaction (aucune transaction ouverte pendant l'appel reseau).
     */
    @Audited(action = "CREATE", entityType = "PaiementSalaire")
    public PaiementSalaireResponse create(CreatePaiementSalaireRequest dto, CurrentUser user) {
        EmployeView employe = employeClient.get(dto.employeId());

        PaiementSalaire paiement = new PaiementSalaire();
        paiement.setEmployeId(employe.id());
        paiement.setEmployeMatricule(employe.matricule());
        paiement.setEmployeFullName(employe.fullName());
        paiement.setEmployeFunction(employe.function());
        paiement.setRegisteredByUserId(user.id());
        paiement.setRegisteredByUsername(user.username());
        paiement.setPaymentMethod(dto.paymentMethod());
        paiement.setAmount(employe.baseSalary());
        paiement.setExpectedAmount(employe.baseSalary());
        paiement.setConcernedMonth(LocalDate.of(dto.concernedYear(), dto.concernedMonth(), 1));
        if (dto.paymentDate() != null) {
            paiement.setPaymentDate(dto.paymentDate());
        }

        try {
            return toResponse(repository.saveAndFlush(paiement));
        } catch (DataIntegrityViolationException e) {
            throw new PaymentAlreadyExistsException("Le salaire de " + dto.concernedMonth() + "/" + dto.concernedYear()
                    + " a déjà été enregistré pour cet employé");
        }
    }

    @Audited(action = "CANCEL", entityType = "PaiementSalaire")
    @Transactional
    public PaiementSalaireResponse cancel(Long id) {
        PaiementSalaire paiement = findPaiement(id);
        if (paiement.getStatus() == StatutPaiement.ANNULE) {
            throw new IllegalArgumentException("Ce paiement est déjà annulé");
        }
        paiement.setStatus(StatutPaiement.ANNULE);
        return toResponse(paiement);
    }

    @Audited(action = "PRINT_RECEIPT", entityType = "PaiementSalaire")
    @Transactional(readOnly = true)
    public PrintActionResponse recordReceiptPrint(Long id) {
        PaiementSalaire paiement = findPaiement(id);
        return new PrintActionResponse(paiement.getId(),
                "Impression du reçu enregistrée pour le salaire de " + paiement.getEmployeFullName());
    }

    @Transactional(readOnly = true)
    public RecuSalaireResponse getReceiptData(Long id) {
        PaiementSalaire p = findPaiement(id);
        return new RecuSalaireResponse(p.getId(), p.getEmployeMatricule(), p.getEmployeFullName(), p.getEmployeFunction(),
                p.getPaymentMethod(), p.getAmount(), p.getConcernedMonth(), p.getPaymentDate(), p.getRegisteredByUsername());
    }

    @Transactional(readOnly = true)
    public List<PaiementSalaireResponse> getHistorique(Long employeId) {
        return repository.findByEmployeIdOrderByPaymentDateDesc(employeId).stream()
                .map(PaiementSalaireService::toResponse).toList();
    }

    /**
     * Suivi des salaires d'un mois : employes ACTIF et EN_CONGE, statut PAYE ou IMPAYE, tri par nom.
     * Hors transaction : aucune connexion n'est retenue pendant l'appel a personnel-service.
     */
    public List<SalaireSuiviResponse> getSuiviMensuel(int year, int month, String statusFilter) {
        Map<Long, PaiementSalaire> paidByEmploye = repository
                .findByConcernedMonthAndStatus(LocalDate.of(year, month, 1), StatutPaiement.VALIDE).stream()
                .collect(Collectors.toMap(PaiementSalaire::getEmployeId, Function.identity()));

        List<SalaireSuiviResponse> all = employeClient.listByStatuses(SUIVI_STATUSES).stream()
                .map(employe -> {
                    PaiementSalaire p = paidByEmploye.get(employe.id());
                    boolean paid = p != null;
                    return new SalaireSuiviResponse(employe.id(), employe.matricule(), employe.fullName(),
                            employe.baseSalary(), paid ? "PAYE" : "IMPAYE",
                            paid ? p.getPaymentDate() : null, paid ? p.getId() : null);
                })
                .sorted(Comparator.comparing(SalaireSuiviResponse::fullName))
                .toList();

        return "ALL".equals(statusFilter) ? all : all.stream().filter(r -> r.statut().equals(statusFilter)).toList();
    }

    private PaiementSalaire findPaiement(Long id) {
        return repository.findById(id).orElseThrow(() -> new IllegalArgumentException("Paiement introuvable : " + id));
    }

    static PaiementSalaireResponse toResponse(PaiementSalaire p) {
        return new PaiementSalaireResponse(p.getId(), p.getEmployeId(), p.getEmployeFullName(), p.getEmployeMatricule(),
                p.getPaymentMethod(), p.getAmount(), p.getConcernedMonth(), p.getPaymentDate(),
                p.getRegisteredByUsername(), p.getStatus());
    }
}

package com.daara.internatadmin.personnel;

import com.daara.internatadmin.audit.Audited;
import com.daara.internatadmin.common.DuplicateEntityException;
import com.daara.internatadmin.common.MatriculeGenerator;
import com.daara.internatadmin.common.PrintActionResponse;
import com.daara.internatadmin.common.TransactionalRetry;
import com.daara.internatadmin.finance.paiementsalaire.PaiementSalaireService;
import com.daara.internatadmin.finance.paiementsalaire.dto.SalaireSuiviResponse;
import com.daara.internatadmin.personnel.dto.CreateEmployeRequest;
import com.daara.internatadmin.personnel.dto.EmployeResponse;
import com.daara.internatadmin.personnel.dto.EmployeStatsResponse;
import com.daara.internatadmin.personnel.dto.UpdateEmployeRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class EmployeService {

    private final EmployeRepository employeRepository;
    private final TransactionalRetry transactionalRetry;
    private static final int MAX_ATTEMPTS = 5;
    @Audited(action = "CREATE", entityType = "Employe")
    public EmployeResponse create(CreateEmployeRequest dto, boolean confirmDuplicate) {
        if (!confirmDuplicate && employeRepository.existsByFirstNameIgnoreCaseAndLastNameIgnoreCaseAndPhone(
                dto.firstName(), dto.lastName(), dto.phone())) {
            throw new DuplicateEntityException(
                    "Un employé avec le même nom, prénom et téléphone existe déjà. "
            );
        }

        int year = dto.hireDate().getYear();
        String prefix = "EMP-" + year;

        // matricule déjà pris entre-temps : on retente, dans une nouvelle transaction
        return transactionalRetry.onUniqueViolation(MAX_ATTEMPTS, "matricule", attempt -> {
            long sequence = employeRepository.countByMatriculeStartingWith(prefix) + 1 + attempt;

            Employe employe = new Employe();
            employe.setMatricule(MatriculeGenerator.format("EMP", year, sequence));
            employe.setFirstName(dto.firstName());
            employe.setLastName(dto.lastName());
            employe.setPhone(dto.phone());
            employe.setFunction(dto.function());
            employe.setContractType(dto.contractType());
            employe.setBaseSalary(dto.baseSalary());
            employe.setHireDate(dto.hireDate());

            employeRepository.saveAndFlush(employe);
            return toResponse(employe);
        });
    }
    @Audited(action = "UPDATE_STATUS", entityType = "Employe")
    @Transactional
    public EmployeResponse updateStatus(Long id, StatutEmploye newStatus) {
        Employe employe = employeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Employé introuvable : " + id));

        employe.setStatus(newStatus);
        return toResponse(employe);
    }

    public List<EmployeResponse> listAll() {
        return employeRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    public List<EmployeResponse> listByStatus(StatutEmploye status) {
        return employeRepository.findByStatus(status).stream()
                .map(this::toResponse)
                .toList();
    }

    public List<EmployeResponse> search(String query) {
        return employeRepository
                .findByLastNameContainingIgnoreCaseOrFirstNameContainingIgnoreCase(query, query)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public EmployeResponse getById(Long id) {
        Employe employe = employeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Employé introuvable : " + id));
        return toResponse(employe);
    }

    private EmployeResponse toResponse(Employe employe) {
        return new EmployeResponse(
                employe.getId(),
                employe.getMatricule(),
                employe.getFirstName(),
                employe.getLastName(),
                employe.getFullName(),
                employe.getPhone(),
                employe.getFunction().name(),
                employe.getContractType().name(),
                employe.getBaseSalary(),
                employe.getHireDate(),
                employe.getStatus().name()
        );
    }

    public EmployeResponse getByMatricule(String matricule) {
        Employe employe = employeRepository.findByMatricule(matricule)
                .orElseThrow(() -> new IllegalArgumentException("Aucun employé trouvé avec le matricule : " + matricule));
        return toResponse(employe);
    }

    @Audited(action = "UPDATE", entityType = "Employe")
    @Transactional
    public EmployeResponse update(Long id, UpdateEmployeRequest dto) {
        Employe employe = employeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Employé introuvable : " + id));

        employe.setFirstName(dto.firstName());
        employe.setLastName(dto.lastName());
        employe.setPhone(dto.phone());
        employe.setFunction(dto.function());
        employe.setContractType(dto.contractType());
        employe.setBaseSalary(dto.baseSalary());

        return toResponse(employe);
    }

    @Audited(action = "PRINT_CARD", entityType = "Employe")
    @Transactional
    public PrintActionResponse recordCardPrint(Long id) {
        Employe employe = employeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Employé introuvable : " + id));
        return new PrintActionResponse(employe.getId(), "Impression de la carte enregistrée pour " + employe.getFullName());
    }

    @Audited(action = "PRINT_CERTIFICATE", entityType = "Employe")
    @Transactional
    public PrintActionResponse recordCertificatePrint(Long id) {
        Employe employe = employeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Employé introuvable : " + id));
        return new PrintActionResponse(employe.getId(), "Impression du certificat enregistrée pour " + employe.getFullName());
    }
    public EmployeStatsResponse getStats(PaiementSalaireService paiementSalaireService) {
        LocalDate now = LocalDate.now();

        long actifs = employeRepository.findByStatus(StatutEmploye.ACTIF).size();
        long conge = employeRepository.findByStatus(StatutEmploye.EN_CONGE).size();
        long anciens = employeRepository.findByStatus(StatutEmploye.ANCIEN).size();

        List<SalaireSuiviResponse> suivi = paiementSalaireService.getSuiviMensuel(now.getYear(), now.getMonthValue(), "ALL");
        long payes = suivi.stream().filter(s -> s.statut().equals("PAYE")).count();
        long restants = suivi.stream().filter(s -> s.statut().equals("IMPAYE")).count();

        return new EmployeStatsResponse(actifs, conge, anciens, payes, restants);
    }
}
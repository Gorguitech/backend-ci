package com.daara.internatadmin.personnel;

import com.daara.internatadmin.common.api.PrintActionResponse;
import com.daara.internatadmin.common.audit.Audited;
import com.daara.internatadmin.common.error.DuplicateEntityException;
import com.daara.internatadmin.common.tx.MatriculeAllocator;
import com.daara.internatadmin.personnel.dto.CreateEmployeRequest;
import com.daara.internatadmin.personnel.dto.EmployeCountsResponse;
import com.daara.internatadmin.personnel.dto.EmployeResponse;
import com.daara.internatadmin.personnel.dto.UpdateEmployeRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;

@Service
public class EmployeService {

    static final String MATRICULE_PREFIX = "EMP";

    private final EmployeRepository employeRepository;
    private final MatriculeAllocator matriculeAllocator;

    public EmployeService(EmployeRepository employeRepository, MatriculeAllocator matriculeAllocator) {
        this.employeRepository = employeRepository;
        this.matriculeAllocator = matriculeAllocator;
    }

    /**
     * Creation : controle de doublon (meme prenom, nom et telephone, sauf confirmation), puis matricule
     * EMP-annee d'embauche-sequence attribue par {@link MatriculeAllocator} (nouvelle transaction par collision).
     */
    @Audited(action = "CREATE", entityType = "Employe")
    public EmployeResponse create(CreateEmployeRequest dto, boolean confirmDuplicate) {
        if (!confirmDuplicate && employeRepository.existsByFirstNameIgnoreCaseAndLastNameIgnoreCaseAndPhone(
                dto.firstName(), dto.lastName(), dto.phone())) {
            // message repris tel quel du monolithe, espace final compris : le frontend l'affiche
            throw new DuplicateEntityException("Un employé avec le même nom, prénom et téléphone existe déjà. ");
        }

        return matriculeAllocator.allocate(MATRICULE_PREFIX, dto.hireDate().getYear(),
                employeRepository::countByMatriculeStartingWith, matricule -> {
            Employe employe = new Employe();
            employe.setMatricule(matricule);
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

    @Audited(action = "UPDATE", entityType = "Employe")
    @Transactional
    public EmployeResponse update(Long id, UpdateEmployeRequest dto) {
        Employe employe = findEmploye(id);
        employe.setFirstName(dto.firstName());
        employe.setLastName(dto.lastName());
        employe.setPhone(dto.phone());
        employe.setFunction(dto.function());
        employe.setContractType(dto.contractType());
        employe.setBaseSalary(dto.baseSalary());
        return toResponse(employe);
    }

    @Audited(action = "UPDATE_STATUS", entityType = "Employe")
    @Transactional
    public EmployeResponse updateStatus(Long id, StatutEmploye newStatus) {
        Employe employe = findEmploye(id);
        employe.setStatus(newStatus);
        return toResponse(employe);
    }

    @Audited(action = "PRINT_CARD", entityType = "Employe")
    @Transactional(readOnly = true)
    public PrintActionResponse recordCardPrint(Long id) {
        Employe employe = findEmploye(id);
        return new PrintActionResponse(employe.getId(), "Impression de la carte enregistrée pour " + employe.getFullName());
    }

    @Audited(action = "PRINT_CERTIFICATE", entityType = "Employe")
    @Transactional(readOnly = true)
    public PrintActionResponse recordCertificatePrint(Long id) {
        Employe employe = findEmploye(id);
        return new PrintActionResponse(employe.getId(), "Impression du certificat enregistrée pour " + employe.getFullName());
    }

    @Transactional(readOnly = true)
    public List<EmployeResponse> listAll() {
        return employeRepository.findAll().stream().map(EmployeService::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<EmployeResponse> listByStatus(StatutEmploye status) {
        return employeRepository.findByStatus(status).stream().map(EmployeService::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<EmployeResponse> listByStatuses(Collection<StatutEmploye> statuses) {
        return employeRepository.findByStatusIn(statuses).stream().map(EmployeService::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<EmployeResponse> search(String query) {
        return employeRepository.findByLastNameContainingIgnoreCaseOrFirstNameContainingIgnoreCase(query, query)
                .stream().map(EmployeService::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public EmployeResponse getById(Long id) {
        return toResponse(findEmploye(id));
    }

    @Transactional(readOnly = true)
    public EmployeResponse getByMatricule(String matricule) {
        Employe employe = employeRepository.findByMatricule(matricule)
                .orElseThrow(() -> new IllegalArgumentException("Aucun employé trouvé avec le matricule : " + matricule));
        return toResponse(employe);
    }

    /** Effectifs par statut (statistiques calculees par pilotage-service). */
    @Transactional(readOnly = true)
    public EmployeCountsResponse counts() {
        return new EmployeCountsResponse(
                employeRepository.countByStatus(StatutEmploye.ACTIF),
                employeRepository.countByStatus(StatutEmploye.EN_CONGE),
                employeRepository.countByStatus(StatutEmploye.ANCIEN));
    }

    private Employe findEmploye(Long id) {
        return employeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Employé introuvable : " + id));
    }

    static EmployeResponse toResponse(Employe employe) {
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
                employe.getStatus().name());
    }
}

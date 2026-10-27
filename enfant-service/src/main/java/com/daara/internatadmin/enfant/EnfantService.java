package com.daara.internatadmin.enfant;

import com.daara.internatadmin.common.api.PrintActionResponse;
import com.daara.internatadmin.common.audit.Audited;
import com.daara.internatadmin.common.error.DuplicateEntityException;
import com.daara.internatadmin.common.tx.TransactionalRetry;
import com.daara.internatadmin.common.util.MatriculeGenerator;
import com.daara.internatadmin.enfant.dto.CreateEnfantRequest;
import com.daara.internatadmin.enfant.dto.EnfantCountsResponse;
import com.daara.internatadmin.enfant.dto.EnfantResponse;
import com.daara.internatadmin.enfant.dto.UpdateEnfantRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Collection;
import java.util.List;

@Service
public class EnfantService {

    static final int MAX_ATTEMPTS = 5;
    static final String MATRICULE_PREFIX = "ENF";
    static final String BIRTH_INFO_REQUIRED = "La date de naissance ou l'âge approximatif doit être renseigné";

    private final EnfantRepository enfantRepository;
    private final TransactionalRetry transactionalRetry;

    public EnfantService(EnfantRepository enfantRepository, TransactionalRetry transactionalRetry) {
        this.enfantRepository = enfantRepository;
        this.transactionalRetry = transactionalRetry;
    }

    /**
     * Creation d'une fiche : controle de doublon (meme prenom, nom et telephone du tuteur, sauf confirmation),
     * puis matricule ENF-annee d'entree-sequence. En cas de collision sur le matricule, la sequence suivante est
     * essayee dans une nouvelle transaction (correction du retry inoperant du monolithe) ; apres 5 essais : 500.
     */
    @Audited(action = "CREATE", entityType = "Enfant")
    public EnfantResponse create(CreateEnfantRequest dto, boolean confirmDuplicate) {
        requireBirthInfo(dto.birthDate(), dto.approximateAge());

        if (!confirmDuplicate && enfantRepository.existsByFirstNameIgnoreCaseAndLastNameIgnoreCaseAndGuardianPhone(
                dto.firstName(), dto.lastName(), dto.guardianPhone())) {
            throw new DuplicateEntityException(
                    "Un enfant avec le même nom, prénom et téléphone de tuteur existe déjà. "
                            + "Si c'est intentionnel, renvoyez la requête avec confirmDuplicate=true.");
        }

        int year = dto.entryDate().getYear();
        String prefix = MatriculeGenerator.yearPrefix(MATRICULE_PREFIX, year);

        return transactionalRetry.onUniqueViolation(MAX_ATTEMPTS, "matricule", attempt -> {
            long sequence = enfantRepository.countByMatriculeStartingWith(prefix) + 1 + attempt;

            Enfant enfant = new Enfant();
            enfant.setMatricule(MatriculeGenerator.format(MATRICULE_PREFIX, year, sequence));
            enfant.setFirstName(dto.firstName());
            enfant.setLastName(dto.lastName());
            enfant.setBirthDate(dto.birthDate());
            enfant.setApproximateAge(dto.approximateAge());
            enfant.setGender(dto.gender());
            enfant.setGuardianName(dto.guardianName());
            enfant.setGuardianPhone(dto.guardianPhone());
            enfant.setNeighborhood(dto.neighborhood());
            enfant.setEntryDate(dto.entryDate());
            enfant.setRoomGroup(dto.roomGroup());
            enfant.setRegistrationFee(dto.registrationFee());
            enfant.setMonthlyFee(dto.monthlyFee());

            enfantRepository.saveAndFlush(enfant);
            return toResponse(enfant);
        });
    }

    @Audited(action = "UPDATE", entityType = "Enfant")
    @Transactional
    public EnfantResponse update(Long id, UpdateEnfantRequest dto) {
        requireBirthInfo(dto.birthDate(), dto.approximateAge());
        Enfant enfant = findEnfant(id);

        enfant.setFirstName(dto.firstName());
        enfant.setLastName(dto.lastName());
        enfant.setBirthDate(dto.birthDate());
        enfant.setApproximateAge(dto.approximateAge());
        enfant.setGender(dto.gender());
        enfant.setGuardianName(dto.guardianName());
        enfant.setGuardianPhone(dto.guardianPhone());
        enfant.setNeighborhood(dto.neighborhood());
        enfant.setRoomGroup(dto.roomGroup());
        enfant.setRegistrationFee(dto.registrationFee());
        enfant.setMonthlyFee(dto.monthlyFee());
        return toResponse(enfant);
    }

    // Action d'audit corrigee : UPDATE_STATUS (le monolithe enregistrait CREATE par erreur).
    @Audited(action = "UPDATE_STATUS", entityType = "Enfant")
    @Transactional
    public EnfantResponse updateStatus(Long id, StatutEnfant newStatus) {
        Enfant enfant = findEnfant(id);
        enfant.setStatus(newStatus);
        return toResponse(enfant);
    }

    @Audited(action = "PRINT_CARD", entityType = "Enfant")
    @Transactional(readOnly = true)
    public PrintActionResponse recordCardPrint(Long id) {
        Enfant enfant = findEnfant(id);
        return new PrintActionResponse(enfant.getId(), "Impression de la carte enregistrée pour " + enfant.getFullName());
    }

    @Audited(action = "PRINT_CERTIFICATE", entityType = "Enfant")
    @Transactional(readOnly = true)
    public PrintActionResponse recordCertificatePrint(Long id) {
        Enfant enfant = findEnfant(id);
        return new PrintActionResponse(enfant.getId(), "Impression du certificat enregistrée pour " + enfant.getFullName());
    }

    @Transactional(readOnly = true)
    public List<EnfantResponse> listAll() {
        return enfantRepository.findAll().stream().map(EnfantService::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<EnfantResponse> listByStatus(StatutEnfant status) {
        return enfantRepository.findByStatus(status).stream().map(EnfantService::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<EnfantResponse> listByStatuses(Collection<StatutEnfant> statuses) {
        return enfantRepository.findByStatusIn(statuses).stream().map(EnfantService::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<EnfantResponse> search(String query) {
        return enfantRepository.findByLastNameContainingIgnoreCaseOrFirstNameContainingIgnoreCase(query, query)
                .stream().map(EnfantService::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public EnfantResponse getById(Long id) {
        return toResponse(findEnfant(id));
    }

    @Transactional(readOnly = true)
    public EnfantResponse getByMatricule(String matricule) {
        Enfant enfant = enfantRepository.findByMatricule(matricule)
                .orElseThrow(() -> new IllegalArgumentException("Aucun enfant trouvé avec le matricule : " + matricule));
        return toResponse(enfant);
    }

    /** Effectifs par statut et entrees du mois demande (statistiques calculees par pilotage-service). */
    @Transactional(readOnly = true)
    public EnfantCountsResponse counts(YearMonth month) {
        return new EnfantCountsResponse(
                enfantRepository.countByStatus(StatutEnfant.ACTIF),
                enfantRepository.countByStatus(StatutEnfant.SORTI),
                enfantRepository.countByStatus(StatutEnfant.EN_VACANCES),
                enfantRepository.countByEntryDateBetween(month.atDay(1), month.atEndOfMonth()));
    }

    private Enfant findEnfant(Long id) {
        return enfantRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Enfant introuvable : " + id));
    }

    private static void requireBirthInfo(LocalDate birthDate, Integer approximateAge) {
        if (birthDate == null && approximateAge == null) {
            throw new IllegalArgumentException(BIRTH_INFO_REQUIRED);
        }
    }

    static EnfantResponse toResponse(Enfant enfant) {
        return new EnfantResponse(
                enfant.getId(),
                enfant.getMatricule(),
                enfant.getFirstName(),
                enfant.getLastName(),
                enfant.getFullName(),
                enfant.getBirthDate(),
                enfant.getApproximateAge(),
                enfant.getGender(),
                enfant.getGuardianName(),
                enfant.getGuardianPhone(),
                enfant.getNeighborhood(),
                enfant.getEntryDate(),
                enfant.getStatus().name(),
                enfant.getRoomGroup(),
                enfant.getRegistrationFee(),
                enfant.getMonthlyFee());
    }
}

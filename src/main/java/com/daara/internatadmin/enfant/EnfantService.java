package com.daara.internatadmin.enfant;

import com.daara.internatadmin.audit.Audited;
import com.daara.internatadmin.common.DuplicateEntityException;
import com.daara.internatadmin.common.MatriculeGenerator;
import com.daara.internatadmin.common.PrintActionResponse;
import com.daara.internatadmin.common.TransactionalRetry;
import com.daara.internatadmin.enfant.dto.CreateEnfantRequest;
import com.daara.internatadmin.enfant.dto.EnfantResponse;
import com.daara.internatadmin.enfant.dto.EnfantStatsResponse;
import com.daara.internatadmin.enfant.dto.UpdateEnfantRequest;
import com.daara.internatadmin.finance.paiementenfant.PaiementEnfantService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class EnfantService {

    private final EnfantRepository enfantRepository;
    private final TransactionalRetry transactionalRetry;
    private static final int MAX_ATTEMPTS = 5;

    @Audited(action = "CREATE", entityType = "Enfant")
    public EnfantResponse create(CreateEnfantRequest dto, boolean confirmDuplicate) {
        if (dto.birthDate() == null && dto.approximateAge() == null) {
            throw new IllegalArgumentException(
                    "La date de naissance ou l'âge approximatif doit être renseigné"
            );
        }

        if (!confirmDuplicate && enfantRepository.existsByFirstNameIgnoreCaseAndLastNameIgnoreCaseAndGuardianPhone(
                dto.firstName(), dto.lastName(), dto.guardianPhone())) {
            throw new DuplicateEntityException(
                    "Un enfant avec le même nom, prénom et téléphone de tuteur existe déjà. " +
                            "Si c'est intentionnel, renvoyez la requête avec confirmDuplicate=true."
            );
        }

        int year = dto.entryDate().getYear();
        String prefix = "ENF-" + year;

        // matricule déjà pris entre-temps : on tente la séquence suivante, dans une nouvelle transaction
        return transactionalRetry.onUniqueViolation(MAX_ATTEMPTS, "matricule", attempt -> {
            long sequence = enfantRepository.countByMatriculeStartingWith(prefix) + 1 + attempt;

            Enfant enfant = new Enfant();
            enfant.setMatricule(MatriculeGenerator.format("ENF", year, sequence));
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

    public List<EnfantResponse> listAll() {
        return enfantRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    public List<EnfantResponse> listByStatus(StatutEnfant status) {
        return enfantRepository.findByStatus(status).stream()
                .map(this::toResponse)
                .toList();
    }

    public List<EnfantResponse> search(String query) {
        return enfantRepository
                .findByLastNameContainingIgnoreCaseOrFirstNameContainingIgnoreCase(query, query)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public EnfantResponse getById(Long id) {
        Enfant enfant = enfantRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Enfant introuvable : " + id));
        return toResponse(enfant);
    }

    private EnfantResponse toResponse(Enfant enfant) {
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
                enfant.getMonthlyFee()
        );

    }
    @Audited(action = "UPDATE_STATUS", entityType = "Enfant")
    @Transactional
    public EnfantResponse updateStatus(Long id, StatutEnfant newStatus) {
        Enfant enfant = enfantRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Enfant introuvable : " + id));

        enfant.setStatus(newStatus);
        // dirty checking : pas de save() explicite nécessaire

        return toResponse(enfant);
    }

    @Audited(action = "PRINT_CARD", entityType = "Enfant")
    @Transactional
    public PrintActionResponse recordCardPrint(Long id) {
        Enfant enfant = enfantRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Enfant introuvable : " + id));
        return new PrintActionResponse(enfant.getId(), "Impression de la carte enregistrée pour " + enfant.getFullName());
    }

    @Audited(action = "PRINT_CERTIFICATE", entityType = "Enfant")
    @Transactional
    public PrintActionResponse recordCertificatePrint(Long id) {
        Enfant enfant = enfantRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Enfant introuvable : " + id));
        return new PrintActionResponse(enfant.getId(), "Impression du certificat enregistrée pour " + enfant.getFullName());
    }

    public EnfantResponse getByMatricule(String matricule) {
        Enfant enfant = enfantRepository.findByMatricule(matricule)
                .orElseThrow(() -> new IllegalArgumentException("Aucun enfant trouvé avec le matricule : " + matricule));
        return toResponse(enfant);
    }
    @Audited(action = "UPDATE", entityType = "Enfant")
    @Transactional
    public EnfantResponse update(Long id, UpdateEnfantRequest dto) {
        if (dto.birthDate() == null && dto.approximateAge() == null) {
            throw new IllegalArgumentException(
                    "La date de naissance ou l'âge approximatif doit être renseigné"
            );
        }

        Enfant enfant = enfantRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Enfant introuvable : " + id));

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
        // dirty checking : pas de save() explicite nécessaire

        return toResponse(enfant);
    }
    public EnfantStatsResponse getStats(PaiementEnfantService paiementEnfantService) {
        LocalDate now = LocalDate.now();
        LocalDate debutMois = now.withDayOfMonth(1);
        LocalDate finMois = now.withDayOfMonth(now.lengthOfMonth());

        long actifs = enfantRepository.findByStatus(StatutEnfant.ACTIF).size();
        long sortis = enfantRepository.findByStatus(StatutEnfant.SORTI).size();
        long vacances = enfantRepository.findByStatus(StatutEnfant.EN_VACANCES).size();
        long nouveaux = enfantRepository.countByEntryDateBetween(debutMois, finMois);

        long impayes = paiementEnfantService.getSuiviMensuel(now.getYear(), now.getMonthValue(), "ALL").stream()
                .filter(s -> !s.statutPaiement().equals("PAYE"))
                .count();

        return new EnfantStatsResponse(actifs, sortis, vacances, nouveaux, impayes);
    }
}
package com.daara.internatadmin.enfant;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface EnfantRepository extends JpaRepository<Enfant, Long> {

    List<Enfant> findByStatus(StatutEnfant status);
    long countByMatriculeStartingWith(String prefix);


    List<Enfant> findByLastNameContainingIgnoreCaseOrFirstNameContainingIgnoreCase(
            String lastName, String firstName
    );

    boolean existsByFirstNameIgnoreCaseAndLastNameIgnoreCaseAndGuardianPhone(
            String firstName, String lastName, String guardianPhone
    );
    Optional<Enfant> findByMatricule(String matricule);
    long countByEntryDateBetween(LocalDate start, LocalDate end);
    List<Enfant> findByStatusIn(List<StatutEnfant> statuses);
}
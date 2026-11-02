package com.daara.internatadmin.personnel;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EmployeRepository extends JpaRepository<Employe, Long> {
    List<Employe> findByStatus(StatutEmploye status);
    List<Employe> findByLastNameContainingIgnoreCaseOrFirstNameContainingIgnoreCase(
            String lastName, String firstName
    );
    long countByMatriculeStartingWith(String prefix);

    Optional<Employe> findByMatricule(String matricule);

    boolean existsByFirstNameIgnoreCaseAndLastNameIgnoreCaseAndPhone(
            String firstName, String lastName, String phone
    );

    List<Employe> findByStatusIn(List<StatutEmploye> statuses);
}
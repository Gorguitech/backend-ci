package com.daara.internatadmin.personnel;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface EmployeRepository extends JpaRepository<Employe, Long> {

    List<Employe> findByStatus(StatutEmploye status);

    List<Employe> findByStatusIn(Collection<StatutEmploye> statuses);

    long countByStatus(StatutEmploye status);

    long countByMatriculeStartingWith(String prefix);

    List<Employe> findByLastNameContainingIgnoreCaseOrFirstNameContainingIgnoreCase(String lastName, String firstName);

    boolean existsByFirstNameIgnoreCaseAndLastNameIgnoreCaseAndPhone(String firstName, String lastName, String phone);

    Optional<Employe> findByMatricule(String matricule);
}

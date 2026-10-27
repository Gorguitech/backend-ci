package com.daara.internatadmin.auth.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);

    boolean existsByRole(Role role);

    long countByActive(boolean active);

    long countByRole(Role role);

    long countByMustChangePassword(boolean mustChangePassword);
}

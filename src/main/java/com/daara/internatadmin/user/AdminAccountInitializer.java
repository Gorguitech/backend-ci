package com.daara.internatadmin.user;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * Cree le compte admin (System Admin, admin, ADMIN, changement de mot de passe obligatoire) au premier demarrage,
 * quand aucun compte ADMIN n'existe. Le hash vient de ADMIN_INITIAL_PASSWORD_HASH : aucun mot de passe ni hash
 * n'est versionne. Hash absent ou invalide a ce moment : le demarrage echoue.
 */
@Slf4j
@Component
public class AdminAccountInitializer implements ApplicationRunner {

    private static final Pattern BCRYPT = Pattern.compile("^\\$2[aby]?\\$\\d{2}\\$[./A-Za-z0-9]{53}$");

    private final UserRepository userRepository;
    private final String initialPasswordHash;

    public AdminAccountInitializer(UserRepository userRepository,
                                   @Value("${app.admin.initial-password-hash:}") String initialPasswordHash) {
        this.userRepository = userRepository;
        this.initialPasswordHash = initialPasswordHash;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (userRepository.countByRole(Role.ADMIN) > 0) {
            return;
        }

        if (!BCRYPT.matcher(initialPasswordHash).matches()) {
            throw new IllegalStateException("Aucun compte ADMIN : ADMIN_INITIAL_PASSWORD_HASH doit contenir un hash BCrypt "
                    + "valide (voir scripts/hash-admin-password.sh, valeur entre apostrophes dans .env)");
        }

        User admin = new User();
        admin.setLastName("System");
        admin.setFirstName("Admin");
        admin.setUsername("admin");
        admin.setPasswordHash(initialPasswordHash);
        admin.setRole(Role.ADMIN);
        admin.setActive(true);
        admin.setMustChangePassword(true);
        try {
            userRepository.saveAndFlush(admin);
            log.info("Compte admin initial cree (changement de mot de passe exige a la premiere connexion)");
        } catch (DataIntegrityViolationException e) {
            // une autre instance l'a cree au meme moment
            log.info("Compte admin initial deja cree par une autre instance");
        }
    }
}

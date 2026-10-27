package com.daara.internatadmin.auth.config;

import com.daara.internatadmin.auth.user.Role;
import com.daara.internatadmin.auth.user.User;
import com.daara.internatadmin.auth.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * Cree le compte admin du monolithe (System Admin, admin, ADMIN, changement de mot de passe obligatoire)
 * au premier demarrage, quand aucun compte ADMIN n'existe. Le hash vient de ADMIN_INITIAL_PASSWORD_HASH :
 * aucun mot de passe ni hash n'est versionne. Hash absent ou invalide a ce moment : le demarrage echoue.
 */
@Component
public class AdminAccountInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminAccountInitializer.class);
    static final Pattern BCRYPT = Pattern.compile("^\\$2[aby]?\\$\\d{2}\\$[./A-Za-z0-9]{53}$");

    private final UserRepository userRepository;
    private final AdminAccountProperties properties;

    public AdminAccountInitializer(UserRepository userRepository, AdminAccountProperties properties) {
        this.userRepository = userRepository;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (userRepository.existsByRole(Role.ADMIN)) {
            return;
        }

        String hash = properties.initialPasswordHash();
        if (hash == null || !BCRYPT.matcher(hash).matches()) {
            throw new IllegalStateException("Aucun compte ADMIN : ADMIN_INITIAL_PASSWORD_HASH doit contenir un hash BCrypt "
                    + "valide (voir scripts/hash-admin-password.sh, valeur entre apostrophes dans .env)");
        }

        User admin = new User();
        admin.setLastName("System");
        admin.setFirstName("Admin");
        admin.setUsername("admin");
        admin.setPasswordHash(hash);
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

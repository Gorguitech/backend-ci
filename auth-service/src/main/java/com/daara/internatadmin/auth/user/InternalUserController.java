package com.daara.internatadmin.auth.user;

import com.daara.internatadmin.auth.user.dto.UserStateResponse;
import com.daara.internatadmin.auth.user.dto.UserStatsResponse;
import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Endpoints inter-services (X-Internal-Key exige, jamais routes par la gateway). */
@Hidden
@RestController
@RequestMapping("/internal/users")
public class InternalUserController {

    private final UserService userService;

    public InternalUserController(UserService userService) {
        this.userService = userService;
    }

    /** Lu par la gateway a chaque requete : blocage immediat d'un compte desactive. */
    @GetMapping("/{id}/state")
    public UserStateResponse state(@PathVariable Long id) {
        return userService.getState(id);
    }

    /** Lu par pilotage-service (notification COMPTES_DESACTIVES). */
    @GetMapping("/stats")
    public UserStatsResponse stats() {
        return userService.getStats();
    }
}

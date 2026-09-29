package com.devtunde.authservice.service;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import com.devtunde.authservice.config.AdminBootstrapProperties;
import com.devtunde.authservice.model.User;

@Component
public class AdminBootstrapRunner implements ApplicationRunner {

    public static final String ADMIN_ROLE = "ROLE_ADMIN";

    private final AdminBootstrapProperties props;
    private final UserService userService;
    private final AuthAudit authAudit;

    public AdminBootstrapRunner(AdminBootstrapProperties props, UserService userService, AuthAudit authAudit) {
        this.props = props;
        this.userService = userService;
        this.authAudit = authAudit;
    }

    @Override
    public void run(ApplicationArguments args) {

        if (props.email() == null
                || props.email().isBlank()
                || props.password() == null
                || props.password().isBlank()) {
            return;
        }

        if (userService.existsByRole(ADMIN_ROLE)) {
            return;
        }

        if (userService.findByEmail(props.email()).isPresent()) {
            authAudit.log("admin_bootstrap_skipped", props.email(), "email_already_registered", "failure");
            return;
        }

        User admin = userService.create(props.email(), props.password(), ADMIN_ROLE);

        authAudit.log("admin_bootstrapped", admin.getId(), "env_configured", "success");
    }
}

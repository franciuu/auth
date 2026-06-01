package com.example.auth.config;

import com.example.auth.entity.AuditEventType;
import com.example.auth.entity.Role;
import com.example.auth.entity.Severity;
import com.example.auth.entity.User;
import com.example.auth.repository.UserRepository;
import com.example.auth.service.AuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Seeds an initial ADMIN account on startup if one does not already exist, so
 * the RBAC flow can be exercised out of the box. Credentials are configurable.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    @Value("${app.admin.email:admin@test.com}")
    private String adminEmail;

    @Value("${app.admin.password:AdminPass123!}")
    private String adminPassword;

    @Value("${app.admin.seed-enabled:true}")
    private boolean seedEnabled;

    @Override
    public void run(String... args) {
        if (!seedEnabled) {
            return;
        }
        String email = adminEmail.trim().toLowerCase();
        if (userRepository.existsByEmail(email)) {
            return;
        }
        User admin = User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(adminPassword))
                .fullName("System Administrator")
                .emailVerified(true)
                .active(true)
                .roles(Set.of(Role.ROLE_ADMIN, Role.ROLE_USER))
                .build();
        User saved = userRepository.save(admin);
        auditService.record(AuditEventType.USER_REGISTERED, saved.getId(), Severity.INFO,
                "Seed admin account created");
        log.info("Seeded admin account: {}", email);
    }
}

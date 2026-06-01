package com.example.auth.service;

import com.example.auth.dto.AdminUserResponse;
import com.example.auth.dto.UserProfileResponse;
import com.example.auth.exception.AuthException;
import com.example.auth.model.AuditEventType;
import com.example.auth.model.Severity;
import com.example.auth.model.User;
import com.example.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public UserProfileResponse getProfile(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new AuthException(HttpStatus.NOT_FOUND, "User not found"));
        return toProfile(user);
    }

    @Transactional(readOnly = true)
    public List<AdminUserResponse> listAllUsers() {
        return userRepository.findAll().stream()
                .map(this::toAdminUser)
                .toList();
    }

    @Transactional
    public void disableUser(Long userId, String adminEmail) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AuthException(HttpStatus.NOT_FOUND, "User not found"));

        user.setActive(false);
        // Force the disabled user to be logged out everywhere.
        user.setRefreshToken(null);
        userRepository.save(user);

        auditService.record(AuditEventType.ADMIN_DISABLED_USER, user.getId(), Severity.CRITICAL,
                "User disabled by admin '" + adminEmail + "'");
    }

    // --- mapping: model (DB table) -> response DTO ----------------------

    private UserProfileResponse toProfile(User user) {
        return new UserProfileResponse(
                user.getId(), user.getEmail(), user.getFullName(), user.getRoles());
    }

    private AdminUserResponse toAdminUser(User user) {
        return new AdminUserResponse(
                user.getId(), user.getEmail(), user.getFullName(),
                user.getRoles(), user.isActive(), user.getCreatedAt());
    }
}

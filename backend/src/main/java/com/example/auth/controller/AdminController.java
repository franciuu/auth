package com.example.auth.controller;

import com.example.auth.dto.AdminUserResponse;
import com.example.auth.dto.MessageResponse;
import com.example.auth.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin", description = "Administrative endpoints (ROLE_ADMIN only)")
public class AdminController {

    private final UserService userService;

    @Operation(summary = "List all users (admin action: LIST_USERS)")
    @GetMapping("/users")
    public List<AdminUserResponse> listUsers() {
        return userService.listAllUsers();
    }

    @Operation(summary = "Disable a user account")
    @PostMapping("/disable-user/{userId}")
    public MessageResponse disableUser(@PathVariable Long userId,
                                       @AuthenticationPrincipal String adminEmail) {
        userService.disableUser(userId, adminEmail);
        return MessageResponse.ok("User disabled");
    }
}

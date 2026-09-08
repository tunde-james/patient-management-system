package com.devtunde.authservice.controller;

import java.net.URI;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.devtunde.authservice.dto.AdminCreateUserReqDto;
import com.devtunde.authservice.dto.RegisterResDto;
import com.devtunde.authservice.exception.UserNotFoundException;
import com.devtunde.authservice.mapper.UserMapper;
import com.devtunde.authservice.model.User;
import com.devtunde.authservice.service.AuthAudit;
import com.devtunde.authservice.service.RefreshTokenService;
import com.devtunde.authservice.service.UserService;

@RestController
@RequestMapping("/api/v1/admin/users")
public class AdminUserController {

    private final UserService userService;
    private final RefreshTokenService refreshTokenService;
    private final AuthAudit authAudit;

    public AdminUserController(UserService userService, RefreshTokenService refreshTokenService, AuthAudit authAudit) {
        this.userService = userService;
        this.refreshTokenService = refreshTokenService;
        this.authAudit = authAudit;
    }

    @PostMapping
    public ResponseEntity<RegisterResDto> createUser(@Valid @RequestBody AdminCreateUserReqDto reqDto) {

        User created = userService.create(reqDto.email(), reqDto.password(), reqDto.role());
        authAudit.log("admin_created_user", callerId(), "role=" + reqDto.role(), "success");

        return ResponseEntity.created(URI.create("/api/v1/users/" + created.getId()))
                .body(UserMapper.toDTO(created));
    }

    @PutMapping("/{id}/unlock")
    public ResponseEntity<Void> unlock(@PathVariable UUID id) {

        userService.unlock(id);
        authAudit.log("admin_unlocked_user", callerId(), "target=" + id, "success");

        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/sessions/revoke")
    public ResponseEntity<Void> revokeSessions(@PathVariable UUID id) {

        userService.findById(id).orElseThrow(() -> new UserNotFoundException("User not found"));

        refreshTokenService.revokeAllSessions(id);
        authAudit.log("admin_revoked_sessions", callerId(), "target=" + id, "success");

        return ResponseEntity.ok().build();
    }

    private String callerId() {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        return auth == null ? "unknown" : String.valueOf(auth.getPrincipal());
    }
}

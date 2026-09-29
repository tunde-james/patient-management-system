package com.devtunde.authservice.service;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.devtunde.authservice.exception.UserNotFoundException;
import com.devtunde.authservice.model.User;
import com.devtunde.authservice.repository.UserRepository;
import com.devtunde.common.exception.EmailAlreadyExistsException;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public User create(String email, String rawPassword) {

        return create(email, rawPassword, "ROLE_USER");
    }

    @Transactional
    public User create(String email, String rawPassword, String role) {

        String normalized = normalizedEmail(email);

        if (userRepository.findByEmail(normalized).isPresent()) {
            throw new EmailAlreadyExistsException("Email already registered");
        }

        User user = new User(normalized, passwordEncoder.encode(rawPassword));
        user.setRole(role);

        try {
            return userRepository.saveAndFlush(user);

        } catch (DataIntegrityViolationException ex) {
            throw new EmailAlreadyExistsException("Email already registered");
        }
    }

    public Optional<User> findByEmailForUpdate(String email) {

        return userRepository.findByEmailForUpdate(normalizedEmail(email));
    }

    public Optional<User> findByEmail(String email) {

        return userRepository.findByEmail(normalizedEmail(email));
    }

    public Optional<User> findById(UUID id) {

        return userRepository.findById(id);
    }

    public boolean existsByRole(String role) {

        return userRepository.existsByRole(role);
    }

    @Transactional
    public void unlock(UUID userId) {

        User user = userRepository.findById(userId).orElseThrow(() -> new UserNotFoundException("User not found"));

        user.setLockedUntil(null);
        user.setFailedLoginCount(0);
        user.setFailedWindowStartedAt(null);

        userRepository.save(user);
    }

    @Transactional
    public void updatePassword(UUID userId, String rawPassword) {

        User user = userRepository.findById(userId).orElseThrow(() -> new UserNotFoundException("User not found"));

        user.setPasswordHash(passwordEncoder.encode(rawPassword));

        userRepository.save(user);
    }

    private static String normalizedEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}

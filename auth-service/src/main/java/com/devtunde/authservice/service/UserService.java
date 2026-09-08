package com.devtunde.authservice.service;

import java.util.Optional;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

        if (userRepository.findByEmail(email).isPresent()) {
            throw new EmailAlreadyExistsException("Email already registered");
        }

        User user = new User(email, passwordEncoder.encode(rawPassword));
        user.setRole(role);

        return userRepository.save(user);
    }

    public Optional<User> findByEmail(String email) {

        return userRepository.findByEmail(email);
    }

    public Optional<User> findById(UUID id) {

        return userRepository.findById(id);
    }

    public boolean existsByRole(String role) {

        return userRepository.existsByRole(role);
    }
}

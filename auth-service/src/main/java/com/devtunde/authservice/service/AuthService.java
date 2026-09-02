package com.devtunde.authservice.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.devtunde.authservice.dto.RegisterReqDto;
import com.devtunde.authservice.mapper.UserMapper;
import com.devtunde.authservice.model.User;
import com.devtunde.authservice.repository.UserRepository;
import com.devtunde.common.exception.EmailAlreadyExistsException;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public User register(RegisterReqDto reqDto) {

        if (userRepository.findByEmail(reqDto.email()).isPresent()) {
            throw new EmailAlreadyExistsException("Email already registered");
        }

        String hash = passwordEncoder.encode(reqDto.password());

        return userRepository.save(UserMapper.toModel(reqDto, hash));
    }
}

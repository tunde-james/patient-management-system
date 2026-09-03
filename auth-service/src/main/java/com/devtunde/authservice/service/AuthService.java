package com.devtunde.authservice.service;

import java.time.Instant;
import java.util.Optional;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.devtunde.authservice.config.LockoutProperties;
import com.devtunde.authservice.dto.LoginReqDto;
import com.devtunde.authservice.dto.RegisterReqDto;
import com.devtunde.authservice.exception.AccountDisabledException;
import com.devtunde.authservice.exception.AccountLockedException;
import com.devtunde.authservice.exception.InvalidCredentialsException;
import com.devtunde.authservice.mapper.UserMapper;
import com.devtunde.authservice.model.User;
import com.devtunde.authservice.repository.UserRepository;
import com.devtunde.common.exception.EmailAlreadyExistsException;
import io.micrometer.core.instrument.MeterRegistry;

@Service
public class AuthService {

    private static final Logger audit = LoggerFactory.getLogger("AUDIT");

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final MeterRegistry meterRegistry;
    private final LockoutProperties lockout;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            MeterRegistry meterRegistry,
            LockoutProperties lockout) {

        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.meterRegistry = meterRegistry;
        this.lockout = lockout;
    }

    @Transactional
    public User register(RegisterReqDto reqDto) {

        if (userRepository.findByEmail(reqDto.email()).isPresent()) {
            throw new EmailAlreadyExistsException("Email already registered");
        }

        String hash = passwordEncoder.encode(reqDto.password());

        return userRepository.save(UserMapper.toModel(reqDto, hash));
    }

    @Transactional(
            noRollbackFor = {
                InvalidCredentialsException.class,
                AccountDisabledException.class,
                AccountLockedException.class
            })
    public User login(LoginReqDto reqDto) {

        Instant now = Instant.now();

        Optional<User> optionalUser = userRepository.findByEmail(reqDto.email());

        if (optionalUser.isEmpty()) {
            loginMetric("failure");
            audit("login_failure", "unknown", "unknown_email", "failure");
            throw new InvalidCredentialsException("Invalid credentials");
        }

        User user = optionalUser.get();

        if (isLocked(user, now)) {
            loginMetric("locked");
            audit("login_failure", user.getId(), "account_locked", "locked");
            throw new AccountLockedException(
                    "Your account has been temporarily locked due to excessive failed attempts.");
        }

        if (!user.isEnabled()) {
            loginMetric("disabled");
            audit("login_failure", user.getId(), "account_disabled", "disabled");
            throw new AccountDisabledException("Account disabled");
        }

        if (!passwordEncoder.matches(reqDto.password(), user.getPasswordHash())) {

            boolean lockTriggered = recordFailure(user, now);
            loginMetric("failure");
            if (lockTriggered) {
                audit("account_locked", user.getId(), "max_failed_attempts", "locked");
            } else {
                audit("login_failure", user.getId(), "invalid_credentials", "failure");
            }
            throw new InvalidCredentialsException("Invalid credentials");
        }

        user.setFailedLoginCount(0);
        user.setLockedUntil(null);
        loginMetric("success");
        audit("login_success", user.getId(), "ok", "success");

        return user;
    }

    private boolean isLocked(User user, Instant now) {

        return user.getLockedUntil() != null && user.getLockedUntil().isAfter(now);
    }

    private boolean recordFailure(User user, Instant now) {

        Instant windowStart = user.getFailedWindowStartedAt();

        if (windowStart == null || windowStart.plus(lockout.quietWindow()).isBefore(now)) {
            user.setFailedLoginCount(0);
            user.setFailedWindowStartedAt(now);
        }

        user.setFailedLoginCount(user.getFailedLoginCount() + 1);

        if (user.getFailedLoginCount() >= lockout.maxAttempts()) {

            user.setLockedUntil(now.plus(lockout.lockDuration()));
            user.setFailedLoginCount(0);

            user.setFailedWindowStartedAt(null);

            meterRegistry.counter("auth_lockout_triggered_total").increment();
            return true;
        }

        return false;
    }

    private void loginMetric(String outcome) {

        meterRegistry.counter("auth_login_total", "outcome", outcome).increment();
    }

    private void audit(String event, Object userId, String reason, String outcome) {

        audit.info(
                "AUDIT event={} userId={} timestamp={} sourceIp={} outcome={} reason={}",
                event,
                userId,
                Instant.now(),
                sourceIp(),
                outcome,
                reason);
    }

    private String sourceIp() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();

        return attributes == null ? "unknown" : attributes.getRequest().getRemoteAddr();
    }
}

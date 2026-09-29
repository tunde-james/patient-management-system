package com.devtunde.authservice.service;

import java.time.Instant;
import java.util.Optional;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.devtunde.authservice.config.LockoutProperties;
import com.devtunde.authservice.dto.LoginReqDto;
import com.devtunde.authservice.dto.RegisterReqDto;
import com.devtunde.authservice.exception.AccountDisabledException;
import com.devtunde.authservice.exception.AccountLockedException;
import com.devtunde.authservice.exception.BreachedPasswordException;
import com.devtunde.authservice.exception.InvalidCredentialsException;
import com.devtunde.authservice.model.User;
import io.micrometer.core.instrument.MeterRegistry;

@Service
public class AuthService {

    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final MeterRegistry meterRegistry;
    private final LockoutProperties lockout;
    private final AuthAudit authAudit;
    private final BreachedPasswordChecker breachedPasswordChecker;

    public AuthService(
            UserService userService,
            PasswordEncoder passwordEncoder,
            MeterRegistry meterRegistry,
            LockoutProperties lockout,
            AuthAudit authAudit,
            BreachedPasswordChecker breachedPasswordChecker) {

        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
        this.meterRegistry = meterRegistry;
        this.lockout = lockout;
        this.authAudit = authAudit;
        this.breachedPasswordChecker = breachedPasswordChecker;
    }

    public User register(RegisterReqDto reqDto) {

        if (breachedPasswordChecker.isBreached(reqDto.password())) {
            throw new BreachedPasswordException("This password has appeared in a data breach; choose another.");
        }

        return userService.create(reqDto.email(), reqDto.password());
    }

    @Transactional(
            noRollbackFor = {
                InvalidCredentialsException.class,
                AccountDisabledException.class,
                AccountLockedException.class
            })
    public User login(LoginReqDto reqDto) {

        Instant now = Instant.now();

        Optional<User> optionalUser = userService.findByEmailForUpdate(reqDto.email());

        if (optionalUser.isEmpty()) {
            loginMetric("failure");
            authAudit.log("login_failure", "unknown", "unknown_email", "failure");
            throw new InvalidCredentialsException("Invalid credentials");
        }

        User user = optionalUser.get();

        if (isLocked(user, now)) {
            loginMetric("locked");
            authAudit.log("login_failure", user.getId(), "account_locked", "locked");
            throw new AccountLockedException(
                    "Your account has been temporarily locked due to excessive failed attempts.");
        }

        if (!user.isEnabled()) {
            loginMetric("disabled");
            authAudit.log("login_failure", user.getId(), "account_disabled", "failure");
            throw new AccountDisabledException("Account disabled");
        }

        if (!passwordEncoder.matches(reqDto.password(), user.getPasswordHash())) {

            boolean lockTriggered = recordFailure(user, now);
            loginMetric("failure");
            if (lockTriggered) {
                authAudit.log("account_locked", user.getId(), "max_failed_attempts", "locked");
            } else {
                authAudit.log("login_failure", user.getId(), "invalid_credentials", "failure");
            }
            throw new InvalidCredentialsException("Invalid credentials");
        }

        user.setFailedLoginCount(0);
        user.setLockedUntil(null);
        user.setFailedWindowStartedAt(null);
        loginMetric("success");
        authAudit.log("login_success", user.getId(), "ok", "success");

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
}

package com.devtunde.authservice.service;

import java.time.Instant;

import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class AuthAudit {

    private final Logger audit = LoggerFactory.getLogger("AUDIT");

    public void log(String event, Object userId, String reason, String outcome) {
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

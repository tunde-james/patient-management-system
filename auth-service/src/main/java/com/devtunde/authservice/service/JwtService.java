package com.devtunde.authservice.service;

import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.devtunde.authservice.config.JwtProperties;
import com.devtunde.authservice.model.User;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import io.jsonwebtoken.Jwts;

@Service
public class JwtService {

    public static final String KEY_ID = "auth-1";

    private final KeyPair keyPair;
    private final JwtProperties jwt;

    public JwtService(JwtProperties jwt) {
        this.jwt = jwt;

        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            this.keyPair = generator.generateKeyPair();
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("RSA keypair generator unavailable", ex);
        }
    }

    public String issue(User user) {

        Instant now = Instant.now();

        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(user.getId().toString())
                .claim("roles", List.of(user.getRole()))
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(jwt.accessTokenTtl())))
                .header()
                .add("kid", KEY_ID)
                .and()
                .signWith(keyPair.getPrivate(), Jwts.SIG.RS256)
                .compact();
    }

    public JWKSet jwks() {

        RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();

        return new JWKSet(new RSAKey.Builder(publicKey)
                .algorithm(JWSAlgorithm.RS256)
                .keyUse(KeyUse.SIGNATURE)
                .keyID(KEY_ID)
                .build());
    }

    public long accessTokenTtlSeconds() {

        return jwt.accessTokenTtl().toSeconds();
    }
}

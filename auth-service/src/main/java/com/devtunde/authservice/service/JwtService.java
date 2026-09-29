package com.devtunde.authservice.service;

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.devtunde.authservice.config.JwtProperties;
import com.devtunde.authservice.exception.InvalidAccessTokenException;
import com.devtunde.authservice.model.User;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;

@Service
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);

    private final KeyPair keyPair;
    private final String keyId;
    private final JwtProperties jwt;

    public JwtService(JwtProperties jwt) {
        this.jwt = jwt;

        if (jwt.privateKey() != null && !jwt.privateKey().isBlank()) {
            this.keyPair = loadKeyPair(jwt.privateKey());
        } else {
            this.keyPair = generateKeyPair();

            log.warn(
                    "auth.jwt.private-key is not set: using an ephemeral key pair - token will not survive a restart and cannot be verified by other instances");
        }

        this.keyId = keyThumbprint(this.keyPair.getPublic());
    }

    private static KeyPair loadKeyPair(String pem) {
        String base64 = pem.replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");

        try {
            PrivateKey privateKey = KeyFactory.getInstance("RSA")
                    .generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(base64)));

            if (privateKey instanceof RSAPrivateCrtKey crt) {
                RSAPublicKey publicKey = (RSAPublicKey) KeyFactory.getInstance("RSA")
                        .generatePublic(new RSAPublicKeySpec(crt.getModulus(), crt.getPublicExponent()));

                return new KeyPair(publicKey, privateKey);
            }

            throw new IllegalStateException(
                    "auth.jwt.private-key must be a PKCS#8 RSA key with CRT parameters (openssl genpkey -algorithm RSA)");
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Could not load auth.jwt.private-key PEM", ex);
        }
    }

    private static KeyPair generateKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("RSA keypair generator unavailable", ex);
        }
    }

    private static String keyThumbprint(PublicKey publicKey) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(publicKey.getEncoded());
            StringBuilder hex = new StringBuilder();

            for (int i = 0; i < 8; i++) {
                hex.append(String.format("%02x", hash[i]));
            }

            return hex.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
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
                .add("kid", keyId)
                .and()
                .signWith(keyPair.getPrivate(), Jwts.SIG.RS256)
                .compact();
    }

    public JWKSet jwks() {

        RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();

        return new JWKSet(new RSAKey.Builder(publicKey)
                .algorithm(JWSAlgorithm.RS256)
                .keyUse(KeyUse.SIGNATURE)
                .keyID(keyId)
                .build());
    }

    public long accessTokenTtlSeconds() {

        return jwt.accessTokenTtl().toSeconds();
    }

    public Jws<Claims> verify(String token) {
        try {
            return Jwts.parser().verifyWith(keyPair.getPublic()).build().parseSignedClaims(token);
        } catch (JwtException | IllegalArgumentException ex) {
            throw new InvalidAccessTokenException("Invalid access token");
        }
    }
}

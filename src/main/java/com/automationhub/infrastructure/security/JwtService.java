package com.automationhub.infrastructure.security;

import com.automationhub.auth.entity.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

@Service
public class JwtService {

    private static final String CLAIM_EMAIL = "email";
    private static final String CLAIM_ROLE = "role";

    private final SecretKey signingKey;
    private final long expirationMillis;

    public JwtService(
            @Value("${automationhub.jwt.secret}") String secret,
            @Value("${automationhub.jwt.expiration}") long expirationMillis
    ) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMillis = expirationMillis;
    }

    public String generateToken(UUID userId, String email, Role role) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationMillis);
        return Jwts.builder()
                .subject(userId.toString())
                .claims(Map.of(
                        CLAIM_EMAIL, email,
                        CLAIM_ROLE, role.name()
                ))
                .issuedAt(now)
                .expiration(expiry)
                .signWith(signingKey)
                .compact();
    }

    public boolean validateToken(String token) {
        try {
            parse(token);
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    public UUID extractUserId(String token) {
        return UUID.fromString(parse(token).getPayload().getSubject());
    }

    public String extractEmail(String token) {
        return parse(token).getPayload().get(CLAIM_EMAIL, String.class);
    }

    public Role extractRole(String token) {
        return Role.valueOf(parse(token).getPayload().get(CLAIM_ROLE, String.class));
    }

    private Jws<Claims> parse(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token);
    }
}

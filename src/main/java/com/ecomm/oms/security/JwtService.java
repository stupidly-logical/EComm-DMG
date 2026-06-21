package com.ecomm.oms.security;

import com.ecomm.oms.user.Role;
import com.ecomm.oms.user.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

/**
 * Issues and verifies stateless HS256 JWTs. The token subject is the user id; email and role
 * travel as claims so request authorisation needs no database lookup.
 */
@Service
public class JwtService {

    private final SecretKey key;
    private final String issuer;
    private final long expiryMinutes;

    public JwtService(JwtProperties props) {
        this.key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(props.secret()));
        this.issuer = props.issuer();
        this.expiryMinutes = props.expiryMinutes();
    }

    /** Mint a signed token for a persisted user. */
    public IssuedToken issue(User user) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(expiryMinutes, ChronoUnit.MINUTES);
        String token = Jwts.builder()
                .issuer(issuer)
                .subject(String.valueOf(user.getId()))
                .claim("email", user.getEmail())
                .claim("role", user.getRole().name())
                .issuedAt(java.util.Date.from(now))
                .expiration(java.util.Date.from(expiresAt))
                .signWith(key)
                .compact();
        return new IssuedToken(token, expiresAt);
    }

    /**
     * Parse and verify a token, returning the principal. Empty if the token is malformed,
     * expired, wrongly signed, or from another issuer.
     */
    public Optional<AuthPrincipal> parse(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .requireIssuer(issuer)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return Optional.of(new AuthPrincipal(
                    Long.valueOf(claims.getSubject()),
                    claims.get("email", String.class),
                    Role.valueOf(claims.get("role", String.class))));
        } catch (JwtException | IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    public record IssuedToken(String token, Instant expiresAt) {
    }
}

package com.ecomm.oms.security;

import com.ecomm.oms.user.Role;
import com.ecomm.oms.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for token issue/verify with no Spring context.
 */
class JwtServiceTest {

    private static final String SECRET =
            "dGVzdC1zZWNyZXQtdGVzdC1zZWNyZXQtdGVzdC1zZWNyZXQtMzJieXRlcw==";

    private final JwtService service =
            new JwtService(new JwtProperties(SECRET, "oms-test", 60));

    private User persistedUser(Long id, Role role) {
        User user = new User("alice@example.com", "hash", "Alice", role);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    @Test
    void issuesTokenThatRoundTripsToPrincipal() {
        JwtService.IssuedToken issued = service.issue(persistedUser(7L, Role.ADMIN));

        assertThat(issued.expiresAt()).isAfter(Instant.now());

        Optional<AuthPrincipal> principal = service.parse(issued.token());
        assertThat(principal).isPresent();
        assertThat(principal.get().userId()).isEqualTo(7L);
        assertThat(principal.get().email()).isEqualTo("alice@example.com");
        assertThat(principal.get().role()).isEqualTo(Role.ADMIN);
    }

    @Test
    void rejectsTamperedToken() {
        String token = service.issue(persistedUser(1L, Role.CUSTOMER)).token();
        String tampered = token.substring(0, token.length() - 2)
                + (token.endsWith("a") ? "bb" : "aa");

        assertThat(service.parse(tampered)).isEmpty();
    }

    @Test
    void rejectsTokenFromAnotherIssuer() {
        JwtService foreign = new JwtService(new JwtProperties(SECRET, "someone-else", 60));
        String token = foreign.issue(persistedUser(1L, Role.CUSTOMER)).token();

        assertThat(service.parse(token)).isEmpty();
    }

    @Test
    void rejectsGarbage() {
        assertThat(service.parse("not-a-jwt")).isEmpty();
    }
}

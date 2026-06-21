package com.ecomm.oms.support;

import com.ecomm.oms.security.JwtService;
import com.ecomm.oms.user.Role;
import com.ecomm.oms.user.User;
import com.ecomm.oms.user.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

/**
 * Base class for MockMvc integration tests. Boots the full context against H2 and provides
 * JWT auth helpers so any slice can authenticate as a given role in one line:
 *
 * <pre>{@code
 *   mockMvc.perform(get("/api/users").header(AUTHORIZATION, loginAs(Role.ADMIN)))
 * }</pre>
 *
 * Use {@link #createUser(Role)} when a test needs the persisted user (e.g. for ownership
 * checks) and {@link #bearer(User)} to mint that user's token.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class IntegrationTestSupport {

    public static final String DEFAULT_PASSWORD = "password123";

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected PasswordEncoder passwordEncoder;

    @Autowired
    protected JwtService jwtService;

    /** Persist a fresh user with a unique email for the given role. */
    protected User createUser(Role role) {
        String email = role.name().toLowerCase() + "-" + UUID.randomUUID() + "@test.com";
        return userRepository.save(new User(
                email,
                passwordEncoder.encode(DEFAULT_PASSWORD),
                role.name() + " User",
                role));
    }

    /** A ready-to-use {@code Authorization} header value for the given user. */
    protected String bearer(User user) {
        return "Bearer " + jwtService.issue(user).token();
    }

    /** Create a new user with {@code role} and return its bearer header value. */
    protected String loginAs(Role role) {
        return bearer(createUser(role));
    }

    protected String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialise test payload", e);
        }
    }
}

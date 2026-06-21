package com.ecomm.oms.user;

import com.ecomm.oms.common.error.ConflictException;
import com.ecomm.oms.common.error.UnauthorizedException;
import com.ecomm.oms.security.JwtService;
import com.ecomm.oms.user.dto.AuthResponse;
import com.ecomm.oms.user.dto.LoginRequest;
import com.ecomm.oms.user.dto.RegisterRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registration and credential authentication. Self-service registration always creates a
 * {@link Role#CUSTOMER}; privileged roles (ADMIN, WAREHOUSE_STAFF) are provisioned via seed
 * data so the public endpoint cannot escalate privilege.
 */
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                       JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = normalize(request.email());
        if (userRepository.existsByEmail(email)) {
            throw new ConflictException("Email already registered", "EMAIL_TAKEN");
        }
        User user = new User(
                email,
                passwordEncoder.encode(request.password()),
                request.displayName().trim(),
                Role.CUSTOMER);
        userRepository.save(user);
        return toAuthResponse(user);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(normalize(request.email()))
                .filter(u -> passwordEncoder.matches(request.password(), u.getPasswordHash()))
                .orElseThrow(() -> new UnauthorizedException("Invalid email or password"));
        return toAuthResponse(user);
    }

    private AuthResponse toAuthResponse(User user) {
        JwtService.IssuedToken issued = jwtService.issue(user);
        return AuthResponse.of(issued.token(), issued.expiresAt(),
                user.getEmail(), user.getDisplayName(), user.getRole());
    }

    private static String normalize(String email) {
        return email.trim().toLowerCase();
    }
}

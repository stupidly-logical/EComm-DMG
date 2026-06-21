package com.ecomm.oms.user.dto;

import com.ecomm.oms.user.Role;

import java.time.Instant;

/** Authentication result: the bearer token plus the identity it represents. */
public record AuthResponse(
        String token,
        String tokenType,
        Instant expiresAt,
        String email,
        String displayName,
        Role role) {

    public static AuthResponse of(String token, Instant expiresAt,
                                  String email, String displayName, Role role) {
        return new AuthResponse(token, "Bearer", expiresAt, email, displayName, role);
    }
}

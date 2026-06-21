package com.ecomm.oms.security;

import com.ecomm.oms.user.Role;

/**
 * Lightweight authenticated identity carried as the Spring Security {@code Authentication}
 * principal. Holds just enough to authorise requests and run ownership checks without a DB
 * round-trip per request. Injected into controllers via {@code @CurrentUser}.
 */
public record AuthPrincipal(Long userId, String email, Role role) {
}

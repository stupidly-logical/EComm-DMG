package com.ecomm.oms.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds the {@code oms.jwt.*} configuration. {@code secret} is a base64-encoded key of at
 * least 256 bits (HS256).
 */
@ConfigurationProperties(prefix = "oms.jwt")
public record JwtProperties(String secret, String issuer, long expiryMinutes) {
}

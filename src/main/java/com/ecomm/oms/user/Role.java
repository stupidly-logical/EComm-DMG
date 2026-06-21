package com.ecomm.oms.user;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/**
 * Application roles. Spring Security authorities are the role name prefixed with
 * {@code ROLE_}, so {@code hasRole('ADMIN')} matches {@link #ADMIN}.
 */
public enum Role {
    ADMIN,
    CUSTOMER,
    WAREHOUSE_STAFF;

    public GrantedAuthority asAuthority() {
        return new SimpleGrantedAuthority("ROLE_" + name());
    }
}

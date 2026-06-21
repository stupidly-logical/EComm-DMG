package com.ecomm.oms.user.dto;

import com.ecomm.oms.user.Role;
import com.ecomm.oms.user.User;

public record UserResponse(Long id, String email, String displayName, Role role) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getDisplayName(), user.getRole());
    }
}

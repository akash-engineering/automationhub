package com.automationhub.auth.dto;

import com.automationhub.auth.entity.Role;
import com.automationhub.auth.entity.User;

import java.util.UUID;

public record MeResponse(UUID id, String email, Role role) {

    public static MeResponse from(User user) {
        return new MeResponse(user.getId(), user.getEmail(), user.getRole());
    }
}

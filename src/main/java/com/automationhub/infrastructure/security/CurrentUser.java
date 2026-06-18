package com.automationhub.infrastructure.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
public class CurrentUser {

    public Optional<UUID> id() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof UUID uuid) {
            return Optional.of(uuid);
        }
        try {
            return Optional.of(UUID.fromString(principal.toString()));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    public UUID requireId() {
        return id().orElseThrow(() -> new IllegalStateException("No authenticated user in security context"));
    }
}

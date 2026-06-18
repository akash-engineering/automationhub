package com.automationhub.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @Email(message = "must be a valid email")
        @NotBlank(message = "must not be blank")
        String email,

        @NotBlank(message = "must not be blank")
        @Size(min = 8, message = "must be at least 8 characters")
        String password
) {
}

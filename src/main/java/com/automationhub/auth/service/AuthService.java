package com.automationhub.auth.service;

import com.automationhub.auth.dto.AuthResponse;
import com.automationhub.auth.dto.LoginRequest;
import com.automationhub.auth.dto.RegisterRequest;
import com.automationhub.auth.repository.UserRepository;
import com.automationhub.infrastructure.security.JwtService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public AuthResponse register(RegisterRequest request) {
        throw new UnsupportedOperationException("register not implemented");
    }

    public AuthResponse login(LoginRequest request) {
        throw new UnsupportedOperationException("login not implemented");
    }
}

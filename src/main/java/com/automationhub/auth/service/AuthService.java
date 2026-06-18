package com.automationhub.auth.service;

import com.automationhub.auth.dto.AuthResponse;
import com.automationhub.auth.dto.LoginRequest;
import com.automationhub.auth.dto.MeResponse;
import com.automationhub.auth.dto.RegisterRequest;
import com.automationhub.auth.entity.Role;
import com.automationhub.auth.entity.User;
import com.automationhub.auth.repository.UserRepository;
import com.automationhub.infrastructure.security.JwtService;
import com.automationhub.shared.exception.EmailAlreadyExistsException;
import com.automationhub.shared.exception.InvalidCredentialsException;
import com.automationhub.shared.exception.ResourceNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

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

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = request.email().toLowerCase();
        if (userRepository.findByEmail(email).isPresent()) {
            throw new EmailAlreadyExistsException(email);
        }
        User user = User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(request.password()))
                .role(Role.USER)
                .build();
        User saved = userRepository.save(user);
        return AuthResponse.bearer(jwtService.generateToken(saved.getId(), saved.getEmail(), saved.getRole()));
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        String email = request.email().toLowerCase();
        User user = userRepository.findByEmail(email)
                .orElseThrow(InvalidCredentialsException::new);
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        return AuthResponse.bearer(jwtService.generateToken(user.getId(), user.getEmail(), user.getRole()));
    }

    @Transactional(readOnly = true)
    public MeResponse me(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
        return MeResponse.from(user);
    }
}

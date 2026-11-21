package com.pulse.connected.auth;

import com.pulse.connected.auth.dto.*;
import com.pulse.connected.common.exception.DuplicateResourceException;
import com.pulse.connected.common.exception.ResourceNotFoundException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import com.pulse.connected.common.AuditLogService;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuditLogService auditLogService;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService, AuditLogService auditLogService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        // Emails are stored normalised, so the check must use the same form: otherwise "Foo@x.com"
        // passes it while "foo@x.com" exists, and the unique constraint fails instead.
        String email = normaliseEmail(request.email());
        if (userRepository.existsByEmail(email)) {
            throw new DuplicateResourceException("Email '" + email + "' is already registered");
        }

        // Self-registration always yields an OWNER; privileged roles are never caller-controlled.
        User user = User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(request.password()))
                .fullName(request.fullName())
                .roles(new HashSet<>(Set.of(Role.OWNER)))
                .build();

        User savedUser = userRepository.save(user);
        auditLogService.log(savedUser.getId(), "USER_REGISTERED", "User", savedUser.getId().toString(), null);

        return issueTokens(savedUser);
    }

    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(normaliseEmail(request.email()))
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid email or password");
        }
        
        auditLogService.log(user.getId(), "USER_LOGIN", "User", user.getId().toString(), null);

        return issueTokens(user);
    }

    public AuthResponse refreshToken(RefreshTokenRequest request) {
        String refreshToken = request.refreshToken();
        UUID userId = jwtService.parseRefreshToken(refreshToken)
                .orElseThrow(() -> new BadCredentialsException("Invalid or expired refresh token"));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        return issueTokens(user);
    }

    /**
     * The form emails are stored and looked up in. {@code Locale.ROOT}, so a JVM whose default
     * locale is Turkish doesn't lowercase "I" to a dotless "ı".
     */
    public static String normaliseEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private AuthResponse issueTokens(User user) {
        return new AuthResponse(
                jwtService.generateAccessToken(user),
                jwtService.generateRefreshToken(user),
                "Bearer",
                jwtService.getExpirationMs(),
                mapToUserResponse(user));
    }

    public UserResponse mapToUserResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getRoles(),
                user.getCreatedAt());
    }
}

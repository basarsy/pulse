package com.pulse.connected.auth;

import com.pulse.connected.auth.dto.*;
import com.pulse.connected.common.exception.DuplicateResourceException;
import com.pulse.connected.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @InjectMocks
    private AuthService authService;

    private User sampleUser;

    @BeforeEach
    void setUp() {
        sampleUser = User.builder()
                .id(UUID.randomUUID())
                .email("basar@example.com")
                .passwordHash("hashed_pass")
                .fullName("Başar Soytürk")
                .roles(Set.of(Role.OWNER))
                .build();
    }

    @Test
    void register_Success() {
        RegisterRequest request = RegisterRequest.builder()
                .email("basar@example.com")
                .password("password123")
                .fullName("Başar Soytürk")
                .build();

        when(userRepository.existsByEmail("basar@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("hashed_pass");
        when(userRepository.save(any(User.class))).thenReturn(sampleUser);
        when(jwtService.generateAccessToken(any(User.class))).thenReturn("mock_access_token");
        when(jwtService.generateRefreshToken(any(User.class))).thenReturn("mock_refresh_token");
        when(jwtService.getExpirationMs()).thenReturn(3600000L);

        AuthResponse response = authService.register(request);

        assertThat(response).isNotNull();
        assertThat(response.getAccessToken()).isEqualTo("mock_access_token");
        assertThat(response.getUser().getEmail()).isEqualTo("basar@example.com");
        verify(userRepository).save(any(User.class));
    }

    @Test
    void register_DuplicateEmail_ThrowsException() {
        RegisterRequest request = RegisterRequest.builder()
                .email("basar@example.com")
                .password("password123")
                .fullName("Başar Soytürk")
                .build();

        when(userRepository.existsByEmail("basar@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("Email 'basar@example.com' is already registered");

        verify(userRepository, never()).save(any());
    }

    @Test
    void login_Success() {
        LoginRequest request = LoginRequest.builder()
                .email("basar@example.com")
                .password("password123")
                .build();

        when(userRepository.findByEmail("basar@example.com")).thenReturn(Optional.of(sampleUser));
        when(passwordEncoder.matches("password123", "hashed_pass")).thenReturn(true);
        when(jwtService.generateAccessToken(sampleUser)).thenReturn("mock_access_token");
        when(jwtService.generateRefreshToken(sampleUser)).thenReturn("mock_refresh_token");

        AuthResponse response = authService.login(request);

        assertThat(response).isNotNull();
        assertThat(response.getAccessToken()).isEqualTo("mock_access_token");
    }

    @Test
    void login_InvalidPassword_ThrowsException() {
        LoginRequest request = LoginRequest.builder()
                .email("basar@example.com")
                .password("wrong_password")
                .build();

        when(userRepository.findByEmail("basar@example.com")).thenReturn(Optional.of(sampleUser));
        when(passwordEncoder.matches("wrong_password", "hashed_pass")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("Invalid email or password");
    }
}

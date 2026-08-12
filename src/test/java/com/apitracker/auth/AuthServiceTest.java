package com.apitracker.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.apitracker.auth.dto.LoginRequest;
import com.apitracker.auth.dto.LoginResponse;
import com.apitracker.config.SecurityProperties;
import com.apitracker.exception.UnauthorizedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AuthServiceTest {

    private AuthService authService;
    private JwtTokenService jwtTokenService;

    @BeforeEach
    void setUp() {
        SecurityProperties properties = new SecurityProperties(
                "",
                false,
                "admin",
                "admin",
                "test-secret-key-which-is-long-enough-01",
                3_600_000L);
        jwtTokenService = new JwtTokenService(properties);
        authService = new AuthService(properties, jwtTokenService);
    }

    @Test
    void loginReturnsJwt() {
        LoginResponse response = authService.login(new LoginRequest("admin", "admin"));

        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.accessToken()).isNotBlank();
        assertThat(response.username()).isEqualTo("admin");
        assertThat(jwtTokenService.isValid(response.accessToken())).isTrue();
        assertThat(jwtTokenService.extractUsername(response.accessToken())).isEqualTo("admin");
    }

    @Test
    void loginRejectsBadPassword() {
        assertThatThrownBy(() -> authService.login(new LoginRequest("admin", "wrong")))
                .isInstanceOf(UnauthorizedException.class);
    }
}

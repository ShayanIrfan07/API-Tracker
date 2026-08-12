package com.apitracker.auth;

import com.apitracker.auth.dto.LoginRequest;
import com.apitracker.auth.dto.LoginResponse;
import com.apitracker.config.SecurityProperties;
import com.apitracker.exception.UnauthorizedException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final SecurityProperties securityProperties;
    private final JwtTokenService jwtTokenService;

    public LoginResponse login(LoginRequest request) {
        boolean usernameOk = securityProperties.adminUsername().equals(request.username());
        boolean passwordOk = securityProperties.adminPassword().equals(request.password());
        if (!usernameOk || !passwordOk) {
            throw new UnauthorizedException("Invalid username or password");
        }

        String token = jwtTokenService.createToken(request.username());
        return new LoginResponse(
                token,
                "Bearer",
                jwtTokenService.expirationMs(),
                request.username());
    }
}

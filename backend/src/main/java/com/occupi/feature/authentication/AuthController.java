package com.occupi.feature.authentication;

import com.occupi.feature.authentication.dto.UserInfoResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exposes the authenticated user's details. Login itself is handled entirely by
 * Keycloak — there is no custom login endpoint; this backend only validates the
 * bearer token and reflects its claims back to the frontend.
 *
 * <pre>
 * GET /api/auth/userinfo  → 200 with username/email/roles, or 401 without a token
 * </pre>
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @GetMapping("/userinfo")
    public UserInfoResponse userInfo(@AuthenticationPrincipal Jwt jwt) {
        return authService.getUserInfo(jwt);
    }
}

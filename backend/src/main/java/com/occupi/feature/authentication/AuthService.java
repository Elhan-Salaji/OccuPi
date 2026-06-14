package com.occupi.feature.authentication;

import com.occupi.feature.authentication.dto.UserInfoResponse;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Extracts authenticated-user details from a validated Keycloak JWT.
 */
public interface AuthService {

    /**
     * Builds a {@link UserInfoResponse} from the claims of a validated token.
     *
     * @param jwt the authenticated Keycloak JWT
     * @return username, email and realm roles extracted from the token
     */
    UserInfoResponse getUserInfo(Jwt jwt);
}

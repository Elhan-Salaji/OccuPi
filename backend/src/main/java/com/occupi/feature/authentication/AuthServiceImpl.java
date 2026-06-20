package com.occupi.feature.authentication;

import com.occupi.feature.authentication.dto.UserInfoResponse;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Default {@link AuthService} — reads standard Keycloak claims from the JWT.
 * No credentials are handled here; token validation is done by the resource
 * server filter chain before this code runs.
 */
@Service
public class AuthServiceImpl implements AuthService {

    @Override
    public UserInfoResponse getUserInfo(Jwt jwt) {
        return new UserInfoResponse(
                jwt.getClaimAsString("preferred_username"),
                jwt.getClaimAsString("email"),
                extractRealmRoles(jwt)
        );
    }

    private List<String> extractRealmRoles(Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
        if (realmAccess == null) {
            return List.of();
        }
        Object roles = realmAccess.get("roles");
        if (!(roles instanceof Collection<?> roleList)) {
            return List.of();
        }
        return roleList.stream().map(Object::toString).toList();
    }
}

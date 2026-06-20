package com.occupi.feature.authentication.dto;

import java.util.List;

/**
 * Authenticated user's details extracted from the Keycloak JWT.
 *
 * @param username the {@code preferred_username} claim
 * @param email    the {@code email} claim (may be null if not present in the token)
 * @param roles    realm roles from the {@code realm_access.roles} claim
 */
public record UserInfoResponse(String username, String email, List<String> roles) {}

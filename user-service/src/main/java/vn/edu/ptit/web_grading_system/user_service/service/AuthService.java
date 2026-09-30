package vn.edu.ptit.web_grading_system.user_service.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import vn.edu.ptit.web_grading_system.user_service.config.KeycloakProperties;
import vn.edu.ptit.web_grading_system.user_service.dto.request.ChangePasswordRequest;
import vn.edu.ptit.web_grading_system.user_service.dto.request.LoginRequest;
import vn.edu.ptit.web_grading_system.user_service.dto.response.LoginResponse;
import vn.edu.ptit.web_grading_system.user_service.exception.BadRequestException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final KeycloakProperties keycloakProperties;
    private final Keycloak keycloakAdminClient;
    private final RestTemplate restTemplate;

    private String tokenEndpoint() {
        return keycloakProperties.serverUrl()
                + "/realms/" + keycloakProperties.realm()
                + "/protocol/openid-connect/token";
    }

    /**
     * Authenticates the user via Keycloak Direct Access Grant.
     * After a successful login, checks Keycloak user requiredActions to detect
     * if the user must change their password (first-login flow).
     */
    public LoginResponse login(LoginRequest request) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "password");
        form.add("client_id", "wgs-postman");   // public client for user logins
        form.add("username", request.getUsername());
        form.add("password", request.getPassword());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    tokenEndpoint(), new HttpEntity<>(form, headers), Map.class);
            @SuppressWarnings("unchecked")
            Map<String, Object> body = response.getBody();
            if (body == null) throw new BadRequestException("Empty response from Keycloak");

            String accessToken  = (String) body.get("access_token");
            String refreshToken = (String) body.get("refresh_token");
            String tokenType    = (String) body.getOrDefault("token_type", "Bearer");
            long expiresIn      = ((Number) body.get("expires_in")).longValue();

            // Detect first-login: check requiredActions via Admin API
            boolean requiresPasswordChange = hasUpdatePasswordAction(request.getUsername());

            LoginResponse.LoginResponseBuilder builder = LoginResponse.builder()
                    .accessToken(accessToken)
                    .refreshToken(refreshToken)
                    .tokenType(tokenType)
                    .expiresIn(expiresIn);
            if (requiresPasswordChange) {
                builder.requiresPasswordChange(true);
            }
            return builder.build();

        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                throw new BadRequestException("Invalid username or password");
            }
            throw new BadRequestException("Authentication failed: " + e.getMessage());
        }
    }

    /** Refreshes an access token using a refresh token. */
    public LoginResponse refresh(String refreshToken) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "refresh_token");
        form.add("client_id", "wgs-postman");
        form.add("refresh_token", refreshToken);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    tokenEndpoint(), new HttpEntity<>(form, headers), Map.class);
            @SuppressWarnings("unchecked")
            Map<String, Object> body = response.getBody();
            if (body == null) throw new BadRequestException("Empty response from Keycloak");
            return LoginResponse.builder()
                    .accessToken((String) body.get("access_token"))
                    .refreshToken((String) body.get("refresh_token"))
                    .tokenType((String) body.getOrDefault("token_type", "Bearer"))
                    .expiresIn(((Number) body.get("expires_in")).longValue())
                    .build();
        } catch (HttpClientErrorException e) {
            throw new BadRequestException("Invalid or expired refresh token");
        }
    }

    /** Revokes the user's refresh token (logout). */
    public void logout(String refreshToken) {
        String logoutUrl = keycloakProperties.serverUrl()
                + "/realms/" + keycloakProperties.realm()
                + "/protocol/openid-connect/logout";
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", "wgs-postman");
        form.add("refresh_token", refreshToken);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        try {
            restTemplate.postForEntity(logoutUrl, new HttpEntity<>(form, headers), Void.class);
        } catch (HttpClientErrorException e) {
            log.warn("Logout failed (token may already be invalid): {}", e.getMessage());
        }
    }

    /**
     * Changes the user's password and clears the UPDATE_PASSWORD required action.
     * Verifies the current password first by attempting a token request.
     *
     * @param userId          Keycloak user UUID (from X-User-Id header)
     * @param request         contains currentPassword and newPassword
     */
    public void changePassword(UUID userId, ChangePasswordRequest request) {
        // 1. Verify current password by trying to get a token
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "password");
        form.add("client_id", "wgs-postman");
        // We need the username — fetch it from Admin API
        UserRepresentation user = keycloakAdminClient.realm(keycloakProperties.realm())
                .users().get(userId.toString()).toRepresentation();
        form.add("username", user.getUsername());
        form.add("password", request.getCurrentPassword());
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        try {
            restTemplate.postForEntity(tokenEndpoint(), new HttpEntity<>(form, headers), Map.class);
        } catch (HttpClientErrorException e) {
            throw new BadRequestException("Current password is incorrect");
        }

        // 2. Set new password via Admin API
        CredentialRepresentation credential = new CredentialRepresentation();
        credential.setType(CredentialRepresentation.PASSWORD);
        credential.setValue(request.getNewPassword());
        credential.setTemporary(false);
        keycloakAdminClient.realm(keycloakProperties.realm())
                .users().get(userId.toString()).resetPassword(credential);

        // 3. Clear UPDATE_PASSWORD required action
        user.setRequiredActions(List.of());
        keycloakAdminClient.realm(keycloakProperties.realm())
                .users().get(userId.toString()).update(user);
    }

    /** Returns true if the given username has UPDATE_PASSWORD in their requiredActions. */
    private boolean hasUpdatePasswordAction(String username) {
        try {
            List<UserRepresentation> users = keycloakAdminClient.realm(keycloakProperties.realm())
                    .users().searchByUsername(username, true);
            if (users.isEmpty()) return false;
            List<String> actions = users.get(0).getRequiredActions();
            return actions != null && actions.contains("UPDATE_PASSWORD");
        } catch (Exception e) {
            log.warn("Could not check requiredActions for user {}: {}", username, e.getMessage());
            return false;
        }
    }
}

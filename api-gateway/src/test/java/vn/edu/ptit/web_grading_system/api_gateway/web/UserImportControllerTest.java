package vn.edu.ptit.web_grading_system.api_gateway.web;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit cover for the ADMIN gate: the endpoint must admit only callers whose
 * validated JWT carries the admin role, with or without Keycloak's
 * {@code ROLE_} prefix. Anything else — students, lecturers, missing or
 * malformed claims — is not admin.
 *
 * <p>Review: 2026-10-09, bulk user import plan.
 */
class UserImportControllerTest {

    private static JwtAuthenticationToken token(Object realmAccess) {
        Map<String, Object> claims = new java.util.HashMap<>(Map.of("sub", "tester"));
        if (realmAccess != null) {
            claims.put("realm_access", realmAccess);
        }
        Jwt jwt = new Jwt("token", Instant.now(), Instant.now().plusSeconds(60),
                Map.of("alg", "none"), claims);
        return new JwtAuthenticationToken(jwt);
    }

    @Test
    void adminRole_withAndWithoutPrefix_isAdmitted() {
        assertThat(UserImportController.isAdmin(
                token(Map.of("roles", List.of("ROLE_ADMIN"))))).isTrue();
        assertThat(UserImportController.isAdmin(
                token(Map.of("roles", List.of("ADMIN"))))).isTrue();
    }

    @Test
    void otherRoles_missingOrMalformedClaims_areRejected() {
        assertThat(UserImportController.isAdmin(
                token(Map.of("roles", List.of("ROLE_LECTURER"))))).isFalse();
        assertThat(UserImportController.isAdmin(
                token(Map.of("roles", List.of("STUDENT"))))).isFalse();
        assertThat(UserImportController.isAdmin(token(Map.of()))).isFalse();
        assertThat(UserImportController.isAdmin(token(null))).isFalse();
        assertThat(UserImportController.isAdmin(null)).isFalse();
    }
}

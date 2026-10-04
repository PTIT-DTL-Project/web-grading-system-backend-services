package vn.edu.ptit.web_grading_system.api_gateway.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import vn.edu.ptit.web_grading_system.api_gateway.config.KeycloakAdminProperties;
import vn.edu.ptit.web_grading_system.api_gateway.web.ChangePasswordRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Guards the four-step change-password flow of UC-14 without network or Spring context:
 * Keycloak answers are mocked (or served by a stubbed WebClient exchange function).
 *
 * <p>The two "credential failure" tests assert the SAME code constant on purpose — wrong
 * password and unknown user must stay indistinguishable.
 *
 * <p>Review: 2026-10-03, Phase 1 plan keycloak-password-gateway-plan-2026-10-03-v1.
 */
@ExtendWith(MockitoExtension.class)
class PasswordChangeServiceTest {

    /** Load-bearing sameness: wrong password AND unknown user answer exactly this. */
    private static final String SAME_CODE_FOR_WRONG_PASSWORD_AND_UNKNOWN_USER =
            "current_password_invalid";

    private static final String USERNAME = "alice";
    private static final String NEW_PASSWORD = "Dev2026!!";

    @Mock
    private KeycloakAdminClient keycloak;

    @InjectMocks
    private PasswordChangeService service;

    private static ChangePasswordRequest request(String currentPassword, String newPassword) {
        return ChangePasswordRequest.of(USERNAME, currentPassword, newPassword);
    }

    private static void assertFailure(Mono<Void> flow, HttpStatus expectedStatus,
            String expectedCode) {
        StepVerifier.create(flow)
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(ChangePasswordException.class);
                    ChangePasswordException failure = (ChangePasswordException) error;
                    assertThat(failure.status()).isEqualTo(expectedStatus);
                    assertThat(failure.code()).isEqualTo(expectedCode);
                })
                .verify();
    }

    @Test
    void wrongCurrentPassword_isRejectedWithoutEverLookingTheUserUp() {
        when(keycloak.verifyPassword(USERNAME, "wrong")).thenReturn(Mono.just(false));

        assertFailure(service.changePassword(request("wrong", NEW_PASSWORD)),
                HttpStatus.BAD_REQUEST, SAME_CODE_FOR_WRONG_PASSWORD_AND_UNKNOWN_USER);

        verify(keycloak, never()).findUserId(anyString());
    }

    @Test
    void unknownUser_answersTheExactSameCodeAsAWrongPassword() {
        when(keycloak.verifyPassword(USERNAME, "right")).thenReturn(Mono.just(true));
        when(keycloak.findUserId(USERNAME)).thenReturn(Mono.empty());

        assertFailure(service.changePassword(request("right", NEW_PASSWORD)),
                HttpStatus.BAD_REQUEST, SAME_CODE_FOR_WRONG_PASSWORD_AND_UNKNOWN_USER);
    }

    @Test
    void accountNotFullySetUp_countsAsCorrectAndCompletesWithNoContent() {
        WebClient stub = WebClient.builder().exchangeFunction(call -> {
            String body = ClientRequestBodies.of(call);
            if (call.method().equals(HttpMethod.POST)) {
                if (body.contains("grant_type=password")) {
                    return response(HttpStatus.BAD_REQUEST, "{\"error\":\"invalid_grant\","
                            + "\"error_description\":\"Account is not fully set up\"}");
                }
                return response(HttpStatus.OK, "{\"access_token\":\"admin-token\"}");
            }
            if (call.method().equals(HttpMethod.GET)) {
                return response(HttpStatus.OK, "[{\"id\":\"user-1\",\"username\":\"alice\"}]");
            }
            return Mono.just(ClientResponse.create(HttpStatus.NO_CONTENT).build());
        }).build();
        PasswordChangeService flow = new PasswordChangeService(
                new KeycloakAdminWebClient(testProperties(), stub));

        StepVerifier.create(flow.changePassword(request("temp-password", NEW_PASSWORD)))
                .verifyComplete();
    }

    @Test
    void blankField_isRejectedBeforeKeycloakIsCalled() {
        assertFailure(service.changePassword(
                        ChangePasswordRequest.of(USERNAME, " ", NEW_PASSWORD)),
                HttpStatus.BAD_REQUEST, "validation_failed");

        verifyNoInteractions(keycloak);
    }

    @Test
    void keycloakRejectingTheNewPassword_is400WeakPassword() {
        when(keycloak.verifyPassword(USERNAME, "right")).thenReturn(Mono.just(true));
        when(keycloak.findUserId(USERNAME)).thenReturn(Mono.just("user-1"));
        when(keycloak.resetPassword("user-1", NEW_PASSWORD)).thenReturn(Mono.just(false));

        assertFailure(service.changePassword(request("right", NEW_PASSWORD)),
                HttpStatus.BAD_REQUEST, "weak_password");
    }

    @Test
    void adminResetFailing_is502IdentityProviderUnavailable() {
        when(keycloak.verifyPassword(USERNAME, "right")).thenReturn(Mono.just(true));
        when(keycloak.findUserId(USERNAME)).thenReturn(Mono.just("user-1"));
        when(keycloak.resetPassword("user-1", NEW_PASSWORD))
                .thenReturn(Mono.error(new IllegalStateException("reset-password answered 503")));

        assertFailure(service.changePassword(request("right", NEW_PASSWORD)),
                HttpStatus.BAD_GATEWAY, "identity_provider_unavailable");
    }

    @Test
    void networkFailureDuringVerify_is502IdentityProviderUnavailable() {
        when(keycloak.verifyPassword(USERNAME, "wrong"))
                .thenReturn(Mono.error(new RuntimeException("connect timed out")));

        assertFailure(service.changePassword(request("wrong", NEW_PASSWORD)),
                HttpStatus.BAD_GATEWAY, "identity_provider_unavailable");

        verify(keycloak, never()).findUserId(anyString());
    }

    private static KeycloakAdminProperties testProperties() {
        return KeycloakAdminProperties.builder()
                .issuerUri("https://keycloak.example/realms/ptit-wgs")
                .adminClientId("wgs-user-service")
                .adminClientSecret("admin-secret")
                .passwordClientId("web-grading-fe")
                .build();
    }

    private static Mono<ClientResponse> response(HttpStatus status, String body) {
        return Mono.just(ClientResponse.create(status).body(body).build());
    }
}
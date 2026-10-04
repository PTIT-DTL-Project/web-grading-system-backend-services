package vn.edu.ptit.web_grading_system.api_gateway.web;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;
import tools.jackson.databind.json.JsonMapper;
import vn.edu.ptit.web_grading_system.api_gateway.service.ChangePasswordException;
import vn.edu.ptit.web_grading_system.api_gateway.service.PasswordChangeService;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Pins the HTTP contract of {@code POST /api/v1/account/change-password} (UC-14): 204 on
 * success, envelope bodies everywhere else, and validation rejected before the service is
 * even called. Runs against a stubbed WebFlux handler — no server, no Spring context.
 *
 * <p>Review: 2026-10-03, Phase 1 plan keycloak-password-gateway-plan-2026-10-03-v1.
 */
class ChangePasswordControllerTest {

    private static final String PATH = "/api/v1/account/change-password";

    private final PasswordChangeService service = mock(PasswordChangeService.class);
    private final WebTestClient webTestClient = WebTestClient
            .bindToController(new ChangePasswordController(service))
            .build();

    private WebTestClient.ResponseSpec post(Object body) {
        return webTestClient.post().uri(PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchange();
    }

    private static void assertEnvelopeKeys(String rawBody, int expectedStatus, String expectedMessage) {
        var node = JsonMapper.shared().readTree(rawBody);
        assertThat(node.size()).isEqualTo(3);
        assertThat(Set.of("status", "message", "data"))
                .allSatisfy(key -> assertThat(node.has(key)).isTrue());
        assertThat(node.has("error")).isFalse();
        assertThat(node.path("status").asInt()).isEqualTo(expectedStatus);
        assertThat(node.path("message").asString()).isEqualTo(expectedMessage);
        assertThat(node.path("data").isNull()).isTrue();
    }

    @Test
    void validRequest_returns204() {
        when(service.changePassword(any())).thenReturn(Mono.empty());

        webTestClient.post().uri(PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(ChangePasswordRequest.of("alice", "old", "new"))
                .exchange()
                .expectStatus().isEqualTo(204)
                .expectBody().isEmpty();
    }

    @Test
    void blankField_isValidationFailed_andTheServiceIsNeverCalled() {
        post(Map.of("username", "alice", "currentPassword", " ", "newPassword", "x"))
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.status").isEqualTo(400)
                .jsonPath("$.message").isEqualTo("validation_failed");

        verifyNoInteractions(service);
    }

    @Test
    void wrongCurrentPassword_answers400InTheEnvelopeShape() {
        when(service.changePassword(any()))
                .thenReturn(Mono.error(ChangePasswordException.currentPasswordInvalid()));

        String body = post(ChangePasswordRequest.of("alice", "old", "new"))
                .expectStatus().isBadRequest()
                .expectBody(String.class)
                .returnResult()
                .getResponseBody();

        assertEnvelopeKeys(body, 400, "current_password_invalid");
    }

    @Test
    void unparseableBody_isValidationFailedNotAFrameworkErrorBody() {
        post("{not-json")
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.status").isEqualTo(400)
                .jsonPath("$.message").isEqualTo("validation_failed");

        verifyNoInteractions(service);
    }

    @Test
    void unexpectedFailure_stillAnswersInTheEnvelopeShape() {
        when(service.changePassword(any()))
                .thenReturn(Mono.error(new IllegalStateException("boom")));

        String body = post(ChangePasswordRequest.of("alice", "old", "new"))
                .expectStatus().isEqualTo(500)
                .expectBody(String.class)
                .returnResult()
                .getResponseBody();

        assertEnvelopeKeys(body, 500, "internal_error");
    }
}
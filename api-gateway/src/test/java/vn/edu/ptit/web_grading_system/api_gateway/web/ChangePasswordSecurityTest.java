package vn.edu.ptit.web_grading_system.api_gateway.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;
import vn.edu.ptit.web_grading_system.api_gateway.service.ChangePasswordException;
import vn.edu.ptit.web_grading_system.api_gateway.service.PasswordChangeService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Full-context proof of the UC-14 invariant the controller Javadoc asserts: this endpoint
 * must never answer 401, whatever Authorization header the caller sends. The unit test
 * ({@link ChangePasswordControllerTest}) binds the controller WITHOUT the security filter
 * chain, so it passes even if the split chain regresses — and being reachable with no
 * token is the load-bearing property of this PR. {@code bindToApplicationContext} keeps
 * the context's WebFilters (including Spring Security's WebFilterChainProxy) in the path,
 * which is exactly what the controller-only test cannot see.
 *
 * <p>Review: 2026-10-04, Pullfrog review (permitAll does not guarantee no-401;
 * bindToController cannot see the chain).
 */
@SpringBootTest(properties = "rate-limit.enabled=true")
class ChangePasswordSecurityTest {

    private static final String PATH = "/api/v1/account/change-password";

    @MockitoBean
    private PasswordChangeService passwordChangeService;

    @Autowired
    private ApplicationContext applicationContext;

    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        webTestClient = WebTestClient.bindToApplicationContext(applicationContext).build();
    }

    @Test
    void noAuthorizationHeader_successIs204Not401() {
        when(passwordChangeService.changePassword(any())).thenReturn(Mono.empty());

        webTestClient.post().uri(PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(ChangePasswordRequest.of("alice", "old", "new-Password1"))
                .exchange()
                .expectStatus().isEqualTo(204)
                .expectBody().isEmpty();
    }

    @Test
    void garbageBearerToken_isNever401() {
        when(passwordChangeService.changePassword(any()))
                .thenReturn(Mono.error(ChangePasswordException.currentPasswordInvalid()));

        // A stale/expired/invalid token MUST not reach bearer processing: the dedicated
        // /api/v1/account/** chain has no oauth2ResourceServer, so the request lands on
        // the endpoint and gets the envelope — not the resource-server 401 entry point.
        String body = webTestClient.post().uri(PATH)
                .header("Authorization", "Bearer not-a-real-token")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(ChangePasswordRequest.of("alice", "wrong", "new-Password1"))
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody(String.class)
                .returnResult()
                .getResponseBody();

        assertThat(body).contains("\"message\":\"current_password_invalid\"");
        assertThat(body).doesNotContain("invalid_token");
    }
}

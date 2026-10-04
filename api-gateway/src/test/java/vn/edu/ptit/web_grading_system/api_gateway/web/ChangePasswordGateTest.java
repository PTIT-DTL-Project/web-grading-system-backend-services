package vn.edu.ptit.web_grading_system.api_gateway.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Pins the exposure gate of UC-14: with {@code rate-limit.enabled} unset (base profile
 * default false) the controller bean must not exist, so an unconfigured deployment never
 * exposes the anonymous password-verification oracle — the path answers 404, not 401 and
 * not 200. The enabled-side behaviour lives in {@link ChangePasswordSecurityTest}.
 *
 * <p>Review: 2026-10-04, Pullfrog review (unthrottled public endpoint).
 */
@SpringBootTest
class ChangePasswordGateTest {

    @Autowired
    private ApplicationContext applicationContext;

    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        webTestClient = WebTestClient.bindToApplicationContext(applicationContext).build();
    }

    @Test
    void endpointAbsentByDefault_answers404() {
        webTestClient.post().uri("/api/v1/account/change-password")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(ChangePasswordRequest.of("alice", "old", "new-Password1"))
                .exchange()
                .expectStatus().isEqualTo(404);
    }
}

package vn.edu.ptit.web_grading_system.api_gateway.service;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import tools.jackson.databind.json.JsonMapper;
import vn.edu.ptit.web_grading_system.api_gateway.config.KeycloakAdminProperties;

import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers the wire behaviour of the three Keycloak calls through a stubbed
 * {@code ExchangeFunction}: no network, no Spring context. Asserts the load-bearing
 * details — the password grant carries {@code client_secret} only when one is configured
 * (Phase 3, D11), the reset body stays flat, an unexpected answer is an error (502
 * downstream) rather than a verdict about credentials.
 *
 * <p>Review: 2026-10-03, Phase 1 plan keycloak-password-gateway-plan-2026-10-03-v1;
 * Phase 3 PKCE plan .opencode/plan/phase-3-pkce.md (D11).
 */
class KeycloakAdminWebClientTest {

    private static final String ISSUER = "https://keycloak.example/realms/ptit-wgs";
    private static final String TOKEN_URL = ISSUER + "/protocol/openid-connect/token";
    private static final String USERS_URL = "https://keycloak.example/admin/realms/ptit-wgs/users";
    private static final String ADMIN_TOKEN_RESPONSE =
            "{\"access_token\":\"admin-token\",\"token_type\":\"Bearer\"}";

    private final KeycloakAdminProperties properties = KeycloakAdminProperties.builder()
            .issuerUri(ISSUER)
            .adminClientId("wgs-user-service")
            .adminClientSecret("admin-secret")
            .passwordClientId("web-grading-fe")
            .build();

    private ClientRequest lastRequest;
    private String lastBody;

    private KeycloakAdminWebClient client(Function<ClientRequest, Mono<ClientResponse>> exchange) {
        return client(properties, exchange);
    }

    private KeycloakAdminWebClient client(KeycloakAdminProperties props,
            Function<ClientRequest, Mono<ClientResponse>> exchange) {
        WebClient webClient = WebClient.builder().exchangeFunction(call -> {
            lastRequest = call;
            lastBody = ClientRequestBodies.of(call);
            return exchange.apply(call);
        }).build();
        return new KeycloakAdminWebClient(props, webClient);
    }

    private static Mono<ClientResponse> respond(HttpStatus status, String body) {
        return Mono.just(ClientResponse.create(status).body(body).build());
    }

    private static Mono<ClientResponse> noContent() {
        return Mono.just(ClientResponse.create(HttpStatus.NO_CONTENT).build());
    }

    @Test
    void verifyPassword_blankSecret_sendsNoClientSecret() {
        // Default fixture: no passwordClientSecret configured → the form must look
        // exactly like before Phase 3 (public client, nothing extra on the wire), so a
        // deployment that has not set KEYCLOAK_PASSWORD_CLIENT_SECRET yet keeps working.
        KeycloakAdminWebClient client = client(call -> respond(HttpStatus.OK,
                "{\"access_token\":\"short-lived\",\"token_type\":\"Bearer\"}"));

        StepVerifier.create(client.verifyPassword("alice", "current-pw"))
                .expectNext(Boolean.TRUE)
                .verifyComplete();

        assertThat(lastRequest.method()).isEqualTo(HttpMethod.POST);
        assertThat(lastRequest.url().toString()).isEqualTo(TOKEN_URL);
        assertThat(lastBody)
                .contains("grant_type=password", "client_id=web-grading-fe", "username=alice",
                        "password=current-pw")
                .doesNotContain("client_secret");
    }

    @Test
    void verifyPassword_configuredSecret_sendsClientSecret() {
        // Phase 3 (D11): the grant runs against the confidential wgs-password-verify
        // client, so the form has to authenticate the gateway with client_secret.
        KeycloakAdminProperties confidential = KeycloakAdminProperties.builder()
                .issuerUri(ISSUER)
                .adminClientId("wgs-user-service")
                .adminClientSecret("admin-secret")
                .passwordClientId("wgs-password-verify")
                .passwordClientSecret("verify-secret")
                .build();
        KeycloakAdminWebClient client = client(confidential, call -> respond(HttpStatus.OK,
                "{\"access_token\":\"short-lived\",\"token_type\":\"Bearer\"}"));

        StepVerifier.create(client.verifyPassword("alice", "current-pw"))
                .expectNext(Boolean.TRUE)
                .verifyComplete();

        assertThat(lastBody)
                .contains("grant_type=password", "client_id=wgs-password-verify",
                        "client_secret=verify-secret", "username=alice", "password=current-pw");
    }

    @Test
    void verifyPassword_forcedChangeMarker_countsAsPasswordCorrect() {
        KeycloakAdminWebClient client = client(call -> respond(HttpStatus.BAD_REQUEST,
                "{\"error\":\"invalid_grant\","
                        + "\"error_description\":\"Account is not fully set up\"}"));

        StepVerifier.create(client.verifyPassword("alice", "temp-password"))
                .expectNext(Boolean.TRUE)
                .verifyComplete();
    }

    @Test
    void verifyPassword_invalidCredentials_answersFalse() {
        KeycloakAdminWebClient client = client(call -> respond(HttpStatus.BAD_REQUEST,
                "{\"error\":\"invalid_grant\","
                        + "\"error_description\":\"Invalid user credentials\"}"));

        StepVerifier.create(client.verifyPassword("alice", "wrong"))
                .expectNext(Boolean.FALSE)
                .verifyComplete();
    }

    @Test
    void verifyPassword_invalidCredentials_as401_answersFalse() {
        // Keycloak 26 answers invalid credentials with 401, not 400. Without this the
        // wrong-password path fell through to provider trouble and the endpoint returned
        // 502 instead of current_password_invalid. Review: 2026-10-03, runbook §6.2.
        KeycloakAdminWebClient client = client(call -> respond(HttpStatus.UNAUTHORIZED,
                "{\"error\":\"invalid_grant\","
                        + "\"error_description\":\"Invalid user credentials\"}"));

        StepVerifier.create(client.verifyPassword("alice", "wrong"))
                .expectNext(Boolean.FALSE)
                .verifyComplete();
    }

    @Test
    void verifyPassword_unexpectedAnswer_errorsInsteadOfGuessing() {
        // invalid_client = misconfigured client, not a credential verdict.
        KeycloakAdminWebClient client = client(
                call -> respond(HttpStatus.BAD_REQUEST, "{\"error\":\"invalid_client\"}"));

        StepVerifier.create(client.verifyPassword("alice", "whatever"))
                .expectError(IllegalStateException.class)
                .verify();
    }

    @Test
    void verifyPassword_networkFailure_propagates() {
        KeycloakAdminWebClient client = client(
                call -> Mono.error(new RuntimeException("connect refused")));

        StepVerifier.create(client.verifyPassword("alice", "whatever"))
                .expectError(RuntimeException.class)
                .verify();
    }

    @Test
    void findUserId_returnsTheId_withAdminBearerToken() {
        KeycloakAdminWebClient client = client(call ->
                lastBody.contains("grant_type=client_credentials")
                        ? respond(HttpStatus.OK, ADMIN_TOKEN_RESPONSE)
                        : respond(HttpStatus.OK, "[{\"id\":\"user-1\",\"username\":\"alice\"}]"));

        StepVerifier.create(client.findUserId("alice"))
                .expectNext("user-1")
                .verifyComplete();

        assertThat(lastRequest.method()).isEqualTo(HttpMethod.GET);
        assertThat(lastRequest.url().toString()).isEqualTo(USERS_URL + "?username=alice");
        assertThat(lastRequest.headers().getFirst(HttpHeaders.AUTHORIZATION))
                .isEqualTo("Bearer admin-token");
    }

    @Test
    void findUserId_emptyArray_isEmptyMonoNotAnError() {
        KeycloakAdminWebClient client = client(call ->
                lastBody.contains("grant_type=client_credentials")
                        ? respond(HttpStatus.OK, ADMIN_TOKEN_RESPONSE)
                        : respond(HttpStatus.OK, "[]"));

        StepVerifier.create(client.findUserId("ghost"))
                .verifyComplete();
    }

    @Test
    void findUserId_emailInput_queriesTheEmailParameter() {
        KeycloakAdminWebClient client = client(call ->
                lastBody.contains("grant_type=client_credentials")
                        ? respond(HttpStatus.OK, ADMIN_TOKEN_RESPONSE)
                        : respond(HttpStatus.OK,
                                "[{\"id\":\"user-9\",\"username\":\"lecturer_test\","
                                        + "\"email\":\"lecturer@ptit.edu.vn\"}]"));

        // loginWithEmailAllowed lets the TOKEN endpoint accept an address, but the admin
        // search keeps username and email in separate parameters — without switching to
        // ?email= the header modal (which sends session.email) always got an empty array
        // and was told a correct password was wrong.
        StepVerifier.create(client.findUserId("lecturer@ptit.edu.vn"))
                .expectNext("user-9")
                .verifyComplete();

        String url = lastRequest.url().toString();
        assertThat(url).contains(USERS_URL + "?email=");
        assertThat(url).doesNotContain("?username=");
        assertThat(url).contains("lecturer");
    }

    @Test
    void findUserId_prefixOnlyMatch_isEmptyRatherThanAnotherAccount() {
        KeycloakAdminWebClient client = client(call ->
                lastBody.contains("grant_type=client_credentials")
                        ? respond(HttpStatus.OK, ADMIN_TOKEN_RESPONSE)
                        : respond(HttpStatus.OK, "[{\"id\":\"user-2\",\"username\":\"alice2\"}]"));

        // Keycloak's user search is "starts with", so row 0 can be a DIFFERENT account
        // that shares the prefix. Resetting it would overwrite someone else's password
        // with one that was only verified against the caller's own credentials.
        StepVerifier.create(client.findUserId("alice"))
                .verifyComplete();
    }

    @Test
    void findUserId_serverError_errorsRatherThanPretendingTheUserIsMissing() {
        KeycloakAdminWebClient client = client(call ->
                lastBody.contains("grant_type=client_credentials")
                        ? respond(HttpStatus.OK, ADMIN_TOKEN_RESPONSE)
                        : respond(HttpStatus.INTERNAL_SERVER_ERROR, "oops"));

        StepVerifier.create(client.findUserId("alice"))
                .expectError(IllegalStateException.class)
                .verify();
    }

    @Test
    void resetPassword_sendsTheFlatCredentialBody_andTreats204AsApplied() {
        KeycloakAdminWebClient client = client(call -> call.method().equals(HttpMethod.PUT)
                ? noContent()
                : respond(HttpStatus.OK, ADMIN_TOKEN_RESPONSE));

        StepVerifier.create(client.resetPassword("user-1", "Dev2026!!"))
                .expectNext(Boolean.TRUE)
                .verifyComplete();

        assertThat(lastRequest.method()).isEqualTo(HttpMethod.PUT);
        assertThat(lastRequest.url().toString()).isEqualTo(USERS_URL + "/user-1/reset-password");
        assertThat(lastRequest.headers().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
        assertThat(lastRequest.headers().getFirst(HttpHeaders.AUTHORIZATION))
                .isEqualTo("Bearer admin-token");

        // Flat body — Keycloak 400s a nested {"credential": {...}} wrapper.
        var sent = JsonMapper.shared().readTree(lastBody);
        assertThat(sent.size()).isEqualTo(3);
        assertThat(sent.path("type").asString()).isEqualTo("password");
        assertThat(sent.path("value").asString()).isEqualTo("Dev2026!!");
        assertThat(sent.path("temporary").asBoolean()).isFalse();
        assertThat(sent.has("credential")).isFalse();
    }

    @Test
    void resetPassword_policyRejection_answersFalseForWeakPassword() {
        KeycloakAdminWebClient client = client(call -> call.method().equals(HttpMethod.PUT)
                ? respond(HttpStatus.BAD_REQUEST, "{\"error\":\"invalid_password\"}")
                : respond(HttpStatus.OK, ADMIN_TOKEN_RESPONSE));

        StepVerifier.create(client.resetPassword("user-1", "too-weak"))
                .expectNext(Boolean.FALSE)
                .verifyComplete();
    }

    @Test
    void resetPassword_unexpectedStatus_errors() {
        KeycloakAdminWebClient client = client(call -> call.method().equals(HttpMethod.PUT)
                ? respond(HttpStatus.SERVICE_UNAVAILABLE, "maintenance")
                : respond(HttpStatus.OK, ADMIN_TOKEN_RESPONSE));

        StepVerifier.create(client.resetPassword("user-1", "Dev2026!!"))
                .expectError(IllegalStateException.class)
                .verify();
    }

    @Test
    void adminTokenGrantFailure_errors() {
        KeycloakAdminWebClient client = client(
                call -> respond(HttpStatus.BAD_REQUEST, "{\"error\":\"invalid_client\"}"));

        StepVerifier.create(client.findUserId("alice"))
                .expectError(IllegalStateException.class)
                .verify();
    }
}

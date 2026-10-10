package vn.edu.ptit.web_grading_system.api_gateway.service;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import vn.edu.ptit.web_grading_system.api_gateway.config.KeycloakAdminProperties;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers the Keycloak calls the bulk import adds, through the same stubbed
 * {@code ExchangeFunction} harness as {@link KeycloakAdminWebClientTest}: user
 * creation (201 + Location id, 409 typed error), temporary password shape,
 * role lookup parsing, role-mapping assignment, and the bulk session's single
 * token grant across many operations.
 *
 * <p>Review: 2026-10-09, bulk user import plan.
 */
class KeycloakAdminImportTest {

    private static final String ISSUER = "https://keycloak.example/realms/ptit-wgs";
    private static final String USERS_URL = "https://keycloak.example/admin/realms/ptit-wgs/users";
    private static final String ROLES_URL = "https://keycloak.example/admin/realms/ptit-wgs/roles";
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
        WebClient webClient = WebClient.builder().exchangeFunction(call -> {
            lastRequest = call;
            lastBody = ClientRequestBodies.of(call);
            return exchange.apply(call);
        }).build();
        return new KeycloakAdminWebClient(properties, webClient);
    }

    private static Mono<ClientResponse> respond(HttpStatus status, String body) {
        return Mono.just(ClientResponse.create(status).body(body).build());
    }

    private static Mono<ClientResponse> created(String location) {
        return Mono.just(ClientResponse.create(HttpStatus.CREATED)
                .header(HttpHeaders.LOCATION, location)
                .build());
    }

    private static Mono<ClientResponse> noContent() {
        return Mono.just(ClientResponse.create(HttpStatus.NO_CONTENT).build());
    }

    @Test
    void createUser_postsRepresentation_returnsIdFromLocation() {
        KeycloakAdminWebClient client = client(call ->
                call.url().toString().contains("/protocol/openid-connect/token")
                        ? respond(HttpStatus.OK, ADMIN_TOKEN_RESPONSE)
                        : created(USERS_URL + "/new-user-id"));

        StepVerifier.create(client.createUser("B22DCCN001", "a@ptit.edu.vn", "Van An", "Nguyen"))
                .expectNext("new-user-id")
                .verifyComplete();

        assertThat(lastRequest.method()).isEqualTo(HttpMethod.POST);
        assertThat(lastRequest.url().toString()).isEqualTo(USERS_URL);
        assertThat(lastBody)
                .contains("\"username\":\"B22DCCN001\"")
                .contains("\"firstName\":\"Van An\"")
                .contains("\"lastName\":\"Nguyen\"")
                .contains("\"UPDATE_PASSWORD\"");
    }

    @Test
    void createUser_conflict_throwsTypedErrorWithStatus() {
        KeycloakAdminWebClient client = client(call ->
                call.url().toString().contains("/protocol/openid-connect/token")
                        ? respond(HttpStatus.OK, ADMIN_TOKEN_RESPONSE)
                        : respond(HttpStatus.CONFLICT, "{\"error\":\"User exists\"}"));

        StepVerifier.create(client.createUser("taken", "t@ptit.edu.vn", "User", "Taken"))
                .verifyErrorMatches(error -> error instanceof CreateUserException
                        && ((CreateUserException) error).status() == 409);
    }

    @Test
    void setTemporaryPassword_sendsTemporaryFlag() {
        KeycloakAdminWebClient client = client(call ->
                call.url().toString().contains("/protocol/openid-connect/token")
                        ? respond(HttpStatus.OK, ADMIN_TOKEN_RESPONSE)
                        : noContent());

        StepVerifier.create(client.setTemporaryPassword("user-id", "B22DCCN001"))
                .expectNext(Boolean.TRUE)
                .verifyComplete();

        assertThat(lastRequest.url().toString()).isEqualTo(USERS_URL + "/user-id/reset-password");
        assertThat(lastBody).contains("\"temporary\":true");
    }

    @Test
    void findRealmRole_parsesIdAndName() {
        KeycloakAdminWebClient client = client(call ->
                call.url().toString().contains("/protocol/openid-connect/token")
                        ? respond(HttpStatus.OK, ADMIN_TOKEN_RESPONSE)
                        : respond(HttpStatus.OK, "{\"id\":\"role-id\",\"name\":\"ROLE_STUDENT\"}"));

        StepVerifier.create(client.findRealmRole("ROLE_STUDENT"))
                .expectNext(Map.of("id", "role-id", "name", "ROLE_STUDENT"))
                .verifyComplete();

        assertThat(lastRequest.url().toString()).isEqualTo(ROLES_URL + "/ROLE_STUDENT");
    }

    @Test
    void assignRealmRoles_postsRepresentations() {
        KeycloakAdminWebClient client = client(call ->
                call.url().toString().contains("/protocol/openid-connect/token")
                        ? respond(HttpStatus.OK, ADMIN_TOKEN_RESPONSE)
                        : noContent());

        StepVerifier.create(client.assignRealmRoles("user-id",
                        List.of(Map.of("id", "role-id", "name", "ROLE_STUDENT"))))
                .verifyComplete();

        assertThat(lastRequest.url().toString())
                .isEqualTo(USERS_URL + "/user-id/role-mappings/realm");
        assertThat(lastBody).contains("ROLE_STUDENT");
    }

    @Test
    void bulkSession_fetchesOneTokenForManyOperations() {
        AtomicInteger tokenCalls = new AtomicInteger();
        KeycloakAdminWebClient client = client(call -> {
            if (call.url().toString().contains("/protocol/openid-connect/token")) {
                tokenCalls.incrementAndGet();
                return respond(HttpStatus.OK, ADMIN_TOKEN_RESPONSE);
            }
            String url = call.url().toString();
            if (url.equals(ROLES_URL + "/ROLE_STUDENT")) {
                return respond(HttpStatus.OK, "{\"id\":\"role-id\",\"name\":\"ROLE_STUDENT\"}");
            }
            return respond(HttpStatus.OK, "[]");
        });

        KeycloakAdminClient.BulkOperations ops = client.bulk();
        StepVerifier.create(ops.findRealmRole("ROLE_STUDENT")
                        .then(ops.findUserId("B22DCCN001"))
                        .then(ops.findUserId("B22DCCN002")))
                .verifyComplete();

        // Three admin calls, one token grant: the session memoizes the token
        // instead of re-authenticating per operation.
        assertThat(tokenCalls.get()).isEqualTo(1);
    }
}

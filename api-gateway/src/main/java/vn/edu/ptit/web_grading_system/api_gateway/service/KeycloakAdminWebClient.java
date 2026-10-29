package vn.edu.ptit.web_grading_system.api_gateway.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import vn.edu.ptit.web_grading_system.api_gateway.config.KeycloakAdminProperties;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * WebClient implementation of {@link KeycloakAdminClient}: the three Keycloak calls of the
 * change-password flow — password-grant verification, admin user lookup, password reset.
 *
 * <p>Every response is read as a String and parsed with Jackson directly, so the client
 * leans on nothing but a bare {@link WebClient}'s default String/form codecs. Tokens are
 * used in-flight only: never persisted, never forwarded to anyone.
 *
 * <p>Review: 2026-10-03, Phase 1 plan keycloak-password-gateway-plan-2026-10-03-v1.
 */
@Slf4j
@Service
public class KeycloakAdminWebClient implements KeycloakAdminClient {

    /**
     * Keycloak's {@code invalid_grant} description while ANY required action is pending.
     * Named for the predicate, not for UPDATE_PASSWORD: the same sentence appears for
     * VERIFY_PROFILE, VERIFY_EMAIL, CONFIGURE_TOTP, etc. The verdict (password accepted)
     * is what the flow relies on — the frontend must not infer "forced password change"
     * from this signal alone.
     */
    static final String PENDING_REQUIRED_ACTION_MARKER = "Account is not fully set up";

    /**
     * Bounded retry budget for admin calls that are safe to repeat. A burst of
     * parallel user-list reads makes a single-node Keycloak answer 5xx, or its
     * streaming user search fail with 400 "Cannot parse the JSON"
     * (keycloak#34857); the same call is fine a moment later.
     * Review: 2026-10-29, bulk-import overload incident.
     */
    static final int TRANSIENT_MAX_ATTEMPTS = 3;
    static final Duration TRANSIENT_BACKOFF = Duration.ofMillis(250);
    private static final Duration TRANSIENT_MAX_BACKOFF = Duration.ofSeconds(2);

    /**
     * Keycloak's answer to overload, not a verdict about the request. Kept an
     * {@link IllegalStateException} so every unexpected provider answer still
     * surfaces as the same 502 class downstream.
     * Review: 2026-10-29, bulk-import overload incident.
     */
    static final class TransientKeycloakException extends IllegalStateException {
        TransientKeycloakException(String message) {
            super(message);
        }
    }

    private static final JsonMapper JSON = JsonMapper.shared();

    private final KeycloakAdminProperties properties;
    private final WebClient webClient;
    private final String tokenEndpoint;
    private final String usersUri;
    private final String rolesUri;

    @Autowired
    public KeycloakAdminWebClient(KeycloakAdminProperties properties) {
        this(properties, WebClient.builder().build());
    }

    /**
     * Secondary constructor used by tests: swap in a WebClient whose ExchangeFunction
     * serves canned responses, so HTTP behaviour is covered without network access.
     */
    KeycloakAdminWebClient(KeycloakAdminProperties properties, WebClient webClient) {
        this.properties = properties;
        this.webClient = webClient;
        // Precomputed at startup: a misconfigured issuer (no /realms/) fails the context
        // load instead of surfacing as a 502 on the first password change.
        this.tokenEndpoint = properties.issuerUri() + "/protocol/openid-connect/token";
        this.usersUri = properties.adminBaseUri() + "/admin/realms/" + properties.realm() + "/users";
        this.rolesUri = properties.adminBaseUri() + "/admin/realms/" + properties.realm() + "/roles";
    }

    @Override
    public Mono<Boolean> verifyPassword(String username, String password) {
        return webClient.post()
                .uri(tokenEndpoint)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .bodyValue(passwordGrantForm(properties.passwordClientId(),
                        properties.passwordClientSecret(), username, password))
                .exchangeToMono(response -> response.bodyToMono(String.class)
                        .defaultIfEmpty("")
                        .flatMap(body -> passwordVerdict(response.statusCode(), body)));
    }

    @Override
    public Mono<String> findUserId(String username) {
        return freshAdminToken()
                .flatMap(token -> retryTransient(findUserIdWith(token, username)));
    }

    private Mono<String> findUserIdWith(String token, String username) {
        // The admin search keeps username and email in two different query parameters,
        // while the realm's loginWithEmailAllowed only affects the TOKEN endpoint. A
        // caller who identified themselves by email (the header modal sends
        // session.email) would therefore always land on an empty array and be told a
        // correct password is wrong.
        //
        // Exactly one parameter is sent on purpose: the caller was authenticated in step
        // 2 as THIS string, so only the candidate it resolves to may be reset — querying
        // both would admit a second, different account into the result set.
        //
        // A username containing '@' but differing from the account's email is the one
        // input this cannot resolve; the answer is then the same
        // current_password_invalid as a wrong password, never someone else's account.
        boolean byEmail = username.contains("@");
        String query = byEmail ? "?email={value}" : "?username={value}";
        return webClient.get()
                .uri(usersUri + query, username)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .exchangeToMono(response -> response.bodyToMono(String.class)
                        .defaultIfEmpty("")
                        .flatMap(body -> userLookupVerdict(response.statusCode(), body,
                                username, byEmail)));
    }

    @Override
    public Mono<Boolean> resetPassword(String userId, String newPassword) {
        return freshAdminToken().flatMap(token -> retryTransient(
                resetPasswordWith(token, userId, resetCredential(newPassword))));
    }

    /**
     * One wire call shared by both reset paths — only the credential body differs
     * ({@code temporary} true/false). Safe to retry: writing the same password
     * twice leaves the same state.
     */
    private Mono<Boolean> resetPasswordWith(String token, String userId,
            Map<String, Object> credential) {
        return webClient.put()
                .uri(usersUri + "/" + userId + "/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .bodyValue(credential)
                .exchangeToMono(response -> response.bodyToMono(String.class)
                        .defaultIfEmpty("")
                        .flatMap(body -> resetVerdict(response.statusCode(), body)));
    }

    /**
     * Bulk account import (admin UI): create → temporary password → realm role,
     * one user at a time so a single bad row cannot kill the batch. The caller
     * checks existence first; a {@code 409} here is a race, surfaced as an error
     * so the row lands in the report instead of vanishing.
     *
     * <p>Review: 2026-10-09, bulk user import plan.
     */
    @Override
    public Mono<String> createUser(String username, String email, String firstName, String lastName) {
        // The token fetch is retried; the create POST is not. A 5xx after Keycloak
        // already committed the user would make a retry answer 409 and hide the
        // account that still needs provisioning — a re-import finishes it instead.
        // Review: 2026-10-29, bulk-import overload incident.
        return freshAdminToken().flatMap(token ->
                createUserWith(token, username, email, firstName, lastName));
    }

    private Mono<String> createUserWith(String token, String username, String email,
            String firstName, String lastName) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("username", username);
        body.put("email", email);
        body.put("firstName", firstName);
        // lastName is required by the realm's user profile — the caller
        // guarantees non-blank (username fallback), so this is always sent.
        // Review: 2026-10-10, missing-lastName login wall.
        body.put("lastName", lastName);
        body.put("enabled", Boolean.TRUE);
        body.put("requiredActions", List.of("UPDATE_PASSWORD"));
        return webClient.post()
                .uri(usersUri)
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .bodyValue(body)
                .exchangeToMono(response -> {
                    if (response.statusCode().value() != 201) {
                        log.warn("User creation failed: status={} username={}",
                                response.statusCode().value(), username);
                        return Mono.error(new CreateUserException(response.statusCode().value(),
                                "user creation answered " + response.statusCode().value()));
                    }
                    String location = response.headers().header(HttpHeaders.LOCATION).stream()
                            .findFirst().orElse("");
                    String id = location.contains("/")
                            ? location.substring(location.lastIndexOf('/') + 1)
                            : "";
                    if (!StringUtils.hasText(id)) {
                        log.warn("User creation answered 201 without a Location id");
                        return Mono.error(new IllegalStateException(
                                "user creation carried no user id"));
                    }
                    return Mono.just(id);
                });
    }

    @Override
    public Mono<Boolean> setTemporaryPassword(String userId, String newPassword) {
        return freshAdminToken().flatMap(token -> retryTransient(
                setTemporaryPasswordWith(token, userId, newPassword)));
    }

    private Mono<Boolean> setTemporaryPasswordWith(String token, String userId, String newPassword) {
        return resetPasswordWith(token, userId, temporaryCredential(newPassword));
    }

    /**
     * Keycloak's credential list for a user: empty means the account was created
     * but never got a password, so nobody can log into it yet.
     *
     * <p>Review: 2026-10-29, bulk-import overload incident — rows that died
     * between create and reset left such accounts behind.
     */
    private Mono<Boolean> hasPasswordWith(String token, String userId) {
        return webClient.get()
                .uri(usersUri + "/" + userId + "/credentials")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .exchangeToMono(response -> response.bodyToMono(String.class)
                        .defaultIfEmpty("")
                        .flatMap(body -> credentialsVerdict(response.statusCode(), body, userId)));
    }

    private Mono<Boolean> credentialsVerdict(HttpStatusCode status, String body, String userId) {
        if (status.is5xxServerError()) {
            log.warn("Credential lookup failed: status={} user={}", status.value(), userId);
            return Mono.error(new TransientKeycloakException(
                    "credential lookup answered " + status.value()));
        }
        if (!status.is2xxSuccessful()) {
            log.warn("Credential lookup failed: status={} user={}", status.value(), userId);
            return Mono.error(new IllegalStateException(
                    "credential lookup answered " + status.value()));
        }
        JsonNode credentials = JSON.readTree(body);
        if (!credentials.isArray()) {
            // A non-array node iterates FIELD VALUES, which would read an error
            // payload's properties as if they were credentials.
            log.warn("Credential lookup answered a non-array body: {}", body);
            return Mono.error(new IllegalStateException("credential body was not an array"));
        }
        for (JsonNode credential : credentials) {
            if ("password".equals(credential.path("type").asString())) {
                return Mono.just(Boolean.TRUE);
            }
        }
        return Mono.just(Boolean.FALSE);
    }

    @Override
    public Mono<Map<String, String>> findRealmRole(String roleName) {
        return freshAdminToken()
                .flatMap(token -> retryTransient(findRealmRoleWith(token, roleName)));
    }

    private Mono<Map<String, String>> findRealmRoleWith(String token, String roleName) {
        return webClient.get()
                .uri(rolesUri + "/" + roleName)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .exchangeToMono(response -> response.bodyToMono(String.class)
                        .defaultIfEmpty("")
                        .flatMap(body -> {
                            if (!response.statusCode().is2xxSuccessful()) {
                                log.warn("Role lookup failed: status={} role={}",
                                        response.statusCode().value(), roleName);
                                return Mono.error(statusFailure("role lookup",
                                        response.statusCode().value()));
                            }
                            JsonNode role = JSON.readTree(body);
                            String id = role.path("id").asString();
                            String name = role.path("name").asString();
                            if (!StringUtils.hasText(id) || !StringUtils.hasText(name)) {
                                log.warn("Role lookup body carried no id/name: {}", body);
                                return Mono.error(new IllegalStateException(
                                        "role lookup body carried no id/name"));
                            }
                            return Mono.just(Map.of("id", id, "name", name));
                        }));
    }

    @Override
    public Mono<Void> assignRealmRoles(String userId, List<Map<String, String>> roles) {
        // Idempotent by contract: mapping an already-assigned realm role is a no-op 204.
        return freshAdminToken()
                .flatMap(token -> retryTransient(assignRealmRolesWith(token, userId, roles)));
    }

    private Mono<Void> assignRealmRolesWith(String token, String userId, List<Map<String, String>> roles) {
        return webClient.post()
                .uri(usersUri + "/" + userId + "/role-mappings/realm")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .bodyValue(roles)
                .exchangeToMono(response -> {
                    if (response.statusCode().value() != 204) {
                        log.warn("Role assignment failed: status={} user={}",
                                response.statusCode().value(), userId);
                        return Mono.error(statusFailure("role assignment",
                                response.statusCode().value()));
                    }
                    return Mono.empty();
                });
    }

    /**
     * Bulk-operation session sharing one admin token, memoized for the session's
     * lifetime. The session object is short-lived by construction (one per import
     * request) and never stored in a field, so concurrent imports cannot share
     * or race on a token. A token-fetch failure fails every operation with the
     * same error — the import aborts before any row runs.
     */
    @Override
    public BulkOperations bulk() {
        // Retry the fetch BEFORE caching: cache() memoizes a failure too, so a
        // retry outside the cache would replay the same error instead of refetching.
        // Review: 2026-10-29, bulk-import overload incident.
        Mono<String> token = freshAdminToken().cache();
        return new BulkOperations() {
            @Override
            public Mono<String> findUserId(String username) {
                return token.flatMap(t -> retryTransient(findUserIdWith(t, username)));
            }

            @Override
            public Mono<String> createUser(String username, String email, String firstName,
                    String lastName) {
                return token.flatMap(t -> createUserWith(t, username, email, firstName, lastName));
            }

            @Override
            public Mono<Boolean> setTemporaryPassword(String userId, String newPassword) {
                return token.flatMap(t -> retryTransient(
                        setTemporaryPasswordWith(t, userId, newPassword)));
            }

            @Override
            public Mono<Boolean> hasPassword(String userId) {
                return token.flatMap(t -> retryTransient(hasPasswordWith(t, userId)));
            }

            @Override
            public Mono<Map<String, String>> findRealmRole(String roleName) {
                return token.flatMap(t -> retryTransient(findRealmRoleWith(t, roleName)));
            }

            @Override
            public Mono<Void> assignRealmRoles(String userId, List<Map<String, String>> roles) {
                return token.flatMap(t -> retryTransient(assignRealmRolesWith(t, userId, roles)));
            }
        };
    }

    /**
     * Decides what the token endpoint's answer means for the CURRENT password. The issued
     * tokens are dropped on the floor here — verification must not disturb any session or
     * leave a token behind.
     *
     * @return {@code TRUE} = correct (including the forced-change case below),
     *         {@code FALSE} = rejected; error = none of the expected shapes, which the
     *         service reports as {@code identity_provider_unavailable} rather than lying
     *         about the credentials
     */
    private Mono<Boolean> passwordVerdict(HttpStatusCode status, String body) {
        if (status.is2xxSuccessful()) {
            return Mono.just(Boolean.TRUE);
        }
        // A required action is pending (most often UPDATE_PASSWORD = forced change; the
        // marker also covers VERIFY_PROFILE / VERIFY_EMAIL / CONFIGURE_TOTP): Keycloak
        // answers invalid_grant with this description although the password IS correct,
        // so the change can proceed (UC-14 flow A).
        if (body.contains(PENDING_REQUIRED_ACTION_MARKER)) {
            return Mono.just(Boolean.TRUE);
        }
        // Keycloak 26.x answers invalid credentials with 401 on some releases and 400 on
        // others (26.0 -> 401 in the release/26.0 source; Red Hat's KB and some later
        // builds -> 400; a live 401 capture is recorded in PASSWORD-GATEWAY-RUNBOOK §6.2).
        // Do NOT "clean up" one branch as dead: the deployed build decides, so accepting
        // both keeps every wrong-password case a clean current_password_invalid instead of
        // a 502, while a body without those markers (invalid_client, 5xx) still falls
        // through to provider trouble.
        // Review: 2026-10-03, PASSWORD-GATEWAY-RUNBOOK §6.2 live verification;
        // 2026-10-04, Pullfrog review (version-dependent status).
        if (status.value() == 400 || status.value() == 401) {
            if (body.contains("invalid_grant") || body.contains("Invalid user credentials")) {
                return Mono.just(Boolean.FALSE);
            }
        }
        // Anything else (misconfigured client, 5xx, …) is provider trouble. Mapping it to
        // current_password_invalid would tell the user their password is wrong when we
        // simply do not know.
        log.warn("Unexpected token-endpoint answer: status={} body={}", status.value(), body);
        return Mono.error(new IllegalStateException("token endpoint answered " + status.value()));
    }

    /**
     * @param byEmail which field identifies the caller — {@code email} when step 2
     *                authenticated an address, {@code username} otherwise
     * @return the id of the user whose identifying field equals the search value, or
     *         empty when Keycloak found nobody — the service maps empty to the same
     *         {@code current_password_invalid} as a wrong password, which is what keeps
     *         the endpoint from becoming an oracle. A non-2xx answer is provider
     *         trouble, never "user does not exist".
     *
     * <p>The match is exact and case-insensitive instead of taking {@code users[0]}:
     * Keycloak's user search is a "starts with" query, so the first row can be a
     * DIFFERENT account whenever another username shares the prefix — and that is the
     * account {@code reset-password} would overwrite, using a password that was only
     * verified against the caller's own. Review: 2026-10-03, Phase 1 plan.
     */
    private Mono<String> userLookupVerdict(HttpStatusCode status, String body, String wanted,
            boolean byEmail) {
        if (!status.is2xxSuccessful()) {
            log.warn("User lookup failed: status={} body={}", status.value(), body);
            // Keycloak's streaming user search answers 400 "Cannot parse the JSON" when
            // its user-list read fails under load (keycloak#34857). That is NOT "user
            // does not exist": treating it as empty let a duplicate reach createUser,
            // whose 409 then hid the account. Retry it, and if it persists report
            // provider trouble instead of inventing a verdict.
            // Review: 2026-10-29, bulk-import overload incident.
            if (status.is5xxServerError()
                    || (status.value() == 400 && body != null
                            && body.contains("Cannot parse the JSON"))) {
                return Mono.error(new TransientKeycloakException(
                        "user lookup answered " + status.value()));
            }
            return Mono.error(new IllegalStateException("user lookup answered " + status.value()));
        }
        JsonNode users = JSON.readTree(body);
        if (!users.isArray()) {
            // Guarded because a non-array Node iterates FIELD VALUES, which would read an
            // error payload's properties as if they were users.
            log.warn("User lookup answered a non-array body: {}", body);
            return Mono.error(new IllegalStateException("user lookup body was not an array"));
        }
        String field = byEmail ? "email" : "username";
        for (JsonNode user : users) {
            if (!wanted.equalsIgnoreCase(user.path(field).asString())) {
                continue;
            }
            String id = user.path("id").asString();
            return StringUtils.hasText(id) ? Mono.just(id) : Mono.empty();
        }
        return Mono.empty();
    }

    /**
     * @return {@code TRUE} = applied (204), {@code FALSE} = Keycloak rejected the new
     *         password (400 — realm password policy), which the service reports as
     *         {@code weak_password}; every other status is provider trouble (502)
     */
    private Mono<Boolean> resetVerdict(HttpStatusCode status, String body) {
        if (status.value() == 204) {
            return Mono.just(Boolean.TRUE);
        }
        if (status.value() == 400) {
            log.debug("Keycloak rejected the new password: {}", body);
            return Mono.just(Boolean.FALSE);
        }
        log.warn("reset-password failed: status={} body={}", status.value(), body);
        // 5xx is overload, not a verdict about the password: answering FALSE would
        // report weak_password, blaming the CSV for a server hiccup and leaving the
        // account without a credential. Retry it; a persistent failure stays an error.
        // Review: 2026-10-29, bulk-import overload incident.
        if (status.is5xxServerError()) {
            return Mono.error(new TransientKeycloakException(
                    "reset-password answered " + status.value()));
        }
        return Mono.error(new IllegalStateException("reset-password answered " + status.value()));
    }

    /**
     * Service-account token for the admin API (client_credentials of the confidential
     * admin client). Fetched per call on purpose: this endpoint is low-volume, and a
     * cached token would add expiry bookkeeping for no measurable gain.
     */
    private Mono<String> adminAccessToken() {
        return webClient.post()
                .uri(tokenEndpoint)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .bodyValue(clientCredentialsForm(properties.adminClientId(), properties.adminClientSecret()))
                .exchangeToMono(response -> response.bodyToMono(String.class)
                        .defaultIfEmpty("")
                        .flatMap(body -> {
                            if (!response.statusCode().is2xxSuccessful()) {
                                log.warn("Admin token grant failed: status={} body={}",
                                        response.statusCode().value(), body);
                                return Mono.error(statusFailure("admin token endpoint",
                                        response.statusCode().value()));
                            }
                            String token = JSON.readTree(body).path("access_token").asString();
                            return StringUtils.hasText(token)
                                    ? Mono.just(token)
                                    : Mono.error(new IllegalStateException(
                                            "admin token response carried no access_token"));
                        }));
    }

    /**
     * An admin token with the transient-failure retry already applied. Callers that
     * repeat an idempotent operation wrap only the OPERATION, so a retry reuses this
     * token instead of asking for a new one.
     */
    private Mono<String> freshAdminToken() {
        return retryTransient(adminAccessToken());
    }

    /**
     * Retries only what a retry cannot make worse: connection failures and Keycloak's
     * overload answers. Backoff is capped so one slow row cannot stall the batch.
     * Attempts, not retries — three tries cover a hiccup without hiding a real outage.
     * Review: 2026-10-29, bulk-import overload incident.
     */
    private static <T> Mono<T> retryTransient(Mono<T> call) {
        return call.retryWhen(Retry.backoff(TRANSIENT_MAX_ATTEMPTS - 1L, TRANSIENT_BACKOFF)
                .maxBackoff(TRANSIENT_MAX_BACKOFF)
                .filter(KeycloakAdminWebClient::isTransient)
                .onRetryExhaustedThrow((spec, signal) -> signal.failure()));
    }

    private static boolean isTransient(Throwable error) {
        return error instanceof TransientKeycloakException
                || error instanceof WebClientRequestException;
    }

    /** 5xx is overload; every other status is a genuine refusal and fails at once. */
    private static IllegalStateException statusFailure(String call, int status) {
        if (status >= 500) {
            return new TransientKeycloakException(call + " answered " + status);
        }
        return new IllegalStateException(call + " answered " + status);
    }

    /**
     * ROPC form of the password-verification client.
     *
     * <p>{@code client_secret} is added ONLY when a secret is configured: since Phase 3
     * (2026-10-03, D11) the gateway verifies against the confidential client
     * {@code wgs-password-verify} and must authenticate itself. A blank
     * {@code KEYCLOAK_PASSWORD_CLIENT_SECRET} still means "nothing secret is appended to
     * the form" — but that is NOT a working fallback: a confidential client answers that
     * request with {@code unauthorized_client} -> 502 identity_provider_unavailable,
     * which is deliberate fail-loud for an unconfigured deployment (the pre-Phase-3
     * public-client behaviour died with fe Direct Access Grants). Wire the env var
     * (runbook §10.2) instead of expecting the form shape to compensate.
     * Review: 2026-10-03, Phase 3 PKCE plan (D11); 2026-10-04, Pullfrog review
     * (shipped default contradicted Phase 3).
     */
    static MultiValueMap<String, String> passwordGrantForm(String clientId, String clientSecret,
            String username, String password) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "password");
        form.add("client_id", clientId);
        if (StringUtils.hasText(clientSecret)) {
            form.add("client_secret", clientSecret);
        }
        form.add("username", username);
        form.add("password", password);
        return form;
    }

    static MultiValueMap<String, String> clientCredentialsForm(String clientId, String clientSecret) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        return form;
    }

    /**
     * FLAT credential payload. Keycloak answers 400 when this is wrapped in a
     * {@code "credential"} property, so the shape is asserted by
     * {@code KeycloakAdminWebClientTest}. LinkedHashMap keeps insertion order stable for
     * that assertion.
     *
     * <p>Review: 2026-10-03, Phase 1 plan keycloak-password-gateway-plan-2026-10-03-v1.
     */
    static Map<String, Object> resetCredential(String newPassword) {
        Map<String, Object> credential = new LinkedHashMap<>();
        credential.put("type", "password");
        credential.put("value", newPassword);
        credential.put("temporary", false);
        return credential;
    }

    /**
     * Same flat shape, but {@code temporary: true} — Keycloak stamps the
     * {@code UPDATE_PASSWORD} required action itself (verified in
     * {@code UserResource.resetPassword}, Keycloak 26), so the credential
     * doubles as the forced-change trigger for freshly imported accounts.
     */
    static Map<String, Object> temporaryCredential(String newPassword) {
        Map<String, Object> credential = new LinkedHashMap<>();
        credential.put("type", "password");
        credential.put("value", newPassword);
        credential.put("temporary", true);
        return credential;
    }
}

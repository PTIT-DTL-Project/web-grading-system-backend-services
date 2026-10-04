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
import reactor.core.publisher.Mono;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import vn.edu.ptit.web_grading_system.api_gateway.config.KeycloakAdminProperties;

import java.util.LinkedHashMap;
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

    private static final JsonMapper JSON = JsonMapper.shared();

    private final KeycloakAdminProperties properties;
    private final WebClient webClient;
    private final String tokenEndpoint;
    private final String usersUri;

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
        return adminAccessToken().flatMap(token -> webClient.get()
                .uri(usersUri + query, username)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .exchangeToMono(response -> response.bodyToMono(String.class)
                        .defaultIfEmpty("")
                        .flatMap(body -> userLookupVerdict(response.statusCode(), body,
                                username, byEmail))));
    }

    @Override
    public Mono<Boolean> resetPassword(String userId, String newPassword) {
        return adminAccessToken().flatMap(token -> webClient.put()
                .uri(usersUri + "/" + userId + "/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .bodyValue(resetCredential(newPassword))
                .exchangeToMono(response -> response.bodyToMono(String.class)
                        .defaultIfEmpty("")
                        .flatMap(body -> resetVerdict(response.statusCode(), body))));
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
                                return Mono.error(new IllegalStateException(
                                        "admin token endpoint answered " + response.statusCode().value()));
                            }
                            String token = JSON.readTree(body).path("access_token").asString();
                            return StringUtils.hasText(token)
                                    ? Mono.just(token)
                                    : Mono.error(new IllegalStateException(
                                            "admin token response carried no access_token"));
                        }));
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
}

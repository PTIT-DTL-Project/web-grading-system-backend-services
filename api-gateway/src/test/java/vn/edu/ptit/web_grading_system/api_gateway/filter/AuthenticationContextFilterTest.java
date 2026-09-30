package vn.edu.ptit.web_grading_system.api_gateway.filter;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import vn.edu.ptit.web_grading_system.api_gateway.config.GatewayTrustProperties;

import java.security.Principal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the anti-spoofing contract added for Review: 2026-09-30, Pullfrog review
 * (feat/DAT-8): client-supplied identity headers are stripped before the principal is
 * inspected, identity is only ever injected from a validated JWT, without a configured
 * secret nothing is injected at all (fail closed), and roles only travel after the
 * allowlist has filtered them.
 */
class AuthenticationContextFilterTest {

	private static final String SECRET = "test-gateway-secret";
	private static final String USER_ID = "2d93941a-4221-458b-a03d-43bd6315d02e";
	private static final String EMAIL = "lecturer@ptit.edu.vn";
	private static final List<String> ALLOWED_ROLES = List.of("LECTURER", "STUDENT");

	private final GatewayTrustProperties properties = new GatewayTrustProperties(SECRET, ALLOWED_ROLES);
	private final AuthenticationContextFilter filter = new AuthenticationContextFilter(properties);

	/** Captures the exchange the filter hands to the rest of the chain. */
	private static final class CapturingChain implements GatewayFilterChain {
		private ServerWebExchange forwarded;

		@Override
		public Mono<Void> filter(ServerWebExchange exchange) {
			this.forwarded = exchange;
			return Mono.empty();
		}
	}

	private CapturingChain run(MockServerHttpRequest.BaseBuilder<?> request, Principal principal) {
		return run(request, principal, filter);
	}

	private CapturingChain run(MockServerHttpRequest.BaseBuilder<?> request, Principal principal,
			AuthenticationContextFilter target) {
		MockServerWebExchange.Builder builder = MockServerWebExchange.builder(request);
		if (principal != null) {
			builder.principal(principal);
		}
		CapturingChain chain = new CapturingChain();
		target.filter(builder.build(), chain).block();
		return chain;
	}

	private static JwtAuthenticationToken jwtPrincipal() {
		return jwtPrincipal(null);
	}

	/**
	 * @param realmRoles value for the Keycloak {@code realm_access.roles} claim; {@code null}
	 *                   omits the claim entirely, {@link #wrongShapedRealmClaim()} covers a
	 *                   present-but-unusable one
	 */
	private static JwtAuthenticationToken jwtPrincipal(List<String> realmRoles) {
		Map<String, Object> claims = new HashMap<>();
		claims.put("sub", USER_ID);
		claims.put("email", EMAIL);
		if (realmRoles != null) {
			claims.put("realm_access", Map.of("roles", realmRoles));
		}
		Jwt jwt = new Jwt("token-value", Instant.now(), Instant.now().plusSeconds(300),
				Map.of("alg", "RS256"), claims);
		return new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_STUDENT")));
	}

	private static MockServerHttpRequest.BaseBuilder<?> requestWithClientHeaders() {
		return MockServerHttpRequest.get("/api/v1/classes")
				.header("X-User-Id", "attacker-chosen-uuid")
				.header("X-User-Email", "attacker@evil.example")
				.header("X-User-Roles", "ROLE_ADMIN")
				.header("X-Gateway-Secret", "guessed-secret");
	}

	@Test
	void stripsClientIdentityHeaders_evenWhenThereIsNoPrincipal() {
		CapturingChain chain = run(requestWithClientHeaders(), null);

		assertThat(chain.forwarded).isNotNull();
		HttpHeaders headers = chain.forwarded.getRequest().getHeaders();
		assertThat(headers.containsHeader("X-User-Id")).isFalse();
		assertThat(headers.containsHeader("X-User-Email")).isFalse();
		assertThat(headers.containsHeader("X-User-Roles")).isFalse();
		assertThat(headers.containsHeader("X-Gateway-Secret")).isFalse();
	}

	@Test
	void replacesClientHeadersWithValidatedJwtIdentity() {
		CapturingChain chain = run(requestWithClientHeaders(), jwtPrincipal());

		HttpHeaders headers = chain.forwarded.getRequest().getHeaders();
		assertThat(headers.get("X-User-Id")).containsExactly(USER_ID);
		assertThat(headers.get("X-User-Email")).containsExactly(EMAIL);
		assertThat(headers.get("X-Gateway-Secret")).containsExactly(SECRET);
		// No realm_access claim on this token, so no roles are asserted — the client's
		// ROLE_ADMIN must not survive either.
		assertThat(headers.containsHeader("X-User-Roles")).isFalse();
	}

	@Test
	void forwardsOnlyAllowlistedRealmRoles() {
		CapturingChain chain = run(requestWithClientHeaders(),
				jwtPrincipal(List.of("LECTURER", "offline_access", "STUDENT", "account")));

		HttpHeaders headers = chain.forwarded.getRequest().getHeaders();
		// The attacker's ROLE_ADMIN is replaced, not appended to, and noise roles are dropped.
		assertThat(headers.get("X-User-Roles")).containsExactly("LECTURER,STUDENT");
	}

	@Test
	void omitsRoleHeader_whenRealmRolesMatchNothingOnTheAllowlist() {
		CapturingChain chain = run(requestWithClientHeaders(),
				jwtPrincipal(List.of("offline_access", "account")));

		assertThat(chain.forwarded.getRequest().getHeaders().containsHeader("X-User-Roles")).isFalse();
	}

	@Test
	void omitsRoleHeader_whenAllowlistIsNotConfigured() {
		// Fail closed: no configured policy must mean no roles, not every role.
		AuthenticationContextFilter unconfigured = new AuthenticationContextFilter(
				new GatewayTrustProperties(SECRET, List.of()));

		CapturingChain chain = run(requestWithClientHeaders(),
				jwtPrincipal(List.of("LECTURER")), unconfigured);

		assertThat(chain.forwarded.getRequest().getHeaders().containsHeader("X-User-Roles")).isFalse();
	}

	@Test
	void omitsRoleHeader_whenRealmClaimHasTheWrongShape() {
		// realm_access present but "roles" is not a list — a malformed token degrades to
		// "no roles", never to "all roles".
		Jwt malformed = new Jwt("token-value", Instant.now(), Instant.now().plusSeconds(300),
				Map.of("alg", "RS256"),
				Map.of("sub", USER_ID, "email", EMAIL,
						"realm_access", Map.of("roles", "LECTURER")));

		CapturingChain chain = run(requestWithClientHeaders(),
				new JwtAuthenticationToken(malformed, List.of(new SimpleGrantedAuthority("ROLE_STUDENT"))));

		assertThat(chain.forwarded.getRequest().getHeaders().containsHeader("X-User-Roles")).isFalse();
	}

	@Test
	void withoutConfiguredSecret_injectsNoIdentityAtAll() {
		AuthenticationContextFilter unconfigured = new AuthenticationContextFilter(
				new GatewayTrustProperties("", ALLOWED_ROLES));

		CapturingChain chain = run(requestWithClientHeaders(), jwtPrincipal(List.of("LECTURER")), unconfigured);

		assertThat(chain.forwarded).isNotNull();
		HttpHeaders headers = chain.forwarded.getRequest().getHeaders();
		assertThat(headers.containsHeader("X-User-Id")).isFalse();
		assertThat(headers.containsHeader("X-User-Email")).isFalse();
		assertThat(headers.containsHeader("X-User-Roles")).isFalse();
		assertThat(headers.containsHeader("X-Gateway-Secret")).isFalse();
	}
}

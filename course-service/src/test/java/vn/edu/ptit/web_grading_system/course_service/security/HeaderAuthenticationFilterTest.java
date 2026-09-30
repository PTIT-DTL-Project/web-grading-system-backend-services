package vn.edu.ptit.web_grading_system.course_service.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import vn.edu.ptit.web_grading_system.course_service.config.GatewayTrustProperties;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the header-trust boundary added for Review: 2026-09-30, Pullfrog review
 * (feat/DAT-8): a caller that cannot present the gateway secret must not get a
 * SecurityContext, permitAll paths must never get one, and roles are only ever read
 * from a request the secret has already vouched for.
 */
class HeaderAuthenticationFilterTest {

    private static final String SECRET = "test-gateway-secret";
    private static final String USER_ID = "2d93941a-4221-458b-a03d-43bd6315d02e";

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private static HeaderAuthenticationFilter filter(String configuredSecret) {
        return new HeaderAuthenticationFilter(new GatewayTrustProperties(configuredSecret));
    }

    private static MockHttpServletRequest protectedRequest() {
        return new MockHttpServletRequest("POST", "/api/v1/classes");
    }

    private static List<String> authorities() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(authentication);
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toList());
    }

    // --- shouldNotFilter -------------------------------------------------------------

    @Test
    void permitAllPathsAreNeverFiltered_evenWithValidSecret() {
        HeaderAuthenticationFilter filter = filter(SECRET);

        // Paths shared by all three services' PUBLIC_PATHS so this fixture stays valid
        // in course, result and submission alike.
        for (String path : new String[]{
                "/actuator/health",
                "/api/v1/internal/assignments/any-id",
                "/api/v1/internal/results/weighted",
                "/v3/api-docs/swagger-config",
                "/swagger-ui.html"}) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
            request.addHeader(HeaderAuthenticationFilter.HEADER_GATEWAY_SECRET, SECRET);
            assertTrue(filter.shouldNotFilter(request), "expected skip on " + path);
        }
    }

    @Test
    void protectedPathWithoutSecret_isSkippedSoItStaysAnonymous() {
        assertTrue(filter(SECRET).shouldNotFilter(protectedRequest()));
    }

    @Test
    void protectedPathWithWrongSecret_isSkippedSoItStaysAnonymous() {
        MockHttpServletRequest request = protectedRequest();
        request.addHeader(HeaderAuthenticationFilter.HEADER_GATEWAY_SECRET, "not-the-secret");
        assertTrue(filter(SECRET).shouldNotFilter(request));
    }

    @Test
    void protectedPathWithValidSecret_isFiltered() {
        MockHttpServletRequest request = protectedRequest();
        request.addHeader(HeaderAuthenticationFilter.HEADER_GATEWAY_SECRET, SECRET);
        assertFalse(filter(SECRET).shouldNotFilter(request));
    }

    @Test
    void blankConfiguredSecret_neverTrustsAnyCaller() {
        // Fail closed: "no secret configured" must not mean "trust everyone".
        MockHttpServletRequest request = protectedRequest();
        request.addHeader(HeaderAuthenticationFilter.HEADER_GATEWAY_SECRET, "");
        assertTrue(filter("").shouldNotFilter(request));
        assertTrue(filter(null).shouldNotFilter(request));
    }

    // --- doFilterInternal ------------------------------------------------------------

    @Test
    void validGatewayRequest_populatesContextFromForwardedRoles() throws Exception {
        MockHttpServletRequest request = protectedRequest();
        request.addHeader(HeaderAuthenticationFilter.HEADER_USER_ID, USER_ID);
        request.addHeader(HeaderAuthenticationFilter.HEADER_USER_EMAIL, "a@b.c");
        request.addHeader(HeaderAuthenticationFilter.HEADER_GATEWAY_SECRET, SECRET);
        request.addHeader(HeaderAuthenticationFilter.HEADER_USER_ROLES, "LECTURER,STUDENT");

        filter(SECRET).doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(authentication, "gateway-attested request should authenticate");
        assertTrue(authentication.isAuthenticated());
        assertEquals(List.of("ROLE_LECTURER", "ROLE_STUDENT"), authorities());
        assertNotNull(authentication.getPrincipal());
        assertEquals(UUID.fromString(USER_ID), ((UserPrincipal) authentication.getPrincipal()).userId());
    }

    @Test
    void noRolesHeader_leavesContextWithNoAuthorities() throws Exception {
        // Fail closed: no roles asserted means no @PreAuthorize can pass.
        MockHttpServletRequest request = protectedRequest();
        request.addHeader(HeaderAuthenticationFilter.HEADER_USER_ID, USER_ID);
        request.addHeader(HeaderAuthenticationFilter.HEADER_GATEWAY_SECRET, SECRET);

        filter(SECRET).doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertTrue(authorities().isEmpty());
    }

    @Test
    void incomingRolePrefixIsCollapsedAndDuplicatesDropped() throws Exception {
        // The gateway forwards bare role names, but a caller that already added ROLE_ (or
        // repeated a role) must not end up with ROLE_ROLE_LECTURER or two authorities.
        MockHttpServletRequest request = protectedRequest();
        request.addHeader(HeaderAuthenticationFilter.HEADER_USER_ID, USER_ID);
        request.addHeader(HeaderAuthenticationFilter.HEADER_GATEWAY_SECRET, SECRET);
        request.addHeader(HeaderAuthenticationFilter.HEADER_USER_ROLES, " ROLE_LECTURER ,  ,LECTURER,STUDENT ");

        filter(SECRET).doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertEquals(List.of("ROLE_LECTURER", "ROLE_STUDENT"), authorities());
    }

    @Test
    void requestWithoutSecret_leavesContextEmpty() throws Exception {
        MockHttpServletRequest request = protectedRequest();
        request.addHeader(HeaderAuthenticationFilter.HEADER_USER_ID, USER_ID);
        request.addHeader(HeaderAuthenticationFilter.HEADER_USER_ROLES, "LECTURER");

        filter(SECRET).doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void malformedUserId_leavesContextEmpty() throws Exception {
        MockHttpServletRequest request = protectedRequest();
        request.addHeader(HeaderAuthenticationFilter.HEADER_USER_ID, "not-a-uuid");
        request.addHeader(HeaderAuthenticationFilter.HEADER_GATEWAY_SECRET, SECRET);
        request.addHeader(HeaderAuthenticationFilter.HEADER_USER_ROLES, "LECTURER");

        filter(SECRET).doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }
}

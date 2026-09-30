package vn.edu.ptit.web_grading_system.submission_service.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import vn.edu.ptit.web_grading_system.submission_service.config.GatewayTrustProperties;
import vn.edu.ptit.web_grading_system.submission_service.config.SecurityConfig;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Filter that parses identity headers injected by the API Gateway (X-User-Id,
 * X-User-Email, X-User-Roles) and builds the Spring SecurityContext.
 *
 * <p>Two gates apply before any context is populated:
 * <ul>
 *   <li>the path must not match {@link SecurityConfig#PUBLIC_PATHS} — permitAll prefixes
 *       stay context-free so anything added under them later cannot inherit
 *       caller-supplied identity;</li>
 *   <li>the request must carry the configured {@code X-Gateway-Secret} — a direct caller
 *       that does not present it is left anonymous and fails
 *       {@code anyRequest().authenticated()} with 401.</li>
 * </ul>
 *
 * <p>Roles ride the same gate as identity (Review: 2026-09-30, Pullfrog review feat/DAT-8).
 * Before slice 1 the gateway forwarded an unverified {@code ROLE_}-prefixed header that any
 * direct caller could mint, and nothing consumed it; roles are now only read once the trust
 * secret has matched, and only after the gateway allowlisted them. A caller able to forge
 * them already holds the secret that lets them forge {@code X-User-Id}, so this adds no new
 * privilege. A missing header means no authorities, which is what makes every
 * {@code @PreAuthorize} fail closed.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HeaderAuthenticationFilter extends OncePerRequestFilter {

    public static final String HEADER_USER_ID = "X-User-Id";
    public static final String HEADER_USER_EMAIL = "X-User-Email";
    public static final String HEADER_USER_ROLES = "X-User-Roles";
    public static final String HEADER_GATEWAY_SECRET = "X-Gateway-Secret";

    private static final String ROLE_PREFIX = "ROLE_";

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    private final GatewayTrustProperties properties;

    /** Reports a missing secret once instead of once per request. */
    private final AtomicBoolean secretWarned = new AtomicBoolean(false);

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        for (String pattern : SecurityConfig.PUBLIC_PATHS) {
            if (PATH_MATCHER.match(pattern, path)) {
                return true;
            }
        }
        return !hasGatewaySecret(request);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String userIdStr = request.getHeader(HEADER_USER_ID);
        String email = request.getHeader(HEADER_USER_EMAIL);

        if (StringUtils.hasText(userIdStr) && !"anonymous".equalsIgnoreCase(userIdStr.trim())) {
            try {
                UUID userId = UUID.fromString(userIdStr.trim());
                List<GrantedAuthority> authorities = parseRoles(request.getHeader(HEADER_USER_ROLES));
                UserPrincipal principal = new UserPrincipal(userId, email, authorities);
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(principal, null, authorities);

                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (IllegalArgumentException e) {
                log.warn("Invalid UUID format for X-User-Id header: {}", userIdStr);
                SecurityContextHolder.clearContext();
            }
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Splits the gateway's comma-separated {@code X-User-Roles} into {@code ROLE_}-prefixed
     * authorities. Any prefix the caller already added is stripped first so a forwarded
     * {@code ROLE_LECTURER} can never become {@code ROLE_ROLE_LECTURER}; blank entries and
     * duplicates are dropped. A missing header yields an empty list — fail closed.
     */
    private static List<GrantedAuthority> parseRoles(String header) {
        if (!StringUtils.hasText(header)) {
            return List.of();
        }
        return Arrays.stream(header.split(","))
                .map(String::trim)
                .map(role -> role.startsWith(ROLE_PREFIX) ? role.substring(ROLE_PREFIX.length()) : role)
                .filter(role -> !role.isEmpty())
                .map(role -> (GrantedAuthority) new SimpleGrantedAuthority(ROLE_PREFIX + role))
                .distinct()
                .toList();
    }

    /**
     * Constant-time comparison so a caller cannot probe the secret byte by byte. A blank
     * configuration is rejected outright — fail closed, never "no secret configured = open".
     */
    private boolean hasGatewaySecret(HttpServletRequest request) {
        String expected = properties.secret();
        if (!StringUtils.hasText(expected)) {
            if (secretWarned.compareAndSet(false, true)) {
                log.error("gateway.security.secret is blank — every authenticated request "
                        + "will be rejected. Set GATEWAY_TRUSTED_SECRET.");
            }
            return false;
        }
        String provided = request.getHeader(HEADER_GATEWAY_SECRET);
        if (!StringUtils.hasText(provided)) {
            return false;
        }
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                provided.getBytes(StandardCharsets.UTF_8));
    }
}

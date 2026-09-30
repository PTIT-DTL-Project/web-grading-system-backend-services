package vn.edu.ptit.web_grading_system.submission_service.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

public final class SecurityUtils {

    private SecurityUtils() {}

    public static Optional<UUID> getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return Optional.empty();
        }
        if (auth.getPrincipal() instanceof UserPrincipal principal) {
            return Optional.ofNullable(principal.userId());
        }
        try {
            return Optional.of(UUID.fromString(auth.getName()));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public static Optional<UserPrincipal> getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserPrincipal principal) {
            return Optional.of(principal);
        }
        return Optional.empty();
    }

    public static List<String> getCurrentUserRoles() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return Collections.emptyList();
        }
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toList());
    }

    /**
     * Generic fail-closed role probe: accepts either {@code "LECTURER"} or
     * {@code "ROLE_LECTURER"}; no authentication, or none carrying the role, means
     * {@code false}.
     *
     * <p>Review: 2026-09-30, Pullfrog review feat/DAT-8. Rewritten 2026-09-30 with the
     * role-split slice (plan role-split-result-apis-v1.0): this javadoc used to sell it as
     * the way to "read a result if owner <em>or</em> lecturer", i.e. a role bypass inside
     * an ownership check. That bypass had no class scope, so every lecturer could read
     * every class — it is now the anti-pattern §12.9 of the backend skill forbids. A role
     * that legitimately needs wider data gets its own role-gated, owner-scoped route in
     * course-service (see AssignmentGradingController); data services answer strictly for
     * the caller's own rows.
     */
    public static boolean hasRole(String role) {
        if (role == null || role.isBlank()) {
            return false;
        }
        String prefixed = role.startsWith("ROLE_") ? role : "ROLE_" + role;
        return getCurrentUserRoles().contains(prefixed);
    }
}

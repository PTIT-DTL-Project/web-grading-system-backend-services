package vn.edu.ptit.web_grading_system.course_service.security;

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
     * Role check for the places {@code @PreAuthorize} cannot express the rule — e.g. a
     * caller may read a result if they are the owner <em>or</em> hold LECTURER.
     * Accepts either {@code "LECTURER"} or {@code "ROLE_LECTURER"}.
     * No authentication, or none carrying the role, means {@code false} — fail closed.
     *
     * <p>Review: 2026-09-30, Pullfrog review feat/DAT-8.
     */
    public static boolean hasRole(String role) {
        if (role == null || role.isBlank()) {
            return false;
        }
        String prefixed = role.startsWith("ROLE_") ? role : "ROLE_" + role;
        return getCurrentUserRoles().contains(prefixed);
    }
}

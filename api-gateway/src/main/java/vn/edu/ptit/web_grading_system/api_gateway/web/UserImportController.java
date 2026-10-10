package vn.edu.ptit.web_grading_system.api_gateway.web;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebInputException;
import reactor.core.publisher.Mono;
import vn.edu.ptit.web_grading_system.api_gateway.service.UserImportException;
import vn.edu.ptit.web_grading_system.api_gateway.service.UserImportService;

import java.util.List;
import java.util.Map;

/**
 * Bulk account import for the system admin (UC-17): one CSV row becomes one
 * Keycloak user (student or lecturer) with a temporary initial password and a
 * forced change on first login.
 *
 * <p>ADMIN-only by an explicit in-handler role check: the gateway has no
 * {@code @PreAuthorize} support on its own routes (roles are stamped for
 * downstream services instead), so the gate lives here where it is auditable.
 * Bulk-creating lecturers is the reason this stays admin-only — a LECTURER
 * must never mint higher-privilege accounts through it.
 *
 * <p>Unlike the public change-password endpoint this one keeps the resource
 * server (a stale token answers 401 before the handler runs) and answers every
 * failure — including authorization — in the {@code ApiEnvelope} shape.
 *
 * <p>Review: 2026-10-09, bulk user import plan.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
public class UserImportController {

    private final UserImportService userImportService;

    @PostMapping(value = "/import", consumes = "multipart/form-data")
    public Mono<ResponseEntity<ApiEnvelope>> importUsers(
            JwtAuthenticationToken authentication,
            @RequestPart("file") FilePart file) {
        if (!isAdmin(authentication)) {
            return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiEnvelope.error(HttpStatus.FORBIDDEN, "forbidden")));
        }
        return userImportService.importUsers(file)
                .map(summary -> ResponseEntity.ok(new ApiEnvelope(200, "success", summary)));
    }

    /**
     * The caller must carry the realm ADMIN role (with or without the
     * {@code ROLE_} prefix — Keycloak mints both shapes across versions).
     */
    static boolean isAdmin(JwtAuthenticationToken authentication) {
        if (authentication == null) {
            return false;
        }
        Jwt jwt = authentication.getToken();
        Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
        if (realmAccess == null) {
            return false;
        }
        Object roles = realmAccess.get("roles");
        if (!(roles instanceof List<?> list)) {
            return false;
        }
        return list.stream()
                .filter(String.class::isInstance)
                .map(role -> ((String) role).startsWith("ROLE_")
                        ? ((String) role).substring("ROLE_".length())
                        : (String) role)
                .anyMatch("ADMIN"::equals);
    }

    /** Maps every flow failure onto its envelope entry (status + machine code). */
    @ExceptionHandler(UserImportException.class)
    public ResponseEntity<ApiEnvelope> handleUserImport(UserImportException ex) {
        if (ex.getCause() != null) {
            log.warn("user import failed: {}", ex.code(), ex.getCause());
        }
        return ResponseEntity.status(ex.status())
                .body(ApiEnvelope.error(ex.status(), ex.code()));
    }

    /** Safety net: a bug must still answer in the envelope shape. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiEnvelope> handleUnexpected(Exception ex) {
        log.error("Unexpected failure in user import", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiEnvelope.error(HttpStatus.INTERNAL_SERVER_ERROR, "internal_error"));
    }

    /** Unreadable multipart (missing part falls here via ServerWebInputException). */
    @ExceptionHandler(ServerWebInputException.class)
    public ResponseEntity<ApiEnvelope> handleUnreadableBody(ServerWebInputException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiEnvelope.error(HttpStatus.BAD_REQUEST, "validation_failed"));
    }
}

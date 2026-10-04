package vn.edu.ptit.web_grading_system.api_gateway.web;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.ServerWebInputException;
import reactor.core.publisher.Mono;
import vn.edu.ptit.web_grading_system.api_gateway.service.ChangePasswordException;
import vn.edu.ptit.web_grading_system.api_gateway.service.PasswordChangeService;

/**
 * Self-service change-password endpoint (UC-14), hosted in the gateway so the frontend
 * never calls the Keycloak Admin API — the admin secret used to ship in the browser
 * bundle.
 *
 * <p>Reachable WITHOUT a Bearer token (a forced password change has no token yet) and it
 * must never answer {@code 401}: the frontend's axios interceptor treats 401 as "session
 * expired", silent-refreshes and redirects to /login, which loops during the forced
 * change. Every failure is answered as an {@link ApiEnvelope}, including unexpected bugs.
 *
 * <p>Review: 2026-10-03, Phase 1 plan keycloak-password-gateway-plan-2026-10-03-v1.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/account")
@RequiredArgsConstructor
public class ChangePasswordController {

    private final PasswordChangeService passwordChangeService;

    @PostMapping("/change-password")
    public Mono<ResponseEntity<Void>> changePassword(
            @Valid @RequestBody ChangePasswordRequest request,
            ServerWebExchange exchange) {
        return passwordChangeService.changePassword(request)
                .then(Mono.fromSupplier(() -> ResponseEntity.noContent().build()));
    }

    /** Maps every flow failure onto its envelope entry (status + machine code). */
    @ExceptionHandler(ChangePasswordException.class)
    public ResponseEntity<ApiEnvelope> handleChangePassword(ChangePasswordException ex) {
        if (ex.getCause() != null) {
            // The envelope shows only the code, so the cause stays diagnosable in logs.
            log.warn("change-password failed: {}", ex.code(), ex.getCause());
        }
        return ResponseEntity.status(ex.status())
                .body(ApiEnvelope.error(ex.status(), ex.code()));
    }

    /**
     * Blank/missing fields: Spring Framework 7 validates handler arguments inside
     * {@code InvocableHandlerMethod} and answers with this exception type.
     */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiEnvelope> handleValidation(HandlerMethodValidationException ex) {
        return validationFailed();
    }

    /** Unreadable or absent JSON body (subclasses such as WebExchangeBindException too). */
    @ExceptionHandler(ServerWebInputException.class)
    public ResponseEntity<ApiEnvelope> handleUnreadableBody(ServerWebInputException ex) {
        return validationFailed();
    }

    /**
     * Safety net: a bug must still answer in the envelope shape — Spring's default error
     * body has keys the frontend's envelope detection does not accept.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiEnvelope> handleUnexpected(Exception ex) {
        log.error("Unexpected failure in change-password", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiEnvelope.error(HttpStatus.INTERNAL_SERVER_ERROR, "internal_error"));
    }

    private static ResponseEntity<ApiEnvelope> validationFailed() {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiEnvelope.error(HttpStatus.BAD_REQUEST, "validation_failed"));
    }
}

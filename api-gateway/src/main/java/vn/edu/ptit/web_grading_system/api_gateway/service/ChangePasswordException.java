package vn.edu.ptit.web_grading_system.api_gateway.service;

import lombok.Builder;
import org.springframework.http.HttpStatus;

/**
 * A change-password failure, carrying the HTTP status and the machine code the frontend
 * maps to i18n. The controller converts it into the {@code ApiEnvelope} response; the
 * cause (if any) is kept for logging only and never reaches the client.
 *
 * <p>Review: 2026-10-03, Phase 1 plan keycloak-password-gateway-plan-2026-10-03-v1.
 */
public class ChangePasswordException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final HttpStatus status;
    private final String code;

    // @Builder instead of a positional 3-arg constructor — project convention for
    // constructor calls with more than 2 arguments.
    @Builder
    private ChangePasswordException(HttpStatus status, String code, Throwable cause) {
        super(code, cause);
        this.status = status;
        this.code = code;
    }

    /**
     * Wrong current password OR no such user — deliberately the same code on purpose,
     * so the endpoint never becomes a user-enumeration oracle.
     */
    public static ChangePasswordException currentPasswordInvalid() {
        return ChangePasswordException.builder()
                .status(HttpStatus.BAD_REQUEST)
                .code("current_password_invalid")
                .build();
    }

    /** Keycloak's password policy rejected the new password. */
    public static ChangePasswordException weakPassword() {
        return ChangePasswordException.builder()
                .status(HttpStatus.BAD_REQUEST)
                .code("weak_password")
                .build();
    }

    /** A required field is missing or blank. */
    public static ChangePasswordException validationFailed() {
        return ChangePasswordException.builder()
                .status(HttpStatus.BAD_REQUEST)
                .code("validation_failed")
                .build();
    }

    // rateLimited()/429 was removed 2026-10-04: no gateway-side limiter was ever
    // implemented, so the factory was unreachable and the code advertised a control that
    // did not exist. Reintroduce it together with the actual limiter.
    // Review: 2026-10-04, Pullfrog review (RateLimitProperties dead code).

    /** Keycloak was unreachable or answered something the flow cannot interpret. */
    public static ChangePasswordException identityProviderUnavailable(Throwable cause) {
        return ChangePasswordException.builder()
                .status(HttpStatus.BAD_GATEWAY)
                .code("identity_provider_unavailable")
                .cause(cause)
                .build();
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }
}

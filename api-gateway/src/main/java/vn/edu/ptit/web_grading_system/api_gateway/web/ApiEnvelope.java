package vn.edu.ptit.web_grading_system.api_gateway.web;

import org.springframework.http.HttpStatus;

/**
 * The only body this endpoint sends besides {@code 204 No Content}: the frontend detects
 * it by {@code keys ⊆ {status,message,data,error}} with a numeric {@code status}
 * ({@code ENVELOPE_KEYS} in {@code frontend-src/web-grading-system-fe/src/shared/api/http.ts}).
 * {@code message} is a machine code ({@code current_password_invalid}, {@code weak_password},
 * {@code validation_failed}, {@code identity_provider_unavailable})
 * that the frontend maps to i18n — never a sentence to show raw. (A {@code rate_limited}
 * code existed in the draft only and was removed 2026-10-04 with the unreachable 429
 * factory — no limiter was ever built. Re-add both together with the limiter.)
 *
 * <p>Review: 2026-10-03, Phase 1 plan keycloak-password-gateway-plan-2026-10-03-v1;
 * 2026-10-04, Pullfrog review (unreachable rate_limited code).
 */
public record ApiEnvelope(int status, String message, Object data) {

    /**
     * @param status HTTP status, mirrored into the body — the FE reads it from either
     */
    public static ApiEnvelope error(HttpStatus status, String message) {
        return new ApiEnvelope(status.value(), message, null);
    }
}

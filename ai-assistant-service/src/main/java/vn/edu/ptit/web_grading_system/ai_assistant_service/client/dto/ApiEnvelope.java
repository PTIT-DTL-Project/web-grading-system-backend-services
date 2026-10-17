package vn.edu.ptit.web_grading_system.ai_assistant_service.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Mirror of the sibling services' {@code ApiResponse<T>} envelope
 * ({@code {status, message, data, error}}) that their {@code FormatRestResponse}
 * advice wraps around every controller return. This service returns DTOs raw
 * (no envelope of its own), so outbound Feign calls unwrap this wrapper.
 *
 * <p>Review: 2026-10-17, Pullfrog follow-up — without it the Feign clients tried
 * to bind the envelope object to the bare payload type and threw
 * {@code feign.codec.DecodeException} (500, then masked to 401 by the /error
 * re-secure). The AI service talks to the student-facing
 * {@code /api/v1/results/**} and {@code /api/v1/student/assignments/**}
 * endpoints, which are enveloped; there is no raw alternative.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiEnvelope<T> {
    private Integer status;
    private String message;
    private T data;
    private String error;
}

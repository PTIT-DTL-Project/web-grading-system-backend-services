package vn.edu.ptit.web_grading_system.course_service.dto.internal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** Response of test-plan validation. Always returns HTTP 200 so the caller can
 * distinguish validation errors from infrastructure failures. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ValidateQuestionResponse {
    private boolean valid;
    private List<ValidationError> errors;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ValidationError {
        /** JSON path into the request, e.g. steps[2].config.assertions[1].kind */
        private String path;
        /** Human-readable error message from StepConfigValidator */
        private String message;
    }
}

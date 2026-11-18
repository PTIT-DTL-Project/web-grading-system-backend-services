package vn.edu.ptit.web_grading_system.course_service.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import tools.jackson.databind.JsonNode;

import java.util.UUID;

/**
 * Student-facing view of a grading step.
 *
 * <p>Deliberately has no {@code required} field: {@code test_steps.is_required} is
 * executor semantics ("a failure here cascades SKIPPED onto the following steps"),
 * not an instruction that the student must answer this question. Exposing it here
 * (and therefore to the AI assistant's student tools, which forward this payload
 * verbatim to the model) made the agent tell students a step was mandatory.
 * Review: 2026-11-12, lecturer report that the AI confused step-level required
 * with question-level "must submit".
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentStepResponse {
    private UUID id;
    private Integer stepOrder;
    private String name;
    private String description;
    private String type;
    private JsonNode config;
    private Integer weight;
    private Integer timeoutMs;
}

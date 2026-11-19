package vn.edu.ptit.web_grading_system.course_service.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import tools.jackson.databind.JsonNode;

import java.util.UUID;

/**
 * Student-facing view of a grading step. Two fields of {@link StepResponse} are
 * deliberately absent:
 *
 * <ul>
 *   <li>{@code required} — {@code test_steps.is_required} is executor semantics
 *       ("a failure here cascades SKIPPED onto the following steps"), not an
 *       instruction that the student must answer this question. Exposing it here
 *       (and therefore to the AI assistant's student tools, which forward this
 *       payload verbatim to the model) made the agent tell students a step was
 *       mandatory.</li>
 *   <li>{@code expectedResult} — it holds the grader's expected answers, so it is
 *       part of the same "no hidden answer key" boundary as the sanitized
 *       {@code config} (see {@code StudentAssignmentService.sanitizeConfig}).</li>
 * </ul>
 *
 * Review: 2026-11-12, lecturer report that the AI confused step-level required
 * with question-level "must submit"; expectedResult note added per Pullfrog review.
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

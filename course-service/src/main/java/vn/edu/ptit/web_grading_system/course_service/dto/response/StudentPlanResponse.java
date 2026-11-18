package vn.edu.ptit.web_grading_system.course_service.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

/**
 * Student-facing view of a test plan (one "question"/câu kiểm tra) with sanitized steps.
 *
 * <p>Separate from {@link PlanResponse} so the student payload simply cannot carry
 * lecturer/executor-only fields ({@code required} on steps). The AI assistant's
 * student tools forward this body to the model unchanged.
 * Review: 2026-11-12, see {@link StudentStepResponse}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentPlanResponse {
    private UUID id;
    private String name;
    private String description;
    private Integer sequenceOrder;
    private Integer weight;
    private List<StudentStepResponse> steps;
}

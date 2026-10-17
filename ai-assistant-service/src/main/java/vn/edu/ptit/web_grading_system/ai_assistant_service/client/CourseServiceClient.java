package vn.edu.ptit.web_grading_system.ai_assistant_service.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import vn.edu.ptit.web_grading_system.ai_assistant_service.client.dto.ApiEnvelope;
import vn.edu.ptit.web_grading_system.ai_assistant_service.client.dto.AssignmentDetailDto;

import java.util.UUID;

@FeignClient(name = "course-service", url = "${feign.course-service.url}")
public interface CourseServiceClient {

    // Review: 2026-10-17, Pullfrog follow-up — enveloped endpoint
    // ({status,message,data}); bind the wrapper and unwrap in the service.
    @GetMapping("/api/v1/student/assignments/{assignmentId}")
    ApiEnvelope<AssignmentDetailDto> getAssignment(
            @PathVariable("assignmentId") UUID assignmentId,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-Gateway-Secret") String gatewaySecret
    );

    /**
     * The lecturer-facing twin of {@link #getAssignment}, and the project's ownership oracle for
     * a lecturer: that endpoint is {@code @PreAuthorize("hasRole('LECTURER')")} and resolves
     * through an owner-scoped lookup, so a {@code 404} means "this lecturer does not own this
     * assignment". Called with the lecturer's own id (the one the gateway stamped) — nothing is
     * impersonated. Same {@code AssignmentResponse} payload as the student route, so the same
     * mirror binds it.
     *
     * <p>{@code roles} is required, not optional: without {@code X-User-Roles} that endpoint
     * answers {@code 403} (no authorities ⇒ fail closed), which would surface here as a 502.
     * The caller asserts {@code ROLE_LECTURER} only because its own request already passed a
     * {@code hasRole('LECTURER')} gate — it forwards a role it verified, not a claimed one.
     */
    @GetMapping("/api/v1/assignments/{assignmentId}")
    ApiEnvelope<AssignmentDetailDto> getOwnedAssignment(
            @PathVariable("assignmentId") UUID assignmentId,
            @RequestHeader("X-User-Id") String lecturerId,
            @RequestHeader("X-User-Roles") String roles,
            @RequestHeader("X-Gateway-Secret") String gatewaySecret
    );
}

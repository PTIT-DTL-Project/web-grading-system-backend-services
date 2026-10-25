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

    @GetMapping("/api/v1/student/classes")
    vn.edu.ptit.web_grading_system.ai_assistant_service.client.dto.PageDto<vn.edu.ptit.web_grading_system.ai_assistant_service.client.dto.ClassSummaryDto> listStudentClasses(
            @RequestHeader("X-User-Id") String studentId,
            @RequestHeader(value = "X-User-Email", required = false) String email,
            @org.springframework.web.bind.annotation.RequestParam(value = "page", defaultValue = "0") int page,
            @org.springframework.web.bind.annotation.RequestParam(value = "size", defaultValue = "20") int size,
            @RequestHeader(value = "X-Gateway-Secret", required = false) String gatewaySecret
    );

    @GetMapping("/api/v1/classes")
    vn.edu.ptit.web_grading_system.ai_assistant_service.client.dto.PageDto<vn.edu.ptit.web_grading_system.ai_assistant_service.client.dto.ClassSummaryDto> listLecturerClasses(
            @RequestHeader("X-User-Id") String lecturerId,
            @RequestHeader(value = "X-User-Roles", defaultValue = "ROLE_LECTURER") String roles,
            @org.springframework.web.bind.annotation.RequestParam(value = "page", defaultValue = "0") int page,
            @org.springframework.web.bind.annotation.RequestParam(value = "size", defaultValue = "20") int size,
            @RequestHeader(value = "X-Gateway-Secret", required = false) String gatewaySecret
    );

    @GetMapping("/api/v1/student/assignments")
    vn.edu.ptit.web_grading_system.ai_assistant_service.client.dto.PageDto<vn.edu.ptit.web_grading_system.ai_assistant_service.client.dto.AssignmentSummaryDto> listStudentAssignments(
            @RequestHeader("X-User-Id") String studentId,
            @RequestHeader(value = "X-User-Email", required = false) String email,
            @org.springframework.web.bind.annotation.RequestParam(value = "classId", required = false) UUID classId,
            @org.springframework.web.bind.annotation.RequestParam(value = "page", defaultValue = "0") int page,
            @org.springframework.web.bind.annotation.RequestParam(value = "size", defaultValue = "20") int size,
            @RequestHeader(value = "X-Gateway-Secret", required = false) String gatewaySecret
    );

    @GetMapping("/api/v1/assignments")
    vn.edu.ptit.web_grading_system.ai_assistant_service.client.dto.PageDto<vn.edu.ptit.web_grading_system.ai_assistant_service.client.dto.AssignmentSummaryDto> listLecturerAssignments(
            @RequestHeader("X-User-Id") String lecturerId,
            @RequestHeader(value = "X-User-Roles", defaultValue = "ROLE_LECTURER") String roles,
            @org.springframework.web.bind.annotation.RequestParam(value = "classId", required = false) UUID classId,
            @org.springframework.web.bind.annotation.RequestParam(value = "page", defaultValue = "0") int page,
            @org.springframework.web.bind.annotation.RequestParam(value = "size", defaultValue = "20") int size,
            @RequestHeader(value = "X-Gateway-Secret", required = false) String gatewaySecret
    );

    @GetMapping("/api/v1/assignments/{assignmentId}/submissions")
    vn.edu.ptit.web_grading_system.ai_assistant_service.client.dto.PageDto<vn.edu.ptit.web_grading_system.ai_assistant_service.client.dto.SubmissionSummaryDto> listLecturerAssignmentSubmissions(
            @PathVariable("assignmentId") UUID assignmentId,
            @RequestHeader("X-User-Id") String lecturerId,
            @RequestHeader(value = "X-User-Roles", defaultValue = "ROLE_LECTURER") String roles,
            @org.springframework.web.bind.annotation.RequestParam(value = "page", defaultValue = "0") int page,
            @org.springframework.web.bind.annotation.RequestParam(value = "size", defaultValue = "20") int size,
            @RequestHeader(value = "X-Gateway-Secret", required = false) String gatewaySecret
    );

    @GetMapping("/api/v1/classes/{classId}/students")
    vn.edu.ptit.web_grading_system.ai_assistant_service.client.dto.PageDto<Object> listClassStudents(
            @PathVariable("classId") UUID classId,
            @RequestHeader("X-User-Id") String lecturerId,
            @RequestHeader(value = "X-User-Roles", defaultValue = "ROLE_LECTURER") String roles,
            @org.springframework.web.bind.annotation.RequestParam(value = "page", defaultValue = "0") int page,
            @org.springframework.web.bind.annotation.RequestParam(value = "size", defaultValue = "50") int size,
            @RequestHeader(value = "X-Gateway-Secret", required = false) String gatewaySecret
    );

    @org.springframework.web.bind.annotation.PostMapping(value = "/api/v1/assignments", consumes = "application/json")
    ApiEnvelope<vn.edu.ptit.web_grading_system.ai_assistant_service.client.dto.AssignmentSummaryDto> createAssignment(
            @RequestHeader("X-User-Id") String lecturerId,
            @RequestHeader(value = "X-User-Roles", defaultValue = "ROLE_LECTURER") String roles,
            @org.springframework.web.bind.annotation.RequestBody vn.edu.ptit.web_grading_system.ai_assistant_service.client.dto.CreateAssignmentCommand request,
            @RequestHeader(value = "X-Gateway-Secret", required = false) String gatewaySecret
    );
}

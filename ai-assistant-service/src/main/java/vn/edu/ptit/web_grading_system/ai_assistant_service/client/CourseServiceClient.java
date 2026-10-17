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
}

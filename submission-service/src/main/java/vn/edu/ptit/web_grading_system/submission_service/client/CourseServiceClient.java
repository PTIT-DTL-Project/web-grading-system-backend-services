package vn.edu.ptit.web_grading_system.submission_service.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import vn.edu.ptit.web_grading_system.submission_service.dto.internal.AssignmentOwnerResponse;

import java.util.UUID;

/**
 * Feign client gọi course-service internal API để kiểm tra ownerId của assignment.
 * Dùng để xác định lecturer có quyền xem submission của lớp đó hay không.
 */
@FeignClient(name = "course-service", url = "${feign.course-service.url}")
public interface CourseServiceClient {

    @GetMapping("/api/v1/internal/assignments/{assignmentId}/owner")
    AssignmentOwnerResponse getAssignmentOwner(@PathVariable UUID assignmentId);
}

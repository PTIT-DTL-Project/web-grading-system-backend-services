package vn.edu.ptit.web_grading_system.course_service.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import vn.edu.ptit.web_grading_system.course_service.config.FeignLoggingConfiguration;
import vn.edu.ptit.web_grading_system.course_service.dto.response.SubmissionResponse;

import java.util.List;
import java.util.UUID;

/**
 * The submission rows behind the lecturer's grading view. Pattern copied from
 * executor-service {@code CourseInternalClient} — url from yaml, never inline.
 */
@FeignClient(name = "submission-service", url = "${feign.submission-service.url}",
        configuration = FeignLoggingConfiguration.class)
public interface SubmissionInternalClient {

    @GetMapping("/api/v1/internal/submissions/assignment/{assignmentId}")
    List<SubmissionResponse> listByAssignment(@PathVariable("assignmentId") UUID assignmentId);
}

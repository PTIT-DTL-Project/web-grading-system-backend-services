package vn.edu.ptit.web_grading_system.course_service.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import vn.edu.ptit.web_grading_system.course_service.config.FeignLoggingConfiguration;
import vn.edu.ptit.web_grading_system.course_service.dto.response.SubmissionResponse;

import java.util.UUID;

/**
 * The submission rows behind the lecturer's grading view. Pattern copied from
 * executor-service {@code CourseInternalClient} — url from yaml, never inline.
 *
 * <p>Both {@code page} and {@code size} are optional: when both are absent the
 * submission-service returns a {@code Page} wrapping the full list (unpaged),
 * preserving backward compatibility for callers that ignore pagination.
 */
@FeignClient(name = "submission-service", url = "${feign.submission-service.url}",
        configuration = FeignLoggingConfiguration.class)
public interface SubmissionInternalClient {

    @GetMapping("/api/v1/internal/submissions/assignment/{assignmentId}")
    Page<SubmissionResponse> listByAssignment(
            @PathVariable("assignmentId") UUID assignmentId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size);
}

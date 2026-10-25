package vn.edu.ptit.web_grading_system.ai_assistant_service.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import vn.edu.ptit.web_grading_system.ai_assistant_service.client.dto.PageDto;
import vn.edu.ptit.web_grading_system.ai_assistant_service.client.dto.SubmissionSummaryDto;

import java.util.UUID;

@FeignClient(name = "submission-service", url = "${feign.submission-service.url:http://localhost:8082}")
public interface SubmissionServiceClient {

    @GetMapping("/api/v1/submissions")
    PageDto<SubmissionSummaryDto> listMySubmissions(
            @RequestHeader("X-User-Id") String studentId,
            @RequestParam("page") int page,
            @RequestParam("size") int size,
            @RequestHeader(value = "X-Gateway-Secret", required = false) String gatewaySecret
    );

    @GetMapping("/api/v1/submissions/{id}")
    SubmissionSummaryDto getSubmission(
            @PathVariable("id") UUID id,
            @RequestHeader("X-User-Id") String studentId,
            @RequestHeader(value = "X-Gateway-Secret", required = false) String gatewaySecret
    );
}

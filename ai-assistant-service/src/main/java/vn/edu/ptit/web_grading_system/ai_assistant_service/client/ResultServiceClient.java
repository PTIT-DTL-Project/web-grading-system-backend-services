package vn.edu.ptit.web_grading_system.ai_assistant_service.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import vn.edu.ptit.web_grading_system.ai_assistant_service.client.dto.ResultDto;

import java.util.List;
import java.util.UUID;

@FeignClient(name = "result-service", url = "${feign.result-service.url}")
public interface ResultServiceClient {

    @GetMapping("/api/v1/results/{submissionId}")
    List<ResultDto> getBySubmissionId(
            @PathVariable("submissionId") UUID submissionId,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-Gateway-Secret") String gatewaySecret
    );
}

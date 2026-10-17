package vn.edu.ptit.web_grading_system.ai_assistant_service.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import vn.edu.ptit.web_grading_system.ai_assistant_service.client.dto.ApiEnvelope;
import vn.edu.ptit.web_grading_system.ai_assistant_service.client.dto.ResultDto;

import java.util.List;
import java.util.UUID;

@FeignClient(name = "result-service", url = "${feign.result-service.url}")
public interface ResultServiceClient {

    // Review: 2026-10-17, Pullfrog follow-up — the endpoint is enveloped
    // ({status,message,data}); bind the wrapper and unwrap in the service,
    // otherwise Feign throws DecodeException on the raw List<ResultDto>.
    @GetMapping("/api/v1/results/{submissionId}")
    ApiEnvelope<List<ResultDto>> getBySubmissionId(
            @PathVariable("submissionId") UUID submissionId,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-Gateway-Secret") String gatewaySecret
    );

    /**
     * The same rows, read without an identity, for the lecturer path.
     *
     * <p>Only used after the caller has been authorized: a lecturer does not own the student, so
     * the identity-checked route above would answer 403 for them, and these rows are what reveal
     * the submission's assignment and true owner. The endpoint is internal (not gateway-routed,
     * no envelope), which is why there is no header or wrapper here — the caller must not read
     * it before deciding the request is allowed.
     */
    @GetMapping("/api/v1/internal/results/submission/{submissionId}")
    List<ResultDto> getBySubmissionIdInternal(@PathVariable("submissionId") UUID submissionId);
}

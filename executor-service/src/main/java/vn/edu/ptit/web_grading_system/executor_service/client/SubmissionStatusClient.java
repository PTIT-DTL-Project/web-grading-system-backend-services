package vn.edu.ptit.web_grading_system.executor_service.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import vn.edu.ptit.web_grading_system.executor_service.config.FeignLoggingConfiguration;

import java.util.UUID;

@FeignClient(name = "submission-service", url = "${feign.submission-service.url}",
        configuration = FeignLoggingConfiguration.class)
public interface SubmissionStatusClient {

    @PutMapping("/api/v1/internal/submissions/{id}/status")
    void updateStatus(@PathVariable("id") UUID id, @RequestBody UpdateStatusRequest request);

    record UpdateStatusRequest(String status) {
    }
}

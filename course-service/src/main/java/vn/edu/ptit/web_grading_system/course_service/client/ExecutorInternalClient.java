package vn.edu.ptit.web_grading_system.course_service.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import vn.edu.ptit.web_grading_system.course_service.config.FeignLoggingConfiguration;
import vn.edu.ptit.web_grading_system.course_service.dto.internal.DryRunReport;
import vn.edu.ptit.web_grading_system.course_service.dto.internal.DryRunRequest;

/** Internal client to call executor-service dry-run endpoint. */
@FeignClient(name = "executor-service", url = "${feign.executor-service.url}",
        configuration = FeignLoggingConfiguration.class)
public interface ExecutorInternalClient {

    @PostMapping("/api/v1/internal/grading/dry-run")
    ResponseEntity<DryRunReport> dryRun(@RequestBody DryRunRequest request);
}

package vn.edu.ptit.web_grading_system.executor_service.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import vn.edu.ptit.web_grading_system.executor_service.config.FeignLoggingConfiguration;
import vn.edu.ptit.web_grading_system.executor_service.dto.internal.AssignmentGradingConfigDto;
import vn.edu.ptit.web_grading_system.executor_service.dto.internal.InternalPlanDto;

import java.util.List;
import java.util.UUID;

@FeignClient(name = "course-service", url = "${feign.course-service.url}",
        configuration = FeignLoggingConfiguration.class)
public interface CourseInternalClient {

    @GetMapping("/api/v1/internal/assignments/{id}")
    AssignmentGradingConfigDto gradingConfig(@PathVariable("id") UUID id);

    @GetMapping("/api/v1/internal/assignments/{id}/plans")
    List<InternalPlanDto> plans(@PathVariable("id") UUID id);
}

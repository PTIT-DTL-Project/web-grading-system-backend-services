package vn.edu.ptit.web_grading_system.result_service.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.edu.ptit.web_grading_system.result_service.dto.request.CreateResultRequest;
import vn.edu.ptit.web_grading_system.result_service.dto.response.AssignmentResultGroupResponse;
import vn.edu.ptit.web_grading_system.result_service.service.ResultService;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/internal/results")
@RequiredArgsConstructor
public class ResultInternalController {

    private final ResultService resultService;

    public record AverageRequest(List<UUID> assignmentIds, UUID studentId) {
    }

    @PostMapping("/weighted")
    public ResponseEntity<Map<String, BigDecimal>> weighted(
            @RequestBody AverageRequest request) {
        BigDecimal weighted = resultService.weightedScoreByPlan(request.assignmentIds(), request.studentId());
        return ResponseEntity.ok(java.util.Collections.singletonMap("average", weighted));
    }

    /**
     * Class-wide grading view for course-service's lecturer endpoints. Not routed by the
     * gateway — course-service is the only caller and it has already verified that the
     * requesting lecturer owns this assignment's class.
     */
    @GetMapping("/assignment/{assignmentId}")
    public ResponseEntity<List<AssignmentResultGroupResponse>> byAssignment(
            @PathVariable UUID assignmentId,
            @RequestParam(required = false) UUID studentUserId,
            @RequestParam(defaultValue = "false") boolean includeSteps) {
        return ResponseEntity.ok(
                resultService.getByAssignment(assignmentId, studentUserId, includeSteps));
    }

    @PostMapping
    public ResponseEntity<Map<String, UUID>> create(@Valid @RequestBody CreateResultRequest request) {
        UUID id = resultService.createResult(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(java.util.Collections.singletonMap("id", id));
    }
}

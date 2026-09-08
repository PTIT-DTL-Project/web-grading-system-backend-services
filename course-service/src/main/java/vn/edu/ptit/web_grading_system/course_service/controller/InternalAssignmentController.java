package vn.edu.ptit.web_grading_system.course_service.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.edu.ptit.web_grading_system.course_service.dto.internal.AssignmentExistsResponse;
import vn.edu.ptit.web_grading_system.course_service.dto.internal.AssignmentGradingConfigDto;
import vn.edu.ptit.web_grading_system.course_service.dto.internal.InternalPlanDto;
import vn.edu.ptit.web_grading_system.course_service.service.TestPlanService;

import java.util.List;
import java.util.UUID;

/**
 * Internal contracts for executor-service (config + plans) and submission-service
 * (exists validation). Raw DTOs — no envelope. Never exposed via the gateway.
 */
@RestController
@RequestMapping("/api/v1/internal/assignments")
@RequiredArgsConstructor
public class InternalAssignmentController {

    private final TestPlanService testPlanService;

    @GetMapping("/{id}")
    public ResponseEntity<AssignmentGradingConfigDto> gradingConfig(@PathVariable UUID id) {
        return ResponseEntity.ok(testPlanService.internalGradingConfig(id));
    }

    @GetMapping("/{id}/plans")
    public ResponseEntity<List<InternalPlanDto>> plans(@PathVariable UUID id) {
        return ResponseEntity.ok(testPlanService.internalPlans(id));
    }

    @GetMapping("/{id}/exists")
    public ResponseEntity<AssignmentExistsResponse> exists(@PathVariable UUID id) {
        return ResponseEntity.ok(testPlanService.internalExists(id));
    }
}
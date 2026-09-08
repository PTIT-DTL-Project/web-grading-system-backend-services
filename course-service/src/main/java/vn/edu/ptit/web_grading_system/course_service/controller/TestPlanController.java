package vn.edu.ptit.web_grading_system.course_service.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.edu.ptit.web_grading_system.course_service.dto.request.CreatePlanRequest;
import vn.edu.ptit.web_grading_system.course_service.dto.request.CreateStepRequest;
import vn.edu.ptit.web_grading_system.course_service.dto.request.UpdatePlanRequest;
import vn.edu.ptit.web_grading_system.course_service.dto.request.UpdateStepRequest;
import vn.edu.ptit.web_grading_system.course_service.dto.response.PlanResponse;
import vn.edu.ptit.web_grading_system.course_service.dto.response.StepResponse;
import vn.edu.ptit.web_grading_system.course_service.service.TestPlanService;
import vn.edu.ptit.web_grading_system.course_service.util.annotation.ApiMessage;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/assignments/{assignmentId}")
@RequiredArgsConstructor
public class TestPlanController {

    private final TestPlanService testPlanService;

    // ---------- plans ----------

    @PostMapping("/plans")
    @ApiMessage("Plan created")
    public ResponseEntity<PlanResponse> createPlan(
            @PathVariable UUID assignmentId,
            @RequestHeader(value = "X-User-Id", defaultValue = "anonymous") String ownerId,
            @Valid @RequestBody CreatePlanRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(testPlanService.createPlan(assignmentId, UUID.fromString(ownerId), request));
    }

    @GetMapping("/plans")
    public ResponseEntity<List<PlanResponse>> listPlans(
            @PathVariable UUID assignmentId,
            @RequestHeader(value = "X-User-Id", defaultValue = "anonymous") String ownerId) {
        return ResponseEntity.ok(testPlanService.listPlans(assignmentId, UUID.fromString(ownerId)));
    }

    @PutMapping("/plans/{planId}")
    @ApiMessage("Plan updated")
    public ResponseEntity<PlanResponse> updatePlan(
            @PathVariable UUID assignmentId,
            @PathVariable UUID planId,
            @RequestHeader(value = "X-User-Id", defaultValue = "anonymous") String ownerId,
            @Valid @RequestBody UpdatePlanRequest request) {
        return ResponseEntity.ok(
                testPlanService.updatePlan(assignmentId, UUID.fromString(ownerId), planId, request));
    }

    @DeleteMapping("/plans/{planId}")
    @ApiMessage("Plan deleted")
    public ResponseEntity<Void> deletePlan(
            @PathVariable UUID assignmentId,
            @PathVariable UUID planId,
            @RequestHeader(value = "X-User-Id", defaultValue = "anonymous") String ownerId) {
        testPlanService.deletePlan(assignmentId, UUID.fromString(ownerId), planId);
        return ResponseEntity.ok().build();
    }

    // ---------- steps ----------

    @PostMapping("/plans/{planId}/steps")
    @ApiMessage("Step created")
    public ResponseEntity<StepResponse> createStep(
            @PathVariable UUID assignmentId,
            @PathVariable UUID planId,
            @RequestHeader(value = "X-User-Id", defaultValue = "anonymous") String ownerId,
            @Valid @RequestBody CreateStepRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(testPlanService.createStep(assignmentId, UUID.fromString(ownerId), planId, request));
    }

    @GetMapping("/plans/{planId}/steps")
    public ResponseEntity<List<StepResponse>> listSteps(
            @PathVariable UUID assignmentId,
            @PathVariable UUID planId,
            @RequestHeader(value = "X-User-Id", defaultValue = "anonymous") String ownerId) {
        // scoped: plans list already nests steps; this returns the flat sorted list for one plan
        List<PlanResponse> plans = testPlanService.listPlans(assignmentId, UUID.fromString(ownerId));
        return ResponseEntity.ok(plans.stream()
                .filter(p -> p.getId().equals(planId))
                .findFirst()
                .map(PlanResponse::getSteps)
                .orElseThrow(() -> new vn.edu.ptit.web_grading_system.course_service.exception.ResourceNotFoundException(
                        "Plan not found: " + planId)));
    }

    @PutMapping("/plans/{planId}/steps/{stepId}")
    @ApiMessage("Step updated")
    public ResponseEntity<StepResponse> updateStep(
            @PathVariable UUID assignmentId,
            @PathVariable UUID planId,
            @PathVariable UUID stepId,
            @RequestHeader(value = "X-User-Id", defaultValue = "anonymous") String ownerId,
            @Valid @RequestBody UpdateStepRequest request) {
        return ResponseEntity.ok(testPlanService.updateStep(
                assignmentId, UUID.fromString(ownerId), planId, stepId, request));
    }

    @DeleteMapping("/plans/{planId}/steps/{stepId}")
    @ApiMessage("Step deleted")
    public ResponseEntity<Void> deleteStep(
            @PathVariable UUID assignmentId,
            @PathVariable UUID planId,
            @PathVariable UUID stepId,
            @RequestHeader(value = "X-User-Id", defaultValue = "anonymous") String ownerId) {
        testPlanService.deleteStep(assignmentId, UUID.fromString(ownerId), planId, stepId);
        return ResponseEntity.ok().build();
    }
}
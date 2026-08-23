package vn.edu.ptit.web_grading_system.result_service.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
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

    @PostMapping("/average")
    public ResponseEntity<Map<String, BigDecimal>> average(
            @RequestBody AverageRequest request) {
        return ResponseEntity.ok(Map.of(
                "average", resultService.averageBand10(request.assignmentIds(), request.studentId())));
    }

    public record AverageRequest(List<UUID> assignmentIds, UUID studentId) {
    }
}
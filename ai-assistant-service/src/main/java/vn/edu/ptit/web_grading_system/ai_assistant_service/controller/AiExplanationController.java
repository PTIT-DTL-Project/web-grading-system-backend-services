package vn.edu.ptit.web_grading_system.ai_assistant_service.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.edu.ptit.web_grading_system.ai_assistant_service.dto.AiExplanationResponse;
import vn.edu.ptit.web_grading_system.ai_assistant_service.service.AiExplanationService;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
public class AiExplanationController {

    private final AiExplanationService explanationService;

    @GetMapping("/submissions/{submissionId}/explain")
    public ResponseEntity<AiExplanationResponse> getExplanation(
            @PathVariable("submissionId") UUID submissionId,
            @RequestHeader(value = "X-User-Id", defaultValue = "00000000-0000-0000-0000-000000000000") String userId) {
        return explanationService.getExplanation(submissionId, UUID.fromString(userId))
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/submissions/{submissionId}/explain")
    public ResponseEntity<AiExplanationResponse> explainSubmission(
            @PathVariable("submissionId") UUID submissionId,
            @RequestHeader(value = "X-User-Id", defaultValue = "00000000-0000-0000-0000-000000000000") String userId) {
        return ResponseEntity.ok(explanationService.explainSubmission(submissionId, UUID.fromString(userId)));
    }
}

package vn.edu.ptit.web_grading_system.ai_assistant_service.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import vn.edu.ptit.web_grading_system.ai_assistant_service.domain.operation.Operation;
import vn.edu.ptit.web_grading_system.ai_assistant_service.dto.response.OperationResponse;
import vn.edu.ptit.web_grading_system.ai_assistant_service.repository.OperationRepository;
import vn.edu.ptit.web_grading_system.ai_assistant_service.security.UserPrincipal;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ai/operations")
@RequiredArgsConstructor
public class OperationController {

    private final OperationRepository operationRepository;

    @GetMapping("/{id}")
    public ResponseEntity<OperationResponse> getOperation(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id) {
        Operation op = operationRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Operation not found: " + id));

        return ResponseEntity.ok(new OperationResponse(
                op.getId(),
                op.getTaskId(),
                op.getIdempotencyKey(),
                op.getCommandType(),
                op.getStatus(),
                op.getResultRef(),
                op.getAttempts(),
                op.getNextCheckAt(),
                op.getCreatedAt(),
                op.getUpdatedAt()
        ));
    }
}

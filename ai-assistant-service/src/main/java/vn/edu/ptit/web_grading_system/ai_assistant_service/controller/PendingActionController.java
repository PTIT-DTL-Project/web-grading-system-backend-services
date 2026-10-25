package vn.edu.ptit.web_grading_system.ai_assistant_service.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import vn.edu.ptit.web_grading_system.ai_assistant_service.security.UserPrincipal;
import vn.edu.ptit.web_grading_system.ai_assistant_service.service.WriteApprovalService;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ai/pending-actions")
@RequiredArgsConstructor
public class PendingActionController {
    private final WriteApprovalService service;

    @GetMapping("/conversation/{conversationId}")
    public ResponseEntity<Object> list(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID conversationId) {
        return ResponseEntity.ok(service.list(conversationId, principal));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Object> get(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        return ResponseEntity.ok(service.get(id, principal));
    }

    @PostMapping("/{id}/confirm")
    public ResponseEntity<Object> confirm(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        return ResponseEntity.ok(service.confirm(id, principal));
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<Object> reject(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        return ResponseEntity.ok(service.reject(id, principal));
    }
}

package vn.edu.ptit.web_grading_system.ai_assistant_service.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import vn.edu.ptit.web_grading_system.ai_assistant_service.dto.request.CreateConversationRequest;
import vn.edu.ptit.web_grading_system.ai_assistant_service.dto.request.SendMessageRequest;
import vn.edu.ptit.web_grading_system.ai_assistant_service.dto.request.UpdateConversationTitleRequest;
import vn.edu.ptit.web_grading_system.ai_assistant_service.dto.response.ConversationResponse;
import vn.edu.ptit.web_grading_system.ai_assistant_service.dto.response.MessageResponse;
import vn.edu.ptit.web_grading_system.ai_assistant_service.dto.response.SendMessageResponse;
import vn.edu.ptit.web_grading_system.ai_assistant_service.security.UserPrincipal;
import vn.edu.ptit.web_grading_system.ai_assistant_service.service.ConversationService;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ai/conversations")
@RequiredArgsConstructor
public class ChatController {

    private final ConversationService conversationService;

    @PostMapping
    public ResponseEntity<ConversationResponse> createConversation(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody(required = false) CreateConversationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(conversationService.createConversation(principal, request));
    }

    @GetMapping
    public ResponseEntity<Page<ConversationResponse>> listConversations(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(conversationService.listConversations(principal, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ConversationResponse> getConversation(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id) {
        return ResponseEntity.ok(conversationService.getConversation(principal, id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ConversationResponse> updateConversationTitle(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateConversationTitleRequest request) {
        return ResponseEntity.ok(conversationService.updateTitle(principal, id, request.title()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteConversation(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id) {
        conversationService.deleteConversation(principal, id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/messages")
    public ResponseEntity<SendMessageResponse> sendMessage(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody SendMessageRequest request) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(conversationService.sendMessage(id, principal, request));
    }

    @GetMapping("/{id}/messages")
    public ResponseEntity<Page<MessageResponse>> getMessages(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(conversationService.getMessages(id, principal, pageable));
    }
}

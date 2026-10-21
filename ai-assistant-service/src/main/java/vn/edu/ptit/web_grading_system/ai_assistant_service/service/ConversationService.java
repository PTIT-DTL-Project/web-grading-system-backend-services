package vn.edu.ptit.web_grading_system.ai_assistant_service.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.ptit.web_grading_system.ai_assistant_service.domain.conversation.Conversation;
import vn.edu.ptit.web_grading_system.ai_assistant_service.domain.conversation.Message;
import vn.edu.ptit.web_grading_system.ai_assistant_service.domain.run.Run;
import vn.edu.ptit.web_grading_system.ai_assistant_service.domain.run.RunStatus;
import vn.edu.ptit.web_grading_system.ai_assistant_service.dto.request.CreateConversationRequest;
import vn.edu.ptit.web_grading_system.ai_assistant_service.dto.request.SendMessageRequest;
import vn.edu.ptit.web_grading_system.ai_assistant_service.dto.response.ConversationResponse;
import vn.edu.ptit.web_grading_system.ai_assistant_service.dto.response.MessageResponse;
import vn.edu.ptit.web_grading_system.ai_assistant_service.dto.response.SendMessageResponse;
import vn.edu.ptit.web_grading_system.ai_assistant_service.repository.ConversationRepository;
import vn.edu.ptit.web_grading_system.ai_assistant_service.repository.MessageRepository;
import vn.edu.ptit.web_grading_system.ai_assistant_service.repository.RunRepository;
import vn.edu.ptit.web_grading_system.ai_assistant_service.security.UserPrincipal;

import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationService {

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final RunRepository runRepository;
    private final vn.edu.ptit.web_grading_system.ai_assistant_service.orchestrator.RunWorker runWorker;

    @Transactional
    public ConversationResponse createConversation(UserPrincipal principal, CreateConversationRequest request) {
        String title = (request != null && request.getTitle() != null && !request.getTitle().isBlank())
                ? request.getTitle().trim()
                : "Hội thoại mới";

        Conversation conv = Conversation.builder()
                .ownerId(principal.userId())
                .title(title)
                .build();
        conv = conversationRepository.save(conv);
        return mapToResponse(conv);
    }

    @Transactional(readOnly = true)
    public Page<ConversationResponse> listConversations(UserPrincipal principal, Pageable pageable) {
        return conversationRepository.findByOwnerIdOrderByUpdatedAtDesc(principal.userId(), pageable)
                .map(this::mapToResponse);
    }

    @Transactional(readOnly = true)
    public ConversationResponse getConversation(UserPrincipal principal, UUID conversationId) {
        Conversation conv = conversationRepository.findByIdAndOwnerId(conversationId, principal.userId())
                .orElseThrow(() -> new IllegalArgumentException("Conversation not found"));
        return mapToResponse(conv);
    }

    @Transactional
    public void deleteConversation(UserPrincipal principal, UUID conversationId) {
        Conversation conv = conversationRepository.findByIdAndOwnerId(conversationId, principal.userId())
                .orElseThrow(() -> new IllegalArgumentException("Conversation not found"));
        conversationRepository.delete(conv);
    }

    @Transactional
    public SendMessageResponse sendMessage(UUID conversationId, UserPrincipal principal, SendMessageRequest request) {
        Conversation conv = conversationRepository.findByIdAndOwnerId(conversationId, principal.userId())
                .orElseThrow(() -> new IllegalArgumentException("Conversation not found"));

        if (request.getClientMessageId() != null && !request.getClientMessageId().isBlank()) {
            Optional<Message> existing = messageRepository.findByConversationIdAndClientMessageId(conversationId, request.getClientMessageId());
            if (existing.isPresent()) {
                Message msg = existing.get();
                return new SendMessageResponse(conversationId, msg.getRunId(), msg.getId());
            }
        }

        int nextSeq = messageRepository.findMaxSequenceNo(conversationId) + 1;

        Run run = Run.builder()
                .conversationId(conversationId)
                .status(RunStatus.QUEUED)
                .version(1)
                .build();
        run = runRepository.save(run);

        Message message = Message.builder()
                .conversationId(conversationId)
                .runId(run.getId())
                .role("user")
                .content(request.getText())
                .clientMessageId(request.getClientMessageId())
                .sequenceNo(nextSeq)
                .build();
        message = messageRepository.save(message);
        runWorker.processRunAsync(run.getId());

        return new SendMessageResponse(conversationId, run.getId(), message.getId());
    }

    @Transactional(readOnly = true)
    public Page<MessageResponse> getMessages(UUID conversationId, UserPrincipal principal, Pageable pageable) {
        conversationRepository.findByIdAndOwnerId(conversationId, principal.userId())
                .orElseThrow(() -> new IllegalArgumentException("Conversation not found"));

        return messageRepository.findByConversationIdOrderBySequenceNoAsc(conversationId, pageable)
                .map(this::mapMessageToResponse);
    }

    private ConversationResponse mapToResponse(Conversation c) {
        return new ConversationResponse(c.getId(), c.getOwnerId(), c.getTitle(), c.getActiveTaskId(), c.getCreatedAt(), c.getUpdatedAt());
    }

    private MessageResponse mapMessageToResponse(Message m) {
        return new MessageResponse(m.getId(), m.getConversationId(), m.getRunId(), m.getRole(), m.getContent(),
                m.getToolCallId(), m.getToolName(), m.getToolArguments(), m.getToolResult(),
                m.getClientMessageId(), m.getSequenceNo(), m.getCreatedAt());
    }
}

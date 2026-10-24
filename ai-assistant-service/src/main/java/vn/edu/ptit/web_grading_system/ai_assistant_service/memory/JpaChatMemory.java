package vn.edu.ptit.web_grading_system.ai_assistant_service.memory;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.ptit.web_grading_system.ai_assistant_service.repository.MessageRepository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class JpaChatMemory implements ChatMemory {

    private final MessageRepository messageRepository;

    @Override
    @Transactional
    public void add(String conversationId, List<Message> messages) {
        addForRun(conversationId, null, messages);
    }

    @Transactional
    public void addForRun(String conversationId, UUID runId, List<Message> messages) {
        if (conversationId == null || messages == null || messages.isEmpty()) {
            return;
        }
        try {
            UUID convId = UUID.fromString(conversationId);
            int seq = messageRepository.findMaxSequenceNo(convId);
            for (Message msg : messages) {
                seq++;
                String role = switch (msg.getMessageType()) {
                    case USER -> "user";
                    case ASSISTANT -> "assistant";
                    case SYSTEM -> "system";
                    default -> "tool";
                };
                vn.edu.ptit.web_grading_system.ai_assistant_service.domain.conversation.Message entity =
                        vn.edu.ptit.web_grading_system.ai_assistant_service.domain.conversation.Message.builder()
                                .conversationId(convId)
                                .runId(runId)
                                .role(role)
                                .content(msg.getText())
                                .sequenceNo(seq)
                                .build();
                messageRepository.save(entity);
            }
        } catch (IllegalArgumentException e) {
            log.warn("Invalid UUID for conversationId in ChatMemory: {}", conversationId);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<Message> get(String conversationId) {
        if (conversationId == null) {
            return Collections.emptyList();
        }
        try {
            UUID convId = UUID.fromString(conversationId);
            List<vn.edu.ptit.web_grading_system.ai_assistant_service.domain.conversation.Message> dbMessages =
                    messageRepository.findByConversationIdOrderBySequenceNoAsc(convId);

            List<Message> result = new ArrayList<>();
            for (var dbMsg : dbMessages) {
                String content = dbMsg.getContent() != null ? dbMsg.getContent() : "";
                if ("user".equalsIgnoreCase(dbMsg.getRole())) {
                    result.add(new UserMessage(content));
                } else if ("assistant".equalsIgnoreCase(dbMsg.getRole())) {
                    result.add(new AssistantMessage(content));
                } else if ("system".equalsIgnoreCase(dbMsg.getRole())) {
                    result.add(new SystemMessage(content));
                }
            }
            return result;
        } catch (IllegalArgumentException e) {
            log.warn("Invalid UUID for conversationId in ChatMemory: {}", conversationId);
            return Collections.emptyList();
        }
    }

    @Override
    @Transactional
    public void clear(String conversationId) {
        // Conversation history in database is kept for audit purposes
    }
}

package vn.edu.ptit.web_grading_system.ai_assistant_service.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import vn.edu.ptit.web_grading_system.ai_assistant_service.repository.ConversationRepository;

import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Component
@RequiredArgsConstructor
public class ConversationTitleWorker {
    private final LlmClient llmClient;
    private final ConversationRepository conversationRepository;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public void generateAsync(UUID conversationId, String firstQuestion) {
        executor.submit(() -> {
            try {
                String title = llmClient.generateConversationTitle(firstQuestion);
                conversationRepository.setGeneratedTitleIfUntitled(conversationId, title);
            } catch (Exception e) {
                log.warn("Could not save generated title for conversation {}: {}", conversationId, e.getMessage());
            }
        });
    }
}

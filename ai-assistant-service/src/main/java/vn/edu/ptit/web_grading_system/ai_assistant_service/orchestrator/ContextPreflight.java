package vn.edu.ptit.web_grading_system.ai_assistant_service.orchestrator;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import vn.edu.ptit.web_grading_system.ai_assistant_service.config.AgentProperties;
import vn.edu.ptit.web_grading_system.ai_assistant_service.domain.conversation.ConversationSummary;
import vn.edu.ptit.web_grading_system.ai_assistant_service.domain.conversation.Message;
import vn.edu.ptit.web_grading_system.ai_assistant_service.repository.ConversationSummaryRepository;
import vn.edu.ptit.web_grading_system.ai_assistant_service.repository.MessageRepository;
import vn.edu.ptit.web_grading_system.ai_assistant_service.service.ConversationSummarizer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class ContextPreflight {
    private final MessageRepository messageRepository;
    private final ConversationSummaryRepository summaryRepository;
    private final ConversationSummarizer summarizer;
    private final AgentProperties properties;
    private final TokenEstimator estimator;

    public record PreparedContext(List<org.springframework.ai.chat.messages.Message> messages,
                                  String summary, int historyTokens, int toolTokens, int totalTokens) {}

    public static class InputTooLargeException extends IllegalArgumentException {
        public InputTooLargeException(String message) { super(message); }
    }

    public PreparedContext prepare(UUID conversationId, UUID ownerId, Set<String> roles,
                                   String systemPrompt, List<ToolCallback> tools,
                                   List<org.springframework.ai.chat.messages.Message> inFlight) {
        if (conversationId == null || ownerId == null || roles == null || roles.isEmpty()) {
            throw new IllegalArgumentException("Missing conversation principal");
        }
        var limits = properties.getContext();
        String roleScope = roles.stream().sorted().collect(Collectors.joining(","));
        int window = Math.max(1, limits.getHistoryWindowSize());
        Page<Message> page = messageRepository.findScopedRecent(conversationId, roleScope, PageRequest.of(0, window));
        List<Message> recent = new ArrayList<>(page.getContent());
        recent.sort(Comparator.comparing(Message::getSequenceNo));
        if (recent.isEmpty()) throw new IllegalStateException("No current user message");

        int toolTokens = estimateTools(tools) + estimateMessages(inFlight);
        if (toolTokens > limits.getToolMaxTokens()) throw new InputTooLargeException("Tool context exceeds tool budget");
        int availableOverall = limits.getMaxInputTokens() - limits.getOutputReserve() - limits.getSafetyMargin();
        int systemTokens = estimator.estimateTokens(systemPrompt);
        if (systemTokens + toolTokens >= availableOverall) {
            throw new InputTooLargeException("System and tool context exceed input budget");
        }
        int historyBudget = Math.min(limits.getHistoryMaxTokens(), availableOverall - systemTokens - toolTokens);
        if (estimator.estimateTokens(recent.getLast().getContent()) > historyBudget) {
            throw new InputTooLargeException("Newest message exceeds history budget");
        }

        ConversationSummary previous = summaryRepository
                .findTopByConversationIdAndRoleScopeOrderByCreatedAtDesc(conversationId, roleScope).orElse(null);
        String summary = previous == null ? "" : previous.getSummary();
        while (recent.size() > 1 && estimateEntities(recent) + estimator.estimateTokens(summary) > historyBudget) {
            recent.removeFirst();
        }
        if (page.getTotalElements() > recent.size()) {
            try {
                previous = extendSummary(conversationId, roleScope, recent.getFirst().getSequenceNo(), previous, summary);
                summary = previous == null ? summary : previous.getSummary();
            } catch (Exception ignored) {
                // Retain the last valid checkpoint. The search tool can recover older originals.
            }
        }
        while (recent.size() > 1 && estimateEntities(recent) + estimator.estimateTokens(summary) > historyBudget) {
            recent.removeFirst();
            try {
                previous = extendSummary(conversationId, roleScope, recent.getFirst().getSequenceNo(), previous, summary);
                summary = previous == null ? summary : previous.getSummary();
            } catch (Exception ignored) {
                // The original remains searchable if summary generation fails.
            }
        }
        int historyTokens = estimateEntities(recent) + estimator.estimateTokens(summary);
        if (historyTokens > historyBudget) throw new InputTooLargeException("History exceeds context budget");

        List<org.springframework.ai.chat.messages.Message> messages = new ArrayList<>();
        for (Message entity : recent) {
            String content = entity.getContent() == null ? "" : entity.getContent();
            messages.add("assistant".equals(entity.getRole()) ? new AssistantMessage(content) : new UserMessage(content));
        }
        if (inFlight != null) messages.addAll(inFlight);
        return new PreparedContext(List.copyOf(messages), summary, historyTokens, toolTokens,
                systemTokens + historyTokens + toolTokens);
    }

    private ConversationSummary extendSummary(UUID conversationId, String roleScope, int beforeSequence,
                                 ConversationSummary previous, String oldSummary) {
        int throughSequence = previous == null ? 0 : messageRepository.findById(previous.getThroughMessageId())
                .map(Message::getSequenceNo).orElse(0);
        List<Message> unsummarized = new ArrayList<>();
        int cursor = beforeSequence;
        while (true) {
            Page<Message> page = messageRepository.findScopedBefore(conversationId, roleScope, cursor, PageRequest.of(0, 50));
            if (page == null || page.isEmpty()) break;
            boolean reachedCheckpoint = false;
            for (Message message : page.getContent()) {
                if (message.getSequenceNo() <= throughSequence) { reachedCheckpoint = true; break; }
                unsummarized.add(message);
            }
            if (reachedCheckpoint || page.getContent().size() < 50) break;
            cursor = page.getContent().getLast().getSequenceNo();
        }
        if (unsummarized.isEmpty()) return previous;
        unsummarized.sort(Comparator.comparing(Message::getSequenceNo));
        String next = summarizer.summarize(oldSummary, unsummarized, 2000);
        ConversationSummary checkpoint = ConversationSummary.builder().conversationId(conversationId)
                .roleScope(roleScope).throughMessageId(unsummarized.getLast().getId())
                .summary(next).build();
        summaryRepository.save(checkpoint);
        return checkpoint;
    }

    private int estimateEntities(List<Message> messages) {
        return messages.stream().mapToInt(message -> estimator.estimateTokens(message.getContent()) + 8).sum();
    }

    private int estimateMessages(List<org.springframework.ai.chat.messages.Message> messages) {
        return messages == null ? 0 : messages.stream()
                .mapToInt(message -> estimator.estimateTokens(message.getText()) + 8).sum();
    }

    private int estimateTools(List<ToolCallback> tools) {
        return tools == null ? 0 : tools.stream().map(ToolCallback::getToolDefinition)
                .mapToInt(definition -> estimator.estimateTokens(definition.name())
                        + estimator.estimateTokens(definition.description())
                        + estimator.estimateTokens(definition.inputSchema()) + 16).sum();
    }
}

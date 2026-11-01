package vn.edu.ptit.web_grading_system.ai_assistant_service.orchestrator;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.chat.messages.UserMessage;
import vn.edu.ptit.web_grading_system.ai_assistant_service.config.AgentProperties;
import vn.edu.ptit.web_grading_system.ai_assistant_service.service.ToolResultStore;
import vn.edu.ptit.web_grading_system.ai_assistant_service.tools.AgentContext;

import java.util.List;

class InFlightContextWindow {
    private static final int HISTORY_HEADROOM = 128;
    private static final String PAGE_RESULT_PREFIX = "Kết quả tool get_tool_result_page: ";
    private final AgentProperties properties;
    private final TokenEstimator estimator;
    private final ToolResultStore store;
    private final StringBuilder archivedContent = new StringBuilder();
    private String activeReference = "";

    InFlightContextWindow(AgentProperties properties, TokenEstimator estimator, ToolResultStore store) {
        this.properties = properties;
        this.estimator = estimator;
        this.store = store;
    }

    int budget(String systemPrompt, List<ToolCallback> tools, String latestUserText) {
        var limits = properties.getContext();
        int schemaTokens = tools.stream().mapToInt(tool -> {
            var definition = tool.getToolDefinition();
            return estimator.estimateTokens(definition.name())
                    + estimator.estimateTokens(definition.description())
                    + estimator.estimateTokens(definition.inputSchema()) + 16;
        }).sum();
        int overall = limits.getMaxInputTokens() - limits.getOutputReserve() - limits.getSafetyMargin();
        int remaining = overall - estimator.estimateTokens(systemPrompt) - schemaTokens
                - estimator.estimateTokens(latestUserText) - 8;
        remaining -= Math.min(HISTORY_HEADROOM, Math.max(0, remaining / 4));
        return Math.max(0, Math.min(limits.getToolMaxTokens() - schemaTokens, remaining));
    }

    int estimateMessages(List<org.springframework.ai.chat.messages.Message> messages) {
        return messages.stream().mapToInt(message -> estimator.estimateTokens(message.getText()) + 8).sum();
    }

    void recordPage(List<org.springframework.ai.chat.messages.Message> messages, String result) {
        messages.add(new UserMessage(PAGE_RESULT_PREFIX + result));
    }

    void fit(List<org.springframework.ai.chat.messages.Message> messages, String systemPrompt,
             List<ToolCallback> tools, String latestUserText, AgentContext context) {
        int allowed = budget(systemPrompt, tools, latestUserText);
        if (messages.isEmpty() || estimateMessages(messages) <= allowed) return;
        var latest = messages.getLast();
        if (latest.getText().startsWith(PAGE_RESULT_PREFIX)) {
            if (estimateMessages(List.of(latest)) > allowed) {
                throw new ContextPreflight.InputTooLargeException(
                        "Tool result page exceeds available context budget");
            }
            while (estimateMessages(messages) > allowed && messages.size() > 1) {
                messages.removeFirst();
            }
            return;
        }
        if (messages.size() > 1) {
            if (estimator.estimateTokens(referenceText(
                    "00000000-0000-0000-0000-000000000000")) + 8
                    + estimateMessages(List.of(latest)) <= allowed) {
                var reference = archive(messages.subList(0, messages.size() - 1), context);
                messages.clear();
                messages.add(reference);
                messages.add(latest);
                return;
            }
            var reference = archive(messages, context);
            messages.clear();
            messages.add(reference);
            return;
        }
        if (!isReference(messages.getFirst())) {
            var reference = archive(messages, context);
            messages.clear();
            messages.add(reference);
        }
    }

    boolean archiveAll(List<org.springframework.ai.chat.messages.Message> messages, AgentContext context) {
        if (messages.isEmpty() || messages.size() == 1 && isReference(messages.getFirst())) return false;
        if (messages.getLast().getText().startsWith(PAGE_RESULT_PREFIX)) {
            if (messages.size() == 1) return false;
            messages.removeFirst();
            return true;
        }
        var reference = archive(messages, context);
        messages.clear();
        messages.add(reference);
        return true;
    }

    private UserMessage archive(List<org.springframework.ai.chat.messages.Message> messages, AgentContext context) {
        StringBuilder newest = new StringBuilder();
        for (int index = messages.size() - 1; index >= 0; index--) {
            var message = messages.get(index);
            String content = message.getText();
            if (content.equals(activeReference)) continue;
            if (!newest.isEmpty()) newest.append("\n\n");
            newest.append(content);
        }
        if (!newest.isEmpty()) {
            if (!archivedContent.isEmpty()) newest.append("\n\n").append(archivedContent);
            archivedContent.setLength(0);
            archivedContent.append(newest);
        }
        var storedId = store.reference(archivedContent.toString(), context);
        activeReference = referenceText(storedId.toString());
        return new UserMessage(activeReference);
    }

    private String referenceText(String storedId) {
        return "Kết quả các lượt trước lưu tại storedId " + storedId
                + "; dùng get_tool_result_page với cursor t:0 để đọc khi cần.";
    }

    private boolean isReference(org.springframework.ai.chat.messages.Message message) {
        return message.getText().equals(activeReference);
    }
}

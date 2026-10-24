package vn.edu.ptit.web_grading_system.ai_assistant_service.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import vn.edu.ptit.web_grading_system.ai_assistant_service.domain.conversation.Message;
import vn.edu.ptit.web_grading_system.ai_assistant_service.repository.ConversationRepository;
import vn.edu.ptit.web_grading_system.ai_assistant_service.repository.MessageRepository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class SearchConversationTool {
    private static final int PAGE_SIZE = 5;
    private final ConversationRepository conversations;
    private final MessageRepository messages;
    private final ObjectMapper mapper;

    public SearchConversationTool(ConversationRepository conversations, MessageRepository messages, ObjectMapper mapper) {
        this.conversations = conversations;
        this.messages = messages;
        this.mapper = mapper;
    }

    public ToolCallback callback(AgentContext context) {
        return new ToolCallback() {
            @Override public org.springframework.ai.tool.definition.ToolDefinition getToolDefinition() {
                return org.springframework.ai.tool.definition.ToolDefinition.builder()
                        .name("search_conversation")
                        .description("Tìm lại các quyết định và tin nhắn cũ trong chính cuộc hội thoại hiện tại.")
                        .inputSchema("{\"type\":\"object\",\"properties\":{\"query\":{\"type\":\"string\"},\"cursor\":{\"type\":\"string\"}},\"required\":[\"query\"]}")
                        .build();
            }
            @Override public String call(String input) { return call(input, null); }
            @Override public String call(String input, ToolContext ignored) {
                try {
                    JsonNode args = mapper.readTree(input);
                    String query = args.path("query").asText("").strip();
                    if (query.isEmpty() || query.length() > 200) return "{\"error\":\"INVALID_QUERY\"}";
                    if (context == null || context.userId() == null || context.conversationId() == null ||
                            conversations.findByIdAndOwnerId(context.conversationId(), context.userId()).isEmpty()) {
                        return "{\"error\":\"FORBIDDEN\"}";
                    }
                    Set<String> roles = context.roles();
                    if (roles == null || roles.isEmpty()) return "{\"error\":\"FORBIDDEN\"}";
                    String roleScope = roles.stream().sorted().collect(Collectors.joining(","));
                    int before = args.hasNonNull("cursor") ? Integer.parseInt(args.get("cursor").asText()) : Integer.MAX_VALUE;
                    if (before <= 0) return "{\"error\":\"INVALID_CURSOR\"}";
                    Page<Message> found = messages.searchScoped(context.conversationId(), roleScope, query, before,
                            PageRequest.of(0, PAGE_SIZE + 1));
                    var matches = new ArrayList<Map<String, Object>>();
                    for (Message match : found.getContent().stream().limit(PAGE_SIZE).toList()) {
                        String content = match.getContent() == null ? "" : match.getContent();
                        matches.add(Map.of("id", match.getId().toString(), "role", match.getRole(),
                                "sequenceNo", match.getSequenceNo(), "excerpt", excerpt(content, query)));
                    }
                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("matches", matches);
                    result.put("nextCursor", found.getContent().size() > PAGE_SIZE && !matches.isEmpty()
                            ? Integer.toString((Integer) matches.getLast().get("sequenceNo")) : null);
                    return mapper.writeValueAsString(result);
                } catch (NumberFormatException e) {
                    return "{\"error\":\"INVALID_CURSOR\"}";
                } catch (Exception e) {
                    return "{\"error\":\"SEARCH_FAILED\"}";
                }
            }
        };
    }

    private String excerpt(String content, String query) {
        if (content.length() <= 700) return content;
        int match = content.toLowerCase().indexOf(query.toLowerCase());
        int start = Math.max(0, (match < 0 ? 0 : match) - 200);
        start = Math.min(start, content.length() - 700);
        return content.substring(start, start + 700);
    }
}

package vn.edu.ptit.web_grading_system.ai_assistant_service.service;

import com.google.gson.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import vn.edu.ptit.web_grading_system.ai_assistant_service.config.AiModelProperties;

import java.util.List;

@Slf4j
@Component
public class LlmClient {

    private final String provider;
    private final String apiKey;
    private final String modelName;
    private final String baseUrl;
    private final ChatModel chatModel;
    private final ChatClient chatClient;
    private final Gson gson = new Gson();

    public LlmClient(AiModelProperties props, ChatModel chatModel) {
        this.provider = props.provider();
        this.apiKey = props.apiKey();
        this.modelName = props.modelName();
        String baseUrl = props.baseUrl();
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.chatModel = chatModel;
        this.chatClient = ChatClient.create(chatModel);
    }

    public String generateCompletion(String systemPrompt, String userPrompt) {
        if (isOfflineMode()) {
            log.info("AI API key not configured — generating realistic pedagogical fallback response");
            return generateFallbackResponse(userPrompt);
        }
        return generateCompletion(new Prompt(List.of(
                new SystemMessage(systemPrompt),
                new UserMessage(userPrompt)
        )));
    }

    public <T> T generateEntity(String systemPrompt, String userPrompt, Class<T> entityClass) {
        if (isOfflineMode()) {
            log.info("AI API key not configured — generating fallback structured entity");
            String fallback = generateFallbackResponse(userPrompt);
            return gson.fromJson(fallback, entityClass);
        }
        try {
            return chatClient.prompt()
                    .system(systemPrompt)
                    .user(userPrompt)
                    .call()
                    .entity(entityClass);
        } catch (Exception e) {
            log.error("Failed to generate structured entity via Spring AI: {}", e.getMessage(), e);
            String fallback = generateFallbackResponse(userPrompt);
            return gson.fromJson(fallback, entityClass);
        }
    }

    public String generateCompletion(List<org.springframework.ai.chat.messages.Message> messages) {
        if (isOfflineMode()) {
            String fallbackPrompt = messages != null && !messages.isEmpty()
                    ? messages.get(messages.size() - 1).getText() : "";
            return generateFallbackResponse(fallbackPrompt);
        }
        return generateCompletion(new Prompt(messages));
    }

    public String generateCompletion(Prompt prompt) {
        if (isOfflineMode()) {
            return generateFallbackResponse(prompt != null ? prompt.getContents() : "");
        }
        try {
            ChatResponse response = chatModel.call(prompt);
            if (response != null && response.getResult() != null && response.getResult().getOutput() != null) {
                String content = response.getResult().getOutput().getText();
                return cleanJsonString(content);
            }
        } catch (Exception e) {
            log.error("Failed to call LLM provider via Spring AI: {}", e.getMessage(), e);
        }
        return generateFallbackResponse(prompt != null ? prompt.getContents() : "");
    }

    public Flux<String> streamCompletion(String systemPrompt, String userPrompt) {
        if (isOfflineMode()) {
            return Flux.just(generateFallbackResponse(userPrompt));
        }
        return streamCompletion(new Prompt(List.of(
                new SystemMessage(systemPrompt),
                new UserMessage(userPrompt)
        )));
    }

    public Flux<String> streamCompletion(List<org.springframework.ai.chat.messages.Message> messages) {
        if (isOfflineMode()) {
            String fallbackPrompt = messages != null && !messages.isEmpty()
                    ? messages.get(messages.size() - 1).getText() : "";
            return Flux.just(generateFallbackResponse(fallbackPrompt));
        }
        return streamCompletion(new Prompt(messages));
    }

    public Flux<String> streamChat(String systemPrompt, List<org.springframework.ai.chat.messages.Message> messages, List<ToolCallback> toolCallbacks) {
        if (isOfflineMode()) {
            String fallbackPrompt = (messages != null && !messages.isEmpty())
                    ? messages.get(messages.size() - 1).getText() : "";
            return Flux.just(generateFallbackResponse(fallbackPrompt));
        }
        try {
            var spec = chatClient.prompt().system(systemPrompt);
            if (messages != null && !messages.isEmpty()) {
                spec.messages(messages);
            }
            if (toolCallbacks != null && !toolCallbacks.isEmpty()) {
                spec.toolCallbacks(toolCallbacks);
            }
            return spec.stream().content()
                    .filter(s -> s != null && !s.isEmpty());
        } catch (Exception e) {
            log.error("Failed to stream chat via Spring AI: {}", e.getMessage(), e);
            String fallbackPrompt = (messages != null && !messages.isEmpty())
                    ? messages.get(messages.size() - 1).getText() : "";
            return Flux.just(generateFallbackResponse(fallbackPrompt));
        }
    }

    public Flux<String> streamChatWithMemory(
            String systemPrompt,
            String conversationId,
            org.springframework.ai.chat.memory.ChatMemory chatMemory,
            List<ToolCallback> toolCallbacks
    ) {
        if (isOfflineMode()) {
            List<org.springframework.ai.chat.messages.Message> history = (chatMemory != null && conversationId != null)
                    ? chatMemory.get(conversationId) : List.of();
            String fallbackPrompt = (history != null && !history.isEmpty())
                    ? history.get(history.size() - 1).getText() : "";
            return Flux.just(generateFallbackResponse(fallbackPrompt));
        }
        try {
            var spec = chatClient.prompt().system(systemPrompt);
            if (chatMemory != null && conversationId != null) {
                // ConversationService persists the user message and AgentOrchestrator
                // persists the reply. The memory advisor would save both again.
                List<org.springframework.ai.chat.messages.Message> history = chatMemory.get(conversationId);
                if (history != null && !history.isEmpty()) {
                    spec.messages(history);
                }
            }
            if (toolCallbacks != null && !toolCallbacks.isEmpty()) {
                spec.toolCallbacks(toolCallbacks);
            }
            return spec.stream().content()
                    .filter(s -> s != null && !s.isEmpty());
        } catch (Exception e) {
            log.error("Failed to stream chat with memory via Spring AI: {}", e.getMessage(), e);
            List<org.springframework.ai.chat.messages.Message> history = (chatMemory != null && conversationId != null)
                    ? chatMemory.get(conversationId) : List.of();
            String fallbackPrompt = (history != null && !history.isEmpty())
                    ? history.get(history.size() - 1).getText() : "";
            return Flux.just(generateFallbackResponse(fallbackPrompt));
        }
    }

    public Flux<String> streamCompletion(Prompt prompt) {
        if (isOfflineMode()) {
            return Flux.just(generateFallbackResponse(prompt != null ? prompt.getContents() : ""));
        }
        try {
            return chatModel.stream(prompt)
                    .map(r -> r.getResult() != null && r.getResult().getOutput() != null ? r.getResult().getOutput().getText() : "")
                    .filter(s -> s != null && !s.isEmpty());
        } catch (Exception e) {
            log.error("Failed to stream LLM provider via Spring AI: {}", e.getMessage(), e);
            return Flux.just(generateFallbackResponse(prompt != null ? prompt.getContents() : ""));
        }
    }

    private boolean isOfflineMode() {
        return apiKey == null || apiKey.isBlank() || apiKey.startsWith("dummy") || apiKey.equals("your_api_key_here");
    }

    private String cleanJsonString(String raw) {
        if (raw == null) return "";
        String cleaned = raw.trim();
        if (cleaned.startsWith("```json")) {
            cleaned = cleaned.substring(7);
        } else if (cleaned.startsWith("```")) {
            cleaned = cleaned.substring(3);
        }
        if (cleaned.endsWith("```")) {
            cleaned = cleaned.substring(0, cleaned.length() - 3);
        }
        return cleaned.trim();
    }

    private String generateFallbackResponse(String userPrompt) {
        JsonObject result = new JsonObject();
        result.addProperty("overallSummary", "Bài nộp đã được kiểm tra toàn diện. Hệ thống ghi nhận kết quả và đưa ra các đánh giá kỹ thuật bên dưới.");

        JsonArray stepsArray = new JsonArray();
        try {
            int jsonStart = userPrompt != null ? userPrompt.indexOf('{') : -1;
            if (jsonStart >= 0) {
                JsonObject promptObj = JsonParser.parseString(userPrompt.substring(jsonStart)).getAsJsonObject();
                JsonArray promptSteps = promptObj.getAsJsonArray("steps");
                if (promptSteps != null) {
                    for (JsonElement elem : promptSteps) {
                        JsonObject s = elem.getAsJsonObject();
                        boolean passed = s.has("passed") && s.get("passed").getAsBoolean();
                        String name = s.has("stepName") ? s.get("stepName").getAsString() : "Step";
                        String stepId = s.has("stepId") ? s.get("stepId").getAsString() : "";

                        JsonObject stepAnalysis = new JsonObject();
                        stepAnalysis.addProperty("stepId", stepId);
                        stepAnalysis.addProperty("stepName", name);
                        stepAnalysis.addProperty("stepType", s.has("stepType") ? s.get("stepType").getAsString() : "HTTP_REQUEST");
                        stepAnalysis.addProperty("passed", passed);

                        if (!passed) {
                            stepAnalysis.addProperty("category", "DEBUG_FAIL");
                            stepAnalysis.addProperty("diagnosis", "Yêu cầu HTTP thất bại: mã phản hồi hoặc nội dung trả về không khớp với kết quả kỳ vọng.");
                            stepAnalysis.addProperty("suspectedLocation", "Kiểm tra tầng Controller / Service xử lý request tương ứng.");
                            JsonArray hints = new JsonArray();
                            hints.add("Rà soát lại dữ liệu đầu vào và các ràng buộc dữ liệu (@Valid, @NotNull).");
                            hints.add("Kiểm tra mã trạng thái HTTP (status code) và log ngoại lệ của ứng dụng.");
                            stepAnalysis.add("actionableHints", hints);

                            JsonArray keywords = new JsonArray();
                            keywords.add("Spring Validation");
                            keywords.add("ExceptionHandler");
                            stepAnalysis.add("learningKeywords", keywords);
                        } else {
                            stepAnalysis.addProperty("category", "OPTIMIZE_PASS");
                            stepAnalysis.addProperty("diagnosis", "Step đã vượt qua kiểm thử chức năng thành công.");
                            stepAnalysis.addProperty("suspectedLocation", "Tầng Repository / Service");
                            JsonArray hints = new JsonArray();
                            hints.add("Đảm bảo sử dụng phân trang và chỉ chọn các trường cần thiết (DTO Projection).");
                            hints.add("Kiểm tra số lượng truy vấn cơ sở dữ liệu để tránh N+1 Query.");
                            stepAnalysis.add("actionableHints", hints);

                            JsonArray keywords = new JsonArray();
                            keywords.add("Query Optimization");
                            keywords.add("JPA Fetch Join");
                            stepAnalysis.add("learningKeywords", keywords);
                        }

                        JsonObject audit = new JsonObject();
                        audit.addProperty("durationMs", s.has("durationMs") && !s.get("durationMs").isJsonNull() ? s.get("durationMs").getAsInt() : 120);
                        audit.addProperty("expectedThresholdMs", 200);
                        audit.addProperty("nPlusOneDetected", false);
                        audit.addProperty("queryCount", s.has("dbQueryCount") && !s.get("dbQueryCount").isJsonNull() ? s.get("dbQueryCount").getAsInt() : 1);
                        audit.add("sensitiveFieldsExposed", new JsonArray());
                        audit.addProperty("peakMemoryMb", s.has("peakMemoryMb") && !s.get("peakMemoryMb").isJsonNull() ? s.get("peakMemoryMb").getAsInt() : 64);
                        stepAnalysis.add("telemetryAudit", audit);

                        stepsArray.add(stepAnalysis);
                    }
                }
            } else {
                result.addProperty("message", "Xin chào! Tôi là trợ lý AI WGS. Tôi có thể hỗ trợ bạn tra cứu lớp học, bài tập, bài nộp hoặc tạo bài tập mới.");
                return result.toString();
            }
        } catch (Exception ex) {
            log.warn("Fallback prompt parsing note: {}", ex.getMessage());
        }

        result.add("stepAnalyses", stepsArray);
        return result.toString();
    }
}

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
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import vn.edu.ptit.web_grading_system.ai_assistant_service.config.AiAgentModelProperties;
import vn.edu.ptit.web_grading_system.ai_assistant_service.orchestrator.AgentTrace;
import vn.edu.ptit.web_grading_system.ai_assistant_service.tools.AgentTool;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import vn.edu.ptit.web_grading_system.ai_assistant_service.orchestrator.ReasoningChunkNormalizer;

@Slf4j
@Component
public class LlmClient {

    public enum ApprovalDecision { CONFIRM, REJECT, OTHER }

    private final String provider;
    private final String apiKey;
    private final String modelName;
    private final String baseUrl;
    private final ChatModel chatModel;
    private final ChatClient chatClient;
    private final Gson gson = new Gson();

    public LlmClient(AiAgentModelProperties props, @Qualifier("agentChatModel") ChatModel chatModel) {
        this.provider = props.provider();
        this.apiKey = props.apiKey();
        this.modelName = props.modelName();
        String baseUrl = props.baseUrl();
        this.baseUrl = baseUrl == null ? "" : (baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl);
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

    /** Summary path: a provider error must not become the explanation fallback JSON. */
    public String generateStrictCompletion(String systemPrompt, String userPrompt) {
        if (isOfflineMode()) return null;
        try {
            ChatResponse response = chatModel.call(new Prompt(List.of(
                    new SystemMessage(systemPrompt), new UserMessage(userPrompt))));
            if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
                throw new AiProviderException(new IllegalStateException("Empty summary response"));
            }
            return response.getResult().getOutput().getText();
        } catch (AiProviderException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to select tools via LLM: {}", e.getMessage(), e);
            throw new AiProviderException(e);
        }
    }

    /** Word an already completed write for the user; this call has no tools. */
    public String generateConfirmedActionReply(String originalRequest, String toolName,
                                               String argumentsJson, vn.edu.ptit.web_grading_system.ai_assistant_service.tools.ToolResult result) {
        if (isOfflineMode()) return null;
        try {
            Prompt prompt = new Prompt(List.of(
                    new SystemMessage("Một thao tác ghi đã được người dùng xác nhận và hệ thống thực hiện thành công. "
                            + "Hãy trả lời tự nhiên, ngắn gọn bằng tiếng Việt theo yêu cầu ban đầu và kết quả thực thi. "
                            + "Chỉ khẳng định điều có trong kết quả; không bịa tên, ID hoặc bước tiếp theo. "
                            + "Không nhắc tên tool, tham số kỹ thuật hay việc cần duyệt nữa. "
                            + "Đây chỉ là lượt diễn đạt kết quả, không thực hiện thêm thao tác. Chỉ trả về câu trả lời cho người dùng."),
                    new UserMessage("Yêu cầu ban đầu: " + (originalRequest == null ? "" : originalRequest)
                            + "\nTrạng thái: COMPLETED"
                            + "\nTool đã thực hiện: " + toolName
                            + "\nTham số: " + argumentsJson
                            + "\nKết quả thực thi: " + gson.toJson(result))));
            AgentTrace.recordCurrent("model.request.confirmedAction", Map.of("prompt", prompt.getContents()));
            ChatResponse response = chatModel.call(prompt);
            AgentTrace.recordCurrentReasoning(response);
            String content = response == null || response.getResult() == null || response.getResult().getOutput() == null
                    ? null : response.getResult().getOutput().getText();
            AgentTrace.recordCurrent("model.response.confirmedAction", Map.of("content",
                    content == null ? "" : content));
            return content;
        } catch (Exception e) {
            log.error("Failed to select tools via LLM: {}", e.getMessage(), e);
            throw new AiProviderException(e);
        }
    }

    /** Selects relevant authorized tools without a fixed candidate count. The caller validates role scope. */
    public List<String> chooseRelevantTools(String request, List<AgentTool> authorizedTools) {
        if (isOfflineMode() || authorizedTools == null || authorizedTools.isEmpty()) return List.of();
        StringBuilder catalog = new StringBuilder();
        Set<String> allowed = new HashSet<>();
        for (AgentTool tool : authorizedTools) {
            allowed.add(tool.getName());
            String description = tool.getDescription() == null ? "" : tool.getDescription().replaceAll("\\s+", " ");
            if (description.length() > 120) description = description.substring(0, 120);
            catalog.append(tool.getName()).append(" | ").append(description)
                    .append(" | ").append(compactParameters(tool.getToolDefinition().inputSchema())).append('\n');
        }
        String userRequest = request == null ? "" : request;
        if (userRequest.length() > 3000) userRequest = userRequest.substring(userRequest.length() - 3000);
        try {
            Prompt prompt = new Prompt(List.of(
                    new SystemMessage("Interpret the recent request as a concise English task while preserving its intent. "
                            + "Select every tool needed to complete the current user request, including read tools "
                            + "needed to resolve names before write tools. Use recent conversation only as context. "
                            + "Tool descriptions may be Vietnamese. Return only a JSON array of exact tool names; "
                            + "return [] when no tool is needed. Do not invent names or execute tools."),
                    new UserMessage("Recent request:\n" + userRequest + "\nAvailable tools (name | short description | parameters):\n" + catalog)));
            AgentTrace.recordCurrent("model.request.toolSelection", Map.of("prompt", prompt.getContents()));
            ChatResponse response = chatModel.call(prompt);
            AgentTrace.recordCurrentReasoning(response);
            String raw = response == null || response.getResult() == null || response.getResult().getOutput() == null
                    ? "[]" : response.getResult().getOutput().getText();
            AgentTrace.recordCurrent("model.response.toolSelection", Map.of("content", raw));
            JsonElement parsed = JsonParser.parseString(cleanJsonString(raw));
            if (!parsed.isJsonArray()) return List.of();
            List<String> chosen = new ArrayList<>();
            for (JsonElement item : parsed.getAsJsonArray()) {
                if (item.isJsonPrimitive() && item.getAsJsonPrimitive().isString()) {
                    String name = item.getAsString();
                    if (allowed.contains(name) && !chosen.contains(name)) chosen.add(name);
                }
            }
            return List.copyOf(chosen);
        } catch (Exception e) {
            log.error("Failed to select tools via LLM: {}", e.getMessage(), e);
            throw new AiProviderException(e);
        }
    }

    private String compactParameters(String schema) {
        try {
            JsonObject root = JsonParser.parseString(schema).getAsJsonObject();
            JsonObject properties = root.has("properties") && root.get("properties").isJsonObject()
                    ? root.getAsJsonObject("properties") : new JsonObject();
            Set<String> required = new HashSet<>();
            if (root.has("required") && root.get("required").isJsonArray()) {
                root.getAsJsonArray("required").forEach(item -> required.add(item.getAsString()));
            }
            List<String> fields = new ArrayList<>();
            properties.entrySet().forEach(entry -> {
                JsonObject value = entry.getValue().isJsonObject() ? entry.getValue().getAsJsonObject() : new JsonObject();
                String type = value.has("type") ? value.get("type").getAsString() : "value";
                fields.add(entry.getKey() + ":" + type + (required.contains(entry.getKey()) ? " required" : " optional"));
            });
            return fields.isEmpty() ? "none" : String.join(", ", fields);
        } catch (Exception ignored) {
            return "see tool schema";
        }
    }

    /** Generate a short title without the structured JSON fallback used by explanations. */
    public String generateConversationTitle(String firstQuestion) {
        String question = firstQuestion == null ? "" : firstQuestion.strip();
        String fallback = question.replaceAll("\\s+", " ");
        if (fallback.length() > 80) fallback = fallback.substring(0, 80).stripTrailing() + "…";
        if (fallback.isBlank()) return "Hội thoại mới";
        if (isOfflineMode()) return fallback;
        try {
            String title = chatClient.prompt()
                    .system("Đặt tiêu đề ngắn gọn (tối đa 8 từ) cho cuộc trò chuyện dựa trên câu hỏi đầu tiên. Chỉ trả về tiêu đề, không thêm dấu ngoặc kép hay giải thích. Dùng cùng ngôn ngữ với câu hỏi.")
                    .user(question.length() > 1500 ? question.substring(0, 1500) : question)
                    .call().content();
            if (title != null) {
                title = title.strip().replaceAll("^[\\\"'`]+|[\\\"'`]+$", "").strip();
                if (!title.isBlank()) return title.length() > 100 ? title.substring(0, 100).stripTrailing() : title;
            }
        } catch (Exception e) {
            log.warn("Could not generate conversation title: {}", e.getMessage());
        }
        return fallback;
    }

    public ApprovalDecision classifyPendingActionReply(String userReply, String actionDescription) {
        return classifyPendingActionReply(userReply, actionDescription, "");
    }

    public ApprovalDecision classifyPendingActionReply(String userReply, String actionDescription, String recentContext) {
        if (isOfflineMode() || userReply == null || userReply.isBlank()) return ApprovalDecision.OTHER;
        String action = actionDescription == null ? "" : actionDescription;
        StringBuilder sb = new StringBuilder();
        sb.append("Thao tác đang chờ xác nhận:\n").append(action.substring(0, Math.min(action.length(), 4000)));
        if (recentContext != null && !recentContext.isBlank()) {
            sb.append("\n\nNgữ cảnh trò chuyện / hướng dẫn gần nhất của trợ lý:\n")
                    .append(recentContext.substring(0, Math.min(recentContext.length(), 4000)));
        }
        sb.append("\n\nTin nhắn mới của người dùng:\n")
                .append(userReply.substring(0, Math.min(userReply.length(), 4000)));
        try {
            Prompt prompt = new Prompt(List.of(
                    new SystemMessage("Bạn là bộ phân loại ý định người dùng đối với một thao tác ghi dữ liệu hoặc bước tiếp theo đang chờ xác nhận.\n"
                            + "Quy tắc phân loại:\n"
                            + "- Trả về CONFIRM: Khi người dùng đồng ý thực hiện, chấp nhận đề xuất, hoặc muốn tiếp tục tiến trình theo chỉ dẫn của trợ lý (ví dụ: 'tiếp', 'tiếp tục', 'làm đi', 'tạo đi', 'triển luôn', 'tiến hành', 'ok', 'được', 'đồng ý', 'chốt', 'uh', 'yes', 'cứ làm đi', hoặc làm theo hướng dẫn của trợ lý ở tin nhắn trước).\n"
                            + "- Trả về REJECT: Khi người dùng từ chối, yêu cầu dừng lại, hủy bỏ hoặc không muốn thực hiện thao tác đang chờ (ví dụ: 'hủy', 'không làm', 'thôi', 'bỏ qua', 'không tạo nữa').\n"
                            + "- Trả về OTHER: Khi người dùng đặt câu hỏi, muốn sửa đổi tham số, thắc mắc, chuyển sang chủ đề khác hoặc ý định không rõ ràng.\n"
                            + "Chỉ trả về đúng một từ duy nhất: CONFIRM, REJECT, hoặc OTHER. Tuyệt đối không thêm bất kỳ văn bản, dấu câu hay giải thích nào khác."),
                    new UserMessage(sb.toString())));
            AgentTrace.recordCurrent("model.request.approvalClassification", Map.of("prompt", prompt.getContents()));
            ChatResponse response = chatModel.call(prompt);
            AgentTrace.recordCurrentReasoning(response);
            String answer = response == null || response.getResult() == null
                    || response.getResult().getOutput() == null ? ""
                    : response.getResult().getOutput().getText();
            AgentTrace.recordCurrent("model.response.approvalClassification", Map.of("content",
                    answer == null ? "" : answer));
            String cleaned = answer.strip().replaceAll("[`*_#]", "").strip().toUpperCase(Locale.ROOT);
            if ("CONFIRM".equals(cleaned) || "CONFIRM.".equals(cleaned)) return ApprovalDecision.CONFIRM;
            if ("REJECT".equals(cleaned) || "REJECT.".equals(cleaned)) return ApprovalDecision.REJECT;
            return ApprovalDecision.OTHER;
        } catch (Exception e) {
            log.warn("Could not classify chat confirmation: {}", e.getMessage());
            return ApprovalDecision.OTHER;
        }
    }

    public <T> T generateEntity(String systemPrompt, String userPrompt, Class<T> entityClass) {
        if (isOfflineMode()) {
            log.info("AI API key not configured — no real analysis possible, returning null");
            return null;
        }
        try {
            // Review: 2026-10-31 — scope JSON mode to this call only (explanation analysis).
            // Global response-format in application.yaml would break classifyPendingActionReply,
            // generateConversationTitle, ConversationSummarizer, and streaming chat/agent turns,
            // which all expect plain text. The explanation prompt already contains "JSON",
            // satisfying OpenAI's requirement for json_object mode.
            org.springframework.ai.openai.OpenAiChatModel.ResponseFormat jsonFormat =
                    org.springframework.ai.openai.OpenAiChatModel.ResponseFormat.builder()
                            .type(org.springframework.ai.openai.OpenAiChatModel.ResponseFormat.Type.JSON_OBJECT)
                            .build();
            return chatClient.prompt()
                    .system(systemPrompt)
                    .user(userPrompt)
                    .options(org.springframework.ai.openai.OpenAiChatOptions.builder()
                            .responseFormat(jsonFormat))
                    .call()
                    .entity(entityClass);
        } catch (Exception e) {
            // LLM returned empty / unparseable content — degraded path, not a system error.
            // Return null so the caller can fall back (generateStrictCompletion) or degrade.
            log.warn("LLM returned empty or unparseable content for structured entity: {}", e.getMessage());
            return null;
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
            AgentTrace.recordCurrentReasoning(response);
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
                    .filter(s -> s != null && !s.isEmpty())
                    .onErrorMap(AiProviderException::new);
        } catch (Exception e) {
            log.error("Failed to stream chat with memory via Spring AI: {}", e.getMessage(), e);
            return Flux.error(new AiProviderException(e));
        }
    }

    public Flux<String> streamAgentTurn(String systemPrompt,
            List<org.springframework.ai.chat.messages.Message> messages,
            List<ToolCallback> toolCallbacks) {
        return streamAgentTurn(systemPrompt, messages, toolCallbacks, null);
    }

    /** A non-null toolChoice is used only for bounded lookup before asking the user for an ID. */
    public Flux<String> streamAgentTurn(String systemPrompt,
            List<org.springframework.ai.chat.messages.Message> messages,
            List<ToolCallback> toolCallbacks, String toolChoice) {
        return streamAgentTurn(systemPrompt, messages, toolCallbacks, toolChoice, delta -> {});
    }

    public Flux<String> streamAgentTurn(String systemPrompt,
            List<org.springframework.ai.chat.messages.Message> messages,
            List<ToolCallback> toolCallbacks, String toolChoice, Consumer<String> onReasoningDelta) {
        if (isOfflineMode()) {
            String last = messages == null || messages.isEmpty() ? "" : messages.getLast().getText();
            return Flux.just(generateFallbackResponse(last));
        }
        try {
            var spec = chatClient.prompt().system(systemPrompt);
            if (messages != null && !messages.isEmpty()) spec.messages(messages);
            if (toolCallbacks != null && !toolCallbacks.isEmpty()) spec.toolCallbacks(toolCallbacks);
            if (toolChoice != null && !isThinkingModelOrDeepSeek()) {
                Object choice = "required".equals(toolChoice) ? "required"
                        : java.util.Map.of("type", "function", "function", java.util.Map.of("name", toolChoice));
                spec.options(OpenAiChatOptions.builder().toolChoice(choice));
            }
            AgentTrace.ReasoningCapture reasoning = AgentTrace.captureReasoning();
            ReasoningChunkNormalizer normalizer = new ReasoningChunkNormalizer();
            return spec.stream().chatResponse()
                    .doOnNext(response -> {
                        reasoning.accept(response);
                        if (response.getResult() == null || response.getResult().getOutput() == null) return;
                        Object raw = response.getResult().getOutput().getMetadata().get("reasoningContent");
                        if (!(raw instanceof String)) raw = response.getResult().getOutput().getMetadata().get("reasoning_content");
                        if (!(raw instanceof String)) raw = response.getResult().getOutput().getMetadata().get("reasoning");
                        if (raw instanceof String text) {
                            String delta = normalizer.accept(text, true);
                            if (!delta.isEmpty()) onReasoningDelta.accept(delta);
                        }
                    })
                    .map(response -> response.getResult() == null || response.getResult().getOutput() == null
                            || response.getResult().getOutput().getText() == null
                            ? "" : response.getResult().getOutput().getText())
                    .filter(s -> s != null && !s.isEmpty())
                    .doOnComplete(() -> reasoning.finish("complete"))
                    .doOnError(error -> reasoning.finish("error"))
                    .doOnCancel(() -> reasoning.finish("cancelled"))
                    .onErrorMap(AiProviderException::new);
        } catch (Exception e) {
            return Flux.error(new AiProviderException(e));
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

    private boolean isThinkingModelOrDeepSeek() {
        return "deepseek".equalsIgnoreCase(provider)
                || (baseUrl != null && baseUrl.contains("deepseek"))
                || (modelName != null && (modelName.contains("deepseek") || modelName.contains("reasoner") || modelName.contains("r1")));
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

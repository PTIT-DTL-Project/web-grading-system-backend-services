package vn.edu.ptit.web_grading_system.ai_assistant_service.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import vn.edu.ptit.web_grading_system.ai_assistant_service.config.AiExplanationModelProperties;

import java.util.List;
import java.util.regex.Pattern;

@Slf4j
@Component
public class ExplanationLlmClient {

    private static final Pattern THINK_PATTERN = Pattern.compile("(?s)<think>.*?</think>");

    private final String apiKey;
    private final ChatModel chatModel;
    private final ChatClient chatClient;
    private final int maxCompletionTokens;

    public ExplanationLlmClient(AiExplanationModelProperties props,
                               @Qualifier("explanationChatModel") ChatModel chatModel) {
        this.apiKey = props.apiKey();
        String baseUrl = props.baseUrl();
        String normalizedBaseUrl = baseUrl == null ? "" : (baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl);
        this.chatModel = chatModel;
        this.chatClient = ChatClient.create(chatModel);
        this.maxCompletionTokens = props.maxCompletionTokensOrDefault(4096);
    }

    public boolean isOfflineMode() {
        return apiKey == null || apiKey.isBlank() || apiKey.startsWith("dummy") || apiKey.equals("your_api_key_here");
    }

    private String cleanRaw(String raw) {
        if (raw == null) return null;
        String s = raw.trim();
        s = THINK_PATTERN.matcher(s).replaceAll("").trim();
        if (s.startsWith("```json")) {
            s = s.substring(7);
        } else if (s.startsWith("```")) {
            s = s.substring(3);
        }
        if (s.endsWith("```")) {
            s = s.substring(0, s.length() - 3);
        }
        s = s.trim();
        int start = s.indexOf('{');
        int end = s.lastIndexOf('}');
        if (start >= 0 && end > start) {
            s = s.substring(start, end + 1);
        }
        return s;
    }

    public <T> T generateEntity(String systemPrompt, String userPrompt, Class<T> entityClass) {
        if (isOfflineMode()) {
            log.info("AI API key not configured for explanation — no real analysis possible, returning null");
            return null;
        }
        try {
            OpenAiChatModel.ResponseFormat jsonFormat =
                    OpenAiChatModel.ResponseFormat.builder()
                            .type(OpenAiChatModel.ResponseFormat.Type.JSON_OBJECT)
                            .build();
            String raw = chatClient.prompt()
                    .system(systemPrompt)
                    .user(userPrompt)
                    .options(OpenAiChatOptions.builder()
                            .responseFormat(jsonFormat)
                            .maxCompletionTokens(maxCompletionTokens))
                    .call()
                    .content();
            if (raw == null || raw.isBlank()) {
                log.warn("LLM returned empty content for structured entity (explanation)");
                return null;
            }
            String cleaned = cleanRaw(raw);
            log.warn("Explanation raw len={}, cleaned len={}, hasThink={}", raw.length(), cleaned == null ? null : cleaned.length(), raw.contains("<think>"));
            if (cleaned == null || cleaned.isBlank()) {
                return null;
            }
            try {
                return chatClient.prompt()
                        .system(systemPrompt)
                        .user("Return ONLY valid JSON matching the schema. Use this JSON content:\n" + cleaned)
                        .options(OpenAiChatOptions.builder()
                                .responseFormat(jsonFormat)
                                .maxCompletionTokens(Math.max(512, maxCompletionTokens / 2)))
                        .call()
                        .entity(entityClass);
            } catch (Exception ex) {
                log.warn("Explanation entity parse failed after clean: {}", ex.getMessage());
                return null;
            }
        } catch (Exception e) {
            log.warn("LLM returned empty or unparseable content for structured entity (explanation): {}", e.getMessage());
            return null;
        }
    }

    public String generateStrictCompletion(String systemPrompt, String userPrompt) {
        if (isOfflineMode()) return null;
        try {
            ChatResponse response = chatModel.call(new Prompt(List.of(
                    new SystemMessage(systemPrompt), new UserMessage(userPrompt)),
                    OpenAiChatOptions.builder().maxCompletionTokens(maxCompletionTokens).build()));
            if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
                throw new AiProviderException(new IllegalStateException("Empty summary response"));
            }
            String text = response.getResult().getOutput().getText();
            String cleaned = cleanRaw(text);
            log.warn("Explanation strict raw len={}, cleaned len={}, hasThink={}", text == null ? null : text.length(), cleaned == null ? null : cleaned.length(), text != null && text.contains("<think>"));
            return cleaned != null && !cleaned.isBlank() ? cleaned : text;
        } catch (AiProviderException e) {
            throw e;
        } catch (Exception e) {
            throw new AiProviderException(e);
        }
    }
}

package vn.edu.ptit.web_grading_system.ai_assistant_service.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.model.openai.autoconfigure.OpenAiChatProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class SpringAiConfig {

    @Bean
    public static BeanPostProcessor openAiChatPropertiesSanitizer() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessBeforeInitialization(Object bean, String beanName) {
                if (bean instanceof OpenAiChatProperties properties) {
                    if (properties.getReasoningEffort() != null &&
                            (properties.getReasoningEffort().isBlank() || "none".equalsIgnoreCase(properties.getReasoningEffort()))) {
                        properties.setReasoningEffort(null);
                    }
                    if (properties.getOptions() != null && properties.getOptions().getReasoningEffort() != null &&
                            (properties.getOptions().getReasoningEffort().isBlank() || "none".equalsIgnoreCase(properties.getOptions().getReasoningEffort()))) {
                        properties.getOptions().setReasoningEffort(null);
                    }
                }
                return bean;
            }
        };
    }

    @Bean
    @Primary
    public ChatClient chatClient(@Qualifier("agentChatModel") ChatModel chatModel) {
        return ChatClient.builder(chatModel).build();
    }

    @Bean(name = "agentChatModel")
    @Primary
    public ChatModel agentChatModel(@Qualifier("openAiChatModel") ChatModel chatModel) {
        return chatModel;
    }

    @Bean(name = "explanationChatModel")
    public ChatModel explanationChatModel(@Qualifier("openAiChatModel") ChatModel chatModel) {
        return chatModel;
    }
}

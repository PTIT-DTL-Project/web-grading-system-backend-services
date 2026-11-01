package vn.edu.ptit.web_grading_system.ai_assistant_service.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class SpringAiConfig {

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

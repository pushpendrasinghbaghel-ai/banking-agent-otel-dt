package com.banking.agent.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Configuration for Ollama LLM provider.
 * Supports local/self-hosted Ollama models.
 */
@Configuration
public class LlmConfig {

    /**
     * Ollama chat client for local/self-hosted models
     */
    @Bean
    @Primary
    @ConditionalOnProperty(name = "spring.ai.ollama.base-url")
    public ChatClient ollamaChatClient(OllamaChatModel ollamaChatModel) {
        return ChatClient.builder(ollamaChatModel)
                .defaultSystem("You are a helpful banking assistant with expertise in financial services, " +
                        "banking operations, and customer service. Provide accurate, secure, and compliant responses.")
                .build();
    }
}

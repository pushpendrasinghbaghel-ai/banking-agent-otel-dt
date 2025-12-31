package com.banking.agent.service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for managing Ollama LLM provider.
 */
@Service
@Slf4j
public class LlmProviderService {

    private final Map<String, ChatModel> chatModels = new ConcurrentHashMap<>();

    @Value("${banking.agent.default-llm:ollama}")
    private String defaultProvider;

    @Autowired(required = false)
    public void setOllamaChatModel(org.springframework.ai.ollama.OllamaChatModel ollamaChatModel) {
        if (ollamaChatModel != null) {
            chatModels.put("ollama", ollamaChatModel);
            log.info("Ollama ChatModel registered");
        }
    }

    /**
     * Get ChatClient for the specified provider
     */
    public ChatClient getChatClient(String provider) {
        ChatModel chatModel = chatModels.get(provider.toLowerCase());
        if (chatModel == null) {
            log.warn("Provider {} not available, using default: {}", provider, defaultProvider);
            chatModel = chatModels.get(defaultProvider);
        }
        
        if (chatModel == null) {
            throw new IllegalStateException("No LLM providers available. Please configure at least one provider.");
        }

        return ChatClient.builder(chatModel)
                .defaultSystem("You are a helpful banking assistant with expertise in financial services.")
                .build();
    }

    /**
     * Get default ChatClient
     */
    public ChatClient getDefaultChatClient() {
        return getChatClient(defaultProvider);
    }

    /**
     * Check if a specific provider is available
     */
    public boolean isProviderAvailable(String provider) {
        return chatModels.containsKey(provider.toLowerCase());
    }

    /**
     * Get all available providers
     */
    public Map<String, ChatModel> getAvailableProviders() {
        return Map.copyOf(chatModels);
    }
}

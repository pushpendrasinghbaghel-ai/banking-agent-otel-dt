package com.banking.agent.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import lombok.Getter;

/**
 * Configuration class for Banking Agent settings.
 * Manages LLM provider selection and banking-specific configurations.
 */
@Configuration
@Getter
public class BankingAgentConfig {

    @Value("${banking.agent.default-llm:openai}")
    private String defaultLlm;

    @Value("${banking.agent.fallback-llm:ollama}")
    private String fallbackLlm;

    @Value("${banking.agent.max-transaction-amount:10000}")
    private double maxTransactionAmount;

    @Value("${banking.agent.currency:USD}")
    private String currency;
}

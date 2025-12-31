package com.banking.agent.service;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * OpenLLMetry Service - Enhanced LLM observability with OpenTelemetry Semantic Conventions
 * Implements Gen AI semantic conventions for comprehensive LLM monitoring
 */
@Service
@Slf4j
public class OpenLLMetryService {

    private final Tracer tracer;

    // OpenTelemetry Semantic Conventions for Gen AI (as per spec)
    private static final AttributeKey<String> GEN_AI_SYSTEM = AttributeKey.stringKey("gen_ai.system");
    private static final AttributeKey<String> GEN_AI_REQUEST_MODEL = AttributeKey.stringKey("gen_ai.request.model");
    private static final AttributeKey<Double> GEN_AI_REQUEST_TEMPERATURE = AttributeKey.doubleKey("gen_ai.request.temperature");
    private static final AttributeKey<Long> GEN_AI_REQUEST_MAX_TOKENS = AttributeKey.longKey("gen_ai.request.max_tokens");
    private static final AttributeKey<Long> GEN_AI_USAGE_INPUT_TOKENS = AttributeKey.longKey("gen_ai.usage.input_tokens");
    private static final AttributeKey<Long> GEN_AI_USAGE_OUTPUT_TOKENS = AttributeKey.longKey("gen_ai.usage.output_tokens");
    private static final AttributeKey<String> GEN_AI_RESPONSE_MODEL = AttributeKey.stringKey("gen_ai.response.model");
    private static final AttributeKey<String> GEN_AI_RESPONSE_FINISH_REASONS = AttributeKey.stringKey("gen_ai.response.finish_reasons");
    private static final AttributeKey<String> GEN_AI_OPERATION_NAME = AttributeKey.stringKey("gen_ai.operation.name");
    
    // LLM-specific attributes
    private static final AttributeKey<String> LLM_REQUEST_TYPE = AttributeKey.stringKey("llm.request.type");
    private static final AttributeKey<Long> LLM_PROMPT_TOKENS = AttributeKey.longKey("llm.prompt.tokens");
    private static final AttributeKey<Long> LLM_COMPLETION_TOKENS = AttributeKey.longKey("llm.completion.tokens");
    private static final AttributeKey<Long> LLM_TOTAL_TOKENS = AttributeKey.longKey("llm.total.tokens");
    private static final AttributeKey<Double> LLM_COST_USD = AttributeKey.doubleKey("llm.cost.usd");
    private static final AttributeKey<String> LLM_PROMPT_HASH = AttributeKey.stringKey("llm.prompt.hash");
    private static final AttributeKey<Long> LLM_PROMPT_LENGTH = AttributeKey.longKey("llm.prompt.length");
    private static final AttributeKey<Long> LLM_RESPONSE_LENGTH = AttributeKey.longKey("llm.response.length");

    // Banking-specific attributes
    private static final AttributeKey<String> BANKING_INTENT = AttributeKey.stringKey("banking.intent");
    private static final AttributeKey<String> BANKING_ACCOUNT = AttributeKey.stringKey("banking.account");

    public OpenLLMetryService(Tracer tracer) {
        this.tracer = tracer;
        log.info("OpenLLMetry Service initialized with Gen AI semantic conventions");
    }

    /**
     * Create a span for LLM chat completion
     */
    public LLMSpanContext startChatCompletion(String provider, String model, String operationType) {
        Span span = tracer.spanBuilder("gen_ai.chat.completions")
                .setAttribute(GEN_AI_SYSTEM, normalizeProvider(provider))
                .setAttribute(GEN_AI_REQUEST_MODEL, model)
                .setAttribute(GEN_AI_OPERATION_NAME, "chat")
                .setAttribute(LLM_REQUEST_TYPE, operationType)
                .startSpan();

        return new LLMSpanContext(span, System.currentTimeMillis(), provider, model);
    }

    /**
     * Record prompt details
     */
    public void recordPrompt(LLMSpanContext context, String prompt, Double temperature, Integer maxTokens) {
        if (prompt != null) {
            context.span.setAttribute(LLM_PROMPT_LENGTH, (long) prompt.length());
            context.span.setAttribute(LLM_PROMPT_HASH, String.valueOf(prompt.hashCode()));
            context.promptTokens = estimateTokens(prompt);
            context.span.setAttribute(LLM_PROMPT_TOKENS, context.promptTokens);
        }
        
        if (temperature != null) {
            context.span.setAttribute(GEN_AI_REQUEST_TEMPERATURE, temperature);
        }
        
        if (maxTokens != null) {
            context.span.setAttribute(GEN_AI_REQUEST_MAX_TOKENS, maxTokens.longValue());
        }
    }

    /**
     * Record completion response
     */
    public void recordCompletion(LLMSpanContext context, String response, String finishReason) {
        if (response != null) {
            context.span.setAttribute(LLM_RESPONSE_LENGTH, (long) response.length());
            context.completionTokens = estimateTokens(response);
            context.span.setAttribute(LLM_COMPLETION_TOKENS, context.completionTokens);
            
            long totalTokens = context.promptTokens + context.completionTokens;
            context.span.setAttribute(LLM_TOTAL_TOKENS, totalTokens);
            context.span.setAttribute(GEN_AI_USAGE_INPUT_TOKENS, context.promptTokens);
            context.span.setAttribute(GEN_AI_USAGE_OUTPUT_TOKENS, context.completionTokens);
        }
        
        context.span.setAttribute(GEN_AI_RESPONSE_MODEL, context.model);
        
        if (finishReason != null) {
            context.span.setAttribute(GEN_AI_RESPONSE_FINISH_REASONS, finishReason);
        }
        
        long durationMs = System.currentTimeMillis() - context.startTime;
        context.span.setAttribute(AttributeKey.longKey("llm.latency.ms"), durationMs);
        
        // Calculate estimated cost
        double cost = estimateCost(context.provider, context.promptTokens, context.completionTokens);
        if (cost > 0) {
            context.span.setAttribute(LLM_COST_USD, cost);
        }
    }

    /**
     * Record banking-specific context
     */
    public void recordBankingContext(LLMSpanContext context, String intent, String accountNumber) {
        if (intent != null) {
            context.span.setAttribute(BANKING_INTENT, intent);
        }
        if (accountNumber != null) {
            context.span.setAttribute(BANKING_ACCOUNT, accountNumber);
        }
    }

    /**
     * Mark span as successful
     */
    public void recordSuccess(LLMSpanContext context) {
        context.span.setStatus(StatusCode.OK);
        context.span.end();
        
        log.debug("LLM call completed - Provider: {}, Model: {}, Prompt tokens: {}, Completion tokens: {}, Duration: {}ms",
                context.provider, context.model, context.promptTokens, context.completionTokens,
                System.currentTimeMillis() - context.startTime);
    }

    /**
     * Mark span as failed
     */
    public void recordError(LLMSpanContext context, Exception error) {
        context.span.recordException(error);
        context.span.setStatus(StatusCode.ERROR, error.getMessage());
        context.span.setAttribute(AttributeKey.stringKey("error.type"), error.getClass().getSimpleName());
        context.span.end();
        
        log.error("LLM call failed - Provider: {}, Model: {}, Error: {}",
                context.provider, context.model, error.getMessage());
    }

    /**
     * Estimate token count (rough approximation: 1 token ≈ 4 characters)
     */
    private long estimateTokens(String text) {
        if (text == null) return 0;
        return text.length() / 4;
    }

    /**
     * Estimate cost based on provider and token usage
     * Prices as of 2025 (approximate)
     */
    private double estimateCost(String provider, long promptTokens, long completionTokens) {
        double costPer1kPrompt = 0;
        double costPer1kCompletion = 0;

        switch (provider.toLowerCase()) {
            case "openai":
                // GPT-4 pricing
                costPer1kPrompt = 0.03;
                costPer1kCompletion = 0.06;
                break;
            case "gemini":
                // Gemini Pro pricing
                costPer1kPrompt = 0.00025;
                costPer1kCompletion = 0.0005;
                break;
            case "ollama":
                // Self-hosted, no API cost
                return 0.0;
            default:
                return 0.0;
        }

        double promptCost = (promptTokens / 1000.0) * costPer1kPrompt;
        double completionCost = (completionTokens / 1000.0) * costPer1kCompletion;
        
        return promptCost + completionCost;
    }

    /**
     * Normalize provider name to OpenTelemetry semantic convention
     */
    private String normalizeProvider(String provider) {
        return switch (provider.toLowerCase()) {
            case "openai" -> "openai";
            case "gemini" -> "google";
            case "ollama" -> "ollama";
            default -> provider.toLowerCase();
        };
    }

    /**
     * Context holder for LLM span with token tracking
     */
    public static class LLMSpanContext {
        private final Span span;
        private final long startTime;
        private final String provider;
        private final String model;
        private long promptTokens = 0;
        private long completionTokens = 0;

        public LLMSpanContext(Span span, long startTime, String provider, String model) {
            this.span = span;
            this.startTime = startTime;
            this.provider = provider;
            this.model = model;
        }

        public Span getSpan() {
            return span;
        }

        public long getTotalTokens() {
            return promptTokens + completionTokens;
        }

        public long getPromptTokens() {
            return promptTokens;
        }

        public long getCompletionTokens() {
            return completionTokens;
        }
    }
}

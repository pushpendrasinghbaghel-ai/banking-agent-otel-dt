package com.banking.agent.service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import lombok.extern.slf4j.Slf4j;

/**
 * Traced Chat Service - Wraps ChatClient calls with OpenTelemetry tracing.
 * 
 * This service captures:
 * - gen_ai.prompt: The full prompt sent to the LLM
 * - gen_ai.completion: The response from the LLM
 * - Token estimates and latency metrics
 * 
 * Use this service instead of calling ChatClient directly to get
 * automatic LLM tracing with prompt/response visibility.
 */
@Service
@Slf4j
public class TracedChatService {

    private final Tracer tracer;
    private final LlmProviderService llmProviderService;
    
    @Value("${spring.ai.ollama.chat.options.model:llama3.2}")
    private String ollamaModel;

    // Gen AI Semantic Convention attribute keys
    private static final AttributeKey<String> GEN_AI_SYSTEM = AttributeKey.stringKey("gen_ai.system");
    private static final AttributeKey<String> GEN_AI_REQUEST_MODEL = AttributeKey.stringKey("gen_ai.request.model");
    private static final AttributeKey<String> GEN_AI_OPERATION_NAME = AttributeKey.stringKey("gen_ai.operation.name");
    private static final AttributeKey<Long> GEN_AI_USAGE_INPUT_TOKENS = AttributeKey.longKey("gen_ai.usage.input_tokens");
    private static final AttributeKey<Long> GEN_AI_USAGE_OUTPUT_TOKENS = AttributeKey.longKey("gen_ai.usage.output_tokens");
    private static final AttributeKey<String> GEN_AI_RESPONSE_FINISH_REASONS = AttributeKey.stringKey("gen_ai.response.finish_reasons");
    private static final AttributeKey<String> GEN_AI_PROMPT = AttributeKey.stringKey("gen_ai.prompt");
    private static final AttributeKey<String> GEN_AI_COMPLETION = AttributeKey.stringKey("gen_ai.completion");
    private static final AttributeKey<Long> LLM_LATENCY_MS = AttributeKey.longKey("llm.latency_ms");

    public TracedChatService(Tracer tracer, LlmProviderService llmProviderService) {
        this.tracer = tracer;
        this.llmProviderService = llmProviderService;
        log.info("TracedChatService initialized - LLM calls will include prompt/completion in traces");
    }

    /**
     * Send a chat prompt to the LLM with full tracing.
     * 
     * @param prompt The prompt to send to the LLM
     * @param llmProvider The LLM provider to use (defaults to "ollama")
     * @return The LLM response
     */
    public String chat(String prompt, String llmProvider) {
        long startTime = System.currentTimeMillis();
        
        // Always use "ollama" as the provider since that's what we're actually using
        // The llmProvider parameter is kept for API compatibility
        
        // Create LLM span with Gen AI semantic conventions
        Span llmSpan = tracer.spanBuilder("ollama.chat.completions")
                .setAttribute(GEN_AI_SYSTEM, "ollama")
                .setAttribute(GEN_AI_REQUEST_MODEL, ollamaModel)
                .setAttribute(GEN_AI_OPERATION_NAME, "chat")
                .startSpan();

        // Add prompt to span (truncate if too long to avoid massive payloads)
        String truncatedPrompt = truncateForSpan(prompt, 2000);
        llmSpan.setAttribute(GEN_AI_PROMPT, truncatedPrompt);
        llmSpan.setAttribute(GEN_AI_USAGE_INPUT_TOKENS, estimateTokens(prompt));
        
        log.debug("Sending prompt to ollama ({} chars)", prompt.length());

        try {
            // Get the ChatClient and make the call
            ChatClient chatClient = llmProviderService.getChatClient(llmProvider);
            
            String response = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();

            long durationMs = System.currentTimeMillis() - startTime;
            
            // Add response attributes
            String truncatedResponse = truncateForSpan(response, 2000);
            llmSpan.setAttribute(GEN_AI_COMPLETION, truncatedResponse);
            llmSpan.setAttribute(GEN_AI_USAGE_OUTPUT_TOKENS, estimateTokens(response));
            llmSpan.setAttribute(GEN_AI_RESPONSE_FINISH_REASONS, "stop");
            llmSpan.setAttribute(LLM_LATENCY_MS, durationMs);
            llmSpan.setStatus(StatusCode.OK);
            
            log.debug("LLM response received in {}ms ({} chars)", durationMs, response.length());

            return response;

        } catch (Exception e) {
            llmSpan.recordException(e);
            llmSpan.setStatus(StatusCode.ERROR, e.getMessage());
            llmSpan.setAttribute(AttributeKey.stringKey("error.type"), e.getClass().getSimpleName());
            log.error("LLM call failed: {}", e.getMessage());
            throw e;

        } finally {
            llmSpan.end();
        }
    }

    /**
     * Convenience method - uses default Ollama provider
     */
    public String chat(String prompt) {
        return chat(prompt, "ollama");
    }

    private String truncateForSpan(String text, int maxLength) {
        if (text == null) return "";
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength) + "... [truncated]";
    }

    private long estimateTokens(String text) {
        if (text == null) return 0;
        // Rough approximation: 1 token ≈ 4 characters
        return text.length() / 4;
    }
}

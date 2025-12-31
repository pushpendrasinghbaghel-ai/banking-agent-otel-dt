package com.banking.agent.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * LLM Monitoring Service for tracking LLM performance, correctness, and usage
 * Integrates with Dynatrace for observability
 */
@Service
@Slf4j
public class LlmMonitoringService {

    private final MeterRegistry meterRegistry;
    private final Tracer tracer;
    
    // Metrics
    private final Counter llmRequestCounter;
    private final Counter llmErrorCounter;
    private final Counter llmTokenCounter;
    private final Counter llmCorrectnessCounter;
    private final Map<String, Timer> llmResponseTimers = new ConcurrentHashMap<>();

    // Attribute keys for tracing
    private static final AttributeKey<String> LLM_PROVIDER = AttributeKey.stringKey("llm.provider");
    private static final AttributeKey<String> LLM_MODEL = AttributeKey.stringKey("llm.model");
    private static final AttributeKey<String> LLM_INTENT = AttributeKey.stringKey("llm.intent");
    private static final AttributeKey<Long> LLM_TOKENS = AttributeKey.longKey("llm.tokens");
    private static final AttributeKey<Long> LLM_RESPONSE_TIME_MS = AttributeKey.longKey("llm.response_time_ms");
    private static final AttributeKey<Boolean> LLM_SUCCESS = AttributeKey.booleanKey("llm.success");
    private static final AttributeKey<String> LLM_ERROR = AttributeKey.stringKey("llm.error");
    private static final AttributeKey<Double> LLM_CORRECTNESS_SCORE = AttributeKey.doubleKey("llm.correctness_score");

    public LlmMonitoringService(MeterRegistry meterRegistry, Tracer tracer) {
        this.meterRegistry = meterRegistry;
        this.tracer = tracer;
        
        // Initialize counters
        this.llmRequestCounter = Counter.builder("llm.requests.total")
                .description("Total number of LLM requests")
                .register(meterRegistry);
                
        this.llmErrorCounter = Counter.builder("llm.errors.total")
                .description("Total number of LLM errors")
                .register(meterRegistry);
                
        this.llmTokenCounter = Counter.builder("llm.tokens.total")
                .description("Total number of LLM tokens consumed")
                .register(meterRegistry);
                
        this.llmCorrectnessCounter = Counter.builder("llm.correctness.total")
                .description("Total correctness evaluations")
                .register(meterRegistry);
        
        log.info("LLM Monitoring Service initialized");
    }

    /**
     * Start monitoring an LLM request
     */
    public LlmMonitoringContext startMonitoring(String provider, String model, String intent) {
        Span span = tracer.spanBuilder("llm.request")
                .setAttribute(LLM_PROVIDER, provider)
                .setAttribute(LLM_MODEL, model)
                .setAttribute(LLM_INTENT, intent)
                .startSpan();

        llmRequestCounter.increment();
        
        Timer timer = getOrCreateTimer(provider, model);
        Timer.Sample sample = Timer.start(meterRegistry);
        
        log.debug("Started monitoring LLM request - Provider: {}, Model: {}, Intent: {}", 
                provider, model, intent);
        
        return new LlmMonitoringContext(span, sample, timer, provider, model, intent);
    }

    /**
     * Record successful LLM completion
     */
    public void recordSuccess(LlmMonitoringContext context, int tokens, String response) {
        try {
            long durationMs = context.sample.stop(context.timer);
            
            context.span.setAttribute(LLM_SUCCESS, true);
            context.span.setAttribute(LLM_TOKENS, (long) tokens);
            context.span.setAttribute(LLM_RESPONSE_TIME_MS, durationMs);
            context.span.setStatus(StatusCode.OK);
            
            llmTokenCounter.increment(tokens);
            
            log.debug("LLM request succeeded - Provider: {}, Duration: {}ms, Tokens: {}", 
                    context.provider, durationMs, tokens);
        } finally {
            context.span.end();
        }
    }

    /**
     * Record LLM error
     */
    public void recordError(LlmMonitoringContext context, Exception error) {
        try {
            context.sample.stop(context.timer);
            
            context.span.setAttribute(LLM_SUCCESS, false);
            context.span.setAttribute(LLM_ERROR, error.getMessage());
            context.span.recordException(error);
            context.span.setStatus(StatusCode.ERROR, error.getMessage());
            
            llmErrorCounter.increment();
            
            log.error("LLM request failed - Provider: {}, Error: {}", 
                    context.provider, error.getMessage());
        } finally {
            context.span.end();
        }
    }

    /**
     * Record LLM response correctness
     * Score: 0.0 (incorrect) to 1.0 (correct)
     */
    public void recordCorrectness(String provider, String intent, double correctnessScore, String feedback) {
        Span span = tracer.spanBuilder("llm.correctness_evaluation")
                .setAttribute(LLM_PROVIDER, provider)
                .setAttribute(LLM_INTENT, intent)
                .setAttribute(LLM_CORRECTNESS_SCORE, correctnessScore)
                .setAttribute(AttributeKey.stringKey("feedback"), feedback != null ? feedback : "")
                .startSpan();

        try {
            llmCorrectnessCounter.increment();
            
            // Record as gauge for current correctness level
            meterRegistry.gauge("llm.correctness.score", 
                    List.of(
                            Tag.of("provider", provider),
                            Tag.of("intent", intent)
                    ), 
                    correctnessScore);
            
            span.setStatus(StatusCode.OK);
            log.info("Recorded LLM correctness - Provider: {}, Intent: {}, Score: {}, Feedback: {}", 
                    provider, intent, correctnessScore, feedback);
        } finally {
            span.end();
        }
    }

    /**
     * Create a business event for significant LLM operations
     */
    public void recordBusinessEvent(String eventType, Map<String, String> attributes) {
        Span span = tracer.spanBuilder("banking.business_event")
                .setAttribute(AttributeKey.stringKey("event.type"), eventType)
                .startSpan();

        try {
            attributes.forEach((key, value) -> 
                    span.setAttribute(AttributeKey.stringKey("event." + key), value));
            
            span.setStatus(StatusCode.OK);
            log.info("Business event recorded - Type: {}, Attributes: {}", eventType, attributes);
        } finally {
            span.end();
        }
    }

    /**
     * Get or create a timer for a specific provider/model combination
     */
    private Timer getOrCreateTimer(String provider, String model) {
        String key = provider + ":" + model;
        return llmResponseTimers.computeIfAbsent(key, k ->
                Timer.builder("llm.response.time")
                        .description("LLM response time")
                        .tag("provider", provider)
                        .tag("model", model)
                        .register(meterRegistry)
        );
    }

    /**
     * Context holder for LLM monitoring
     */
    public static class LlmMonitoringContext {
        private final Span span;
        private final Timer.Sample sample;
        private final Timer timer;
        private final String provider;
        private final String model;
        private final String intent;

        public LlmMonitoringContext(Span span, Timer.Sample sample, Timer timer, 
                                    String provider, String model, String intent) {
            this.span = span;
            this.sample = sample;
            this.timer = timer;
            this.provider = provider;
            this.model = model;
            this.intent = intent;
        }

        public Span getSpan() {
            return span;
        }
    }
}

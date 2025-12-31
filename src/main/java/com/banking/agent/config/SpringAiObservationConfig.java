package com.banking.agent.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.observation.ChatModelObservationContext;
import org.springframework.ai.chat.observation.ChatModelObservationConvention;
import org.springframework.ai.chat.observation.DefaultChatModelObservationConvention;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationRegistry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import jakarta.annotation.PostConstruct;

/**
 * Configuration for Spring AI native observations.
 * 
 * This creates an ObservationHandler that bridges Spring AI's Micrometer-based
 * observations to OpenTelemetry spans with Gen AI semantic conventions.
 * 
 * This works alongside TracedChatService to provide:
 * - TracedChatService: Custom spans with full prompt/completion capture
 * - Spring AI Observations: Native Spring AI metrics and additional context (token usage)
 */
@Configuration
public class SpringAiObservationConfig {

    private static final Logger log = LoggerFactory.getLogger(SpringAiObservationConfig.class);

    private final OpenTelemetry openTelemetry;
    private final ObservationRegistry observationRegistry;

    public SpringAiObservationConfig(OpenTelemetry openTelemetry, ObservationRegistry observationRegistry) {
        this.openTelemetry = openTelemetry;
        this.observationRegistry = observationRegistry;
    }

    /**
     * Provides the default Spring AI observation convention.
     */
    @Bean
    public ChatModelObservationConvention chatModelObservationConvention() {
        return new DefaultChatModelObservationConvention();
    }

    /**
     * Register the Spring AI to OpenTelemetry bridge after all beans are initialized.
     * Using @PostConstruct avoids circular dependency issues with ObservationRegistry.
     */
    @PostConstruct
    public void registerSpringAiObservationHandler() {
        log.info("Registering Spring AI to OpenTelemetry observation bridge");
        
        ObservationHandler<ChatModelObservationContext> handler = 
                new SpringAiOtelObservationHandler(openTelemetry);
        observationRegistry.observationConfig().observationHandler(handler);
        
        log.info("Spring AI observation handler registered successfully");
    }

    /**
     * ObservationHandler implementation that converts Spring AI chat observations
     * to OpenTelemetry spans with Gen AI semantic conventions.
     */
    private static class SpringAiOtelObservationHandler implements ObservationHandler<ChatModelObservationContext> {

        private static final Logger log = LoggerFactory.getLogger(SpringAiOtelObservationHandler.class);
        private final Tracer tracer;
        
        // Thread-local storage for span and scope management
        private static final ThreadLocal<Span> currentSpan = new ThreadLocal<>();
        private static final ThreadLocal<Scope> currentScope = new ThreadLocal<>();

        public SpringAiOtelObservationHandler(OpenTelemetry openTelemetry) {
            this.tracer = openTelemetry.getTracer("spring-ai-observations", "1.0.0");
        }

        @Override
        public boolean supportsContext(Observation.Context context) {
            return context instanceof ChatModelObservationContext;
        }

        @Override
        public void onStart(ChatModelObservationContext context) {
            String modelName = context.getRequest() != null 
                    && context.getRequest().getOptions() != null
                    ? context.getRequest().getOptions().getModel() 
                    : "unknown";
            
            Span span = tracer.spanBuilder("spring.ai.chat." + modelName)
                    .setSpanKind(SpanKind.CLIENT)
                    .setAttribute("gen_ai.operation.name", "chat")
                    .setAttribute("gen_ai.request.model", modelName)
                    .startSpan();
            
            currentSpan.set(span);
            currentScope.set(span.makeCurrent());
            
            log.debug("Started Spring AI observation span for model: {}", modelName);
        }

        @Override
        public void onStop(ChatModelObservationContext context) {
            Span span = currentSpan.get();
            if (span != null) {
                try {
                    // Add response attributes
                    if (context.getResponse() != null && context.getResponse().getMetadata() != null) {
                        var metadata = context.getResponse().getMetadata();
                        var usage = metadata.getUsage();
                        
                        if (usage != null) {
                            span.setAttribute(AttributeKey.longKey("gen_ai.usage.input_tokens"), 
                                    usage.getPromptTokens());
                            span.setAttribute(AttributeKey.longKey("gen_ai.usage.output_tokens"), 
                                    usage.getCompletionTokens());
                            span.setAttribute(AttributeKey.longKey("gen_ai.usage.total_tokens"), 
                                    usage.getTotalTokens());
                        }
                        
                        // Add model info from response if available
                        if (metadata.getModel() != null) {
                            span.setAttribute("gen_ai.response.model", metadata.getModel());
                        }
                    }
                    
                    log.debug("Completed Spring AI observation span with usage metrics");
                } finally {
                    Scope scope = currentScope.get();
                    if (scope != null) {
                        scope.close();
                        currentScope.remove();
                    }
                    span.end();
                    currentSpan.remove();
                }
            }
        }

        @Override
        public void onError(ChatModelObservationContext context) {
            Span span = currentSpan.get();
            if (span != null && context.getError() != null) {
                span.recordException(context.getError());
                span.setAttribute("error", true);
                span.setAttribute("error.type", context.getError().getClass().getName());
                log.debug("Recorded error in Spring AI observation span: {}", 
                        context.getError().getMessage());
            }
        }
    }
}

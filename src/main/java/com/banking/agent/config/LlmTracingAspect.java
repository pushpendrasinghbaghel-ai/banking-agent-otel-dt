package com.banking.agent.config;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import lombok.extern.slf4j.Slf4j;

/**
 * AOP-based banking tracing aspect.
 * Automatically instruments business logic spans without modifying code.
 * 
 * LLM prompt/completion tracing is handled by TracedChatService.
 * This aspect adds banking-specific context to business spans.
 */
@Aspect
@Component
@Slf4j
public class LlmTracingAspect {

    private final Tracer tracer;
    
    @Value("${spring.ai.ollama.chat.options.model:llama3.2}")
    private String ollamaModel;

    // Gen AI Semantic Convention attribute keys
    private static final AttributeKey<String> GEN_AI_SYSTEM = AttributeKey.stringKey("gen_ai.system");
    private static final AttributeKey<String> GEN_AI_REQUEST_MODEL = AttributeKey.stringKey("gen_ai.request.model");
    private static final AttributeKey<String> GEN_AI_OPERATION_NAME = AttributeKey.stringKey("gen_ai.operation.name");

    public LlmTracingAspect(Tracer tracer) {
        this.tracer = tracer;
        log.info("Banking Tracing Aspect initialized - automatic span instrumentation enabled");
    }

    /**
     * Intercept BankingAgent.processRequest to add banking context to spans.
     */
    @Around("execution(* com.banking.agent.agent.BankingAgent.processRequest(..))")
    public Object traceBankingRequest(ProceedingJoinPoint pjp) throws Throwable {
        Object[] args = pjp.getArgs();
        
        String accountNumber = null;
        String userQuery = null;
        
        if (args.length > 0 && args[0] != null) {
            // Extract from BankingRequest
            try {
                var request = args[0];
                var getAccountNumber = request.getClass().getMethod("getAccountNumber");
                Object accNum = getAccountNumber.invoke(request);
                accountNumber = accNum != null ? accNum.toString() : null;
                
                var getQuery = request.getClass().getMethod("getQuery");
                Object q = getQuery.invoke(request);
                userQuery = q != null ? q.toString() : null;
            } catch (Exception e) {
                log.debug("Could not extract request details", e);
            }
        }
        
        Span span = tracer.spanBuilder("banking.process_request")
                .setAttribute("banking.provider", "ollama")
                .setAttribute("banking.model", ollamaModel)
                .setAttribute("banking.account.number", accountNumber != null ? accountNumber : "none")
                .startSpan();
        
        if (userQuery != null) {
            String truncatedQuery = userQuery.length() > 200 ? userQuery.substring(0, 200) + "..." : userQuery;
            span.setAttribute("banking.user.query", truncatedQuery);
        }
        
        try {
            Object result = pjp.proceed();
            
            // Extract response status
            if (result != null) {
                try {
                    var getStatus = result.getClass().getMethod("getStatus");
                    Object status = getStatus.invoke(result);
                    span.setAttribute("banking.response.status", status != null ? status.toString() : "UNKNOWN");
                } catch (Exception e) {
                    log.debug("Could not extract response status", e);
                }
            }
            
            span.setStatus(StatusCode.OK);
            return result;
            
        } catch (Exception e) {
            span.recordException(e);
            span.setStatus(StatusCode.ERROR, e.getMessage());
            throw e;
            
        } finally {
            span.end();
        }
    }

    /**
     * Intercept determineIntent to capture intent classification.
     */
    @Around("execution(* com.banking.agent.agent.BankingAgent.determineIntent(..))")
    public Object traceIntentClassification(ProceedingJoinPoint pjp) throws Throwable {
        Span span = tracer.spanBuilder("banking.determine_intent")
                .setAttribute(GEN_AI_SYSTEM, "ollama")
                .setAttribute(GEN_AI_REQUEST_MODEL, ollamaModel)
                .setAttribute(GEN_AI_OPERATION_NAME, "classify_intent")
                .startSpan();
        
        try {
            Object result = pjp.proceed();
            
            if (result instanceof String intent) {
                span.setAttribute("banking.intent", intent);
            }
            
            span.setStatus(StatusCode.OK);
            return result;
            
        } catch (Exception e) {
            span.recordException(e);
            span.setStatus(StatusCode.ERROR, e.getMessage());
            throw e;
            
        } finally {
            span.end();
        }
    }
}

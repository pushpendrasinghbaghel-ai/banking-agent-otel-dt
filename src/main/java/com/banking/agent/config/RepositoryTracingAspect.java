package com.banking.agent.config;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Optional;

/**
 * Custom AOP aspect to trace repository methods.
 * 
 * This provides similar visibility to the OTel Java Agent's automatic
 * repository instrumentation, but with full control and lower overhead.
 * 
 * Captures:
 * - Repository method name and class
 * - Execution duration
 * - Parameter values (for key lookups)
 * - Error details on failure
 */
@Aspect
@Component
public class RepositoryTracingAspect {

    private static final Logger log = LoggerFactory.getLogger(RepositoryTracingAspect.class);
    
    private final Tracer tracer;

    public RepositoryTracingAspect(Tracer tracer) {
        this.tracer = tracer;
        log.info("RepositoryTracingAspect initialized - repository methods will be traced");
    }

    /**
     * Trace all repository method executions.
     * Matches any method in classes under com.banking.agent.repository package.
     */
    @Around("execution(* com.banking.agent.repository.*.*(..))")
    public Object traceRepositoryMethod(ProceedingJoinPoint pjp) throws Throwable {
        MethodSignature signature = (MethodSignature) pjp.getSignature();
        String className = signature.getDeclaringType().getSimpleName();
        String methodName = signature.getName();
        String spanName = className + "." + methodName;

        Span span = tracer.spanBuilder(spanName)
                .setSpanKind(SpanKind.CLIENT)
                .setAttribute("db.system", "h2")
                .setAttribute("db.operation", methodName)
                .setAttribute("repository.class", className)
                .setAttribute("repository.method", methodName)
                .startSpan();

        try (Scope scope = span.makeCurrent()) {
            // Add parameter info for key lookup methods
            addParameterAttributes(span, signature, pjp.getArgs());
            
            Object result = pjp.proceed();
            
            // Add result info
            if (result != null) {
                if (result instanceof Collection) {
                    span.setAttribute("db.result.count", ((Collection<?>) result).size());
                } else if (result instanceof Optional) {
                    span.setAttribute("db.result.present", ((Optional<?>) result).isPresent());
                }
            }
            
            span.setStatus(StatusCode.OK);
            return result;
            
        } catch (Throwable t) {
            span.setStatus(StatusCode.ERROR, t.getMessage());
            span.recordException(t);
            throw t;
        } finally {
            span.end();
        }
    }

    /**
     * Add meaningful parameter attributes based on method name patterns.
     */
    private void addParameterAttributes(Span span, MethodSignature signature, Object[] args) {
        if (args == null || args.length == 0) {
            return;
        }

        String methodName = signature.getName();

        // Common Spring Data repository patterns
        if (methodName.startsWith("findBy") || methodName.startsWith("getBy")) {
            // For findByXxx methods, the first param is usually the lookup key
            if (args.length > 0 && args[0] != null) {
                String keyName = extractKeyName(methodName);
                span.setAttribute("db.query.key", keyName);
                span.setAttribute("db.query.value", String.valueOf(args[0]));
            }
        } else if (methodName.equals("save") || methodName.equals("saveAll")) {
            span.setAttribute("db.operation.type", "INSERT_OR_UPDATE");
        } else if (methodName.equals("delete") || methodName.equals("deleteById")) {
            span.setAttribute("db.operation.type", "DELETE");
        } else if (methodName.equals("findAll")) {
            span.setAttribute("db.operation.type", "SELECT_ALL");
        } else if (methodName.equals("count")) {
            span.setAttribute("db.operation.type", "COUNT");
        }
    }

    /**
     * Extract the key name from Spring Data method naming convention.
     * e.g., "findByAccountNumber" -> "accountNumber"
     */
    private String extractKeyName(String methodName) {
        String prefix = methodName.startsWith("findBy") ? "findBy" : "getBy";
        String remainder = methodName.substring(prefix.length());
        if (remainder.isEmpty()) {
            return "id";
        }
        // Convert first char to lowercase
        return Character.toLowerCase(remainder.charAt(0)) + remainder.substring(1);
    }
}

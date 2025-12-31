package com.banking.agent.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.exporter.otlp.http.logs.OtlpHttpLogRecordExporter;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import io.opentelemetry.sdk.logs.export.SimpleLogRecordProcessor;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;

/**
 * Configuration for OpenTelemetry SDK to export traces, logs, and metrics to Dynatrace.
 * 
 * Metrics are handled by Micrometer's OtlpMeterRegistry (configured in application.yml).
 * This class configures the OpenTelemetry SDK for traces and logs export.
 */
@Configuration
public class DynatraceMonitoringConfig {

    private static final Logger log = LoggerFactory.getLogger(DynatraceMonitoringConfig.class);

    @Value("${otel.exporter.otlp.endpoint:https://localhost/api/v2/otlp}")
    private String otlpEndpoint;

    @Value("${otel.exporter.otlp.authorization:}")
    private String authorizationHeader;

    @Value("${spring.application.name:banking-agent}")
    private String serviceName;

    @Value("${dynatrace.environment:dev}")
    private String environment;

    @Bean
    public OpenTelemetry openTelemetry() {
        // Use System.out to ensure these messages appear regardless of logger state
        System.out.println("=== Initializing OpenTelemetry SDK for Dynatrace ===");
        System.out.println("OTLP Endpoint: " + otlpEndpoint);
        System.out.println("Authorization configured: " + !authorizationHeader.isEmpty());

        // Build resource with service information
        Resource resource = Resource.getDefault()
                .merge(Resource.create(Attributes.of(
                        AttributeKey.stringKey("service.name"), serviceName,
                        AttributeKey.stringKey("service.version"), "0.0.1-SNAPSHOT",
                        AttributeKey.stringKey("deployment.environment"), environment
                )));

        String logsEndpoint = otlpEndpoint + "/v1/logs";
        String tracesEndpoint = otlpEndpoint + "/v1/traces";
        
        System.out.println("Logs endpoint: " + logsEndpoint);
        System.out.println("Traces endpoint: " + tracesEndpoint);

        // Configure OTLP Log Exporter
        OtlpHttpLogRecordExporter logExporter = OtlpHttpLogRecordExporter.builder()
                .setEndpoint(logsEndpoint)
                .addHeader("Authorization", authorizationHeader)
                .build();

        // Configure Logger Provider with SimpleLogRecordProcessor for immediate export
        // For production, switch to BatchLogRecordProcessor
        SdkLoggerProvider loggerProvider = SdkLoggerProvider.builder()
                .setResource(resource)
                .addLogRecordProcessor(SimpleLogRecordProcessor.create(logExporter))
                .build();

        // Configure OTLP Trace Exporter
        OtlpHttpSpanExporter spanExporter = OtlpHttpSpanExporter.builder()
                .setEndpoint(tracesEndpoint)
                .addHeader("Authorization", authorizationHeader)
                .build();

        // Configure Tracer Provider with batch processor
        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                .setResource(resource)
                .addSpanProcessor(BatchSpanProcessor.builder(spanExporter).build())
                .build();

        // Build and register the OpenTelemetry SDK globally
        OpenTelemetrySdk openTelemetry = OpenTelemetrySdk.builder()
                .setLoggerProvider(loggerProvider)
                .setTracerProvider(tracerProvider)
                .buildAndRegisterGlobal();

        // CRITICAL: Install the OpenTelemetry instance on the Logback appender
        // This connects the already-initialized Logback appender to our SDK
        OpenTelemetryAppender.install(openTelemetry);

        System.out.println("=== OpenTelemetry SDK initialized and installed on Logback ===");
        
        // Now these log messages should be captured and exported
        log.info("OpenTelemetry SDK initialized successfully - this log should be exported to Dynatrace");
        log.info("Logs will be exported to: {}", logsEndpoint);
        log.info("Traces will be exported to: {}", tracesEndpoint);

        return openTelemetry;
    }
}

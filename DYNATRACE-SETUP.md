# Dynatrace Monitoring Setup Guide

This Banking AI Agent is fully instrumented with Dynatrace for comprehensive observability covering:
1. **Distributed Tracing** - End-to-end request flow visibility via OpenTelemetry
2. **Metrics Export** - JVM, HTTP, and custom metrics via Micrometer OTLP
3. **Log Export** - Application logs with trace correlation via OpenTelemetry Logback Appender
4. **LLM Performance** - LLM response times, token usage, provider comparison
5. **User Satisfaction** - Customer satisfaction scores and feedback

---

## Spring Boot 3.5.x Configuration Summary

This section provides the complete configuration needed to export **traces, logs, and metrics** to Dynatrace from Spring Boot 3.5.x applications.

### Architecture Overview

```
┌─────────────────────────────────────────────────────────────────┐
│                    Spring Boot 3.5.x Application                │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌─────────────┐    ┌─────────────┐    ┌─────────────────────┐ │
│  │   Metrics   │    │   Traces    │    │       Logs          │ │
│  │             │    │             │    │                     │ │
│  │ Micrometer  │    │ OpenTelemetry│   │ Logback + OTel     │ │
│  │ OTLP        │    │ SDK         │    │ Appender           │ │
│  │ Registry    │    │             │    │                     │ │
│  └──────┬──────┘    └──────┬──────┘    └──────────┬──────────┘ │
│         │                  │                      │             │
└─────────┼──────────────────┼──────────────────────┼─────────────┘
          │                  │                      │
          ▼                  ▼                      ▼
    /v1/metrics        /v1/traces              /v1/logs
          │                  │                      │
          └──────────────────┼──────────────────────┘
                             │
                             ▼
              ┌──────────────────────────────┐
              │     Dynatrace OTLP API       │
              │  /api/v2/otlp/v1/{signal}    │
              └──────────────────────────────┘
```

### Required Dependencies (pom.xml)

```xml
<!-- Spring Boot Actuator (for metrics endpoint) -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>

<!-- Micrometer Tracing Bridge for OpenTelemetry -->
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-tracing-bridge-otel</artifactId>
</dependency>

<!-- Micrometer OTLP Registry for Metrics Export -->
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-otlp</artifactId>
</dependency>

<!-- OpenTelemetry OTLP Exporter (for traces and logs) -->
<dependency>
    <groupId>io.opentelemetry</groupId>
    <artifactId>opentelemetry-exporter-otlp</artifactId>
</dependency>

<!-- OpenTelemetry Logback Appender (for log export) -->
<dependency>
    <groupId>io.opentelemetry.instrumentation</groupId>
    <artifactId>opentelemetry-logback-appender-1.0</artifactId>
    <version>2.13.0-alpha</version>
</dependency>

<!-- OpenTelemetry SDK Autoconfigure -->
<dependency>
    <groupId>io.opentelemetry</groupId>
    <artifactId>opentelemetry-sdk-extension-autoconfigure</artifactId>
</dependency>
<dependency>
    <groupId>io.opentelemetry</groupId>
    <artifactId>opentelemetry-sdk-extension-autoconfigure-spi</artifactId>
</dependency>
```

### application.yml Configuration

```yaml
spring:
  application:
    name: banking-agent

# Dynatrace Configuration
dynatrace:
  environment: dev

# Management & Actuator Configuration
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus,env,beans
  endpoint:
    health:
      show-details: always
  
  # Metrics Configuration
  metrics:
    distribution:
      percentiles-histogram:
        '[http.server.requests]': true
    tags:
      application: ${spring.application.name}
      environment: ${dynatrace.environment}
      '[service.name]': ${spring.application.name}
  
  # Tracing Configuration (Spring Boot 3.x)
  tracing:
    sampling:
      probability: 1.0
    enabled: true
  
  # OTLP Metrics Export (Spring Boot 3.x format)
  otlp:
    metrics:
      export:
        enabled: true
        url: https://{your-environment-id}.live.dynatrace.com/api/v2/otlp/v1/metrics
        step: 10s
        headers:
          Authorization: "Api-Token {your-api-token}"

# OpenTelemetry SDK Configuration (for traces and logs)
otel:
  exporter:
    otlp:
      endpoint: https://{your-environment-id}.live.dynatrace.com/api/v2/otlp
      authorization: "Api-Token {your-api-token}"
```

### Java Configuration Class (DynatraceMonitoringConfig.java)

```java
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
        // Build resource with service information
        Resource resource = Resource.getDefault()
                .merge(Resource.create(Attributes.of(
                        AttributeKey.stringKey("service.name"), serviceName,
                        AttributeKey.stringKey("service.version"), "0.0.1-SNAPSHOT",
                        AttributeKey.stringKey("deployment.environment"), environment
                )));

        String logsEndpoint = otlpEndpoint + "/v1/logs";
        String tracesEndpoint = otlpEndpoint + "/v1/traces";

        // Configure OTLP Log Exporter
        OtlpHttpLogRecordExporter logExporter = OtlpHttpLogRecordExporter.builder()
                .setEndpoint(logsEndpoint)
                .addHeader("Authorization", authorizationHeader)
                .build();

        // Configure Logger Provider with SimpleLogRecordProcessor for immediate export
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
        OpenTelemetryAppender.install(openTelemetry);

        log.info("OpenTelemetry SDK initialized successfully");
        log.info("Logs will be exported to: {}", logsEndpoint);
        log.info("Traces will be exported to: {}", tracesEndpoint);

        return openTelemetry;
    }
}
```

### Logback Configuration (logback-spring.xml)

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    <include resource="org/springframework/boot/logging/logback/defaults.xml"/>
    <include resource="org/springframework/boot/logging/logback/console-appender.xml"/>

    <!-- OpenTelemetry Appender for Log Export -->
    <appender name="OTEL" class="io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender">
        <captureExperimentalAttributes>true</captureExperimentalAttributes>
        <captureCodeAttributes>true</captureCodeAttributes>
        <captureMarkerAttribute>true</captureMarkerAttribute>
        <captureKeyValuePairAttributes>true</captureKeyValuePairAttributes>
        <captureLoggerContext>true</captureLoggerContext>
        <captureMdcAttributes>*</captureMdcAttributes>
    </appender>

    <!-- Root logger with both console and OTEL appenders -->
    <root level="INFO">
        <appender-ref ref="CONSOLE"/>
        <appender-ref ref="OTEL"/>
    </root>

    <!-- Application-specific loggers -->
    <logger name="com.banking.agent" level="DEBUG"/>
</configuration>
```

### API Token Requirements

Your Dynatrace API token must have the following scopes:
- `metrics.ingest` - For metrics export
- `traces.ingest` - For traces export  
- `logs.ingest` - For logs export

### Key Configuration Points

| Signal | Component | Configuration Property |
|--------|-----------|----------------------|
| Metrics | Micrometer OTLP Registry | `management.otlp.metrics.export.*` |
| Traces | OpenTelemetry SDK | `otel.exporter.otlp.endpoint` + Java Config |
| Logs | OpenTelemetry Logback Appender | `logback-spring.xml` + Java Config |

### Important Notes

1. **Metrics** use Spring Boot's built-in `management.otlp.metrics.export` configuration
2. **Traces and Logs** require programmatic SDK configuration via `DynatraceMonitoringConfig.java`
3. **OpenTelemetryAppender.install()** must be called to connect Logback to the SDK
4. **SimpleLogRecordProcessor** is used for immediate log export (use BatchLogRecordProcessor for production)

---

## Enhanced with OpenLLMetry

This application now includes **OpenLLMetry** - industry-standard LLM observability using OpenTelemetry Semantic Conventions for Generative AI. This provides:

✨ **Standardized LLM Metrics:**
- Automatic token counting (prompt, completion, total)
- Cost estimation per LLM call
- Prompt/response length tracking
- Model and temperature tracking

✨ **Semantic Conventions:**
- `gen_ai.system` - Provider (openai, google, ollama)
- `gen_ai.request.model` - Model name
- `gen_ai.usage.input_tokens` - Prompt tokens
- `gen_ai.usage.output_tokens` - Completion tokens
- `gen_ai.response.finish_reasons` - Completion status
- `llm.cost.usd` - Estimated cost per call

✨ **Banking-Specific Attributes:**
- `banking.intent` - Customer intent classification
- `banking.account` - Account number (when applicable)

## Prerequisites

1. **Dynatrace Environment**
   - Dynatrace SaaS or Managed environment
   - API Token with these permissions:
     - `metrics.ingest` - For metrics export
     - `traces.ingest` - For traces export
     - `logs.ingest` - For logs export

2. **Java 21+** and **Maven 3.8+**

3. **Spring Boot 3.5.x** with the dependencies listed above

## Quick Start

### Step 1: Configure Your Dynatrace Endpoint

Edit `src/main/resources/application.yml`:

```yaml
# OTLP Metrics Export
management:
  otlp:
    metrics:
      export:
        enabled: true
        url: https://{your-environment-id}.live.dynatrace.com/api/v2/otlp/v1/metrics
        step: 10s
        headers:
          Authorization: "Api-Token {your-api-token}"

# OpenTelemetry SDK Configuration (for traces and logs)
otel:
  exporter:
    otlp:
      endpoint: https://{your-environment-id}.live.dynatrace.com/api/v2/otlp
      authorization: "Api-Token {your-api-token}"
```

### Step 2: Build and Run

```bash
mvn clean package -DskipTests
java -jar target/banking-agent-0.0.1-SNAPSHOT.jar
```

### Step 3: Verify in Dynatrace

1. **Logs**: Go to **Logs & Events > Logs**, filter by `service.name: "banking-agent"`
2. **Traces**: Go to **Distributed Traces**, search for your service
3. **Metrics**: Go to **Metrics**, search for `http.server.requests` or custom metrics

## Viewing Data in Dynatrace

### Query Logs with DQL

```dql
fetch logs, from:now()-1h
| filter service.name == "banking-agent"
| sort timestamp desc
| limit 100
```

### Query Traces with DQL

```dql
fetch spans, from:now()-1h
| filter service.name == "banking-agent"
| summarize count(), by:{span.name}
```

### Verify All Signals

```dql
// Check logs are being received
fetch logs, from:now()-15m | filter service.name == "banking-agent" | summarize count()

// Check traces are being received  
fetch spans, from:now()-15m | filter service.name == "banking-agent" | summarize count()

// Check metrics are being received
fetch dt.metrics.list | filter contains(metric.key, "http")
```

---

## Monitoring Capabilities

### 1. Distributed Tracing

**What's Tracked:**
- Complete request flow from API to LLM and back
- Service-to-service communication
- Database queries
- LLM provider calls
- Response times at each step

**Custom Spans Created:**
- `banking.process_request` - Overall request processing
- `gen_ai.chat.completions` - LLM API calls (OpenLLMetry standard)
- `llm.request` - Individual LLM API calls (legacy)
- `llm.correctness_evaluation` - Correctness assessments
- `banking.business_event` - Business events

**OpenLLMetry Attributes on Spans:**
- `gen_ai.system` - Provider (openai/google/ollama)
- `gen_ai.request.model` - Model name
- `gen_ai.request.temperature` - Temperature setting
- `gen_ai.usage.input_tokens` - Prompt tokens
- `gen_ai.usage.output_tokens` - Completion tokens
- `llm.cost.usd` - Estimated API cost
- `llm.prompt.length` - Character count
- `llm.response.length` - Character count
- `llm.latency.ms` - Response time
- `banking.intent` - Customer intent
- `banking.account` - Account number

**View in Dynatrace:**
- Navigate to: **Services > banking-agent > View PurePaths**
- Filter by: `banking.process_request` span

### 2. Service Performance

**Metrics Tracked:**
- HTTP request rates and response times
- JVM memory and CPU usage
- Thread pool utilization
- Error rates and exceptions
- OpenLLMetry Span Attributes (queryable via DQL):**
- `gen_ai.usage.input_tokens` - Prompt tokens per request
- `gen_ai.usage.output_tokens` - Completion tokens per request
- `llm.total.tokens` - Total tokens per request
- `llm.cost.usd` - Estimated cost per request
- `llm.latency.ms` - Response latency

**Database query performance

**View in Dynatrace:**
- Navigate to: **Services > banking-agent**
- Check: Response time, Failure rate, Throughput

### 3. LLM Performance

**Custom Metrics:**
- `llm.requests.total` - Total LLM requests by provider
- `llm.response.time` - LLM response time histogram (by provider/model)
- `llm.tokens.total` - Token consumption tracking
- `llm.errors.total` - LLM errors by provider

**Custom Attributes on Spans:**
- `llm.provider` - Provider name (ollama, openai, gemini)
- `llm.model` - Model name
- `llm.intent` - Classification intent
- `llm.tokens` - Tokens used
- `llm.response_time_ms` - Response time
- `llm.success` - Success/failure indicator

**View in Dynatrace:**
1. Navigate to: **Metrics**
2. Search for: `llm.response.time`
3. Split by: `provider` dimension
4. Create custom chart to compare providers

**Create Dashboard:**
```
1. Go to Dashboards > Create Dashboard
2. Add tiles:
   - LLM Response Time (llm.response.time) - Line chart, split by provider
   - LLM Request Rate (llm.requests.total) - Bar chart, split by provider
   - Token Usage (llm.tokens.total) - Area chart
   - Error Rate (llm.errors.total) - Single value
```

### 4. LLM Response Correctness

**How It Works:**
- Users submit feedback via API endpoints
- Feedback is converted to correctness scores (0.0 - 1.0)
- Tracked per provider and intent type

**Metrics:**
- `llm.correctness.total` - Total correctness evaluations
- `llm.correctness.score` - Current correctness score (gauge)

**Feedback Endpoints:**

```bash
# Submit detailed satisfaction feedback
curl -X POST http://localhost:8080/api/feedback/satisfaction \
  -H "Content-Type: application/json" \
  -d '{
    "sessionId": "sess-123",
    "accountNumber": "ACC001",
    "llmProvider": "ollama",
    "intent": "CHECK_BALANCE",
    "satisfactionScore": 4.5,
    "feedback": "Very helpful response",
    "wasHelpful": true,
    "wasAccurate": true
  }'

# Quick thumbs up/down feedback
curl -X POST "http://localhost:8080/api/feedback/quick-feedback?sessionId=sess-123&llmProvider=ollama&intent=CHECK_BALANCE&helpful=true"

# Report an issue
curl -X POST "http://localhost:8080/api/feedback/report-issue?sessionId=sess-123&llmProvider=ollama&intent=CHECK_BALANCE&issueType=inaccurate&description=Wrong balance shown"
```

**View in Dynatrace:**
1. Navigate to: **Metrics**
2. Search for: `llm.correctness.score`
3. Split by: `provider` and `intent` dimensions
4. Track trends over time

### 5. User Satisfaction

**Metrics:**
- `user.satisfaction.submissions` - Total feedback submissions
- `user.satisfaction.score` - Satisfaction scores (1-5 scale)

**Business Events:**
- `user_satisfaction_submitted` - Full satisfaction feedback
- `quick_feedback_submitted` - Quick helpful/not helpful
- `issue_reported` - User-reported issues

**View in Dynatrace:**
1. Navigate to: **Business Events**
2. Filter by: `banking_request_processed`, `user_satisfaction_submitted`
3. Analyze patterns and correlations

## Dynatrace Dashboards

### Create LLM Performance Dashboard

1. Go to **Dashboards > Create Dashboard**
2. Name it "Banking AI Agent - LLM Performance"
3. Add these tiles:

**Tile 1: LLM Response Time by Provider**
- Metric: `llm.response.time`
- Visualization: Line chart
- Split by: `provider`
- Aggregation: Average

**Tile 2: LLM Request Volume**
- Metric: `llm.requests.total`
- Visualization: Bar chart
- Split by: `provider`
- Aggregation: Count

**Tile 3: Token Consumption**
- Metric: `llm.tokens.total`
- Visualization: Area chart
- Aggregation: Sum

**Tile 4: Error Rate**
- Metric: `llm.errors.total`
- Visualization: Single value
- Aggregation: Count

**Tile 5: Correctness Score**
- Metric: `llm.correctness.score`
- Visualization: Line chart
- Split by: `provider`
- Aggregation: Average

**Tile 6: User Satisfaction**
- Metric: `user.satisfaction.score`
- Visualization: Line chart
- Split by: `provider`
- Aggregation: Average

### Create Alerting Rules

1. Go to **Settings > Anomaly Detection > Custom Events for Alerting**

**Alert 1: High LLM Error Rate**
```
Metric: llm.errors.total
Condition: Rate increase > 5 errors/min
Severity: Error
```

**Alert 2: Slow LLM Response**
```
Metric: llm.response.time
Condition: p95 > 5000ms
Severity: Warning
```

**Alert 3: Low Correctness Score**
```
Metric: llm.correctness.score
Condition: Average < 0.6 over 15 minutes
Severity: Warning
```

**Alert 4: Low User Satisfaction**
```
Metric: user.satisfaction.score
Condition: Average < 3.0 over 30 minutes
Severity: Warning
```

## Using the Dynatrace MCP Server (Optional)

If you have access to the Dynatrace MCP server, you can query monitoring data using DQL:

### Query LLM Performance
```typescript
// Get average token usage by provider (OpenLLMetry data)
await executeDQL(`
  fetch spans
  | filter span.name == "gen_ai.chat.completions"
  | summarize 
      avg_input_tokens = avg(gen_ai.usage.input_tokens),
      avg_output_tokens = avg(gen_ai.usage.output_tokens),
      total_cost = sum(llm.cost.usd),
      by: {gen_ai.system, gen_ai.request.model}
  | sort total_cost desc
`)

// Get LLM response times
await executeDQL(`
  fetch spans
  | filter span.name == "gen_ai.chat.completions"
  | summarize 
      avg_latency = avg(llm.latency.ms),
      p95_latency = percentile(llm.latency.ms, 95),
      by: {gen_ai.system}
`)

// Cost analysis by intent
await executeDQL(`
  fetch spans
  | filter span.name == "gen_ai.chat.completions"
  | summarize 
      total_cost = sum(llm.cost.usd),
      request_count = count(),
      avg_cost = avg(llm.cost.usd),
      by: {banking.intent, gen_ai.system}
  | sort total_cost desc
`)
```

### Query User Satisfaction
```typescript
// Get user satisfaction trends
await executeDQL(`
  fetch metrics
  | filter metricId == "user.satisfaction.score"
  | summarize avg(value), p95(value), by:{provider}
  | sort avg(value) desc
`)

// Correlate satisfaction with LLM performance
await executeDQL(`
  fetch spans
  | filter span.name == "gen_ai.chat.completions"
  | lookup [
      fetch metrics
      | filter metricId == "user.satisfaction.score"
    ], sourceField: gen_ai.system, lookupField: provider
  | summarize 
      avg_latency = avg(llm.latency.ms),
      avg_cost = avg(llm.cost.usd),
      avg_satisfaction = avg(value),
      by: {gen_ai.system}
`)
```

### Find Expensive or Slow Requests
```typescript
// Find most expensive LLM calls
await executeDQL(`
  fetch spans
  | filter span.name == "gen_ai.chat.completions"
  | filter llm.cost.usd > 0.01
  | summarize 
      cost = max(llm.cost.usd),
      tokens = max(gen_ai.usage.input_tokens + gen_ai.usage.output_tokens),
      by: {banking.intent, gen_ai.system}
  | sort cost desc
  | limit 20
`)

// Find slow LLM requests
await executeDQL(`
  fetch spans
  | filter span.name == "gen_ai.chat.completions"
  | filter llm.latency.ms > 5000
  | summarize count(), by:{gen_ai.system, gen_ai.request.model, banking.intent}
`)

// Token usage patterns
await executeDQL(`
  fetch spans
  | filter span.name == "gen_ai.chat.completions"
  | summarize 
      total_input = sum(gen_ai.usage.input_tokens),
      total_output = sum(gen_ai.usage.output_tokens),
      avg_prompt_length = avg(llm.prompt.length),
      avg_response_length = avg(llm.response.length),
      by: {banking.intent}
  | sort total_input desc
`)
```

## Verification

1. **Start the application:**
   ```powershell
   mvn spring-boot:run
   ```

2. **Generate some traffic:**
   ```powershell
   # Windows PowerShell
   .\test-api.ps1
   
   # Or use curl
   curl -X POST http://localhost:8080/api/agent/chat?llmProvider=ollama `
     -H "Content-Type: application/json" `
     -d '{"query":"What is my balance?","accountNumber":"ACC001"}'
   ```

3. **Submit feedback:**
   ```powershell
   curl -X POST "http://localhost:8080/api/feedback/quick-feedback?sessionId=test-1&llmProvider=ollama&intent=CHECK_BALANCE&helpful=true"
   ```

4. **Check Dynatrace:**
   - Wait 1-2 minutes for data to appear
   - Navigate to: **Services > banking-agent**
   - Check: **Distributed Traces**, **Metrics**, **Business Events**

## Troubleshooting

### Data Not Appearing in Dynatrace

1. **Check OneAgent Status:**
   - Windows: Services > Dynatrace OneAgent
   - Linux: `systemctl status dynatrace-oneagent`

2. **Check Environment Variables:**
   ```powershell
   echo $env:DYNATRACE_OTLP_ENDPOINT
   echo $env:DYNATRACE_API_TOKEN
   ```

3. **Check Application Logs:**
   ```
   Look for: "OpenTelemetry Tracer configured"
   Look for: "Micrometer metrics configured"
   ```

4. **Verify API Token Permissions:**
   - Go to: Settings > Access Tokens
   - Ensure token has: `metrics.ingest`, `traces.ingest`, `logs.ingest`

### High Cardinality Warning

If you see cardinality warnings for metrics:
1. Reduce the number of dimensions
2. Use dimension filtering in application.yml
3. Consider using business events instead of metrics

## Best Practices

1. **Use OneAgent for Production** - Provides the most comprehensive monitoring
2. **Set Sampling Rates** - Adjust `management.tracing.sampling.probability` for high-traffic environments
3. **Monitor Token Usage** - Track costs via `llm.tokens.total` metric
4. **Create SLOs** - Define Service Level Objectives for LLM response time and correctness
5. **Regular Feedback Collection** - Encourage users to submit satisfaction ratings
6. **Alert on Anomalies** - Set up alerts for unusual patterns in LLM performance

## Additional Resources

- [Dynatrace OpenTelemetry Documentation](https://www.dynatrace.com/support/help/extend-dynatrace/opentelemetry)
- [Dynatrace Java Monitoring](https://www.dynatrace.com/support/help/technology-support/application-software/java)
- [Creating Custom Metrics](https://www.dynatrace.com/support/help/extend-dynatrace/extend-metrics)
- [Business Events](https://www.dynatrace.com/support/help/platform-modules/business-analytics/ba-events-capturing)

## Support

For issues with:
- **Banking AI Agent** - Check application logs
- **Dynatrace Setup** - Contact your Dynatrace administrator
- **OneAgent** - Check Dynatrace support documentation

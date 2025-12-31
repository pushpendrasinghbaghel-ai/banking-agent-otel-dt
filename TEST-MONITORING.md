# Quick Start: Testing Dynatrace Monitoring

This application exports **traces, logs, and metrics** to Dynatrace via OTLP (OpenTelemetry Protocol).

## Configuration Overview

| Signal | Technology | Endpoint |
|--------|------------|----------|
| **Metrics** | Micrometer OTLP Registry | `/api/v2/otlp/v1/metrics` |
| **Traces** | OpenTelemetry SDK | `/api/v2/otlp/v1/traces` |
| **Logs** | OpenTelemetry Logback Appender | `/api/v2/otlp/v1/logs` |

## Test the Monitoring Setup

### 1. Start the Application
```powershell
mvn clean package -DskipTests
java -jar target/banking-agent-0.0.1-SNAPSHOT.jar
```

You should see:
```
=== Initializing OpenTelemetry SDK for Dynatrace ===
OTLP Endpoint: https://xxx.live.dynatrace.com/api/v2/otlp
Authorization configured: true
=== OpenTelemetry SDK initialized and installed on Logback ===
```

### 2. Generate Test Traffic
```powershell
# Check balance
curl -X POST http://localhost:8080/api/agent/chat?llmProvider=ollama `
  -H "Content-Type: application/json" `
  -d '{\"query\":\"What is my balance?\",\"accountNumber\":\"ACC001\"}'

# View transactions
curl -X POST http://localhost:8080/api/agent/chat?llmProvider=ollama `
  -H "Content-Type: application/json" `
  -d '{\"query\":\"Show my recent transactions\",\"accountNumber\":\"ACC001\"}'

# General inquiry
curl -X POST http://localhost:8080/api/agent/chat?llmProvider=ollama `
  -H "Content-Type: application/json" `
  -d '{\"query\":\"What are your banking hours?\"}'
```

### 3. Submit User Feedback
```powershell
# Positive feedback
curl -X POST http://localhost:8080/api/feedback/satisfaction `
  -H "Content-Type: application/json" `
  -d '{\"sessionId\":\"test-1\",\"accountNumber\":\"ACC001\",\"llmProvider\":\"ollama\",\"intent\":\"CHECK_BALANCE\",\"satisfactionScore\":5,\"feedback\":\"Perfect!\",\"wasHelpful\":true,\"wasAccurate\":true}'

# Quick thumbs up
curl -X POST "http://localhost:8080/api/feedback/quick-feedback?sessionId=test-2&llmProvider=ollama&intent=CHECK_BALANCE&helpful=true"

# Negative feedback
curl -X POST http://localhost:8080/api/feedback/satisfaction `
  -H "Content-Type: application/json" `
  -d '{\"sessionId\":\"test-3\",\"accountNumber\":\"ACC001\",\"llmProvider\":\"ollama\",\"intent\":\"VIEW_TRANSACTIONS\",\"satisfactionScore\":2,\"feedback\":\"Response was slow\",\"wasHelpful\":false,\"wasAccurate\":true}'
```

### 4. Check Metrics Endpoint
```powershell
# View all metrics
curl http://localhost:8080/actuator/metrics

# View specific LLM metrics
curl http://localhost:8080/actuator/metrics/llm.requests.total
curl http://localhost:8080/actuator/metrics/llm.response.time
curl http://localhost:8080/actuator/metrics/user.satisfaction.score
```

## Verify Data in Dynatrace

### Query Logs
```dql
fetch logs, from:now()-15m
| filter service.name == "banking-agent"
| sort timestamp desc
| limit 50
```

### Query Traces
```dql
fetch spans, from:now()-15m
| filter service.name == "banking-agent"
| summarize count(), by:{span.name}
```

### Check All Signals
Navigate to:
- **Logs & Events > Logs** → Filter by `service.name: "banking-agent"`
- **Distributed Traces** → Search for `banking-agent`
- **Metrics** → Search for `http.server.requests`

## What You'll See in Dynatrace

### Distributed Traces
- Service: `banking-agent`
- Spans: `banking.process_request`, `llm.request`, `llm.correctness_evaluation`
- Attributes: provider, model, intent, tokens, response_time_ms

### Logs
- All application logs with trace correlation
- Attributes: `trace_id`, `span_id`, `code.filepath`, `code.function`
- Log levels: INFO, DEBUG, WARN, ERROR

### Metrics
- `llm.requests.total` - Split by provider
- `llm.response.time` - Response time histogram
- `llm.tokens.total` - Token consumption
- `llm.correctness.score` - Correctness ratings
- `user.satisfaction.score` - User satisfaction

### Business Events
- `banking_request_processed`
- `user_satisfaction_submitted`
- `quick_feedback_submitted`
- `issue_reported`

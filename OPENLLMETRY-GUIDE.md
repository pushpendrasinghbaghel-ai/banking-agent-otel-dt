# OpenLLMetry Integration Guide

## What is OpenLLMetry?

OpenLLMetry provides **standardized observability for LLM applications** using OpenTelemetry Semantic Conventions for Generative AI. This ensures your LLM monitoring follows industry standards and is compatible with all observability platforms.

> **Note:** This guide focuses on LLM-specific observability. For general traces, logs, and metrics setup, see [DYNATRACE-SETUP.md](DYNATRACE-SETUP.md).

## What You Get with OpenLLMetry

### 1. Standardized Span Attributes

Every LLM call creates a span with these standard attributes:

| Attribute | Description | Example |
|-----------|-------------|---------|
| `gen_ai.system` | LLM provider | `openai`, `google`, `ollama` |
| `gen_ai.request.model` | Model name | `llama3.2`, `gpt-4` |
| `gen_ai.request.temperature` | Temperature setting | `0.7` |
| `gen_ai.usage.input_tokens` | Prompt tokens | `150` |
| `gen_ai.usage.output_tokens` | Completion tokens | `75` |
| `gen_ai.response.finish_reasons` | Why completion stopped | `stop`, `length` |
| `llm.cost.usd` | Estimated cost | `0.0045` |
| `llm.latency.ms` | Response time | `1250` |

### 2. Automatic Token Tracking

- **Prompt tokens** - Accurately counted from input
- **Completion tokens** - Accurately counted from output  
- **Total tokens** - Sum of prompt + completion
- **Cost estimation** - Calculates cost based on provider pricing

### 3. Cost Analysis

Automatic cost calculation based on 2025 pricing:

| Provider | Cost per 1K Prompt | Cost per 1K Completion |
|----------|-------------------|----------------------|
| OpenAI (GPT-4) | $0.03 | $0.06 |
| Gemini Pro | $0.00025 | $0.0005 |
| Ollama (local) | $0 | $0 |

### 4. Banking-Specific Context

Additional attributes for banking use cases:
- `banking.intent` - Customer intent (CHECK_BALANCE, etc.)
- `banking.account` - Account number (when applicable)

## Implementation Details

### Service: OpenLLMetryService

Located at: [src/main/java/com/banking/agent/service/OpenLLMetryService.java](src/main/java/com/banking/agent/service/OpenLLMetryService.java)

**Key Methods:**

```java
// Start tracking an LLM call
LLMSpanContext context = openLLMetryService.startChatCompletion(
    provider,      // "ollama", "openai", "gemini"
    model,         // "llama3.2", "gpt-4"
    operationType  // "classify_intent", "generate_response"
);

// Record the prompt
openLLMetryService.recordPrompt(
    context, 
    prompt,        // The actual prompt text
    0.7,          // temperature
    null          // max tokens (optional)
);

// Record the response
openLLMetryService.recordCompletion(
    context,
    response,      // The LLM's response
    "stop"        // finish reason
);

// Add banking context
openLLMetryService.recordBankingContext(
    context,
    "CHECK_BALANCE",  // intent
    "ACC001"         // account number
);

// Mark as successful
openLLMetryService.recordSuccess(context);

// Or mark as failed
openLLMetryService.recordError(context, exception);
```

## Query Examples in Dynatrace

### Find Most Expensive Operations

```dql
fetch spans
| filter span.name == "gen_ai.chat.completions"
| summarize 
    total_cost = sum(llm.cost.usd),
    avg_cost = avg(llm.cost.usd),
    request_count = count(),
    by: {banking.intent, gen_ai.system}
| sort total_cost desc
```

### Compare Provider Performance

```dql
fetch spans
| filter span.name == "gen_ai.chat.completions"
| summarize 
    avg_latency = avg(llm.latency.ms),
    p95_latency = percentile(llm.latency.ms, 95),
    avg_input_tokens = avg(gen_ai.usage.input_tokens),
    avg_output_tokens = avg(gen_ai.usage.output_tokens),
    by: {gen_ai.system}
```

### Analyze Token Usage by Intent

```dql
fetch spans
| filter span.name == "gen_ai.chat.completions"
| summarize 
    total_tokens = sum(gen_ai.usage.input_tokens + gen_ai.usage.output_tokens),
    avg_prompt_length = avg(llm.prompt.length),
    avg_response_length = avg(llm.response.length),
    by: {banking.intent}
| sort total_tokens desc
```

### Cost Optimization Opportunities

```dql
fetch spans
| filter span.name == "gen_ai.chat.completions"
| filter llm.cost.usd > 0.01
| summarize 
    expensive_calls = count(),
    max_cost = max(llm.cost.usd),
    max_tokens = max(gen_ai.usage.input_tokens + gen_ai.usage.output_tokens),
    by: {banking.intent, gen_ai.system}
```

### Correlate Cost with User Satisfaction

```dql
fetch spans
| filter span.name == "gen_ai.chat.completions"
| lookup [
    fetch metrics
    | filter metricId == "user.satisfaction.score"
  ], sourceField: gen_ai.system, lookupField: provider
| summarize 
    avg_cost = avg(llm.cost.usd),
    avg_satisfaction = avg(value),
    efficiency_ratio = avg(value) / avg(llm.cost.usd),
    by: {gen_ai.system}
| sort efficiency_ratio desc
```

## Dashboards

### Create "LLM Cost Analysis" Dashboard

**Tile 1: Total Cost by Provider**
```dql
fetch spans
| filter span.name == "gen_ai.chat.completions"
| summarize total_cost = sum(llm.cost.usd), by: {gen_ai.system}
```
*Visualization: Pie chart*

**Tile 2: Cost Over Time**
```dql
fetch spans
| filter span.name == "gen_ai.chat.completions"
| summarize cost = sum(llm.cost.usd), by: {gen_ai.system}
```
*Visualization: Area chart (time series)*

**Tile 3: Token Usage**
```dql
fetch spans
| filter span.name == "gen_ai.chat.completions"
| summarize 
    input = sum(gen_ai.usage.input_tokens),
    output = sum(gen_ai.usage.output_tokens),
    by: {gen_ai.system}
```
*Visualization: Stacked bar chart*

**Tile 4: Most Expensive Intents**
```dql
fetch spans
| filter span.name == "gen_ai.chat.completions"
| summarize cost = sum(llm.cost.usd), by: {banking.intent}
| sort cost desc
| limit 10
```
*Visualization: Bar chart*

### Create "LLM Performance vs Cost" Dashboard

**Tile 1: Latency vs Cost Scatter**
```dql
fetch spans
| filter span.name == "gen_ai.chat.completions"
| fields llm.latency.ms, llm.cost.usd, gen_ai.system
```
*Visualization: Scatter plot*

**Tile 2: Efficiency Score**
```dql
fetch spans
| filter span.name == "gen_ai.chat.completions"
| summarize 
    avg_latency = avg(llm.latency.ms),
    avg_cost = avg(llm.cost.usd),
    efficiency = 1000 / (avg(llm.latency.ms) * avg(llm.cost.usd)),
    by: {gen_ai.system}
```
*Visualization: Table*

## Alerting

### High Cost Alert

**Condition:**
```dql
fetch spans
| filter span.name == "gen_ai.chat.completions"
| filter llm.cost.usd > 0.10
```

**Action:** Send notification when any single LLM call costs > $0.10

### Budget Alert

**Condition:**
```dql
fetch spans, from: now() - 1h
| filter span.name == "gen_ai.chat.completions"
| summarize hourly_cost = sum(llm.cost.usd)
| filter hourly_cost > 10.0
```

**Action:** Alert when hourly LLM costs exceed $10

### Token Limit Alert

**Condition:**
```dql
fetch spans
| filter span.name == "gen_ai.chat.completions"
| filter (gen_ai.usage.input_tokens + gen_ai.usage.output_tokens) > 8000
```

**Action:** Alert on unusually high token usage

## Benefits

1. **Industry Standard** - Uses OpenTelemetry semantic conventions
2. **Cost Visibility** - Track spending per provider, model, and intent
3. **Optimization** - Identify expensive operations to optimize
4. **Comparison** - Compare providers on cost, latency, and quality
5. **Forecasting** - Predict monthly costs based on usage patterns
6. **Compliance** - Track token usage for data governance

## Best Practices

1. **Monitor Cost Trends** - Set up daily/weekly cost reports
2. **Set Budgets** - Create alerts for cost thresholds
3. **Optimize Prompts** - Identify and reduce unnecessarily long prompts
4. **Choose Right Provider** - Use cost/performance data to select providers
5. **Cache Responses** - Identify repeated queries to cache
6. **A/B Test** - Compare different providers on same tasks

## Resources

- [OpenTelemetry Semantic Conventions for GenAI](https://opentelemetry.io/docs/specs/semconv/gen-ai/)
- [Dynatrace OpenTelemetry Integration](https://www.dynatrace.com/support/help/extend-dynatrace/opentelemetry)
- [LLM Observability Best Practices](https://opentelemetry.io/docs/specs/semconv/gen-ai/llm-spans/)

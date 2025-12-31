# Banking AI Agent - Quick Start Guide

## Getting Started in 5 Minutes

### Step 1: Choose Your LLM Provider

Pick at least one FREE option:

#### Option A: Ollama (Recommended - 100% Free & Open Source)
```bash
# Install Ollama
# Windows: Download from https://ollama.ai/download
# Or use PowerShell:
curl https://ollama.ai/install.ps1 | powershell

# Pull a model
ollama pull llama3.2

# Verify it's running
ollama list
```

#### Option B: OpenAI ($5 free credit)
1. Sign up at https://platform.openai.com/
2. Create an API key
3. Set environment variable: `OPENAI_API_KEY=sk-...`

#### Option C: Google Gemini (Generous free tier)
1. Create project at https://console.cloud.google.com/
2. Enable Vertex AI API
3. Set: `GOOGLE_CLOUD_PROJECT_ID=your-project-id`

### Step 2: Configure Environment

Create `.env` file in project root:
```bash
# Minimum configuration (using Ollama)
OLLAMA_BASE_URL=http://localhost:11434
```

Or for OpenAI:
```bash
OPENAI_API_KEY=sk-your-key-here
```

### Step 3: Run the Application

```bash
# Build and run
mvn spring-boot:run

# Wait for: "Started BankingAgentApplication in X seconds"
```

### Step 4: Test It!

#### Create a test account:
```bash
curl -X POST http://localhost:8080/api/accounts \
  -H "Content-Type: application/json" \
  -d '{
    "accountNumber": "ACC001",
    "customerName": "John Doe",
    "email": "john@example.com",
    "accountType": "CHECKING",
    "balance": 1000.00,
    "currency": "USD"
  }'
```

#### Query the AI agent:
```bash
curl -X POST http://localhost:8080/api/agent/query \
  -H "Content-Type: application/json" \
  -d '{
    "accountNumber": "ACC001",
    "query": "What is my current balance?"
  }'
```

## Testing Different LLM Providers

### Test Ollama (Free):
```bash
curl -X POST http://localhost:8080/api/agent/query/ollama \
  -H "Content-Type: application/json" \
  -d '{"accountNumber": "ACC001", "query": "Show my balance"}'
```

### Test OpenAI:
```bash
curl -X POST http://localhost:8080/api/agent/query/openai \
  -H "Content-Type: application/json" \
  -d '{"accountNumber": "ACC001", "query": "What transactions do I have?"}'
```

### Test Gemini:
```bash
curl -X POST http://localhost:8080/api/agent/query/gemini \
  -H "Content-Type: application/json" \
  -d '{"query": "Explain different types of bank accounts"}'
```

## Common Commands

### Check Available Providers:
```bash
curl http://localhost:8080/api/agent/providers
```

### Make a Deposit:
```bash
curl -X POST http://localhost:8080/api/transactions/deposit \
  -H "Content-Type: application/json" \
  -d '{
    "accountNumber": "ACC001",
    "amount": 500.00,
    "description": "Paycheck"
  }'
```

### View Transaction History:
```bash
curl http://localhost:8080/api/transactions/account/ACC001
```

### Transfer Money:
```bash
# First create a second account (ACC002)
# Then transfer:
curl -X POST http://localhost:8080/api/transactions/transfer \
  -H "Content-Type: application/json" \
  -d '{
    "fromAccount": "ACC001",
    "toAccount": "ACC002",
    "amount": 250.00,
    "description": "Transfer"
  }'
```

## Troubleshooting

### Error: "No LLM providers available"
- **Solution**: Install Ollama or configure an API key
- Check: `curl http://localhost:8080/api/agent/providers`

### Error: "Account not found"
- **Solution**: Create an account first using `/api/accounts` endpoint

### Ollama not responding:
```bash
# Check if Ollama is running
ollama list

# Restart Ollama service
# Windows: Restart from Services or Task Manager
```

### Port 8080 already in use:
Edit `application.yml`:
```yaml
server:
  port: 8081
```

## Next Steps

1. **Explore the H2 Console**: http://localhost:8080/h2-console
2. **Check the API Documentation**: Import the endpoints into Postman
3. **Try different queries**: Test natural language understanding
4. **Compare LLM responses**: See how different models respond

## Example Queries to Try

```json
{"accountNumber": "ACC001", "query": "What's my balance?"}
{"accountNumber": "ACC001", "query": "Show my last 5 transactions"}
{"accountNumber": "ACC001", "query": "How much did I spend this month?"}
{"query": "What is a savings account?"}
{"query": "How do wire transfers work?"}
{"query": "What are the benefits of online banking?"}
```

## Performance Tips

- **Ollama (Local)**: First request is slower (model loading), subsequent requests are fast
- **OpenAI**: Consistent speed, costs per token
- **Gemini**: Very fast with free tier, good for experimentation

## Dynatrace Observability

This application exports **traces, logs, and metrics** to Dynatrace via OTLP.

### Quick Dynatrace Setup

1. Edit `application.yml` with your Dynatrace endpoint:
```yaml
management:
  otlp:
    metrics:
      export:
        url: https://{env-id}.live.dynatrace.com/api/v2/otlp/v1/metrics
        headers:
          Authorization: "Api-Token {your-token}"

otel:
  exporter:
    otlp:
      endpoint: https://{env-id}.live.dynatrace.com/api/v2/otlp
      authorization: "Api-Token {your-token}"
```

2. Run the application
3. Check Dynatrace **Logs & Events > Logs** and filter by `service.name: "banking-agent"`

For detailed setup, see [DYNATRACE-SETUP.md](DYNATRACE-SETUP.md).

---

🎉 **You're ready to go!** Start experimenting with your banking AI agent!

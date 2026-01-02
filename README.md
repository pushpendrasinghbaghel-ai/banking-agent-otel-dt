# Banking AI Agent

A Spring Boot application that demonstrates AI-powered banking operations with full observability using OpenTelemetry and Dynatrace.

## Features

- 🤖 **LLM Integration**: Integrates with Ollama for AI-powered banking assistance
- 🏦 **Banking Operations**: Account management, transactions, deposits, withdrawals, and transfers
- 🎯 **Intent Recognition**: AI-powered understanding of customer queries
- 📊 **Full Observability**: Traces, logs, and metrics exported to Dynatrace via OTLP
- 💬 **Natural Language Interface**: Interact with banking services using conversational queries

## Technology Stack

- **Framework**: Spring Boot 3.5.9
- **Language**: Java 21
- **AI Framework**: Spring AI 1.0.0-M6
- **Observability**: OpenTelemetry SDK + Micrometer OTLP (Dynatrace)
- **Database**: H2 (in-memory for development)
- **LLM Provider**: Ollama (local models like llama3.2)

## Project Structure

```
src/main/java/com/banking/agent/
├── BankingAgentApplication.java          # Main application entry point
├── agent/
│   └── BankingAgent.java                 # AI agent for processing requests
├── config/
│   ├── BankingAgentConfig.java           # Banking-specific configuration
│   ├── DataInitializer.java              # Sample data initialization
│   ├── DynatraceMonitoringConfig.java    # OpenTelemetry SDK configuration
│   ├── LlmConfig.java                    # LLM provider configuration
│   └── RestClientConfig.java             # REST client configuration
├── controller/
│   ├── AccountController.java            # Account management API
│   ├── BankingAgentController.java       # AI agent interaction API
│   ├── TransactionController.java        # Transaction management API
│   └── UserSatisfactionController.java   # User feedback API
├── domain/
│   ├── Account.java                      # Account entity
│   ├── BankingRequest.java               # Request DTO
│   ├── BankingResponse.java              # Response DTO
│   ├── Transaction.java                  # Transaction entity
│   └── UserSatisfaction.java             # User satisfaction entity
├── repository/
│   ├── AccountRepository.java            # Account data access
│   └── TransactionRepository.java        # Transaction data access
└── service/
    ├── AccountService.java               # Account business logic
    ├── LlmMonitoringService.java         # LLM call monitoring
    ├── LlmProviderService.java           # LLM provider management
    ├── OpenLLMetryService.java           # OpenLLMetry integration
    └── TransactionService.java           # Transaction business logic
```

## Prerequisites

- Java 21 or higher
- Maven 3.8+
- Ollama installed locally (for AI features)
- (Optional) Dynatrace tenant for observability

## Quick Start

### 1. Clone the Repository

```bash
git clone https://github.com/pushpendrasinghbaghel-ai/banking-agent-otel-dt.git
cd banking-agent-otel-dt
```

### 2. Configure Environment Variables

Copy the example environment file and fill in your values:

```bash
# Copy the example file
cp .env.example .env

# Edit .env with your actual values
```

Refer to [.env.example](.env.example) for all available configuration options including:
- **Dynatrace Observability Settings** (OTLP endpoint, API token)
- **Ollama Configuration** (for local AI models)

> **Note**: The `.env` file is git-ignored and will not be committed. Never commit your actual API keys.

### 3. Install Ollama (Required for AI Features)

```bash
# Windows (using PowerShell)
winget install Ollama.Ollama

# Pull a model
ollama pull llama3.2

# Start Ollama server
ollama serve
```

### 4. Build and Run

```bash
# Build the project
mvn clean package -DskipTests

# Run the application
java -jar target/banking-agent-0.0.1-SNAPSHOT.jar
```

The application will start on `http://localhost:8080`

## Testing the API

### Using Postman (Recommended)

Import the included Postman collection and environment for easy API testing:

1. **Import Collection**: [Banking-AI-Agent.postman_collection.json](Banking-AI-Agent.postman_collection.json)
2. **Import Environment**: [Banking-AI-Agent.postman_environment.json](Banking-AI-Agent.postman_environment.json)

The collection includes pre-configured requests for:
- Health checks
- Account management (create, view, balance)
- Transactions (deposit, withdraw, transfer)
- AI agent queries with natural language

### Using cURL

#### Health Check
```bash
curl http://localhost:8080/api/agent/health
```

#### Query the AI Agent
```bash
curl -X POST http://localhost:8080/api/agent/query \
  -H "Content-Type: application/json" \
  -d '{
    "accountNumber": "ACC001",
    "query": "What is my current balance?"
  }'
```

#### Get Account Balance
```bash
curl http://localhost:8080/api/accounts/ACC001/balance
```

#### Make a Deposit
```bash
curl -X POST http://localhost:8080/api/transactions/deposit \
  -H "Content-Type: application/json" \
  -d '{
    "accountNumber": "ACC001",
    "amount": 500.00,
    "description": "Salary deposit"
  }'
```

## API Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/agent/health` | Health check |
| POST | `/api/agent/query` | Query AI agent with natural language |
| GET | `/api/agent/providers` | List available LLM providers |
| GET | `/api/accounts/{id}` | Get account details |
| GET | `/api/accounts/{id}/balance` | Get account balance |
| POST | `/api/accounts` | Create new account |
| POST | `/api/transactions/deposit` | Make a deposit |
| POST | `/api/transactions/withdraw` | Make a withdrawal |
| POST | `/api/transactions/transfer` | Transfer between accounts |
| GET | `/api/transactions/account/{id}` | Get transaction history |

## Observability Approach: Recommended Architecture

This project uses **OpenTelemetry SDK** for LLM/Spring AI observability. However, after extensive testing, here's our recommended production architecture:

### ✅ Recommended: Dynatrace OneAgent + SDK for LLM

```
┌─────────────────────────────────────────────────────────────────┐
│                    RECOMMENDED ARCHITECTURE                      │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│   Dynatrace OneAgent          +        OpenTelemetry SDK         │
│   ─────────────────                    ──────────────────        │
│   • HTTP/REST traces                   • LLM/Gen AI spans        │
│   • JDBC/SQL queries                   • Spring AI observations  │
│   • JVM metrics                        • Custom business spans   │
│   • Process monitoring                 • Prompt/completion data  │
│   • Full-stack correlation                                       │
│                                                                  │
│   WHY: OneAgent provides automatic instrumentation for           │
│        everything EXCEPT LLM calls (which need SDK)              │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

### ❌ Why NOT Manual/Custom Instrumentation

This project contains examples of custom AOP-based instrumentation (`RepositoryTracingAspect.java`, `TracingDataSourceConfig.java`). **These are included for educational purposes only and should NOT be used in production.**

| Approach | Effort | Maintenance | Coverage | Recommendation |
|----------|--------|-------------|----------|----------------|
| **Dynatrace OneAgent** | Zero code | Auto-updated | Full stack | ✅ Use this |
| **OTel Java Agent** | Config only | Community | Wide | ✅ Alternative |
| **Custom AOP aspects** | High | Manual | Partial | ❌ Avoid |
| **Custom DataSource wrapper** | High | Manual | JDBC only | ❌ Avoid |

#### Problems with Custom Instrumentation

```java
// ❌ DON'T DO THIS IN PRODUCTION
@Aspect
@Component
public class RepositoryTracingAspect {
    @Around("execution(* com.banking.agent.repository.*.*(..))")
    public Object traceRepositoryMethod(ProceedingJoinPoint pjp) {
        // This becomes YOUR maintenance burden:
        // - Every new repository pattern needs handling
        // - Library updates may break your pointcuts
        // - You're now the instrumentation team
        // - Testing overhead multiplies
    }
}
```

**Real-world issues we encountered:**
1. Pointcuts break with Spring Data proxy changes
2. SQL capture requires wrapping DataSource, Connection, Statement, PreparedStatement
3. Missing coverage for async operations, reactive streams
4. No correlation with HTTP spans without manual context propagation
5. Significant development time vs. zero-code agents

### What Each Approach Provides

| Capability | OneAgent | OTel Agent | SDK Only | Custom AOP |
|------------|----------|------------|----------|------------|
| HTTP traces | ✅ Auto | ✅ Auto | ❌ Manual | ❌ Manual |
| JDBC/SQL | ✅ Auto | ✅ Auto | ❌ Manual | ⚠️ Partial |
| JVM metrics | ✅ Auto | ⚠️ Limited | ❌ No | ❌ No |
| LLM spans | ❌ No | ❌ No | ✅ Yes | ✅ Yes |
| Process monitoring | ✅ Auto | ❌ No | ❌ No | ❌ No |
| Maintenance | ✅ Zero | ⚠️ Version updates | ⚠️ Code changes | ❌ High |

### Production Recommendation

```bash
# 1. Install Dynatrace OneAgent on your host/container
#    (provides HTTP, JDBC, JVM, infrastructure automatically)

# 2. Keep SDK dependencies for LLM observability only
#    (Spring AI's built-in @Observed + our LlmMonitoringService)

# 3. Remove custom instrumentation code
#    - RepositoryTracingAspect.java (educational only)
#    - TracingDataSourceConfig.java (educational only)
```

## Dynatrace Observability

This application exports **traces, logs, and metrics** to Dynatrace via OTLP.

### What's Monitored

| Signal | Technology | Dynatrace Endpoint |
|--------|------------|-------------------|
| **Metrics** | Micrometer OTLP Registry | `/api/v2/otlp/v1/metrics` |
| **Traces** | OpenTelemetry SDK | `/api/v2/otlp/v1/traces` |
| **Logs** | OpenTelemetry Logback Appender | `/api/v2/otlp/v1/logs` |

### Setup

1. Set `DYNATRACE_OTLP_ENDPOINT` and `DYNATRACE_API_TOKEN` in your `.env` file
2. Run the application
3. View telemetry in Dynatrace

For detailed setup, see [DYNATRACE-SETUP.md](DYNATRACE-SETUP.md).

## Related POV Projects

For a comprehensive comparison of observability approaches, see:

| Repository | Approach | Purpose |
|------------|----------|---------|
| [banking-agent-otel-sdk](https://github.com/pushpendrasinghbaghel-ai/banking-agent-otel-sdk) | OTel SDK only | LLM observability baseline |
| [banking-agent-otel-agent](https://github.com/pushpendrasinghbaghel-ai/banking-agent-otel-agent) | OTel Java Agent | Full auto-instrumentation |
| [spring-ai-observability-pov](https://github.com/pushpendrasinghbaghel-ai/spring-ai-observability-pov) | POV Documentation | Comparison results & evidence |

## Database

The application uses H2 in-memory database with sample data pre-loaded.

Access the H2 console at: `http://localhost:8080/h2-console`
- JDBC URL: `jdbc:h2:mem:bankingdb`
- Username: `sa`
- Password: (leave empty)

## Documentation

- [QUICKSTART.md](QUICKSTART.md) - Quick start guide
- [DYNATRACE-SETUP.md](DYNATRACE-SETUP.md) - Dynatrace configuration details
- [OPENLLMETRY-GUIDE.md](OPENLLMETRY-GUIDE.md) - LLM observability guide
- [TEST-MONITORING.md](TEST-MONITORING.md) - Testing and monitoring guide

## Contributing

Contributions are welcome! Please:

1. Fork the repository
2. Create a feature branch
3. Commit your changes
4. Push to the branch
5. Create a Pull Request

## License

This project is licensed under the Apache License 2.0.

## Resources

- [Spring AI Documentation](https://docs.spring.io/spring-ai/reference/)
- [OpenTelemetry Java](https://opentelemetry.io/docs/languages/java/)
- [Dynatrace OTLP Ingest](https://docs.dynatrace.com/docs/extend-dynatrace/opentelemetry)
- [Ollama](https://ollama.ai/)

---

Built with ❤️ using Spring Boot and OpenTelemetry

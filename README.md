# Banking AI Agent

A Spring Boot application that integrates multiple LLM providers (OpenAI, Google Gemini, Ollama) to create an intelligent banking agent. Built using the Embabel Agent framework principles for agentic AI workflows.

## Features

- 🤖 **Multi-LLM Support**: Integrates with OpenAI (GPT-4), Google Gemini, and Ollama for flexible AI model selection
- 🏦 **Banking Operations**: Account management, transactions, deposits, withdrawals, and transfers
- 🎯 **Intent Recognition**: AI-powered understanding of customer queries
- 🔄 **Dynamic Provider Selection**: Choose between free-tier and open-source LLM models
- 📊 **Transaction History**: View and analyze account transactions
- 💬 **Natural Language Interface**: Interact with banking services using conversational queries

## Technology Stack

- **Framework**: Spring Boot 3.5.9
- **Language**: Java 21
- **AI Framework**: Spring AI 1.0.0-M6
- **Observability**: OpenTelemetry + Micrometer (Dynatrace OTLP)
- **Database**: H2 (development), easily switchable to PostgreSQL/MySQL
- **LLM Providers**:
  - OpenAI (GPT-4o-mini)
  - Google Gemini (gemini-2.0-flash-exp)
  - Ollama (llama3.2 or any local model)

## Project Structure

```
banking-agent/
├── src/main/java/com/banking/agent/
│   ├── BankingAgentApplication.java     # Main application entry point
│   ├── agent/
│   │   └── BankingAgent.java            # AI agent for processing requests
│   ├── config/
│   │   ├── BankingAgentConfig.java      # Banking-specific configuration
│   │   └── LlmConfig.java               # LLM provider configuration
│   ├── controller/
│   │   ├── AccountController.java       # Account management API
│   │   ├── TransactionController.java   # Transaction management API
│   │   └── BankingAgentController.java  # AI agent interaction API
│   ├── domain/
│   │   ├── Account.java                 # Account entity
│   │   ├── Transaction.java             # Transaction entity
│   │   ├── BankingRequest.java          # Request DTO
│   │   └── BankingResponse.java         # Response DTO
│   ├── repository/
│   │   ├── AccountRepository.java       # Account data access
│   │   └── TransactionRepository.java   # Transaction data access
│   └── service/
│       ├── AccountService.java          # Account business logic
│       ├── TransactionService.java      # Transaction business logic
│       └── LlmProviderService.java      # LLM provider management
└── src/main/resources/
    └── application.yml                   # Application configuration
```

## Prerequisites

- Java 21 or higher
- Maven 3.8+
- (Optional) Docker for running Ollama locally
- API Keys for LLM providers (at least one):
  - OpenAI API Key
  - Google Cloud Project (for Gemini)
  - Ollama installation (for local models)

## Setup

### 1. Clone the Repository

```bash
cd c:\workspace\ai-project
```

### 2. Configure Environment Variables

Create a `.env` file or set environment variables:

```bash
# OpenAI (Free tier available)
export OPENAI_API_KEY=your-openai-api-key

# Google Gemini (Free tier available)
export GOOGLE_CLOUD_PROJECT_ID=your-project-id
export GOOGLE_CLOUD_LOCATION=us-central1

# Ollama (Self-hosted, completely free)
export OLLAMA_BASE_URL=http://localhost:11434
```

### 3. Install Ollama (Optional for Local Models)

For completely free, open-source LLM support:

```bash
# Windows (using PowerShell)
curl https://ollama.ai/install.ps1 | powershell

# Pull a model
ollama pull llama3.2
```

### 4. Build the Project

```bash
mvn clean install
```

### 5. Run the Application

```bash
mvn spring-boot:run
```

The application will start on `http://localhost:8080`

## API Endpoints

### Agent Endpoints

#### Query the AI Agent (Default Provider)
```bash
POST /api/agent/query
Content-Type: application/json

{
  "accountNumber": "ACC001",
  "query": "What is my current balance?",
  "context": "checking account"
}
```

#### Query with Specific Provider
```bash
POST /api/agent/query/ollama
Content-Type: application/json

{
  "accountNumber": "ACC001",
  "query": "Show me my recent transactions"
}
```

#### Get Available Providers
```bash
GET /api/agent/providers
```

### Account Management

#### Create Account
```bash
POST /api/accounts
Content-Type: application/json

{
  "accountNumber": "ACC001",
  "customerName": "John Doe",
  "email": "john@example.com",
  "accountType": "CHECKING",
  "balance": 1000.00,
  "currency": "USD"
}
```

#### Get Account Details
```bash
GET /api/accounts/ACC001
```

#### Get Account Balance
```bash
GET /api/accounts/ACC001/balance
```

### Transaction Management

#### Deposit
```bash
POST /api/transactions/deposit
Content-Type: application/json

{
  "accountNumber": "ACC001",
  "amount": 500.00,
  "description": "Salary deposit"
}
```

#### Withdraw
```bash
POST /api/transactions/withdraw
Content-Type: application/json

{
  "accountNumber": "ACC001",
  "amount": 100.00,
  "description": "ATM withdrawal"
}
```

#### Transfer
```bash
POST /api/transactions/transfer
Content-Type: application/json

{
  "fromAccount": "ACC001",
  "toAccount": "ACC002",
  "amount": 250.00,
  "description": "Transfer to savings"
}
```

#### Get Transaction History
```bash
GET /api/transactions/account/ACC001
```

## Usage Examples

### Example 1: Check Balance with Natural Language

```bash
curl -X POST http://localhost:8080/api/agent/query \
  -H "Content-Type: application/json" \
  -d '{
    "accountNumber": "ACC001",
    "query": "How much money do I have?",
    "operationType": "CHECK_BALANCE"
  }'
```

### Example 2: View Transactions Using Ollama (Free)

```bash
curl -X POST http://localhost:8080/api/agent/query/ollama \
  -H "Content-Type: application/json" \
  -d '{
    "accountNumber": "ACC001",
    "query": "Show me my last 10 transactions"
  }'
```

### Example 3: General Banking Inquiry

```bash
curl -X POST http://localhost:8080/api/agent/query/gemini \
  -H "Content-Type: application/json" \
  -d '{
    "query": "What are the benefits of a savings account?",
    "context": "general inquiry"
  }'
```

## LLM Provider Configuration

### Free Tier Options

1. **OpenAI** - $5 free credit for new users
   - Model: gpt-4o-mini (cost-effective)
   - Configure: `OPENAI_API_KEY`

2. **Google Gemini** - Generous free tier
   - Model: gemini-2.0-flash-exp
   - Configure: `GOOGLE_CLOUD_PROJECT_ID`

3. **Ollama** - Completely free and open-source
   - Models: llama3.2, mistral, codellama, etc.
   - Configure: `OLLAMA_BASE_URL`

### Switching Providers

Edit `application.yml`:

```yaml
banking:
  agent:
    default-llm: ollama  # Change to: openai, gemini, or ollama
    fallback-llm: ollama
```

## Embabel Agent Framework Integration

This project uses patterns inspired by the Embabel Agent framework:

- **Action-based architecture**: Each banking operation is an action
- **Goal-oriented design**: Agent works towards customer goals
- **Type-safe domain models**: Strong typing with Jackson annotations
- **Multi-LLM support**: Easy integration with multiple AI providers
- **Testability**: Clean separation of concerns for unit testing

## Dynatrace Observability

This application is fully instrumented to send **traces, logs, and metrics** to Dynatrace via OTLP (OpenTelemetry Protocol).

### Quick Setup

1. Set your Dynatrace endpoint and API token in `application.yml`
2. Run the application
3. All telemetry data is automatically exported

For detailed setup instructions, see [DYNATRACE-SETUP.md](DYNATRACE-SETUP.md).

### What's Monitored

| Signal | Technology | Endpoint |
|--------|------------|----------|
| **Metrics** | Micrometer OTLP Registry | `/api/v2/otlp/v1/metrics` |
| **Traces** | OpenTelemetry SDK | `/api/v2/otlp/v1/traces` |
| **Logs** | OpenTelemetry Logback Appender | `/api/v2/otlp/v1/logs` |

## Database

The application uses H2 in-memory database for development. Access the console at:
- URL: `http://localhost:8080/h2-console`
- JDBC URL: `jdbc:h2:mem:bankingdb`
- Username: `sa`
- Password: (leave empty)

### Switching to Production Database

Update `application.yml` for PostgreSQL:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/bankingdb
    username: your_username
    password: your_password
  jpa:
    database-platform: org.hibernate.dialect.PostgreSQLDialect
```

## Future Enhancements

Based on Embabel Agent framework capabilities:

- [ ] Implement @Agent annotations for cleaner agent definitions
- [ ] Add GOAP (Goal Oriented Action Planning) for complex workflows
- [ ] Integrate Embabel's federation for multi-agent scenarios
- [ ] Add MCP (Model Context Protocol) server support
- [ ] Implement tool groups for specialized banking operations
- [ ] Add comprehensive testing with Embabel test support
- [ ] Deploy as containerized microservice

## Contributing

Contributions are welcome! Please follow these guidelines:

1. Fork the repository
2. Create a feature branch
3. Commit your changes
4. Push to the branch
5. Create a Pull Request

## License

This project is licensed under the Apache License 2.0 - see the LICENSE file for details.

## Resources

- [Embabel Agent Framework](https://github.com/embabel/embabel-agent)
- [Spring AI Documentation](https://docs.spring.io/spring-ai/reference/)
- [OpenAI API](https://platform.openai.com/docs)
- [Google Gemini API](https://ai.google.dev/)
- [Ollama](https://ollama.ai/)

## Support

For issues and questions:
- Create an issue in this repository
- Check Embabel Agent documentation
- Join the Embabel Discord community

---

Built with ❤️ using Spring Boot and Embabel Agent Framework

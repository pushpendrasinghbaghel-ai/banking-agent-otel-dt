package com.banking.agent.agent;

import com.banking.agent.domain.Account;
import com.banking.agent.domain.BankingRequest;
import com.banking.agent.domain.BankingResponse;
import com.banking.agent.domain.Transaction;
import com.banking.agent.service.AccountService;
import com.banking.agent.service.LlmMonitoringService;
import com.banking.agent.service.LlmProviderService;
import com.banking.agent.service.OpenLLMetryService;
import com.banking.agent.service.TransactionService;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * Banking AI Agent using Embabel-inspired patterns.
 * Handles customer queries and performs banking operations.
 */
@Component
@Slf4j
public class BankingAgent {

    private final AccountService accountService;
    private final TransactionService transactionService;
    private final LlmProviderService llmProviderService;
    private final LlmMonitoringService monitoringService;
    private final OpenLLMetryService openLLMetryService;
    private final Tracer tracer;

    public BankingAgent(AccountService accountService, TransactionService transactionService, 
                       LlmProviderService llmProviderService, LlmMonitoringService monitoringService,
                       OpenLLMetryService openLLMetryService, Tracer tracer) {
        this.accountService = accountService;
        this.transactionService = transactionService;
        this.llmProviderService = llmProviderService;
        this.monitoringService = monitoringService;
        this.openLLMetryService = openLLMetryService;
        this.tracer = tracer;
    }

    /**
     * Process a banking request using the specified LLM provider
     */
    public BankingResponse processRequest(BankingRequest request, String llmProvider) {
        log.info("Processing banking request with provider: {}", llmProvider);
        
        Span requestSpan = tracer.spanBuilder("banking.process_request")
                .setAttribute("banking.provider", llmProvider)
                .setAttribute("banking.account_number", request.getAccountNumber() != null ? request.getAccountNumber() : "none")
                .startSpan();
        
        try {
            // Determine the intent of the request
            String intent = determineIntent(request, llmProvider);
            log.debug("Determined intent: {}", intent);
            requestSpan.setAttribute("banking.intent", intent);

            // Execute the appropriate action based on intent
            BankingResponse response = executeAction(request, intent, llmProvider);
            requestSpan.setAttribute("banking.response_status", response.getStatus().toString());
            
            // Record business event
            monitoringService.recordBusinessEvent("banking_request_processed", Map.of(
                    "provider", llmProvider,
                    "intent", intent,
                    "status", response.getStatus().toString(),
                    "account", request.getAccountNumber() != null ? request.getAccountNumber() : "none"
            ));
            
            return response;
            
        } catch (Exception e) {
            log.error("Error processing banking request", e);
            requestSpan.recordException(e);
            return new BankingResponse(
                    "I apologize, but I encountered an error processing your request: " + e.getMessage(),
                    null,
                    BankingResponse.ResponseStatus.ERROR,
                    llmProvider
            );
        } finally {
            requestSpan.end();
        }
    }

    /**
     * Determine the customer's intent using LLM
     */
    private String determineIntent(BankingRequest request, String llmProvider) {
        ChatClient chatClient = llmProviderService.getChatClient(llmProvider);
        
        // Start LLM monitoring (legacy)
        LlmMonitoringService.LlmMonitoringContext monitoringContext = 
                monitoringService.startMonitoring(llmProvider, "intent-classification", "DETERMINE_INTENT");
        
        // Start OpenLLMetry monitoring with semantic conventions
        OpenLLMetryService.LLMSpanContext llmSpan = 
                openLLMetryService.startChatCompletion(llmProvider, "intent-classification", "classify_intent");
        
        String prompt = String.format("""
                Analyze the following customer query and determine their intent.
                Respond with ONLY ONE of these intents: CHECK_BALANCE, VIEW_TRANSACTIONS, DEPOSIT, WITHDRAWAL, TRANSFER, ACCOUNT_INFO, GENERAL_INQUIRY
                
                Customer Query: %s
                Account Number: %s
                Context: %s
                
                Intent:""", 
                request.getQuery(), 
                request.getAccountNumber() != null ? request.getAccountNumber() : "Not provided",
                request.getContext() != null ? request.getContext() : "None"
        );

        try {
            // Record prompt details
            openLLMetryService.recordPrompt(llmSpan, prompt, 0.7, null);
            openLLMetryService.recordBankingContext(llmSpan, "DETERMINE_INTENT", request.getAccountNumber());
            
            String response = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();
            
            // Record completion
            openLLMetryService.recordCompletion(llmSpan, response, "stop");
            openLLMetryService.recordSuccess(llmSpan);
            
            // Legacy monitoring
            int estimatedTokens = (int) llmSpan.getTotalTokens();
            monitoringService.recordSuccess(monitoringContext, estimatedTokens, response);
            
            return response.trim().toUpperCase();
        } catch (Exception e) {
            openLLMetryService.recordError(llmSpan, e);
            monitoringService.recordError(monitoringContext, e);
            throw e;
        }
    }

    /**
     * Execute the appropriate action based on determined intent
     */
    private BankingResponse executeAction(BankingRequest request, String intent, String llmProvider) {
        return switch (intent) {
            case "CHECK_BALANCE" -> handleCheckBalance(request, llmProvider);
            case "VIEW_TRANSACTIONS" -> handleViewTransactions(request, llmProvider);
            case "ACCOUNT_INFO" -> handleAccountInfo(request, llmProvider);
            case "GENERAL_INQUIRY" -> handleGeneralInquiry(request, llmProvider);
            default -> handleGeneralInquiry(request, llmProvider);
        };
    }

    /**
     * Handle balance check requests
     */
    private BankingResponse handleCheckBalance(BankingRequest request, String llmProvider) {
        if (request.getAccountNumber() == null) {
            return new BankingResponse(
                    "To check your balance, please provide your account number.",
                    null,
                    BankingResponse.ResponseStatus.ERROR,
                    llmProvider
            );
        }

        Account account = accountService.findByAccountNumber(request.getAccountNumber())
                .orElse(null);

        if (account == null) {
            return new BankingResponse(
                    "Account not found. Please verify your account number.",
                    null,
                    BankingResponse.ResponseStatus.ERROR,
                    llmProvider
            );
        }

        ChatClient chatClient = llmProviderService.getChatClient(llmProvider);
        String prompt = String.format("""
                Generate a friendly, natural response for a customer balance inquiry.
                
                Account Number: %s
                Account Type: %s
                Current Balance: %s %s
                Account Status: %s
                
                Provide a helpful response that includes the balance information in a conversational way.
                """,
                account.getAccountNumber(),
                account.getAccountType(),
                account.getBalance(),
                account.getCurrency(),
                account.getStatus()
        );

        String message = chatClient.prompt()
                .user(prompt)
                .call()
                .content();

        return new BankingResponse(message, account, BankingResponse.ResponseStatus.SUCCESS, llmProvider);
    }

    /**
     * Handle transaction history requests
     */
    private BankingResponse handleViewTransactions(BankingRequest request, String llmProvider) {
        if (request.getAccountNumber() == null) {
            return new BankingResponse(
                    "To view transactions, please provide your account number.",
                    null,
                    BankingResponse.ResponseStatus.ERROR,
                    llmProvider
            );
        }

        List<Transaction> transactions = transactionService.getTransactionHistory(request.getAccountNumber());

        if (transactions.isEmpty()) {
            return new BankingResponse(
                    "No transactions found for this account.",
                    transactions,
                    BankingResponse.ResponseStatus.SUCCESS,
                    llmProvider
            );
        }

        ChatClient chatClient = llmProviderService.getChatClient(llmProvider);
        
        StringBuilder transactionSummary = new StringBuilder();
        transactions.stream().limit(10).forEach(t -> 
            transactionSummary.append(String.format("- %s: %s %s %s on %s\n",
                    t.getType(),
                    t.getAmount(),
                    t.getCurrency(),
                    t.getDescription() != null ? "(" + t.getDescription() + ")" : "",
                    t.getTransactionDate().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
            ))
        );

        String prompt = String.format("""
                Generate a friendly summary of the customer's recent transactions.
                
                Account Number: %s
                Total Transactions: %d
                Recent Transactions (last 10):
                %s
                
                Provide a helpful summary in a conversational way.
                """,
                request.getAccountNumber(),
                transactions.size(),
                transactionSummary.toString()
        );

        String message = chatClient.prompt()
                .user(prompt)
                .call()
                .content();

        return new BankingResponse(message, transactions, BankingResponse.ResponseStatus.SUCCESS, llmProvider);
    }

    /**
     * Handle account information requests
     */
    private BankingResponse handleAccountInfo(BankingRequest request, String llmProvider) {
        if (request.getAccountNumber() == null) {
            return new BankingResponse(
                    "To view account information, please provide your account number.",
                    null,
                    BankingResponse.ResponseStatus.ERROR,
                    llmProvider
            );
        }

        Account account = accountService.findByAccountNumber(request.getAccountNumber())
                .orElse(null);

        if (account == null) {
            return new BankingResponse(
                    "Account not found. Please verify your account number.",
                    null,
                    BankingResponse.ResponseStatus.ERROR,
                    llmProvider
            );
        }

        ChatClient chatClient = llmProviderService.getChatClient(llmProvider);
        String prompt = String.format("""
                Generate a comprehensive summary of the customer's account information.
                
                Account Number: %s
                Customer Name: %s
                Account Type: %s
                Balance: %s %s
                Status: %s
                Created: %s
                
                Provide a helpful summary in a conversational, professional manner.
                """,
                account.getAccountNumber(),
                account.getCustomerName(),
                account.getAccountType(),
                account.getBalance(),
                account.getCurrency(),
                account.getStatus(),
                account.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        );

        String message = chatClient.prompt()
                .user(prompt)
                .call()
                .content();

        return new BankingResponse(message, account, BankingResponse.ResponseStatus.SUCCESS, llmProvider);
    }

    /**
     * Handle general banking inquiries
     */
    private BankingResponse handleGeneralInquiry(BankingRequest request, String llmProvider) {
        ChatClient chatClient = llmProviderService.getChatClient(llmProvider);
        
        String prompt = String.format("""
                You are a helpful banking assistant. Answer the following customer question:
                
                Question: %s
                Context: %s
                
                Provide a helpful, accurate, and professional response about banking services, policies, or general information.
                If the question requires account-specific information, politely ask for the account number.
                """,
                request.getQuery(),
                request.getContext() != null ? request.getContext() : "General banking inquiry"
        );

        String message = chatClient.prompt()
                .user(prompt)
                .call()
                .content();

        return new BankingResponse(message, null, BankingResponse.ResponseStatus.SUCCESS, llmProvider);
    }
}

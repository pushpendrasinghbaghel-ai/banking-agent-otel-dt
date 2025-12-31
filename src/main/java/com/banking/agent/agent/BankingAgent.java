package com.banking.agent.agent;

import com.banking.agent.domain.Account;
import com.banking.agent.domain.BankingRequest;
import com.banking.agent.domain.BankingResponse;
import com.banking.agent.domain.Transaction;
import com.banking.agent.service.AccountService;
import com.banking.agent.service.TracedChatService;
import com.banking.agent.service.TransactionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Banking AI Agent - Handles customer queries and performs banking operations.
 * 
 * Uses TracedChatService for LLM calls which automatically captures:
 * - gen_ai.prompt: The full prompt sent to the LLM
 * - gen_ai.completion: The response from the LLM
 * - Token usage and latency metrics
 */
@Component
@Slf4j
public class BankingAgent {

    private final AccountService accountService;
    private final TransactionService transactionService;
    private final TracedChatService tracedChatService;

    public BankingAgent(AccountService accountService, 
                       TransactionService transactionService, 
                       TracedChatService tracedChatService) {
        this.accountService = accountService;
        this.transactionService = transactionService;
        this.tracedChatService = tracedChatService;
    }

    /**
     * Process a banking request using the specified LLM provider.
     * Tracing is handled automatically by AOP.
     */
    public BankingResponse processRequest(BankingRequest request, String llmProvider) {
        log.info("Processing banking request with provider: {}", llmProvider);
        
        try {
            // Determine the intent of the request
            String intent = determineIntent(request, llmProvider);
            log.debug("Determined intent: {}", intent);

            // Execute the appropriate action based on intent
            return executeAction(request, intent, llmProvider);
            
        } catch (Exception e) {
            log.error("Error processing banking request", e);
            return new BankingResponse(
                    "I apologize, but I encountered an error processing your request: " + e.getMessage(),
                    null,
                    BankingResponse.ResponseStatus.ERROR,
                    llmProvider
            );
        }
    }

    /**
     * Determine the customer's intent using LLM.
     * TracedChatService captures prompt/completion in traces.
     */
    String determineIntent(BankingRequest request, String llmProvider) {
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

        String response = tracedChatService.chat(prompt, llmProvider);
            
        return response.trim().toUpperCase();
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

        String message = tracedChatService.chat(prompt, llmProvider);

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

        String message = tracedChatService.chat(prompt, llmProvider);

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

        String message = tracedChatService.chat(prompt, llmProvider);

        return new BankingResponse(message, account, BankingResponse.ResponseStatus.SUCCESS, llmProvider);
    }

    /**
     * Handle general banking inquiries
     */
    private BankingResponse handleGeneralInquiry(BankingRequest request, String llmProvider) {
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

        String message = tracedChatService.chat(prompt, llmProvider);

        return new BankingResponse(message, null, BankingResponse.ResponseStatus.SUCCESS, llmProvider);
    }
}

package com.banking.agent.controller;

import com.banking.agent.agent.BankingAgent;
import com.banking.agent.domain.BankingRequest;
import com.banking.agent.domain.BankingResponse;
import com.banking.agent.service.LlmProviderService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST API controller for AI-powered banking agent interactions
 */
@RestController
@RequestMapping("/api/agent")
public class BankingAgentController {

    private final BankingAgent bankingAgent;
    private final LlmProviderService llmProviderService;

    public BankingAgentController(BankingAgent bankingAgent, LlmProviderService llmProviderService) {
        this.bankingAgent = bankingAgent;
        this.llmProviderService = llmProviderService;
    }

    /**
     * Process a banking query using the default LLM provider
     */
    @PostMapping("/query")
    public ResponseEntity<BankingResponse> processQuery(@RequestBody BankingRequest request) {
        BankingResponse response = bankingAgent.processRequest(request, "openai");
        return ResponseEntity.ok(response);
    }

    /**
     * Process a banking query using a specific LLM provider
     */
    @PostMapping("/query/{provider}")
    public ResponseEntity<BankingResponse> processQueryWithProvider(
            @PathVariable String provider,
            @RequestBody BankingRequest request) {
        
        if (!llmProviderService.isProviderAvailable(provider)) {
            BankingResponse errorResponse = new BankingResponse(
                    "LLM provider '" + provider + "' is not available. Please check your configuration.",
                    null,
                    BankingResponse.ResponseStatus.ERROR,
                    provider
            );
            return ResponseEntity.badRequest().body(errorResponse);
        }

        BankingResponse response = bankingAgent.processRequest(request, provider);
        return ResponseEntity.ok(response);
    }

    /**
     * Get available LLM providers
     */
    @GetMapping("/providers")
    public ResponseEntity<Map<String, Boolean>> getAvailableProviders() {
        Map<String, Boolean> providers = Map.of(
                "openai", llmProviderService.isProviderAvailable("openai"),
                "gemini", llmProviderService.isProviderAvailable("gemini"),
                "ollama", llmProviderService.isProviderAvailable("ollama")
        );
        return ResponseEntity.ok(providers);
    }

    /**
     * Health check endpoint
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "Banking AI Agent"
        ));
    }
}

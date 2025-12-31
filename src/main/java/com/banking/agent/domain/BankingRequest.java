package com.banking.agent.domain;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.Data;

/**
 * Request object for banking queries.
 * Used by AI agents to understand customer intent.
 */
@Data
@JsonClassDescription("Customer query or request for banking operations")
public class BankingRequest {

    @JsonPropertyDescription("Customer's account number")
    private String accountNumber;

    @JsonPropertyDescription("Type of banking operation requested")
    private String operationType;

    @JsonPropertyDescription("Natural language query or request from the customer")
    private String query;

    @JsonPropertyDescription("Additional context or parameters")
    private String context;

    @JsonCreator
    public BankingRequest(
            @JsonProperty("accountNumber") String accountNumber,
            @JsonProperty("operationType") String operationType,
            @JsonProperty("query") String query,
            @JsonProperty("context") String context) {
        this.accountNumber = accountNumber;
        this.operationType = operationType;
        this.query = query;
        this.context = context;
    }

    public BankingRequest() {
    }
}

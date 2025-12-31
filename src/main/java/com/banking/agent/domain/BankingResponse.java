package com.banking.agent.domain;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.Data;

/**
 * Response object for banking operations.
 * Contains AI-generated response and structured data.
 */
@Data
@JsonClassDescription("Response from banking AI agent")
public class BankingResponse {

    @JsonPropertyDescription("Natural language response to the customer")
    private String message;

    @JsonPropertyDescription("Structured data related to the response")
    private Object data;

    @JsonPropertyDescription("Status of the operation (SUCCESS, ERROR, PENDING)")
    private ResponseStatus status;

    @JsonPropertyDescription("LLM provider used to generate the response")
    private String llmProvider;

    @JsonCreator
    public BankingResponse(
            @JsonProperty("message") String message,
            @JsonProperty("data") Object data,
            @JsonProperty("status") ResponseStatus status,
            @JsonProperty("llmProvider") String llmProvider) {
        this.message = message;
        this.data = data;
        this.status = status;
        this.llmProvider = llmProvider;
    }

    public BankingResponse() {
    }

    public enum ResponseStatus {
        SUCCESS, ERROR, PENDING
    }
}

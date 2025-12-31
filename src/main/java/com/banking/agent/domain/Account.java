package com.banking.agent.domain;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Domain model representing a bank account.
 * Used by AI agents to understand and manipulate account data.
 */
@Entity
@Table(name = "accounts")
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonClassDescription("Bank account with balance and customer information")
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @JsonPropertyDescription("Unique account identifier")
    private Long id;

    @Column(unique = true, nullable = false)
    @JsonProperty("accountNumber")
    @JsonPropertyDescription("Unique account number")
    private String accountNumber;

    @JsonPropertyDescription("Account holder's name")
    @Column(nullable = false)
    private String customerName;

    @JsonPropertyDescription("Account holder's email address")
    @Column(nullable = false)
    private String email;

    @JsonPropertyDescription("Account type (CHECKING, SAVINGS, or INVESTMENT)")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccountType accountType;

    @JsonPropertyDescription("Current account balance")
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal balance;

    @JsonPropertyDescription("Account currency code (USD, EUR, etc.)")
    @Column(nullable = false)
    private String currency;

    @JsonPropertyDescription("Account status (ACTIVE, SUSPENDED, CLOSED)")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccountStatus status;

    @JsonPropertyDescription("Date and time when the account was created")
    @Column(nullable = false)
    private LocalDateTime createdAt;

    @JsonPropertyDescription("Date and time of the last update")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (status == null) {
            status = AccountStatus.ACTIVE;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public enum AccountType {
        CHECKING, SAVINGS, INVESTMENT
    }

    public enum AccountStatus {
        ACTIVE, SUSPENDED, CLOSED
    }
}

package com.banking.agent.domain;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Domain model representing a financial transaction.
 * Used by AI agents to process and analyze banking transactions.
 */
@Entity
@Table(name = "transactions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonClassDescription("Financial transaction between accounts or with external entities")
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @JsonPropertyDescription("Unique transaction identifier")
    private Long id;

    @JsonPropertyDescription("Account number involved in the transaction")
    @Column(nullable = false)
    private String accountNumber;

    @JsonPropertyDescription("Transaction type (DEPOSIT, WITHDRAWAL, TRANSFER, PAYMENT)")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionType type;

    @JsonPropertyDescription("Transaction amount")
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @JsonPropertyDescription("Transaction currency code")
    @Column(nullable = false)
    private String currency;

    @JsonPropertyDescription("Destination account number (for transfers)")
    private String destinationAccount;

    @JsonPropertyDescription("Transaction description or memo")
    @Column(length = 500)
    private String description;

    @JsonPropertyDescription("Transaction status (PENDING, COMPLETED, FAILED, CANCELLED)")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionStatus status;

    @JsonPropertyDescription("Date and time when the transaction was created")
    @Column(nullable = false)
    private LocalDateTime transactionDate;

    @JsonPropertyDescription("Balance after transaction")
    @Column(precision = 19, scale = 2)
    private BigDecimal balanceAfter;

    @PrePersist
    protected void onCreate() {
        if (transactionDate == null) {
            transactionDate = LocalDateTime.now();
        }
        if (status == null) {
            status = TransactionStatus.PENDING;
        }
    }

    public enum TransactionType {
        DEPOSIT, WITHDRAWAL, TRANSFER, PAYMENT
    }

    public enum TransactionStatus {
        PENDING, COMPLETED, FAILED, CANCELLED
    }
}

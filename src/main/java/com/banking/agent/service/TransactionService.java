package com.banking.agent.service;

import com.banking.agent.domain.Account;
import com.banking.agent.domain.Transaction;
import com.banking.agent.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Service for managing banking transactions
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final AccountService accountService;

    @Transactional
    public Transaction createDeposit(String accountNumber, BigDecimal amount, String description) {
        Account account = accountService.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new IllegalArgumentException("Account not found: " + accountNumber));

        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Deposit amount must be positive");
        }

        BigDecimal newBalance = account.getBalance().add(amount);
        accountService.updateBalance(accountNumber, newBalance);

        Transaction transaction = new Transaction();
        transaction.setAccountNumber(accountNumber);
        transaction.setType(Transaction.TransactionType.DEPOSIT);
        transaction.setAmount(amount);
        transaction.setCurrency(account.getCurrency());
        transaction.setDescription(description);
        transaction.setStatus(Transaction.TransactionStatus.COMPLETED);
        transaction.setBalanceAfter(newBalance);
        transaction.setTransactionDate(LocalDateTime.now());

        log.info("Deposit created: {} {} to account {}", amount, account.getCurrency(), accountNumber);
        return transactionRepository.save(transaction);
    }

    @Transactional
    public Transaction createWithdrawal(String accountNumber, BigDecimal amount, String description) {
        Account account = accountService.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new IllegalArgumentException("Account not found: " + accountNumber));

        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Withdrawal amount must be positive");
        }

        if (account.getBalance().compareTo(amount) < 0) {
            throw new IllegalArgumentException("Insufficient funds");
        }

        BigDecimal newBalance = account.getBalance().subtract(amount);
        accountService.updateBalance(accountNumber, newBalance);

        Transaction transaction = new Transaction();
        transaction.setAccountNumber(accountNumber);
        transaction.setType(Transaction.TransactionType.WITHDRAWAL);
        transaction.setAmount(amount);
        transaction.setCurrency(account.getCurrency());
        transaction.setDescription(description);
        transaction.setStatus(Transaction.TransactionStatus.COMPLETED);
        transaction.setBalanceAfter(newBalance);
        transaction.setTransactionDate(LocalDateTime.now());

        log.info("Withdrawal created: {} {} from account {}", amount, account.getCurrency(), accountNumber);
        return transactionRepository.save(transaction);
    }

    @Transactional
    public Transaction createTransfer(String fromAccount, String toAccount, BigDecimal amount, String description) {
        // Withdraw from source account
        Transaction withdrawal = createWithdrawal(fromAccount, amount, "Transfer to " + toAccount);
        
        // Deposit to destination account
        createDeposit(toAccount, amount, "Transfer from " + fromAccount);

        // Update withdrawal transaction with destination info
        withdrawal.setDestinationAccount(toAccount);
        withdrawal.setType(Transaction.TransactionType.TRANSFER);
        
        log.info("Transfer completed: {} from {} to {}", amount, fromAccount, toAccount);
        return transactionRepository.save(withdrawal);
    }

    @Transactional(readOnly = true)
    public List<Transaction> getTransactionHistory(String accountNumber) {
        return transactionRepository.findByAccountNumberOrderByTransactionDateDesc(accountNumber);
    }

    @Transactional(readOnly = true)
    public List<Transaction> getTransactionsByDateRange(String accountNumber, LocalDateTime startDate, LocalDateTime endDate) {
        return transactionRepository.findByAccountNumberAndTransactionDateBetween(accountNumber, startDate, endDate);
    }
}

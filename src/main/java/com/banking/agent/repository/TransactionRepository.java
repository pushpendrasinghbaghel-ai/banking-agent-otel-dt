package com.banking.agent.repository;

import com.banking.agent.domain.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository for Transaction entity operations
 */
@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    
    List<Transaction> findByAccountNumber(String accountNumber);
    
    List<Transaction> findByAccountNumberAndTransactionDateBetween(
            String accountNumber, 
            LocalDateTime startDate, 
            LocalDateTime endDate
    );
    
    List<Transaction> findByAccountNumberOrderByTransactionDateDesc(String accountNumber);
}

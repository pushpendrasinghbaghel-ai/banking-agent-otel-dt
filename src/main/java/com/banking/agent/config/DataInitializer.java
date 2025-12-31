package com.banking.agent.config;

import com.banking.agent.domain.Account;
import com.banking.agent.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Initializes the database with sample data on application startup
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final AccountRepository accountRepository;

    @Override
    public void run(String... args) {
        log.info("Initializing database with sample data...");

        // Create sample accounts
        createAccount("ACC001", "John Doe", "john.doe@example.com", 
                     Account.AccountType.CHECKING, new BigDecimal("5000.00"));
        
        createAccount("ACC002", "Jane Smith", "jane.smith@example.com", 
                     Account.AccountType.SAVINGS, new BigDecimal("15000.00"));
        
        createAccount("ACC003", "Bob Johnson", "bob.johnson@example.com", 
                     Account.AccountType.CHECKING, new BigDecimal("3500.50"));
        
        createAccount("ACC004", "Alice Williams", "alice.williams@example.com", 
                     Account.AccountType.INVESTMENT, new BigDecimal("25000.00"));
        
        createAccount("ACC005", "Charlie Brown", "charlie.brown@example.com", 
                     Account.AccountType.SAVINGS, new BigDecimal("8750.25"));

        log.info("Database initialization completed. Created {} accounts.", 
                accountRepository.count());
    }

    private void createAccount(String accountNumber, String customerName, String email,
                              Account.AccountType accountType, BigDecimal balance) {
        Account account = new Account();
        account.setAccountNumber(accountNumber);
        account.setCustomerName(customerName);
        account.setEmail(email);
        account.setAccountType(accountType);
        account.setBalance(balance);
        account.setCurrency("USD");
        account.setStatus(Account.AccountStatus.ACTIVE);
        account.setCreatedAt(LocalDateTime.now());
        account.setUpdatedAt(LocalDateTime.now());
        
        accountRepository.save(account);
        log.debug("Created account: {} for {}", accountNumber, customerName);
    }
}

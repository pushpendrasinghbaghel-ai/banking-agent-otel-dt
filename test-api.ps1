# Banking AI Agent - API Testing Script
# Run this script to test all endpoints

Write-Host "==================================" -ForegroundColor Cyan
Write-Host "Banking AI Agent - API Testing" -ForegroundColor Cyan
Write-Host "==================================" -ForegroundColor Cyan
Write-Host ""

$baseUrl = "http://localhost:8080"

# Check if server is running
Write-Host "1. Checking if server is running..." -ForegroundColor Yellow
try {
    $health = Invoke-RestMethod -Uri "$baseUrl/api/agent/health" -Method Get
    Write-Host "✓ Server is running!" -ForegroundColor Green
    Write-Host "  Status: $($health.status)" -ForegroundColor Gray
    Write-Host ""
} catch {
    Write-Host "✗ Server is not running. Start it with: mvn spring-boot:run" -ForegroundColor Red
    exit
}

# Check available providers
Write-Host "2. Checking available LLM providers..." -ForegroundColor Yellow
try {
    $providers = Invoke-RestMethod -Uri "$baseUrl/api/agent/providers" -Method Get
    Write-Host "✓ Available providers:" -ForegroundColor Green
    $providers.PSObject.Properties | ForEach-Object {
        $status = if ($_.Value) { "✓ Available" } else { "✗ Not configured" }
        $color = if ($_.Value) { "Green" } else { "Red" }
        Write-Host "  $($_.Name): $status" -ForegroundColor $color
    }
    Write-Host ""
} catch {
    Write-Host "✗ Failed to check providers" -ForegroundColor Red
}

# Create test accounts
Write-Host "3. Creating test accounts..." -ForegroundColor Yellow
$account1 = @{
    accountNumber = "ACC001"
    customerName = "John Doe"
    email = "john@example.com"
    accountType = "CHECKING"
    balance = 1000.00
    currency = "USD"
} | ConvertTo-Json

$account2 = @{
    accountNumber = "ACC002"
    customerName = "Jane Smith"
    email = "jane@example.com"
    accountType = "SAVINGS"
    balance = 5000.00
    currency = "USD"
} | ConvertTo-Json

try {
    $acc1 = Invoke-RestMethod -Uri "$baseUrl/api/accounts" -Method Post -Body $account1 -ContentType "application/json"
    Write-Host "✓ Created account: $($acc1.accountNumber) - $($acc1.customerName)" -ForegroundColor Green
} catch {
    Write-Host "  Account ACC001 might already exist (this is ok)" -ForegroundColor Gray
}

try {
    $acc2 = Invoke-RestMethod -Uri "$baseUrl/api/accounts" -Method Post -Body $account2 -ContentType "application/json"
    Write-Host "✓ Created account: $($acc2.accountNumber) - $($acc2.customerName)" -ForegroundColor Green
} catch {
    Write-Host "  Account ACC002 might already exist (this is ok)" -ForegroundColor Gray
}
Write-Host ""

# View accounts
Write-Host "4. Viewing all accounts..." -ForegroundColor Yellow
try {
    $accounts = Invoke-RestMethod -Uri "$baseUrl/api/accounts" -Method Get
    Write-Host "✓ Found $($accounts.Count) account(s):" -ForegroundColor Green
    $accounts | ForEach-Object {
        Write-Host "  - $($_.accountNumber): $($_.customerName), Balance: $($_.balance) $($_.currency)" -ForegroundColor Gray
    }
    Write-Host ""
} catch {
    Write-Host "✗ Failed to retrieve accounts" -ForegroundColor Red
}

# Make a deposit
Write-Host "5. Making a deposit..." -ForegroundColor Yellow
$depositRequest = @{
    accountNumber = "ACC001"
    amount = 500.00
    description = "Salary deposit"
} | ConvertTo-Json

try {
    $deposit = Invoke-RestMethod -Uri "$baseUrl/api/transactions/deposit" -Method Post -Body $depositRequest -ContentType "application/json"
    Write-Host "✓ Deposit successful:" -ForegroundColor Green
    Write-Host "  Amount: $($deposit.amount) $($deposit.currency)" -ForegroundColor Gray
    Write-Host "  New Balance: $($deposit.balanceAfter)" -ForegroundColor Gray
    Write-Host ""
} catch {
    Write-Host "✗ Deposit failed: $($_.Exception.Message)" -ForegroundColor Red
}

# Make a withdrawal
Write-Host "6. Making a withdrawal..." -ForegroundColor Yellow
$withdrawRequest = @{
    accountNumber = "ACC001"
    amount = 100.00
    description = "ATM withdrawal"
} | ConvertTo-Json

try {
    $withdraw = Invoke-RestMethod -Uri "$baseUrl/api/transactions/withdraw" -Method Post -Body $withdrawRequest -ContentType "application/json"
    Write-Host "✓ Withdrawal successful:" -ForegroundColor Green
    Write-Host "  Amount: $($withdraw.amount) $($withdraw.currency)" -ForegroundColor Gray
    Write-Host "  New Balance: $($withdraw.balanceAfter)" -ForegroundColor Gray
    Write-Host ""
} catch {
    Write-Host "✗ Withdrawal failed: $($_.Exception.Message)" -ForegroundColor Red
}

# Transfer money
Write-Host "7. Transferring money between accounts..." -ForegroundColor Yellow
$transferRequest = @{
    fromAccount = "ACC001"
    toAccount = "ACC002"
    amount = 250.00
    description = "Transfer to savings"
} | ConvertTo-Json

try {
    $transfer = Invoke-RestMethod -Uri "$baseUrl/api/transactions/transfer" -Method Post -Body $transferRequest -ContentType "application/json"
    Write-Host "✓ Transfer successful:" -ForegroundColor Green
    Write-Host "  Amount: $($transfer.amount) $($transfer.currency)" -ForegroundColor Gray
    Write-Host "  From: $($transfer.accountNumber) to $($transfer.destinationAccount)" -ForegroundColor Gray
    Write-Host ""
} catch {
    Write-Host "✗ Transfer failed: $($_.Exception.Message)" -ForegroundColor Red
}

# View transaction history
Write-Host "8. Viewing transaction history for ACC001..." -ForegroundColor Yellow
try {
    $transactions = Invoke-RestMethod -Uri "$baseUrl/api/transactions/account/ACC001" -Method Get
    Write-Host "✓ Found $($transactions.Count) transaction(s):" -ForegroundColor Green
    $transactions | Select-Object -First 5 | ForEach-Object {
        Write-Host "  - $($_.type): $($_.amount) $($_.currency) - $($_.description)" -ForegroundColor Gray
    }
    Write-Host ""
} catch {
    Write-Host "✗ Failed to retrieve transactions" -ForegroundColor Red
}

# Test AI Agent - Check Balance
Write-Host "9. Testing AI Agent - Check Balance..." -ForegroundColor Yellow
$balanceQuery = @{
    accountNumber = "ACC001"
    query = "What is my current balance?"
} | ConvertTo-Json

try {
    $response = Invoke-RestMethod -Uri "$baseUrl/api/agent/query" -Method Post -Body $balanceQuery -ContentType "application/json"
    Write-Host "✓ AI Response:" -ForegroundColor Green
    Write-Host "  $($response.message)" -ForegroundColor Cyan
    Write-Host "  Provider: $($response.llmProvider)" -ForegroundColor Gray
    Write-Host ""
} catch {
    Write-Host "✗ AI query failed. Make sure you have configured an LLM provider (OpenAI API key or Ollama running)" -ForegroundColor Red
    Write-Host "  Error: $($_.Exception.Message)" -ForegroundColor Gray
    Write-Host ""
}

# Test AI Agent - View Transactions
Write-Host "10. Testing AI Agent - View Transactions..." -ForegroundColor Yellow
$transactionQuery = @{
    accountNumber = "ACC001"
    query = "Show me my recent transactions"
} | ConvertTo-Json

try {
    $response = Invoke-RestMethod -Uri "$baseUrl/api/agent/query" -Method Post -Body $transactionQuery -ContentType "application/json"
    Write-Host "✓ AI Response:" -ForegroundColor Green
    Write-Host "  $($response.message)" -ForegroundColor Cyan
    Write-Host ""
} catch {
    Write-Host "✗ AI query failed" -ForegroundColor Red
}

# Test AI Agent - General Inquiry
Write-Host "11. Testing AI Agent - General Banking Question..." -ForegroundColor Yellow
$generalQuery = @{
    query = "What are the benefits of a savings account?"
} | ConvertTo-Json

try {
    $response = Invoke-RestMethod -Uri "$baseUrl/api/agent/query" -Method Post -Body $generalQuery -ContentType "application/json"
    Write-Host "✓ AI Response:" -ForegroundColor Green
    Write-Host "  $($response.message)" -ForegroundColor Cyan
    Write-Host ""
} catch {
    Write-Host "✗ AI query failed" -ForegroundColor Red
}

Write-Host "==================================" -ForegroundColor Cyan
Write-Host "Testing Complete!" -ForegroundColor Cyan
Write-Host "==================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "Next steps:" -ForegroundColor Yellow
Write-Host "- View H2 Console: http://localhost:8080/h2-console" -ForegroundColor Gray
Write-Host "- Try different queries with the AI agent" -ForegroundColor Gray
Write-Host "- Test with Ollama: Use /api/agent/query/ollama endpoint" -ForegroundColor Gray
Write-Host ""

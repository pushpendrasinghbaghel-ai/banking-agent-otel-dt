#!/bin/bash
# Banking AI Agent - API Testing Script (Bash/curl version)

BASE_URL="http://localhost:8080"

echo "=================================="
echo "Banking AI Agent - API Testing"
echo "=================================="
echo ""

# Check if server is running
echo "1. Checking if server is running..."
curl -s "$BASE_URL/api/agent/health" | grep -q "UP" && echo "✓ Server is running!" || echo "✗ Server not running"
echo ""

# Check available providers
echo "2. Checking available LLM providers..."
curl -s "$BASE_URL/api/agent/providers"
echo ""
echo ""

# Create test accounts
echo "3. Creating test accounts..."
curl -X POST "$BASE_URL/api/accounts" \
  -H "Content-Type: application/json" \
  -d '{
    "accountNumber": "ACC001",
    "customerName": "John Doe",
    "email": "john@example.com",
    "accountType": "CHECKING",
    "balance": 1000.00,
    "currency": "USD"
  }'
echo ""

curl -X POST "$BASE_URL/api/accounts" \
  -H "Content-Type: application/json" \
  -d '{
    "accountNumber": "ACC002",
    "customerName": "Jane Smith",
    "email": "jane@example.com",
    "accountType": "SAVINGS",
    "balance": 5000.00,
    "currency": "USD"
  }'
echo ""
echo ""

# View all accounts
echo "4. Viewing all accounts..."
curl -s "$BASE_URL/api/accounts" | jq '.'
echo ""

# Make a deposit
echo "5. Making a deposit..."
curl -X POST "$BASE_URL/api/transactions/deposit" \
  -H "Content-Type: application/json" \
  -d '{
    "accountNumber": "ACC001",
    "amount": 500.00,
    "description": "Salary deposit"
  }' | jq '.'
echo ""

# Make a withdrawal
echo "6. Making a withdrawal..."
curl -X POST "$BASE_URL/api/transactions/withdraw" \
  -H "Content-Type: application/json" \
  -d '{
    "accountNumber": "ACC001",
    "amount": 100.00,
    "description": "ATM withdrawal"
  }' | jq '.'
echo ""

# Transfer money
echo "7. Transferring money..."
curl -X POST "$BASE_URL/api/transactions/transfer" \
  -H "Content-Type: application/json" \
  -d '{
    "fromAccount": "ACC001",
    "toAccount": "ACC002",
    "amount": 250.00,
    "description": "Transfer to savings"
  }' | jq '.'
echo ""

# View transaction history
echo "8. Viewing transaction history..."
curl -s "$BASE_URL/api/transactions/account/ACC001" | jq '.'
echo ""

# Test AI Agent - Check Balance
echo "9. Testing AI Agent - Check Balance..."
curl -X POST "$BASE_URL/api/agent/query" \
  -H "Content-Type: application/json" \
  -d '{
    "accountNumber": "ACC001",
    "query": "What is my current balance?"
  }' | jq '.'
echo ""

# Test AI Agent - View Transactions
echo "10. Testing AI Agent - View Transactions..."
curl -X POST "$BASE_URL/api/agent/query" \
  -H "Content-Type: application/json" \
  -d '{
    "accountNumber": "ACC001",
    "query": "Show me my recent transactions"
  }' | jq '.'
echo ""

# Test AI Agent - General Question
echo "11. Testing AI Agent - General Question..."
curl -X POST "$BASE_URL/api/agent/query" \
  -H "Content-Type: application/json" \
  -d '{
    "query": "What are the benefits of a savings account?"
  }' | jq '.'
echo ""

echo "=================================="
echo "Testing Complete!"
echo "=================================="

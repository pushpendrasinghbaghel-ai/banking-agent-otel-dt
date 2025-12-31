# Dynatrace OTLP Configuration Script
# =====================================
# This script sets up environment variables for Dynatrace integration

Write-Host "=== Dynatrace OTLP Configuration ===" -ForegroundColor Cyan
Write-Host ""

# Prompt for Dynatrace Environment ID
$environmentId = Read-Host "Enter your Dynatrace Environment ID (e.g., abc12345)"

# Prompt for Dynatrace API Token
$apiToken = Read-Host "Enter your Dynatrace API Token (starts with dt0c01.)" -AsSecureString
$apiTokenPlain = [Runtime.InteropServices.Marshal]::PtrToStringAuto([Runtime.InteropServices.Marshal]::SecureStringToBSTR($apiToken))

# Determine the base URL
$region = Read-Host "Enter your region (live, sprint, or custom domain)"
if ($region -eq "custom") {
    $baseUrl = Read-Host "Enter your custom Dynatrace URL (e.g., https://your-domain.com)"
} else {
    $baseUrl = "https://$environmentId.$region.dynatrace.com"
}

# Set environment variables
$env:DYNATRACE_METRICS_URL = "$baseUrl/api/v2/otlp/v1/metrics"
$env:DYNATRACE_TRACE_URL = "$baseUrl/api/v2/otlp/v1/traces"
$env:DYNATRACE_API_TOKEN = $apiTokenPlain

Write-Host ""
Write-Host "=== Configuration Complete ===" -ForegroundColor Green
Write-Host "DYNATRACE_METRICS_URL: $env:DYNATRACE_METRICS_URL" -ForegroundColor Yellow
Write-Host "DYNATRACE_TRACE_URL: $env:DYNATRACE_TRACE_URL" -ForegroundColor Yellow
Write-Host "DYNATRACE_API_TOKEN: [HIDDEN]" -ForegroundColor Yellow
Write-Host ""
Write-Host "These variables are set for the current PowerShell session only." -ForegroundColor Cyan
Write-Host "To persist them, add them to your system environment variables or .env file." -ForegroundColor Cyan
Write-Host ""
Write-Host "Now you can run: mvn spring-boot:run" -ForegroundColor Green

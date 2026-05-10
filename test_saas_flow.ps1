# ChitChat SaaS Flow Test Script
$BASE_URL = "http://localhost:8080/api"
$TENANT_SLUG = "test_workspace_" + (Get-Date -Format "HHmmss")
$USERNAME = "testuser_" + (Get-Date -Format "HHmmss")
$PASSWORD = "password123"

Write-Host "1. Registering User: $USERNAME" -ForegroundColor Cyan
$regBody = @{
    username = $USERNAME
    displayName = "Test User"
    password = $PASSWORD
} | ConvertTo-Json
$regResp = Invoke-RestMethod -Uri "$BASE_URL/auth/register" -Method Post -Body $regBody -ContentType "application/json"
Write-Host "Register Success"

Write-Host "`n2. Logging In..." -ForegroundColor Cyan
$loginBody = @{
    username = $USERNAME
    password = $PASSWORD
} | ConvertTo-Json
$authResp = Invoke-RestMethod -Uri "$BASE_URL/auth/login" -Method Post -Body $loginBody -ContentType "application/json"
$token = $authResp.token
Write-Host "Login Success. Token acquired."
$tenantId = $authResp.currentTenantId
$tenantSlug = $authResp.tenants[0].slug

$headers = @{
    "Authorization" = "Bearer $token"
    "X-Tenant-Id" = $tenantId
}

Write-Host "Using Workspace: $tenantId ($tenantSlug)" -ForegroundColor Cyan

Write-Host "`n4. Creating Conversation..." -ForegroundColor Cyan
# Create a group conversation with just the creator
$convBody = "[]" # Empty list of additional members
$convResp = Invoke-RestMethod -Uri "$BASE_URL/conversations/group?name=TestGroup" -Method Post -Body $convBody -ContentType "application/json" -Headers $headers
$convId = $convResp.id
Write-Host "Conversation Created: $convId"

Write-Host "`n5. Sending Test Messages..." -ForegroundColor Cyan
$msg1 = @{ content = "Hello world" } | ConvertTo-Json
$msg2 = @{ content = "ChitChat is cool" } | ConvertTo-Json
$msg3 = @{ content = "Secret message" } | ConvertTo-Json

Invoke-RestMethod -Uri "$BASE_URL/messages/$convId" -Method Post -Body $msg1 -ContentType "application/json" -Headers $headers > $null
Invoke-RestMethod -Uri "$BASE_URL/messages/$convId" -Method Post -Body $msg2 -ContentType "application/json" -Headers $headers > $null
Invoke-RestMethod -Uri "$BASE_URL/messages/$convId" -Method Post -Body $msg3 -ContentType "application/json" -Headers $headers > $null
Write-Host "Messages Sent."

Write-Host "`n6. Testing Search (In Conversation)..." -ForegroundColor Cyan
$searchResp = Invoke-RestMethod -Uri "$BASE_URL/messages/$convId/search?query=cool" -Method Get -Headers $headers
Write-Host "Found $($searchResp.Count) messages for query 'cool'"
$searchResp | ConvertTo-Json

Write-Host "`n7. Testing Search (Across All Conversations)..." -ForegroundColor Cyan
$searchAllResp = Invoke-RestMethod -Uri "$BASE_URL/messages/search?query=secret" -Method Get -Headers $headers
Write-Host "Found $($searchAllResp.Count) messages for query 'secret'"
$searchAllResp | ConvertTo-Json

Write-Host "`nTest Complete!" -ForegroundColor Green

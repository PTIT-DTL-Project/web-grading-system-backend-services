param(
    [Parameter(Position=0)]
    [ValidateSet("dev", "local")]
    [string]$TargetEnv = "dev"
)

Write-Host "Switching environment to: $TargetEnv..." -ForegroundColor Cyan

# 1. Copy root .env
if (Test-Path ".env.$TargetEnv") {
    Copy-Item ".env.$TargetEnv" ".env" -Force
    Write-Host "  [OK] Root: .env.$TargetEnv -> .env" -ForegroundColor Green
}

# 2. Copy each service's .env
$services = @("api-gateway", "course-service", "submission-service", "result-service", "executor-service", "ai-assistant-service")
foreach ($svc in $services) {
    $src = "$svc\.env.$TargetEnv"
    $dst = "$svc\.env"
    if (Test-Path $src) {
        Copy-Item $src $dst -Force
        Write-Host "  [OK] ${svc}: .env.$TargetEnv -> .env" -ForegroundColor Green
    }
}

Write-Host "`nEnvironment successfully switched to [$TargetEnv]!" -ForegroundColor Yellow
if ($TargetEnv -eq "dev") {
    Write-Host "Run: docker compose up -d" -ForegroundColor White
} else {
    Write-Host "Run:`n  docker compose -f docker-compose.deps.yml up -d`n  docker compose up -d" -ForegroundColor White
}

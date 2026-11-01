param(
    [Parameter(Mandatory=$true, Position=0)]
    [ValidateSet("course-service", "submission-service", "executor-service", "result-service", "api-gateway", "ai-assistant-service")]
    [string]$ServiceName
)

$rootDir = $PSScriptRoot

# 1. Load root .env
$rootEnv = Join-Path $rootDir ".env"
if (Test-Path $rootEnv) {
    Get-Content $rootEnv | ForEach-Object {
        $line = $_.Trim()
        if ($line -and -not $line.StartsWith("#") -and $line -match '^([^=]+)=(.*)$') {
            [System.Environment]::SetEnvironmentVariable($matches[1].Trim(), $matches[2].Trim(), "Process")
        }
    }
}

# 2. Load service .env
$serviceEnv = Join-Path $rootDir "$ServiceName\.env"
if (Test-Path $serviceEnv) {
    Get-Content $serviceEnv | ForEach-Object {
        $line = $_.Trim()
        if ($line -and -not $line.StartsWith("#") -and $line -match '^([^=]+)=(.*)$') {
            [System.Environment]::SetEnvironmentVariable($matches[1].Trim(), $matches[2].Trim(), "Process")
        }
    }
}

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host " Loaded .env for [$ServiceName]" -ForegroundColor Green
Write-Host " DB_USERNAME : $env:DB_USERNAME" -ForegroundColor Gray
Write-Host " DB_NAME     : $env:DB_NAME" -ForegroundColor Gray
Write-Host " JDBC_URL    : $env:JDBC_URL" -ForegroundColor Gray
Write-Host "==========================================================" -ForegroundColor Cyan

Set-Location (Join-Path $rootDir $ServiceName)
mvn spring-boot:run

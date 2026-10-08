# Starts the ChitChat backend (Spring Boot) from the repo root.
# Reads .env if present; falls back to a dev JWT_SECRET so it always boots.
$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

if (Test-Path .env) {
    foreach ($line in Get-Content .env) {
        if ($line -match "^[^#].+=.*") {
            $parts = $line.Split('=', 2)
            [System.Environment]::SetEnvironmentVariable($parts[0].Trim(), $parts[1].Trim())
        }
    }
}

if (-not $env:JWT_SECRET -or $env:JWT_SECRET.Length -lt 32) {
    $env:JWT_SECRET = "dev-secret-key-at-least-32-characters-long"
}

mvn spring-boot:run

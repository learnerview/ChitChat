# Starts the ChitChat frontend dev server (Vite, http://localhost:5173).
# Proxies /api and /ws to the backend on :8080 — start start_server.ps1 first.
$ErrorActionPreference = "Stop"
Set-Location "$PSScriptRoot\frontend"
npm run dev

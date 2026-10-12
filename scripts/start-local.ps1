param([switch]$Build)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
Push-Location $projectRoot
try {
    $composeArgs = @('compose','-p','signal-iptv','-f','deploy/docker/compose.yaml','up','-d')
    if ($Build) { $composeArgs += '--build' }
    & docker @composeArgs
    if ($LASTEXITCODE -ne 0) { throw 'Docker startup failed. For the first build, use -Build.' }
    $ready = $false
    for ($attempt = 0; $attempt -lt 20; $attempt++) {
        try { $response = Invoke-RestMethod 'http://127.0.0.1:8580/healthz'; if ($response.status -eq 'ok') {$ready=$true; break} } catch {}
        Start-Sleep -Milliseconds 500
    }
    if (-not $ready) { throw 'Server did not become ready.' }
    Write-Output 'VynodeIPTV server: http://localhost:8580'
    Write-Output 'Create or sign in to your administrator account in the WebUI.'
} finally { Pop-Location }

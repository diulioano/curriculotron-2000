param([string]$BaseUrl = 'http://127.0.0.1:8443', [string]$File = '')
$ErrorActionPreference = 'Stop'
if (-not $File) { $File = Join-Path (Split-Path $PSScriptRoot -Parent) 'specs/002-estimativa-salarial/pleno-web-observations.json' }
$sessionInfo = Invoke-RestMethod "$BaseUrl/api/session" -SessionVariable salarySession
if ($sessionInfo.authMode -ne 'local') { throw 'Use uma instância com perfil local para este script. A autenticação padrão permanece ativa.' }
$body = [IO.File]::ReadAllText([IO.Path]::GetFullPath($File))
Invoke-RestMethod "$BaseUrl/api/salary-estimates/import" -WebSession $salarySession -Method Post -ContentType 'application/json; charset=utf-8' -Headers @{'X-CSRF-Token'=$sessionInfo.csrfToken; 'Idempotency-Key'=[Guid]::NewGuid().ToString()} -Body ([Text.Encoding]::UTF8.GetBytes($body))

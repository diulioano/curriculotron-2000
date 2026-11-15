param([int]$Port = 8443, [string]$DataHome = '')
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
if (-not $env:JAVA_HOME) {
  $installedJdk = Get-ChildItem 'C:/Program Files/Eclipse Adoptium' -Directory -ErrorAction SilentlyContinue | Where-Object Name -like 'jdk-25*' | Select-Object -First 1
  if ($installedJdk) { $env:JAVA_HOME = $installedJdk.FullName }
}
if ($DataHome) { $env:CURRICULOTRON_HOME = [IO.Path]::GetFullPath($DataHome) }
& (Join-Path $projectRoot 'backend/gradlew.bat') -p (Join-Path $projectRoot 'backend') bootRun "--args=--spring.profiles.active=local --server.port=$Port"
exit $LASTEXITCODE

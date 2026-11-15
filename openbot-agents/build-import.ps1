# Gera o zip de importação de agentes do OpenBot (Server settings -> Import -> Hermes Agent).
# Uso: powershell -File openbot-agents\build-import.ps1
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression, System.IO.Compression.FileSystem

$root = $PSScriptRoot
$utf8 = New-Object System.Text.UTF8Encoding($false)
$manifestText = [IO.File]::ReadAllText((Join-Path $root 'openbot-import.json'), $utf8)
$memories = [IO.File]::ReadAllText((Join-Path $root 'shared\memories.json'), $utf8).Trim()
$manifestText = $manifestText.Replace('"__SHARED_MEMORIES__"', $memories)
$manifest = $manifestText | ConvertFrom-Json

# Validação das regras do formato
$keys = @{}
foreach ($a in $manifest.agents) {
    if ($a.key -notmatch '^[a-z0-9]([a-z0-9-]{0,62}[a-z0-9])?$') { throw "key inválida: $($a.key)" }
    if ($keys.ContainsKey($a.key)) { throw "key duplicada: $($a.key)" }
    $keys[$a.key] = $true
    if ($a.name.Length -gt 80) { throw "$($a.key): name > 80" }
    if ($a.title.Length -gt 120) { throw "$($a.key): title > 120" }
    if ($a.description.Length -gt 2000) { throw "$($a.key): description > 2000 ($($a.description.Length))" }
    if (@($a.memories).Count -gt 64) { throw "$($a.key): > 64 memórias" }
    foreach ($m in $a.memories) { if ($m.Length -gt 500) { throw "$($a.key): memória > 500: $m" } }
    if (@($a.skills).Count -gt 32) { throw "$($a.key): > 32 skills" }
    foreach ($r in $a.routines) { if ($r.name.Length -gt 80) { throw "$($a.key): routine name > 80" } }
}

$entries = [ordered]@{}
foreach ($a in $manifest.agents) {
    foreach ($skillPath in $a.skills) {
        if (-not $skillPath.StartsWith("agents/$($a.key)/")) { throw "caminho fora do agente: $skillPath" }
        $name = Split-Path $skillPath -Leaf
        $own = Join-Path $root ($skillPath -replace '/', '\')
        $shared = Join-Path $root "shared\$name"
        $src = if (Test-Path (Join-Path $own 'SKILL.md')) { $own } elseif (Test-Path (Join-Path $shared 'SKILL.md')) { $shared } else { throw "skill não encontrada: $skillPath" }
        foreach ($f in Get-ChildItem $src -Recurse -File) {
            $rel = $f.FullName.Substring($src.Length).TrimStart('\') -replace '\\', '/'
            $entries["$skillPath/$rel"] = $f.FullName
        }
    }
}

$outDir = Join-Path $env:USERPROFILE 'Downloads'
$date = Get-Date -Format 'yyyy-MM-dd'
$final = Join-Path $outDir "openbot-import-curriculotron-$date.zip"
$part = "$final.part"
if (Test-Path $part) { Remove-Item $part -Force }

$fs = [IO.File]::Open($part, [IO.FileMode]::CreateNew)
$zip = New-Object IO.Compression.ZipArchive($fs, [IO.Compression.ZipArchiveMode]::Create)
try {
    foreach ($k in $entries.Keys) {
        [void][IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip, $entries[$k], $k)
    }
    $e = $zip.CreateEntry('openbot-import.json')
    $w = New-Object IO.StreamWriter($e.Open(), $utf8)
    $w.Write($manifestText)
    $w.Dispose()
} finally { $zip.Dispose(); $fs.Dispose() }

Move-Item $part $final -Force
Write-Output "Gerado: $final ($($manifest.agents.Count) agentes, $($entries.Count) arquivos de skill)"

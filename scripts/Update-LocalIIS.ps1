param([string]$PublishedFolder = '', [string]$LogFile = '')
$ErrorActionPreference = 'Stop'
$identity = [Security.Principal.WindowsIdentity]::GetCurrent()
$principal = [Security.Principal.WindowsPrincipal]::new($identity)
if (-not $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
    throw 'Run PowerShell as Administrator, then run this script.'
}
if ($LogFile) { Start-Transcript -Path $LogFile -Force | Out-Null }
$projectRoot = Split-Path $PSScriptRoot
if (-not $PublishedFolder) {
    $PublishedFolder = Join-Path $projectRoot 'SolarGridX/bin/Release/net10.0/publish'
    & dotnet publish (Join-Path $projectRoot 'SolarGridX/SolarGridX.csproj') -c Release -o $PublishedFolder
    if ($LASTEXITCODE -ne 0) { throw 'Backend build failed; IIS was not changed.' }
}
if (-not (Test-Path -LiteralPath (Join-Path $PublishedFolder 'SolarGridX.dll'))) { throw 'Published backend is missing.' }
$publishRoot = [IO.Path]::GetFullPath($PublishedFolder).TrimEnd([IO.Path]::DirectorySeparatorChar)
$siteRoot = [IO.Path]::GetFullPath('C:/inetpub/SolarGridX')
$backupRoot = Join-Path $projectRoot ('iis-backups/' + [Guid]::NewGuid().ToString('N'))
$offlineFile = Join-Path $siteRoot 'app_offline.htm'
if (-not (Test-Path -LiteralPath $siteRoot)) { throw 'Expected local IIS folder does not exist.' }
if (Test-Path -LiteralPath $offlineFile) { throw 'Another deployment is in progress.' }
$files = Get-ChildItem -LiteralPath $PublishedFolder -File -Recurse |
    Where-Object { $_.Name -notlike 'appsettings*.json' -and $_.Name -ne 'web.config' }
$plan = @(foreach ($file in $files) {
    $relative = $file.FullName.Substring($publishRoot.Length + 1)
    $destination = [IO.Path]::GetFullPath((Join-Path $siteRoot $relative))
    if (-not $destination.StartsWith($siteRoot + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) { throw 'Invalid deployment path.' }
    [pscustomobject]@{ Source = $file.FullName; Destination = $destination; Backup = (Join-Path $backupRoot $relative); Existed = (Test-Path -LiteralPath $destination) }
})
foreach ($entry in $plan) {
    if ($entry.Existed) {
        New-Item -ItemType Directory -Path (Split-Path $entry.Backup) -Force | Out-Null
        Copy-Item -LiteralPath $entry.Destination -Destination $entry.Backup
    }
}
Set-Content -LiteralPath $offlineFile -Value '<html><body>SolarGridX is updating. Please refresh shortly.</body></html>'
Start-Sleep -Seconds 5
try {
    foreach ($entry in $plan) {
        New-Item -ItemType Directory -Path (Split-Path $entry.Destination) -Force | Out-Null
        Copy-Item -LiteralPath $entry.Source -Destination $entry.Destination -Force
    }
} catch {
    foreach ($entry in $plan) {
        if ($entry.Existed) { Copy-Item -LiteralPath $entry.Backup -Destination $entry.Destination -Force }
        elseif (Test-Path -LiteralPath $entry.Destination) { Remove-Item -LiteralPath $entry.Destination }
    }
    throw
} finally {
    Remove-Item -LiteralPath $offlineFile -ErrorAction SilentlyContinue
}
Write-Output "SolarGridX IIS backend updated. Backup: $backupRoot"
if ($LogFile) { Stop-Transcript | Out-Null }

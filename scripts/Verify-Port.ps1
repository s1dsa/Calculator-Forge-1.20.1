[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$CalculatorJar,
    [Parameter(Mandatory = $true)][string]$SonarCoreJar,
    [Parameter(Mandatory = $true)][string]$GameTestXml,
    [Parameter(Mandatory = $true)][DateTimeOffset]$NotBefore,
    [string]$Python = '',
    [string]$Evidence = ''
)
$ErrorActionPreference = 'Stop'
$Project = Split-Path $PSScriptRoot -Parent
if (-not $Python) {
    $Bundled = Join-Path (Split-Path $Project -Parent) '.tools/python-3.13.7/python.exe'
    if (Test-Path $Bundled) { $Python = $Bundled } else { $Python = 'python' }
}
# This script intentionally never invokes Gradle. The coordinator builds and runs
# the real server first, capturing its start time and XML report location.
$Failed = $false
& $Python (Join-Path $Project 'tools/validate_package.py') --calculator-jar $CalculatorJar --sonarcore-jar $SonarCoreJar
if ($LASTEXITCODE -ne 0) { $Failed = $true }
& $Python (Join-Path $Project 'tools/validate_runtime.py') --junit $GameTestXml --not-before $NotBefore.ToString('o')
if ($LASTEXITCODE -ne 0) { $Failed = $true }
$AuditArgs = @((Join-Path $Project 'tools/audit_port.py'))
if ($Evidence) { $AuditArgs += @('--evidence', $Evidence) }
& $Python @AuditArgs
if ($LASTEXITCODE -ne 0) { $Failed = $true }
if ($Failed) {
    Write-Host 'FAIL: packaging/runtime checks or strict parity audit remain incomplete. Inspect build/reports and audit/port-report.json.'
    exit 1
}
Write-Host 'All validation commands passed. Review the hash-bound behavioral evidence before claiming full parity.'
exit 0

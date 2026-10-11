[CmdletBinding()]
param([ValidateSet('calculator', 'sonarcore')][string]$Module = 'calculator', [switch]$UnitOnly)
$Tasks = @('check')
if (-not $UnitOnly) { $Tasks += ':' + $Module + ':runGameTestServer' }
# Forge deliberately fails runGameTestServer when no GameTests have been registered.
& (Join-Path $PSScriptRoot 'Invoke-Gradle.ps1') -Tasks $Tasks -LogName 'test'
exit $LASTEXITCODE

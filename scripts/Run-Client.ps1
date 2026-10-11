[CmdletBinding()]
param([ValidateSet('calculator', 'sonarcore')][string]$Module = 'calculator')
& (Join-Path $PSScriptRoot 'Invoke-Gradle.ps1') -Tasks @(':' + $Module + ':runClient') -LogName 'client'
exit $LASTEXITCODE

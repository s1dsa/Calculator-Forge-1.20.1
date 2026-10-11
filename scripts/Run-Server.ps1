[CmdletBinding()]
param([ValidateSet('calculator', 'sonarcore')][string]$Module = 'calculator')
# The server's first run creates eula.txt. Review it and accept it explicitly before playing.
& (Join-Path $PSScriptRoot 'Invoke-Gradle.ps1') -Tasks @(':' + $Module + ':runServer') -LogName 'server'
exit $LASTEXITCODE

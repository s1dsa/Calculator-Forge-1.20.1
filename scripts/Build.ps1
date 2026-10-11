[CmdletBinding()]
param([switch]$Clean, [switch]$CompileOnly, [switch]$PrepareOnly)
$Tasks = @()
if ($Clean) { $Tasks += 'clean' }
if ($PrepareOnly) { $Tasks += 'prepareDependencies' }
elseif ($CompileOnly) { $Tasks += 'compileJava' }
else { $Tasks += 'build' }
& (Join-Path $PSScriptRoot 'Invoke-Gradle.ps1') -Tasks $Tasks -LogName 'build'
exit $LASTEXITCODE

[CmdletBinding()]
param(
    [string[]]$Tasks = @('build'),
    [string[]]$GradleArguments = @(),
    [string]$LogName = 'gradle'
)
$ErrorActionPreference = 'Stop'
$ProjectRoot = Split-Path -Parent $PSScriptRoot
$WorkspaceRoot = Split-Path -Parent $ProjectRoot
$JdkPath = Join-Path $WorkspaceRoot '.tools\jdk-17'
if (-not (Test-Path (Join-Path $JdkPath 'bin\java.exe'))) {
    throw "JDK 17 not found at $JdkPath"
}
$OriginalJavaHome = $env:JAVA_HOME
$OriginalGradleHome = $env:GRADLE_USER_HOME
$OriginalPath = $env:PATH
$OriginalConsoleEncoding = [Console]::OutputEncoding
$OriginalOutputEncoding = $OutputEncoding
$ExitCode = 1
try {
    [Console]::OutputEncoding = New-Object System.Text.UTF8Encoding($false)
    $OutputEncoding = [Console]::OutputEncoding
    $env:JAVA_HOME = $JdkPath
    $env:GRADLE_USER_HOME = Join-Path $WorkspaceRoot '.cache\gradle'
    $env:PATH = "$JdkPath\bin;$OriginalPath"
    $Logs = Join-Path $ProjectRoot 'logs'
    New-Item -ItemType Directory -Force -Path $Logs | Out-Null
    $LogFile = Join-Path $Logs ($LogName + '-' + (Get-Date -Format 'yyyyMMdd-HHmmss') + '.log')
    Write-Host "Gradle log: $LogFile"
    # Windows PowerShell represents native stderr as ErrorRecords; preserve output and exit code.
    $ErrorActionPreference = 'Continue'
    & (Join-Path $ProjectRoot 'gradlew.bat') -p $ProjectRoot --console=plain --stacktrace @Tasks @GradleArguments 2>&1 |
        ForEach-Object { $_.ToString() } | Tee-Object -FilePath $LogFile
    $ExitCode = $LASTEXITCODE
} finally {
    $env:JAVA_HOME = $OriginalJavaHome
    $env:GRADLE_USER_HOME = $OriginalGradleHome
    $env:PATH = $OriginalPath
    [Console]::OutputEncoding = $OriginalConsoleEncoding
    $OutputEncoding = $OriginalOutputEncoding
}
exit $ExitCode

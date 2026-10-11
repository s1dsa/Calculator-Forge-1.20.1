[CmdletBinding()]
param(
    # Dedicated-server isolation is verified WITHOUT JEI by default (-Pjei=false).
    [switch]$WithJei,
    # Hard wall-clock bound for the run, in seconds.
    [int]$Seconds = 240,
    # Optional directory to also copy the log into.
    [string]$Evidence = '',
    # Optional override for the readiness marker watched in the server log.
    [string]$ReadyMarker = 'Done \('
)
$ErrorActionPreference = 'Stop'
$Project = Split-Path $PSScriptRoot -Parent
$Workspace = Split-Path $Project -Parent
$JdkPath = Join-Path $Workspace '.tools\jdk-17'
if (-not (Test-Path (Join-Path $JdkPath 'bin\java.exe'))) { throw "JDK 17 not found at $JdkPath" }

$Variant = if ($WithJei) { 'with-jei' } else { 'no-jei' }
$Stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$EvidenceDir = if ($Evidence) { $Evidence } else { Join-Path $Project ("logs/feedback-server-$Variant-$Stamp") }
New-Item -ItemType Directory -Force -Path $EvidenceDir | Out-Null
$OutLog = Join-Path $EvidenceDir 'server.log'
$ErrLog = Join-Path $EvidenceDir 'server.err.log'

$GradleArgs = @('-p', $Project, '--console=plain', ':calculator:runServer')
if (-not $WithJei) { $GradleArgs += '-Pjei=false' }

$OriginalJavaHome = $env:JAVA_HOME
$OriginalGradleHome = $env:GRADLE_USER_HOME
$OriginalPath = $env:PATH
$env:JAVA_HOME = $JdkPath
$env:GRADLE_USER_HOME = Join-Path $Workspace '.cache\gradle'
$env:PATH = "$JdkPath\bin;$OriginalPath"

Write-Host "Feedback server run : $Variant"
Write-Host "Evidence directory  : $EvidenceDir"
Write-Host "Bound               : $Seconds s"

$Process = $null
$Ready = $false
try {
    $Process = Start-Process -FilePath (Join-Path $Project 'gradlew.bat') -ArgumentList $GradleArgs `
        -WorkingDirectory $Project -PassThru -NoNewWindow `
        -RedirectStandardOutput $OutLog -RedirectStandardError $ErrLog

    $Deadline = (Get-Date).AddSeconds($Seconds)
    while ((Get-Date) -lt $Deadline) {
        Start-Sleep -Seconds 5
        if ($Process.HasExited) { break }
        if (Test-Path $OutLog) {
            if (-not $Ready -and (Select-String -Path $OutLog -Pattern $ReadyMarker -Quiet -ErrorAction SilentlyContinue)) {
                $Ready = $true
                Write-Host "Server reported readiness ($ReadyMarker). Stopping after a short settle window."
                $Deadline = (Get-Date).AddSeconds(10)
            }
        }
    }
} finally {
    if ($Process -and -not $Process.HasExited) {
        & taskkill /PID $Process.Id /T /F 2>&1 | Out-Null
    }
    $env:JAVA_HOME = $OriginalJavaHome
    $env:GRADLE_USER_HOME = $OriginalGradleHome
    $env:PATH = $OriginalPath
}

# The dedicated server must never load a client class of this mod. Scan both streams for the failure
# signatures, and require the server to have reached its own readiness marker.
$Bad = @()
foreach ($Log in @($OutLog, $ErrLog)) {
    if (Test-Path $Log) {
        $Bad += Select-String -Path $Log -Pattern 'NoClassDefFoundError','ClassNotFoundException','net\.minecraft\.client','Failed to create mod instance','ModLoadingException: calculator' -ErrorAction SilentlyContinue
    }
}

Write-Host ''
Write-Host "Server log : $OutLog"
if ($Ready) { Write-Host 'Readiness  : reached' } else { Write-Host 'Readiness  : NOT reached within the bound' }
if ($Bad) {
    Write-Host 'Client-class / mod-load failures:'
    $Bad | ForEach-Object { Write-Host ("  {0}:{1}: {2}" -f $_.Filename, $_.LineNumber, $_.Line.Trim()) }
    Write-Host 'RESULT: FAIL - inspect the lines above.'
    exit 1
}
if (-not $Ready) {
    Write-Host 'RESULT: INCONCLUSIVE - the server never reported readiness; inspect the log.'
    exit 2
}
Write-Host 'RESULT: PASS - the dedicated server started with no client-class or mod-load failure for this variant.'
exit 0

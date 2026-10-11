[CmdletBinding()]
param(
    # Launch the client without JEI on the runtime classpath (compile classpath still has the API).
    [switch]$NoJei,
    # Hard wall-clock bound for the run, in seconds. The client is stopped once the bound is reached.
    [int]$Seconds = 300,
    # Seconds to keep the client open after it reports readiness, so screenshots can be taken.
    [int]$GraceSeconds = 45,
    # Optional directory to also copy the log and screenshots into.
    [string]$Evidence = '',
    # Optional override for the readiness marker (regex) watched in the client log.
    [string]$ReadyMarker = 'Sound engine started'
)
$ErrorActionPreference = 'Stop'
$Project = Split-Path $PSScriptRoot -Parent
$Workspace = Split-Path $Project -Parent
$JdkPath = Join-Path $Workspace '.tools\jdk-17'
if (-not (Test-Path (Join-Path $JdkPath 'bin\java.exe'))) { throw "JDK 17 not found at $JdkPath" }

$Variant = if ($NoJei) { 'no-jei' } else { 'with-jei' }
$Stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$EvidenceDir = if ($Evidence) { $Evidence } else { Join-Path $Project ("logs/feedback-client-$Variant-$Stamp") }
New-Item -ItemType Directory -Force -Path $EvidenceDir | Out-Null
$OutLog = Join-Path $EvidenceDir 'client.log'
$ErrLog = Join-Path $EvidenceDir 'client.err.log'
$ShotSource = Join-Path $Project 'calculator\run\client\screenshots'
$ShotTarget = Join-Path $EvidenceDir 'screenshots'

# This script deliberately does NOT reimplement Gradle: it drives the same gradlew wrapper the main
# flow uses, with the identical JAVA_HOME / GRADLE_USER_HOME, and only adds the -Pjei switch.
$GradleArgs = @('-p', $Project, '--console=plain', ':calculator:runClient')
if ($NoJei) { $GradleArgs += '-Pjei=false' }

$OriginalJavaHome = $env:JAVA_HOME
$OriginalGradleHome = $env:GRADLE_USER_HOME
$OriginalPath = $env:PATH
$env:JAVA_HOME = $JdkPath
$env:GRADLE_USER_HOME = Join-Path $Workspace '.cache\gradle'
$env:PATH = "$JdkPath\bin;$OriginalPath"

Write-Host "Feedback client run : $Variant"
Write-Host "Evidence directory  : $EvidenceDir"
Write-Host "Bound               : $Seconds s (grace $GraceSeconds s after '$ReadyMarker')"

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
        if (-not $Ready -and (Test-Path $OutLog)) {
            if (Select-String -Path $OutLog -Pattern $ReadyMarker -Quiet -ErrorAction SilentlyContinue) {
                $Ready = $true
                Write-Host "Client ready; keeping it open for $GraceSeconds s for screenshots."
                $Deadline = (Get-Date).AddSeconds($GraceSeconds)
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

# Collect any screenshots the client wrote (F2) so the evidence directory is self-contained.
$Shots = @()
if (Test-Path $ShotSource) {
    New-Item -ItemType Directory -Force -Path $ShotTarget | Out-Null
    $Shots = Get-ChildItem -Path $ShotSource -Filter '*.png' -File
    foreach ($Shot in $Shots) { Copy-Item $Shot.FullName -Destination $ShotTarget -Force }
}

Write-Host ''
Write-Host "Client log        : $OutLog"
Write-Host "Screenshots found : $($Shots.Count) -> $ShotTarget"
Write-Host 'Scenario checklist to review in the captured screenshots (see notes/feedback-client.md section 5b):'
@(
    '#18 creative-tab tooltip shows 强化铁块/计算器显示屏, never block.calculator.material_3',
    '#2  titles centred, energy white/shadow-less inside the bar',
    '#4  no second dark frame around item slots',
    '#3  advanced cube: GUI face buttons and the world faces agree (yellow=input, white=output, dark=disabled)',
    '#5  module workstation shows the calculator silhouette in the empty calculator slot',
    '#7  /#10 pause and eject ("移除提取物") buttons visible and functional',
    '#9  docking station background follows the mounted calculator',
    '#12 locator top panel shows the four status lines',
    '#6  reinforced chest selection box hugs the chest, lid opens smoothly',
    '#1  /#15 glass and plants render as cutout, not solid opaque planes'
) | ForEach-Object { Write-Host "  - $_" }
Write-Host ''
if ($Ready) {
    Write-Host 'RESULT: client reached readiness and was stopped within the bound. Review the screenshots/log above.'
} else {
    Write-Host 'RESULT: readiness marker was not observed within the bound. Inspect client.log/client.err.log before judging.'
}

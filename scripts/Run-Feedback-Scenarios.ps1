[CmdletBinding()]
param(
    # Which steps to run. All are on by default; switch any off to narrow the sweep.
    [switch]$SkipClientWithJei,
    [switch]$SkipClientNoJei,
    [switch]$SkipServerNoJei,
    [switch]$SkipServerWithJei,
    [int]$ClientSeconds = 300,
    [int]$ServerSeconds = 240,
    [string]$Evidence = ''
)
$ErrorActionPreference = 'Stop'
$Project = Split-Path $PSScriptRoot -Parent
$Stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$EvidenceDir = if ($Evidence) { $Evidence } else { Join-Path $Project "logs/feedback-$Stamp" }
New-Item -ItemType Directory -Force -Path $EvidenceDir | Out-Null

# This orchestrator only shells out to the other scripts; it never runs Gradle itself. The main flow
# owns Gradle execution - run this when nothing else is building the project.
$Results = [System.Collections.Generic.List[object]]::new()

Write-Host "Feedback evidence directory: $EvidenceDir"
Write-Host ''

Write-Host '--- Step 1/5: deterministic resource checks ---'
& (Join-Path $PSScriptRoot 'Validate-Feedback.ps1') -Evidence $EvidenceDir
$Results.Add([pscustomobject]@{ Step = 'validate (deterministic)'; Exit = $LASTEXITCODE })

if (-not $SkipClientWithJei) {
    Write-Host ''
    Write-Host '--- Step 2/5: client WITH JEI (bounded) ---'
    & (Join-Path $PSScriptRoot 'Run-Feedback-Client.ps1') -Seconds $ClientSeconds -Evidence (Join-Path $EvidenceDir 'client-with-jei')
    $Results.Add([pscustomobject]@{ Step = 'client with-jei'; Exit = $LASTEXITCODE })
}

if (-not $SkipClientNoJei) {
    Write-Host ''
    Write-Host '--- Step 3/5: client WITHOUT JEI (bounded, optional-dependency check) ---'
    & (Join-Path $PSScriptRoot 'Run-Feedback-Client.ps1') -NoJei -Seconds $ClientSeconds -Evidence (Join-Path $EvidenceDir 'client-no-jei')
    $Results.Add([pscustomobject]@{ Step = 'client no-jei'; Exit = $LASTEXITCODE })
}

if (-not $SkipServerNoJei) {
    Write-Host ''
    Write-Host '--- Step 4/5: dedicated server WITHOUT JEI (no client classes) ---'
    & (Join-Path $PSScriptRoot 'Run-Feedback-Server.ps1') -Seconds $ServerSeconds -Evidence (Join-Path $EvidenceDir 'server-no-jei')
    $Results.Add([pscustomobject]@{ Step = 'server no-jei'; Exit = $LASTEXITCODE })
}

if (-not $SkipServerWithJei) {
    Write-Host ''
    Write-Host '--- Step 5/5: dedicated server WITH JEI present ---'
    & (Join-Path $PSScriptRoot 'Run-Feedback-Server.ps1') -WithJei -Seconds $ServerSeconds -Evidence (Join-Path $EvidenceDir 'server-with-jei')
    $Results.Add([pscustomobject]@{ Step = 'server with-jei'; Exit = $LASTEXITCODE })
}

Write-Host ''
Write-Host '=== summary ==='
$Results | Format-Table -AutoSize | Out-String | Write-Host
Write-Host "Evidence directory: $EvidenceDir"
$Failed = @($Results | Where-Object { $_.Exit -ne 0 })
if ($Failed.Count -gt 0) {
    Write-Host ("RESULT: {0} step(s) did not exit 0: {1}" -f $Failed.Count, ($Failed.Step -join ', '))
    exit 1
}
Write-Host 'RESULT: all executed steps exited 0. Visual verdicts still require reviewing the captured screenshots.'

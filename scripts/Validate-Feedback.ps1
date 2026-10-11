[CmdletBinding()]
param(
    # Python interpreter; defaults to the bundled one.
    [string]$Python = '',
    # Optional directory to also copy the evidence log into.
    [string]$Evidence = ''
)
$ErrorActionPreference = 'Stop'
$Project = Split-Path $PSScriptRoot -Parent
$Workspace = Split-Path $Project -Parent
if (-not $Python) {
    $Bundled = Join-Path $Workspace '.tools/python-3.13.7/python.exe'
    if (Test-Path $Bundled) { $Python = $Bundled } else { $Python = 'python' }
}

# This script is deterministic and offline: it reads the repository's own sources, models, textures,
# language files and data packs and asserts the feedback fixes are actually present. It NEVER runs
# Gradle, launches a client/server or fabricates a screenshot, so a PASS here is only ever backed by
# the evidence printed below (also written to logs/validate-feedback-<stamp>.log).
$Logs = Join-Path $Project 'logs'
New-Item -ItemType Directory -Force -Path $Logs | Out-Null
$Stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$LogFile = Join-Path $Logs ("validate-feedback-$Stamp.log")

$Failed = $false
function Write-Log([string]$Text) {
    Write-Host $Text
    Add-Content -Path $LogFile -Value $Text -Encoding UTF8
}
function Invoke-Check([string]$Name, [string]$RelativeScript) {
    Write-Log ''
    Write-Log ("=== {0} :: {1}" -f $Name, $RelativeScript)
    $Script = Join-Path $Project $RelativeScript
    if (-not (Test-Path $Script)) {
        Write-Log "FAIL: missing check script $RelativeScript"
        $script:Failed = $true
        return
    }
    $Output = & $Python $Script 2>&1
    $Code = $LASTEXITCODE
    foreach ($Line in $Output) { Write-Log ([string]$Line) }
    if ($Code -ne 0) {
        Write-Log "FAIL: $RelativeScript exited $Code"
        $script:Failed = $true
    } else {
        Write-Log "PASS: $RelativeScript exited 0"
    }
}

Write-Log "Validate-Feedback :: deterministic feedback resource checks"
Write-Log "project : $Project"
Write-Log "python  : $Python"
Write-Log "log     : $LogFile"

Invoke-Check 'Render layers for #1/#6/#10/#13/#15 (cutout wiring vs texture evidence)' 'tools/audit_render_layers.py'
Invoke-Check 'Feedback item prerequisites #1..#19' 'tools/audit_feedback_19.py'
Invoke-Check 'Translation key coverage #18 (all four languages)' 'tools/audit_client_keys.py'
Invoke-Check 'JEI display wiring #19' 'tools/check_jei_display.py'

# The hardcoded-English scanner prints one line per offender and always exits 0, so its stdout is the
# evidence: any offender means #18 is incomplete.
Write-Log ''
Write-Log '=== Hardcoded English scan #18 (tools/scan_hardcoded_en.py)'
$ScanOutput = & $Python (Join-Path $Project 'tools/scan_hardcoded_en.py') 2>&1
foreach ($Line in $ScanOutput) { Write-Log ([string]$Line) }
if ($ScanOutput) {
    Write-Log "FAIL: user-visible hardcoded English literals remain"
    $Failed = $true
} else {
    Write-Log 'PASS: no user-visible hardcoded English literals found'
}

if ($Evidence) {
    New-Item -ItemType Directory -Force -Path $Evidence | Out-Null
    Copy-Item -Path $LogFile -Destination (Join-Path $Evidence (Split-Path $LogFile -Leaf)) -Force
}

Write-Log ''
if ($Failed) {
    Write-Log 'RESULT: FAIL - at least one deterministic feedback resource check failed. See the log above.'
    exit 1
}
Write-Log 'RESULT: PASS - every deterministic feedback resource check passed (evidence above).'
Write-Log 'NOTE: runtime/visual items (#1 CTM appearance, #2/#4/#5/#12 GUI pixels, #3 world face colours,'
Write-Log '      #6 open animation, #9 docking display, #13 lighting, #15 rendering) still require the client'
Write-Log '      run scripts and human screenshots; this script does not and cannot assert them.'
exit 0

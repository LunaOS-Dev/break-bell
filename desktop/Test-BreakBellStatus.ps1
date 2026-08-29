$ErrorActionPreference = 'Stop'

$helper = Join-Path $PSScriptRoot 'Get-BreakBellStatus.ps1'
$testDirectory = Join-Path ([System.IO.Path]::GetTempPath()) ("break-bell-test-{0}" -f [guid]::NewGuid())
$statusPath = Join-Path $testDirectory 'status.json'
$now = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()

function Write-TestStatus([string]$Phase, [long]$PhaseStartedAt, [long]$PhaseEndsAt) {
    $status = [ordered]@{
        receivedAt = $now
        isActive = $true
        phase = $Phase
        phaseStartedAt = $PhaseStartedAt
        phaseEndsAt = $PhaseEndsAt
        completedBreaks = 0
        currentBlock = [ordered]@{ name = 'Deep'; workMinutes = 45; breakMinutes = 10 }
    }
    $status | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $statusPath -Encoding UTF8
}

function Read-TestStatus([double]$IdleSeconds) {
    $raw = & $helper -Json -StatusPath $statusPath -IdleSecondsOverride $IdleSeconds
    return $raw | ConvertFrom-Json
}

function Assert-Equal($Expected, $Actual, [string]$Message) {
    if ($Expected -ne $Actual) {
        throw "$Message Expected '$Expected', got '$Actual'."
    }
}

try {
    New-Item -ItemType Directory -Path $testDirectory | Out-Null

    Write-TestStatus 'WAITING_FOR_BREAK' ($now - 125000) 0
    $due = Read-TestStatus 0
    Assert-Equal 'BREAK_DUE_ACTIVE' $due.engagementState 'Due-break activity was misclassified.'
    Assert-Equal $true $due.shouldRemind 'An active overdue break should remind.'
    Assert-Equal 3 $due.reminderSequence 'Reminder sequencing should follow elapsed minutes.'

    Write-TestStatus 'BREAK' ($now - 65000) ($now + 535000)
    $claimed = Read-TestStatus 12
    Assert-Equal 'BREAK_CLAIMED_ACTIVE' $claimed.engagementState 'A claimed break with input was misclassified.'
    Assert-Equal $false $claimed.verifiedAway 'Recent input must not verify a break.'
    Assert-Equal $true $claimed.shouldRemind 'A claimed-but-active break should remind.'

    $away = Read-TestStatus 95
    Assert-Equal 'BREAK_VERIFIED_AWAY' $away.engagementState 'Sustained inactivity should verify keyboard separation.'
    Assert-Equal $true $away.verifiedAway 'Sustained inactivity should verify a break.'
    Assert-Equal $false $away.shouldRemind 'A verified-away human should not be reminded.'

    $unknown = Read-TestStatus -1
    Assert-Equal 'ACTIVITY_UNAVAILABLE' $unknown.engagementState 'Missing activity evidence must remain unknown.'
    Assert-Equal $false $unknown.verifiedAway 'Missing activity evidence must not verify a break.'

    Write-Output 'Break Bell desktop status tests passed.'
} finally {
    if (Test-Path -LiteralPath $statusPath) { Remove-Item -LiteralPath $statusPath -Force }
    if (Test-Path -LiteralPath $testDirectory) { Remove-Item -LiteralPath $testDirectory -Force }
}

param(
    [switch]$Json,
    [string]$StatusPath = (Join-Path $env:LOCALAPPDATA 'BreakBell\status.json'),
    [ValidateRange(5, 3600)]
    [int]$AwayThresholdSeconds = 90,
    [double]$IdleSecondsOverride = [double]::NaN
)

$nativeType = @'
using System;
using System.Runtime.InteropServices;

public static class BreakBellIdleTime {
    [StructLayout(LayoutKind.Sequential)]
    private struct LASTINPUTINFO {
        public uint cbSize;
        public uint dwTime;
    }

    [DllImport("user32.dll")]
    private static extern bool GetLastInputInfo(ref LASTINPUTINFO info);

    [DllImport("kernel32.dll")]
    private static extern ulong GetTickCount64();

    public static double Seconds() {
        var info = new LASTINPUTINFO();
        info.cbSize = (uint)Marshal.SizeOf(info);
        if (!GetLastInputInfo(ref info)) return -1;
        return (GetTickCount64() - info.dwTime) / 1000.0;
    }
}
'@

if (-not ('BreakBellIdleTime' -as [type])) {
    Add-Type -TypeDefinition $nativeType
}

$idleSeconds = if (-not [double]::IsNaN($IdleSecondsOverride)) {
    [math]::Round($IdleSecondsOverride, 1)
} else {
    [math]::Round([BreakBellIdleTime]::Seconds(), 1)
}
$now = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()

if (-not (Test-Path -LiteralPath $StatusPath)) {
    $result = [ordered]@{
        available = $false
        reason = 'No paired Break Bell status has been received.'
        idleSeconds = $idleSeconds
        shouldRemind = $false
    }
} else {
    try {
        $status = Get-Content -Raw -LiteralPath $StatusPath | ConvertFrom-Json
        $isBreakPhase = $status.phase -in @('BREAK', 'WAITING_FOR_BREAK')
        $receivedAgeSeconds = [math]::Max(0, ($now - [long]$status.receivedAt) / 1000)
        $stillScheduled = $status.phase -eq 'WAITING_FOR_BREAK' -or [long]$status.phaseEndsAt -gt $now
        $freshEnough = $receivedAgeSeconds -lt 180 -or ($status.phase -eq 'BREAK' -and $stillScheduled)
        $activityAvailable = $idleSeconds -ge 0
        $humanAtComputer = $activityAvailable -and $idleSeconds -lt $AwayThresholdSeconds
        $breakAcknowledged = $status.phase -eq 'BREAK'
        $verifiedAway = $breakAcknowledged -and $activityAvailable -and -not $humanAtComputer
        $engagementSeconds = if ($isBreakPhase -and [long]$status.phaseStartedAt -gt 0) {
            [math]::Max(0, ($now - [long]$status.phaseStartedAt) / 1000)
        } else {
            0
        }
        $reminderSequence = if ($isBreakPhase) {
            [math]::Max(1, [math]::Floor($engagementSeconds / 60) + 1)
        } else {
            0
        }
        $roastLevel = if (-not $isBreakPhase) {
            'NONE'
        } elseif ($reminderSequence -ge 4) {
            'INCIDENT'
        } elseif ($reminderSequence -ge 2) {
            'FIRM'
        } else {
            'NUDGE'
        }

        $engagementState = if (-not [bool]$status.isActive -or -not $isBreakPhase) {
            if ([bool]$status.isActive) { 'WORKING' } else { 'IDLE' }
        } elseif (-not $stillScheduled) {
            'BREAK_WINDOW_ENDED'
        } elseif (-not $freshEnough) {
            'STATUS_STALE'
        } elseif (-not $activityAvailable) {
            'ACTIVITY_UNAVAILABLE'
        } elseif ($status.phase -eq 'WAITING_FOR_BREAK') {
            if ($humanAtComputer) { 'BREAK_DUE_ACTIVE' } else { 'AWAY_BEFORE_ACKNOWLEDGEMENT' }
        } elseif ($humanAtComputer) {
            'BREAK_CLAIMED_ACTIVE'
        } else {
            'BREAK_VERIFIED_AWAY'
        }

        $reason = switch ($engagementState) {
            'BREAK_DUE_ACTIVE' { 'The break is due and recent Windows input shows the human is still at the computer.' }
            'AWAY_BEFORE_ACKNOWLEDGEMENT' { 'The human is away, but the phone has not acknowledged the break yet.' }
            'BREAK_CLAIMED_ACTIVE' { 'The break timer is running, but recent Windows input contradicts actual disengagement.' }
            'BREAK_VERIFIED_AWAY' { 'The break timer is running and Windows inactivity verifies keyboard separation.' }
            'STATUS_STALE' { 'The paired timer status is too stale to support a reminder.' }
            'ACTIVITY_UNAVAILABLE' { 'Windows activity evidence is unavailable, so break engagement cannot be classified.' }
            'BREAK_WINDOW_ENDED' { 'The scheduled break window has ended.' }
            'WORKING' { 'A work block is active.' }
            default { 'No workday is active.' }
        }
        $shouldRemind = [bool]$status.isActive -and $isBreakPhase -and $stillScheduled -and $freshEnough -and $humanAtComputer

        $result = [ordered]@{
            available = $true
            isActive = [bool]$status.isActive
            phase = [string]$status.phase
            phaseEndsAt = [long]$status.phaseEndsAt
            currentBlock = $status.currentBlock
            completedBreaks = [int]$status.completedBreaks
            idleSeconds = $idleSeconds
            activityAvailable = $activityAvailable
            humanAtComputer = $humanAtComputer
            awayThresholdSeconds = $AwayThresholdSeconds
            statusAgeSeconds = [math]::Round($receivedAgeSeconds, 1)
            engagementState = $engagementState
            breakAcknowledged = $breakAcknowledged
            verifiedAway = $verifiedAway
            engagementSeconds = [math]::Round($engagementSeconds, 1)
            reminderSequence = [int]$reminderSequence
            roastLevel = $roastLevel
            reason = $reason
            shouldRemind = $shouldRemind
        }
    } catch {
        $result = [ordered]@{
            available = $false
            reason = 'Break Bell status could not be parsed.'
            idleSeconds = $idleSeconds
            shouldRemind = $false
        }
    }
}

if ($Json) {
    $result | ConvertTo-Json -Depth 5 -Compress
} else {
    [pscustomobject]$result
}

param(
    [switch]$Json
)

$statusPath = Join-Path $env:LOCALAPPDATA 'BreakBell\status.json'

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

$idleSeconds = [math]::Round([BreakBellIdleTime]::Seconds(), 1)
$now = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()

if (-not (Test-Path -LiteralPath $statusPath)) {
    $result = [ordered]@{
        available = $false
        reason = 'No paired Break Bell status has been received.'
        idleSeconds = $idleSeconds
        shouldRemind = $false
    }
} else {
    try {
        $status = Get-Content -Raw -LiteralPath $statusPath | ConvertFrom-Json
        $isBreakPhase = $status.phase -in @('BREAK', 'WAITING_FOR_BREAK')
        $receivedAgeSeconds = [math]::Max(0, ($now - [long]$status.receivedAt) / 1000)
        $stillScheduled = $status.phase -eq 'WAITING_FOR_BREAK' -or [long]$status.phaseEndsAt -gt $now
        $freshEnough = $receivedAgeSeconds -lt 180 -or ($status.phase -eq 'BREAK' -and $stillScheduled)
        $humanAtComputer = $idleSeconds -ge 0 -and $idleSeconds -lt 90
        $shouldRemind = [bool]$status.isActive -and $isBreakPhase -and $stillScheduled -and $freshEnough -and $humanAtComputer

        $result = [ordered]@{
            available = $true
            isActive = [bool]$status.isActive
            phase = [string]$status.phase
            phaseEndsAt = [long]$status.phaseEndsAt
            currentBlock = $status.currentBlock
            completedBreaks = [int]$status.completedBreaks
            idleSeconds = $idleSeconds
            humanAtComputer = $humanAtComputer
            statusAgeSeconds = [math]::Round($receivedAgeSeconds, 1)
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

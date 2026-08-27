$taskName = 'Break Bell Agent Bridge'
$starter = Join-Path $PSScriptRoot 'Start-BreakBellBridge.ps1'
$quotedStarter = '"' + $starter + '"'
$action = New-ScheduledTaskAction -Execute 'powershell.exe' -Argument "-NoProfile -WindowStyle Hidden -ExecutionPolicy Bypass -File $quotedStarter"
$trigger = New-ScheduledTaskTrigger -AtLogOn -User $env:USERNAME
$settings = New-ScheduledTaskSettingsSet -ExecutionTimeLimit (New-TimeSpan -Days 3650) -RestartCount 3 -RestartInterval (New-TimeSpan -Minutes 1)

Register-ScheduledTask -TaskName $taskName -Action $action -Trigger $trigger -Settings $settings -Description 'Receives local Break Bell timer status for agent break reminders.' -Force
Start-ScheduledTask -TaskName $taskName

Write-Host "Installed and started '$taskName'."
Write-Host "Run Start-BreakBellBridge.ps1 interactively once to view the phone pairing address and token."

param(
    [string]$StatusPath = (Join-Path $env:LOCALAPPDATA 'BreakBell\status.json'),
    [ValidateRange(0, 300)]
    [int]$PreviewSeconds = 0
)

$ErrorActionPreference = 'Stop'

Add-Type -AssemblyName PresentationFramework
Add-Type -AssemblyName PresentationCore
Add-Type -AssemblyName WindowsBase

$nativeType = @'
using System;
using System.Runtime.InteropServices;

public static class BreakBellPopupIdleTime {
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

if (-not ('BreakBellPopupIdleTime' -as [type])) {
    Add-Type -TypeDefinition $nativeType
}

$createdNew = $false
$mutex = [Threading.Mutex]::new($true, 'Local\BreakBellDesktopReminder', [ref]$createdNew)
if (-not $createdNew) {
    $mutex.Dispose()
    exit 0
}

$xaml = @'
<Window xmlns="http://schemas.microsoft.com/winfx/2006/xaml/presentation"
        xmlns:x="http://schemas.microsoft.com/winfx/2006/xaml"
        Title="Break Bell" Width="540" Height="286" WindowStyle="None"
        ResizeMode="NoResize" ShowInTaskbar="True" Topmost="True"
        AllowsTransparency="True" Background="Transparent" Opacity="0">
    <Border x:Name="Frame" CornerRadius="26" Background="#171C18"
            BorderBrush="#B8F36B" BorderThickness="3" Padding="30">
        <Border.Effect>
            <DropShadowEffect BlurRadius="30" ShadowDepth="7" Opacity="0.48" Color="#000000" />
        </Border.Effect>
        <Grid>
            <Grid.RowDefinitions>
                <RowDefinition Height="Auto" />
                <RowDefinition Height="*" />
                <RowDefinition Height="Auto" />
            </Grid.RowDefinitions>

            <TextBlock x:Name="Eyebrow" Text="REQUIRED HUMAN MAINTENANCE"
                       Foreground="#B8F36B" FontFamily="Segoe UI Semibold"
                       FontSize="13" />

            <StackPanel Grid.Row="1" VerticalAlignment="Center">
                <TextBlock x:Name="Headline" Text="Step away now."
                           Foreground="#F7F4EA" FontFamily="Segoe UI Semibold"
                           FontSize="38" TextWrapping="Wrap" />
                <TextBlock x:Name="Message" Text="Acknowledge on your phone, then leave the keyboard."
                           Margin="0,10,0,0" Foreground="#C8CEC9" FontFamily="Segoe UI"
                           FontSize="17" TextWrapping="Wrap" />
            </StackPanel>

            <Grid Grid.Row="2">
                <Grid.ColumnDefinitions>
                    <ColumnDefinition Width="*" />
                    <ColumnDefinition Width="Auto" />
                </Grid.ColumnDefinitions>
                <StackPanel>
                    <TextBlock x:Name="BlockLabel" Text="DEEP · 45 / 10"
                               Foreground="#89918B" FontFamily="Segoe UI Semibold" FontSize="12" />
                    <TextBlock Text="Ctrl+Shift+Q emergency close" Margin="0,5,0,0"
                               Foreground="#646B66" FontFamily="Segoe UI" FontSize="10" />
                </StackPanel>
                <TextBlock x:Name="Countdown" Grid.Column="1" Text="ACKNOWLEDGE"
                           Foreground="#F7F4EA" FontFamily="Consolas" FontWeight="Bold"
                           FontSize="16" VerticalAlignment="Bottom" />
            </Grid>
        </Grid>
    </Border>
</Window>
'@

$reader = [System.Xml.XmlNodeReader]::new([xml]$xaml)
$window = [System.Windows.Markup.XamlReader]::Load($reader)
$frame = $window.FindName('Frame')
$eyebrow = $window.FindName('Eyebrow')
$headline = $window.FindName('Headline')
$message = $window.FindName('Message')
$blockLabel = $window.FindName('BlockLabel')
$countdown = $window.FindName('Countdown')

$script:allowClose = $false
$script:isExpanded = $false
$script:wasHumanPresent = $false
$script:lastExpandedAt = [DateTime]::MinValue
$script:previewStartedAt = [DateTimeOffset]::Now

function Get-CurrentStatus {
    if ($PreviewSeconds -gt 0) {
        return [pscustomobject]@{
            isActive = $true
            phase = 'WAITING_FOR_BREAK'
            phaseEndsAt = 0
            currentBlock = [pscustomobject]@{ name = 'Deep'; workMinutes = 45; breakMinutes = 10 }
        }
    }

    try {
        return Get-Content -Raw -LiteralPath $StatusPath -ErrorAction Stop | ConvertFrom-Json
    } catch {
        return $null
    }
}

function Set-WindowPosition([bool]$Expanded) {
    $area = [System.Windows.SystemParameters]::WorkArea
    if ($Expanded) {
        $window.Width = 540
        $window.Height = 286
        $window.Left = $area.Left + (($area.Width - $window.Width) / 2)
        $window.Top = $area.Top + [math]::Max(42, $area.Height * 0.12)
        $frame.Padding = [System.Windows.Thickness]::new(30)
        $message.Visibility = 'Visible'
        $blockLabel.Visibility = 'Visible'
        $eyebrow.Text = 'REQUIRED HUMAN MAINTENANCE'
        $headline.FontSize = 38
        $headline.Margin = [System.Windows.Thickness]::new(0)
    } else {
        $window.Width = 390
        $window.Height = 112
        $window.Left = $area.Right - $window.Width - 24
        $window.Top = $area.Top + 24
        $frame.Padding = [System.Windows.Thickness]::new(20, 14, 20, 14)
        $message.Visibility = 'Collapsed'
        $blockLabel.Visibility = 'Collapsed'
        $eyebrow.Text = 'BREAK BELL'
        $headline.FontSize = 22
        $headline.Margin = [System.Windows.Thickness]::new(0, 3, 0, 0)
    }
    $script:isExpanded = $Expanded
}

function Show-Reminder([bool]$Expanded) {
    Set-WindowPosition $Expanded
    if (-not $window.IsVisible) {
        $window.Show()
    }
    $window.Topmost = $true
    $window.Activate() | Out-Null

    $fade = [System.Windows.Media.Animation.DoubleAnimation]::new(0, 1, [TimeSpan]::FromMilliseconds(220))
    $window.BeginAnimation([System.Windows.Window]::OpacityProperty, $fade)

    if ($Expanded) {
        $script:lastExpandedAt = [DateTime]::Now
        $brush = [System.Windows.Media.SolidColorBrush]::new([System.Windows.Media.ColorConverter]::ConvertFromString('#B8F36B'))
        $frame.BorderBrush = $brush
        $pulse = [System.Windows.Media.Animation.ColorAnimation]::new()
        $pulse.From = [System.Windows.Media.ColorConverter]::ConvertFromString('#B8F36B')
        $pulse.To = [System.Windows.Media.ColorConverter]::ConvertFromString('#FFD166')
        $pulse.Duration = [TimeSpan]::FromMilliseconds(650)
        $pulse.AutoReverse = $true
        $pulse.RepeatBehavior = [System.Windows.Media.Animation.RepeatBehavior]::new(3)
        $brush.BeginAnimation([System.Windows.Media.SolidColorBrush]::ColorProperty, $pulse)
    }
}

function Stop-Reminder {
    $script:allowClose = $true
    $window.Close()
}

$window.Add_Closing({
    param($sender, $eventArgs)
    if (-not $script:allowClose) {
        $eventArgs.Cancel = $true
    }
})

$window.Add_KeyDown({
    param($sender, $eventArgs)
    $ctrl = [System.Windows.Input.Keyboard]::IsKeyDown([System.Windows.Input.Key]::LeftCtrl) -or
            [System.Windows.Input.Keyboard]::IsKeyDown([System.Windows.Input.Key]::RightCtrl)
    $shift = [System.Windows.Input.Keyboard]::IsKeyDown([System.Windows.Input.Key]::LeftShift) -or
             [System.Windows.Input.Keyboard]::IsKeyDown([System.Windows.Input.Key]::RightShift)
    if ($ctrl -and $shift -and $eventArgs.Key -eq [System.Windows.Input.Key]::Q) {
        Stop-Reminder
    }
})

$window.Add_MouseLeftButtonDown({
    try { $window.DragMove() } catch { }
})

$timer = [System.Windows.Threading.DispatcherTimer]::new()
$timer.Interval = [TimeSpan]::FromMilliseconds(500)
$timer.Add_Tick({
    if ($PreviewSeconds -gt 0 -and ([DateTimeOffset]::Now - $script:previewStartedAt).TotalSeconds -ge $PreviewSeconds) {
        Stop-Reminder
        return
    }

    $status = Get-CurrentStatus
    $nowMs = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
    $onBreak = $null -ne $status -and [bool]$status.isActive -and
               ($status.phase -eq 'WAITING_FOR_BREAK' -or
               ($status.phase -eq 'BREAK' -and [long]$status.phaseEndsAt -gt $nowMs))

    if (-not $onBreak) {
        Stop-Reminder
        return
    }

    $idleSeconds = [BreakBellPopupIdleTime]::Seconds()
    $humanPresent = $idleSeconds -ge 0 -and $idleSeconds -lt 90

    if (-not $humanPresent) {
        if ($window.IsVisible) { $window.Hide() }
        $script:wasHumanPresent = $false
        return
    }

    $name = [string]$status.currentBlock.name
    $workMinutes = [int]$status.currentBlock.workMinutes
    $breakMinutes = [int]$status.currentBlock.breakMinutes
    $blockLabel.Text = ("{0} · {1} / {2}" -f $name.ToUpperInvariant(), $workMinutes, $breakMinutes)

    if ($status.phase -eq 'WAITING_FOR_BREAK') {
        $headline.Text = 'Step away now.'
        $message.Text = "Acknowledge on your phone, then take the $breakMinutes-minute break."
        $countdown.Text = 'ACKNOWLEDGE'
    } else {
        $remaining = [math]::Max(0, [math]::Ceiling(([long]$status.phaseEndsAt - $nowMs) / 1000))
        $minutes = [math]::Floor($remaining / 60)
        $seconds = $remaining % 60
        $headline.Text = 'Break in progress.'
        $message.Text = 'The agents are working. You are currently assigned to touching grass.'
        $countdown.Text = ('{0:00}:{1:00}' -f $minutes, $seconds)
    }

    $shouldExpand = -not $script:wasHumanPresent -or
                    ([DateTime]::Now - $script:lastExpandedAt).TotalSeconds -ge 60
    if ($shouldExpand) {
        Show-Reminder $true
    } elseif ($script:isExpanded -and ([DateTime]::Now - $script:lastExpandedAt).TotalSeconds -ge 10) {
        Show-Reminder $false
    } elseif (-not $window.IsVisible) {
        Show-Reminder $false
    }

    $script:wasHumanPresent = $true
})

try {
    Set-WindowPosition $true
    $timer.Start()
    $window.ShowDialog() | Out-Null
} finally {
    $timer.Stop()
    if ($createdNew) { $mutex.ReleaseMutex() }
    $mutex.Dispose()
}

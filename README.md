# Break Bell

Break Bell is a native Android workday timer that does not politely disappear when a focus block ends.

**AI can keep producing indefinitely. Humans cannot.** Break Bell lets the agents continue while enforcing the one dependency the workflow cannot replace: required human maintenance.

## What the first release does

- Starts and ends the workday from the app or a home-screen widget.
- Records local start time, stop time, elapsed time, and completed breaks.
- Includes Quick (25/5), Deep (45/10), and Long (60/10) presets.
- Supports one repeating block or an ordered pattern of preset and custom blocks.
- Locks the selected pattern during an active workday.
- Rings for 10 seconds every minute after a work block until **I'm on break** is acknowledged.
- Starts the break countdown only after acknowledgment.
- Starts the next work block automatically when the break ends.
- Restores exact alarms after reboot, clock changes, or timezone changes.
- Optionally publishes timer phase to a paired desktop bridge so agents can remind an active human to take the scheduled break.
- Shows a staged Windows reminder during breaks: prominent for 10 seconds, quietly docked afterward, and prominent again once per minute only while the human is still using the computer.

## Android requirements

The app targets Android 16 and supports Android 12 or newer. On first setup, grant:

1. Notifications.
2. Exact alarms.
3. Full-screen alarm access on Android 14 or newer.
4. Optional Do Not Disturb policy access if alarms should bypass a DND policy that does not already allow alarms.

Silent mode does not silence the alarm audio stream. Android still keeps the user in control: revoked notification, exact-alarm, full-screen, or alarm-volume settings cannot be secretly overridden.

## Build

Open the project in Android Studio, allow it to install Android SDK Platform 36 and Build Tools 35, then run the `app` configuration. From a configured terminal:

```powershell
$env:JAVA_HOME = 'path-to-jdk-17'
$env:ANDROID_HOME = 'path-to-android-sdk'
.\gradlew.bat testDebugUnitTest assembleDebug
```

The debug APK is written to `app\build\outputs\apk\debug\app-debug.apk`.

## Pair the optional agent bridge

The bridge requires Node.js 20 or newer on the Windows computer.

1. Run `desktop\Start-BreakBellBridge.ps1` interactively.
2. Copy the printed pairing address and token into **Agent bridge** in the Android app.
3. Tap **Save and sync** while phone and computer are on the same local network.
4. After pairing works, optionally run `desktop\Install-BreakBellBridge.ps1` to start the bridge automatically at Windows sign-in.

The installer creates a current-user scheduled task named `Break Bell Agent Bridge`. It is provided but is not run automatically by this repository.

The phone sends only timer state: current phase, block lengths, timestamps, and completed-break count. The agent helper and desktop reminder ask Windows only how many seconds have elapsed since the last keyboard or mouse input. The reminder disappears when the human has been away for 90 seconds, returns if they resume computer use during the break, and closes when the break ends. It does not capture keys, screen contents, application names, camera, or microphone data.

The local bridge uses token-authenticated HTTP on the LAN. The payload is low-sensitivity timer metadata but is not encrypted in transit; use it only on a trusted local network.

## Agent behavior

The workspace `break-enforcer` skill checks the paired status before substantive work. When the phone says a break is active and Windows reports input within the last 90 seconds, the agent keeps working but leads its next progress update with one firm, playful human-maintenance reminder. It never treats a reminder as proof that the break happened.

## Privacy and license

Break Bell does not include ads, analytics, accounts, or a maintainer-operated cloud service. See the [privacy policy](PRIVACY.md) for the optional local desktop bridge behavior.

Released under the [MIT License](LICENSE).

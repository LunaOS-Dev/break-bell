# Break Bell

Break Bell is a native Android workday timer that does not politely disappear when a focus block ends.

**AI can keep producing indefinitely. Humans cannot.** Break Bell lets the agents continue while enforcing the one dependency the workflow cannot replace: required human maintenance.

**A break enforcer for people who ignore break reminders. You will be affectionately bullied into touching grass.** The bell gets attention; a fresh, context-aware roast changes mental state.

## What the first release does

- Starts and ends the workday from the app or a home-screen widget.
- Records local start time, stop time, elapsed time, and completed break timers. Actual computer disengagement is classified separately by the optional desktop bridge.
- Includes Quick (25/5), Deep (45/10), and Long (60/10) presets.
- Supports one repeating block or an ordered pattern of preset and custom blocks.
- Locks the selected pattern during an active workday.
- Gives one silent heads-up two minutes before a break. For work blocks under ten minutes, the lead is one fifth of the block (five minutes: one minute; one minute: twelve seconds).
- Offers an optional **Next, I’m going to ___** bookmark during the heads-up, saved locally as you type. It appears on the next work block and break-complete screen.
- Rings for 10 seconds every minute after a work block until **I'm stepping away** is acknowledged.
- Starts the break countdown only after acknowledgment.
- Starts the next work block automatically when the break ends.
- Restores exact alarms after reboot, clock changes, or timezone changes.
- Optionally publishes timer phase to a paired desktop bridge so agents can distinguish a promised break from verified keyboard separation.
- Classifies local break evidence as overdue-and-active, claimed-but-active, or verified-away using Windows idle duration.
- Shows a staged Windows reminder with rotating, situation-aware roasts: prominent for 10 seconds, quietly docked afterward, and prominent again once per minute only while the human is still using the computer.

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

## Quiet transitions

The heads-up appears in the app and as a silent notification that opens the optional bookmark. It has no sound, vibration, full-screen interruption, or repeat. The work-end alarm remains independently scheduled. A late heads-up is skipped once the break is due.

Bookmarks are optional, limited to 500 characters, and saved on each edit without a submit button. At the deadline the editor closes; empty or unfinished notes never delay acknowledgment or the break countdown. Notes survive app recreation and reboot, move to the next work block after the break, and clear when the day ends. A later blank bookmark does not reuse an older note. Bookmarks stay on the phone and are not sent to the desktop bridge or agents.

The acknowledgment, mandatory break duration, no-snooze break alarm, and Windows inactivity checks retain their existing behavior. The heads-up does not create a new agent-reminder phase.

Transition boundary, persistence, notification, and enforcement regression tests run with `testDebugUnitTest`; Android integration tests use Robolectric. Run `lintDebug assembleDebug` for static checks and APK generation. Device testing is still needed to assess notification delivery under device-specific power management.

## Pair the optional agent bridge

The bridge requires Node.js 20 or newer on the Windows computer.

1. Run `desktop\Start-BreakBellBridge.ps1` interactively.
2. Copy the printed pairing address and token into **Agent bridge** in the Android app.
3. Tap **Save and sync** while phone and computer are on the same local network.
4. After pairing works, optionally run `desktop\Install-BreakBellBridge.ps1` to start the bridge automatically at Windows sign-in.

The installer creates a current-user scheduled task named `Break Bell Agent Bridge`. It is provided but is not run automatically by this repository.

The phone sends only timer state: current phase, block lengths, timestamps, and completed-break count. The agent helper and desktop reminder ask Windows only how many seconds have elapsed since the last keyboard or mouse input. A tap starts the phone's break timer; it does not count as proof that the user left. The desktop considers 90 seconds without input verified keyboard separation. The reminder disappears at that point, roasts an early return, and closes when the break ends. It does not capture keys, screen contents, application names, processes, camera, or microphone data.

The local bridge uses token-authenticated HTTP on the LAN. The payload is low-sensitivity timer metadata but is not encrypted in transit; use it only on a trusted local network.

## Agent behavior

The repository's `.agents/skills/break-enforcer` skill checks paired timer state against current Windows idle time before substantive work. It reports explicit engagement states, so `BREAK_CLAIMED_ACTIVE` cannot masquerade as `BREAK_VERIFIED_AWAY`. When a break is due and Windows reports input within the last 90 seconds, the agent keeps working but leads its next progress update with a fresh, evidence-based, affectionate roast. Agents vary the wording and intensity with the live situation; they never treat a user statement or button tap as proof that the break happened.

Run the deterministic desktop policy checks with:

```powershell
.\desktop\Test-BreakBellStatus.ps1
```

## Privacy and license

Break Bell does not include ads, analytics, accounts, or a maintainer-operated cloud service. See the [privacy policy](PRIVACY.md) for the optional local desktop bridge behavior.

Released under the [MIT License](LICENSE).

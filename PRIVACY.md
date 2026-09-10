# Break Bell Privacy Policy

Effective: August 27, 2026

Break Bell is designed to keep workday timer data under the user's control.

## Data stored on the Android device

Break Bell stores timer preferences, the current timer state, workday history, and an optional next-step bookmark locally on the device. The bookmark stays on the phone, appears when work resumes, and is cleared when the workday ends. It is not included in desktop bridge messages. The app does not include advertising, analytics, user accounts, or a cloud service operated by the maintainers.

## Optional desktop bridge

If the user explicitly configures the optional desktop bridge, the app sends timer metadata to the address the user entered on their local network. That metadata contains the current work or break phase, block lengths, timestamps, and completed-break count. Pairing uses a locally generated authentication token.

The desktop bridge stores the latest timer status on that computer. Its reminder and agent helper can read the number of seconds since Windows last received keyboard or mouse input. They use that duration to classify a break as due while active, acknowledged while still active, or verified away after 90 seconds without input. They do not record keys, screen contents, application names, processes, camera data, microphone data, contacts, location, or files. The bridge does not send Windows activity information back to the Android app or to the maintainers.

The optional local bridge currently uses HTTP rather than transport encryption and should only be used on a trusted private network.

## Data sharing

The maintainers do not receive or sell user data. When the optional bridge is enabled, timer metadata is sent only to the user-selected computer address.

## Deletion

Uninstalling the Android app removes its app-private data under Android's normal uninstall behavior. Desktop bridge data can be deleted by removing the `%LOCALAPPDATA%\BreakBell` directory on the paired Windows computer.

## Changes and contact

Material changes to this policy will be published with the project. Questions can be submitted through the public Break Bell GitHub repository.

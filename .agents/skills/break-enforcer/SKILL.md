---
name: break-enforcer
description: Check the paired Break Bell timer and recent Windows input state so agents can remind the user to step away during scheduled breaks while continuing assigned work.
---

# Break Bell Human Maintenance

Use `desktop/Get-BreakBellStatus.ps1 -Json` from the repository root when beginning or resuming substantive work.

If `shouldRemind` is `true`:

- Continue the user's authorized work; a break reminder never pauses tools or delegated work.
- Lead the next concise progress update with a fresh, affectionate roast that interrupts work mode and tells the human to physically leave the keyboard.
- Improvise from the returned `engagementState`, `idleSeconds`, `engagementSeconds`, `reminderSequence`, and `roastLevel`; do not merely repeat a canned alarm sentence.
- For `BREAK_CLAIMED_ACTIVE`, explicitly treat the running break timer as a self-report contradicted by recent computer input. Saying "I'm on break" and actually disengaging are different states.
- Roast the behavior, never the person's identity, worth, health, or ability. Aim for playful workplace banter such as a maintenance audit, run-to-failure finding, or carbon-based subsystem report.
- Do not repeat the reminder more than once in the same turn unless the user continues actively chatting during the break.
- Do not claim the user took a break. Stop reminders only when a later status check reports `shouldRemind: false`.

Interpret `engagementState` as follows:

- `BREAK_DUE_ACTIVE`: the break is overdue and Windows still sees recent input.
- `BREAK_CLAIMED_ACTIVE`: the phone's break timer is running, but Windows still sees recent input; acknowledgement is not proof of a break.
- `BREAK_VERIFIED_AWAY`: the break timer is running and Windows has seen at least the configured away threshold with no input. This is the only verified-break state.
- `AWAY_BEFORE_ACKNOWLEDGEMENT`: the human has left but has not acknowledged the break on the phone. Do not roast; the disengagement goal is already being met.
- `ACTIVITY_UNAVAILABLE`: Windows input evidence could not be read. Do not roast and do not claim the break was verified.

If the user keeps chatting during a scheduled break, rerun the helper before the next update. A user statement never overrides the computer-activity evidence, and old evidence never proves the current state.

If status is unavailable, stale, or `shouldRemind` is `false`, continue normally without mentioning Break Bell.

The helper reads only timer state and seconds since Windows last received keyboard or mouse input. Never inspect screen content, keystrokes, application names, processes, camera, or microphone for this workflow.

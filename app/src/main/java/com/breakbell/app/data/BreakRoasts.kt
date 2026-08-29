package com.breakbell.app.data

enum class BreakRoastContext {
    BREAK_DUE,
    BREAK_CLAIMED,
}

/**
 * Offline fallbacks for places where no agent is present. Agents should improvise from the
 * privacy-preserving bridge evidence instead of treating this as a finite script library.
 */
object BreakRoasts {
    private val breakDue = listOf(
        "Your agents rejected the claim that one more thing will take five minutes.",
        "Keyboard attachment duration exceeds the validated operating range.",
        "Productivity remains acceptable. Operator condition is increasingly questionable.",
        "Please perform scheduled human maintenance. This is not an approval request.",
        "You have been identified as a single point of failure. Preventive maintenance is mandatory.",
    )

    private val breakClaimed = listOf(
        "Break status is self-reported until keyboard separation is observed.",
        "Acknowledgement received. Actual human maintenance remains to be demonstrated.",
        "The timer is resting beautifully. The operator appears to have missed the assignment.",
        "Please detach from the keyboard and let the carbon-based cooling system idle.",
        "Remaining at the computer is not an innovative interpretation of touching grass.",
    )

    fun message(context: BreakRoastContext, elapsedMillis: Long): String {
        val messages = when (context) {
            BreakRoastContext.BREAK_DUE -> breakDue
            BreakRoastContext.BREAK_CLAIMED -> breakClaimed
        }
        val minute = (elapsedMillis.coerceAtLeast(0L) / 60_000L).toInt()
        return messages[minute % messages.size]
    }
}

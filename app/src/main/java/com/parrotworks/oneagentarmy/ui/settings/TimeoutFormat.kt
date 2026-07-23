package com.parrotworks.oneagentarmy.ui.settings

import java.util.Locale

// m:ss <-> total seconds for the request timeout field ("1:30" = 90 seconds).
fun formatTimeout(totalSeconds: Int): String =
    String.format(Locale.US, "%d:%02d", totalSeconds / 60, totalSeconds % 60)

// Strict m:ss / mm:ss with two second-digits and seconds < 60. A bare number is
// rejected rather than guessed at - "130" could plausibly mean 130 seconds or 1:30.
private val TIMEOUT_PATTERN = Regex("""(\d{1,2}):([0-5]\d)""")

fun parseTimeout(text: String): Int? {
    val match = TIMEOUT_PATTERN.matchEntire(text.trim()) ?: return null
    val (minutes, seconds) = match.destructured
    return minutes.toInt() * 60 + seconds.toInt()
}

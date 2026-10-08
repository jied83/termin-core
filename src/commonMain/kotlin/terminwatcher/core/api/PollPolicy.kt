package terminwatcher.core.api

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random

data class PollPolicy(
    val intervalMinutes: Double = 10.0,
    val jitterSeconds: Double = 60.0,
    val maxBackoffMinutes: Double = 60.0,
) {
    init {
        require(intervalMinutes >= MIN_INTERVAL_MINUTES) {
            "intervalMinutes must be at least $MIN_INTERVAL_MINUTES (the portal allows a repeat search after 60 s)"
        }
    }

    fun nextDelaySeconds(consecutiveFailures: Int = 0, random: Random = Random): Double {
        var base = intervalMinutes * 60
        if (consecutiveFailures > 0) {
            base = min(base * 2.0.pow(consecutiveFailures), maxBackoffMinutes * 60)
        }
        return max(MIN_DELAY_SECONDS, base + random.nextDouble(-1.0, 1.0) * jitterSeconds)
    }

    companion object {
        const val MIN_INTERVAL_MINUTES: Double = 2.0

        const val MIN_DELAY_SECONDS: Double = 60.0
    }
}

fun freshDates(known: Set<String>, current: List<String>): List<String> =
    (current.toSet() - known).sorted()

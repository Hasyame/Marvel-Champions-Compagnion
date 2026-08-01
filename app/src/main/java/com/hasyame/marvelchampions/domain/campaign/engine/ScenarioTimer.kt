package com.hasyame.marvelchampions.domain.campaign.engine

/**
 * A pausable stopwatch that survives the app being backgrounded **and the
 * device rebooting**.
 *
 * `SystemClock.elapsedRealtime` resets on reboot, so it cannot be the basis for
 * this. Instead the accumulated time is banked on every pause and the running
 * segment is measured from a wall-clock instant. Wall clock can jump if the
 * user changes the time, which is exactly why the elapsed value is editable by
 * hand.
 */
data class TimerState(
    val accumulatedMillis: Long = 0,
    /** Wall-clock start of the current running segment, null when paused. */
    val runningSinceEpochMillis: Long? = null,
) {
    val isRunning: Boolean get() = runningSinceEpochMillis != null

    fun elapsedAt(nowEpochMillis: Long): Long {
        val running = runningSinceEpochMillis ?: return accumulatedMillis
        // A backwards clock change must not make elapsed time go down.
        val segment = (nowEpochMillis - running).coerceAtLeast(0)
        return accumulatedMillis + segment
    }

    fun start(nowEpochMillis: Long): TimerState =
        if (isRunning) this else copy(runningSinceEpochMillis = nowEpochMillis)

    fun pause(nowEpochMillis: Long): TimerState =
        if (!isRunning) {
            this
        } else {
            TimerState(accumulatedMillis = elapsedAt(nowEpochMillis), runningSinceEpochMillis = null)
        }

    /** Manual correction, because the stop button gets forgotten. */
    fun setElapsed(millis: Long, nowEpochMillis: Long): TimerState {
        val safe = millis.coerceAtLeast(0)
        return if (isRunning) {
            TimerState(accumulatedMillis = safe, runningSinceEpochMillis = nowEpochMillis)
        } else {
            TimerState(accumulatedMillis = safe, runningSinceEpochMillis = null)
        }
    }

    fun reset(): TimerState = TimerState()

    companion object {
        fun format(millis: Long): String {
            val totalSeconds = millis / 1000
            val hours = totalSeconds / 3600
            val minutes = (totalSeconds % 3600) / 60
            val seconds = totalSeconds % 60
            return if (hours > 0) {
                "%d:%02d:%02d".format(hours, minutes, seconds)
            } else {
                "%d:%02d".format(minutes, seconds)
            }
        }
    }
}

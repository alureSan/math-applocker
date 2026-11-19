package com.mathapplocker.foss.service

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.PowerManager

/**
 * Reports the package currently in the foreground using UsageStats events.
 *
 * Usage events are only generated on app switches, so the last known foreground
 * package is cached between polls.
 */
class ForegroundAppDetector(context: Context) {

    private val appContext = context.applicationContext
    private val usageStats: UsageStatsManager? =
        appContext.getSystemService(UsageStatsManager::class.java)
    private val powerManager: PowerManager? =
        appContext.getSystemService(PowerManager::class.java)

    private var lastForeground: String? = null

    val isScreenOn: Boolean
        get() = powerManager?.isInteractive ?: true

    fun currentForeground(now: Long = System.currentTimeMillis()): String? {
        val manager = usageStats ?: return lastForeground
        // First lookup uses a wide window so a long-running app is detected immediately.
        val windowMs = if (lastForeground == null) 10 * 60_000L else 30_000L
        val events = runCatching { manager.queryEvents(now - windowMs, now) }.getOrNull()
            ?: return lastForeground

        val event = UsageEvents.Event()
        var latestPackage: String? = null
        var latestTimestamp = 0L
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            @Suppress("DEPRECATION")
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED ||
                event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND
            ) {
                if (event.timeStamp >= latestTimestamp) {
                    latestTimestamp = event.timeStamp
                    latestPackage = event.packageName
                }
            }
        }
        if (latestPackage != null) {
            lastForeground = latestPackage
        }
        return lastForeground
    }

    fun reset() {
        lastForeground = null
    }
}

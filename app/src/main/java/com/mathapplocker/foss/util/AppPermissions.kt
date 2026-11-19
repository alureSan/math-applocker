package com.mathapplocker.foss.util

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat

/** Helpers for the special permissions this app relies on. */
object AppPermissions {

    fun canDrawOverlays(context: Context): Boolean = Settings.canDrawOverlays(context)

    fun hasUsageAccess(context: Context): Boolean {
        val appOps = context.getSystemService(AppOpsManager::class.java) ?: return false
        val mode = appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName,
        )
        if (mode == AppOpsManager.MODE_DEFAULT) {
            // Some OEMs report MODE_DEFAULT even when access is granted; verify with a query.
            return runCatching {
                val usm = context.getSystemService(UsageStatsManager::class.java)
                val now = System.currentTimeMillis()
                usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - 86_400_000L, now)
                    ?.isNotEmpty() == true
            }.getOrDefault(false)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun notificationsEnabled(context: Context): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled()

    fun ignoresBatteryOptimizations(context: Context): Boolean {
        val pm = context.getSystemService(PowerManager::class.java) ?: return false
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun overlaySettingsIntent(context: Context): Intent =
        Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}"),
        )

    fun usageAccessIntent(): Intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)

    fun batteryOptimizationIntent(context: Context): Intent =
        Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.parse("package:${context.packageName}"),
        )

    fun appDetailsIntent(context: Context): Intent =
        Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:${context.packageName}"),
        )

    /** Everything the protection service needs before it can work. */
    fun missingCorePermissions(context: Context): List<String> = buildList {
        if (!canDrawOverlays(context)) add(PERM_OVERLAY)
        if (!hasUsageAccess(context)) add(PERM_USAGE)
        if (!notificationsEnabled(context)) add(PERM_NOTIFICATIONS)
    }

    const val PERM_OVERLAY = "overlay"
    const val PERM_USAGE = "usage"
    const val PERM_NOTIFICATIONS = "notifications"
}

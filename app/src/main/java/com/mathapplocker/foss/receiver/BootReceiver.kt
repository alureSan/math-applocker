package com.mathapplocker.foss.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.mathapplocker.foss.data.Prefs
import com.mathapplocker.foss.service.OverlaySchedulerService
import com.mathapplocker.foss.util.AppPermissions
import com.mathapplocker.foss.util.Notifications

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) {
            return
        }

        val settings = Prefs.get(context).settings.value
        if (!settings.enabled) return
        if (settings.blockedApps.isEmpty()) return
        if (!AppPermissions.canDrawOverlays(context)) return

        try {
            OverlaySchedulerService.start(context)
        } catch (_: Throwable) {
            // Android 12+ can reject background foreground-service starts.
            Notifications.notifyBootFailure(context)
        }
    }
}

package com.mathapplocker.foss.util

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.mathapplocker.foss.R
import com.mathapplocker.foss.service.OverlaySchedulerService
import com.mathapplocker.foss.ui.MainActivity

object Notifications {

    const val CHANNEL_ID = "protection_service"
    const val SERVICE_NOTIFICATION_ID = 1001
    const val ALERT_NOTIFICATION_ID = 1002

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notif_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = context.getString(R.string.notif_channel_description)
            setShowBadge(false)
            enableVibration(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        manager.createNotificationChannel(channel)
    }

    fun buildServiceNotification(
        context: Context,
        enabled: Boolean,
        blockedCount: Int,
        missingPermissions: Boolean,
    ): Notification {
        val openIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val stopIntent = PendingIntent.getService(
            context,
            1,
            Intent(context, OverlaySchedulerService::class.java)
                .setAction(OverlaySchedulerService.ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val text = when {
            missingPermissions -> context.getString(R.string.notif_permission_missing)
            !enabled -> context.getString(R.string.notif_paused)
            blockedCount == 0 -> context.getString(R.string.notif_no_apps)
            else -> context.getString(R.string.notif_active, blockedCount)
        }

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notif_title))
            .setContentText(text)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(0, context.getString(R.string.notif_stop), stopIntent)
            .build()
    }

    @SuppressLint("MissingPermission") // canPostNotifications() checks the permission above.
    fun updateServiceNotification(context: Context, enabled: Boolean, blockedCount: Int) {
        if (!canPostNotifications(context)) return
        val notification = buildServiceNotification(
            context = context,
            enabled = enabled,
            blockedCount = blockedCount,
            missingPermissions = AppPermissions.missingCorePermissions(context).isNotEmpty(),
        )
        runCatching {
            NotificationManagerCompat.from(context).notify(SERVICE_NOTIFICATION_ID, notification)
        }
    }

    /** Shown when Android refuses a background start (e.g. after reboot on some OEMs). */
    @SuppressLint("MissingPermission") // canPostNotifications() checks the permission above.
    fun notifyBootFailure(context: Context) {
        ensureChannel(context)
        if (!canPostNotifications(context)) return
        val open = PendingIntent.getActivity(
            context,
            2,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notif_title))
            .setContentText(context.getString(R.string.boot_restart_failed))
            .setStyle(NotificationCompat.BigTextStyle().bigText(context.getString(R.string.boot_restart_failed)))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(ALERT_NOTIFICATION_ID, notification) }
    }

    fun canPostNotifications(context: Context): Boolean {
        val granted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        return granted && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }
}

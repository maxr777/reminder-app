package app.everyndays.notify

import android.Manifest
import android.app.AlarmManager
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
import app.everyndays.Format
import app.everyndays.MainActivity
import app.everyndays.R
import app.everyndays.data.Reminder
import app.everyndays.data.ReminderStore
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Keeps notifications and the single wake-up alarm in sync with the reminder list.
 * Safe to call any time: on app start, after every change, from the alarm, after reboot.
 */
object Scheduler {
    const val CHANNEL_ID = "reminders"
    const val EXTRA_ID = "reminder_id"

    @Synchronized
    fun reconcile(context: Context) {
        val ctx = context.applicationContext
        ReminderStore.init(ctx)
        ensureChannel(ctx)

        val nm = ctx.getSystemService(NotificationManager::class.java)
        val canNotify = canNotify(ctx)
        val now = LocalDateTime.now()
        val today = now.toLocalDate()
        val active = nm.activeNotifications.map { it.id }.toSet()
        val items = ReminderStore.items.value

        val notified = mutableMapOf<Long, LocalDate>()
        var nextWake: LocalDateTime? = null
        var anyShowing = false

        for (r in items) {
            val nid = r.id.toInt()
            val dueAt = r.nextDue.atTime(r.time)
            if (!dueAt.isAfter(now)) {
                if (r.notifiedFor != r.nextDue) {
                    // Due and not yet notified: post it (alerting once).
                    if (canNotify) {
                        show(ctx, r, today, silent = false)
                        notified[r.id] = r.nextDue
                        anyShowing = true
                    }
                } else if (nid in active) {
                    // Still showing: refresh the text quietly (e.g. "Due today" -> "1 day late").
                    show(ctx, r, today, silent = true)
                    anyShowing = true
                }
                // If the user swiped it away, leave it be; the app still shows it as overdue.
            } else {
                if (nid in active) nm.cancel(nid)
                if (nextWake == null || dueAt.isBefore(nextWake)) nextWake = dueAt
            }
        }

        // Notifications for reminders that no longer exist.
        val ids = items.map { it.id.toInt() }.toSet()
        active.filter { it !in ids }.forEach { nm.cancel(it) }

        ReminderStore.setNotified(notified)

        // Wake up just after midnight to update "late" counts on notifications still showing.
        if (anyShowing) {
            val midnight = today.plusDays(1).atTime(LocalTime.of(0, 1))
            if (nextWake == null || midnight.isBefore(nextWake)) nextWake = midnight
        }
        scheduleWake(ctx, nextWake)
    }

    fun cancelNotification(context: Context, id: Long) {
        NotificationManagerCompat.from(context).cancel(id.toInt())
    }

    private fun canNotify(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return false
        return NotificationManagerCompat.from(ctx).areNotificationsEnabled()
    }

    private fun ensureChannel(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(CHANNEL_ID, "Reminders", NotificationManager.IMPORTANCE_DEFAULT)
        channel.description = "Shown when something is due"
        nm.createNotificationChannel(channel)
    }

    private fun show(ctx: Context, r: Reminder, today: LocalDate, silent: Boolean) {
        if (!canNotify(ctx)) return
        val nid = r.id.toInt()
        val openIntent = Intent(ctx, MainActivity::class.java)
            .putExtra(EXTRA_ID, r.id)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val open = PendingIntent.getActivity(
            ctx, nid, openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val done = PendingIntent.getBroadcast(
            ctx, nid,
            Intent(ctx, DoneReceiver::class.java).putExtra(EXTRA_ID, r.id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val dueMillis = r.nextDue.atTime(r.time).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        val notification = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFF0F6B5C.toInt())
            .setContentTitle(r.name)
            .setContentText(Format.notificationText(r, today))
            .setContentIntent(open)
            .addAction(0, "Mark done", done)
            .addAction(0, "Open", open)
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .setSilent(silent)
            .setWhen(dueMillis)
            .setShowWhen(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        try {
            NotificationManagerCompat.from(ctx).notify(nid, notification)
        } catch (e: SecurityException) {
            // Permission was revoked in the meantime; nothing to do.
        }
    }

    private fun scheduleWake(ctx: Context, at: LocalDateTime?) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val pi = PendingIntent.getBroadcast(
            ctx, 0, Intent(ctx, AlarmReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        if (at == null) {
            am.cancel(pi)
            return
        }
        val millis = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        // Inexact on purpose: no "alarms & reminders" permission needed. May arrive a few minutes late.
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
    }
}

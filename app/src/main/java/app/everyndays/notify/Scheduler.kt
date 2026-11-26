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
    private const val ADVANCE_ID_OFFSET = 1_000_000L

    private fun mainId(id: Long) = id.toInt()
    private fun advanceId(id: Long) = (id + ADVANCE_ID_OFFSET).toInt()

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

        val mainNotified = mutableMapOf<Long, LocalDate>()
        val advanceNotified = mutableMapOf<Long, LocalDate>()
        var nextWake: LocalDateTime? = null
        var anyShowing = false

        fun wakeAt(t: LocalDateTime) {
            val current = nextWake
            if (current == null || t.isBefore(current)) nextWake = t
        }

        fun cancelIfShowing(nid: Int) {
            if (nid in active) nm.cancel(nid)
        }

        for (r in items) {
            val main = mainId(r.id)
            val adv = advanceId(r.id)

            if (!r.isActive) {
                cancelIfShowing(main)
                cancelIfShowing(adv)
                continue
            }

            if (!r.dueAt.isAfter(now)) {
                // Due: the main notification replaces any heads-up.
                cancelIfShowing(adv)
                if (r.notifiedFor != r.nextDue) {
                    if (canNotify) {
                        showMain(ctx, r, today, silent = false)
                        mainNotified[r.id] = r.nextDue
                        anyShowing = true
                    }
                } else if (main in active) {
                    // Still showing: refresh the text quietly (e.g. "Due today" -> "1 day late").
                    showMain(ctx, r, today, silent = true)
                    anyShowing = true
                }
                // If the user swiped it away, leave it be; the app still shows it as overdue.
            } else {
                cancelIfShowing(main)
                wakeAt(r.dueAt)
                val advanceAt = r.advanceAt
                if (advanceAt == null) {
                    cancelIfShowing(adv)
                } else if (!advanceAt.isAfter(now)) {
                    if (r.advanceNotifiedFor != r.nextDue && canNotify) {
                        showAdvance(ctx, r, today)
                        advanceNotified[r.id] = r.nextDue
                    }
                } else {
                    cancelIfShowing(adv)
                    wakeAt(advanceAt)
                }
            }
        }

        // Notifications for reminders that no longer exist.
        val known = items.flatMap { listOf(mainId(it.id), advanceId(it.id)) }.toSet()
        active.filter { it !in known }.forEach { nm.cancel(it) }

        ReminderStore.setNotified(mainNotified, advanceNotified)

        // Wake up just after midnight to update "late" counts on notifications still showing.
        if (anyShowing) wakeAt(today.plusDays(1).atTime(LocalTime.of(0, 1)))
        scheduleWake(ctx, nextWake)
    }

    fun cancelNotifications(context: Context, id: Long) {
        val nm = NotificationManagerCompat.from(context)
        nm.cancel(mainId(id))
        nm.cancel(advanceId(id))
    }

    fun canNotify(ctx: Context): Boolean {
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

    private fun openIntent(ctx: Context, r: Reminder, requestCode: Int): PendingIntent {
        val intent = Intent(ctx, MainActivity::class.java)
            .putExtra(EXTRA_ID, r.id)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(
            ctx, requestCode, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun millis(t: LocalDateTime): Long = t.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun showMain(ctx: Context, r: Reminder, today: LocalDate, silent: Boolean) {
        val nid = mainId(r.id)
        val open = openIntent(ctx, r, nid)
        val done = PendingIntent.getBroadcast(
            ctx, nid,
            Intent(ctx, DoneReceiver::class.java).putExtra(EXTRA_ID, r.id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
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
            .setWhen(millis(r.dueAt))
            .setShowWhen(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        post(ctx, nid, notification)
    }

    private fun showAdvance(ctx: Context, r: Reminder, today: LocalDate) {
        val nid = advanceId(r.id)
        val notification = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFF0F6B5C.toInt())
            .setContentTitle(r.name)
            .setContentText(Format.advanceText(ctx, r, today))
            .setContentIntent(openIntent(ctx, r, nid))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        post(ctx, nid, notification)
    }

    private fun post(ctx: Context, nid: Int, notification: android.app.Notification) {
        if (!canNotify(ctx)) return
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
        val t = millis(at)
        val exact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
        try {
            if (exact) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, pi)
            }
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, pi)
        }
    }
}

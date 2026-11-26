package app.everyndays.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.everyndays.data.ReminderStore
import java.time.LocalDate

/** Fires when the next reminder (or heads-up) is due. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Scheduler.reconcile(context)
    }
}

/** Reboot, app update, clock or time-zone change, exact-alarm permission change: rebuild everything. */
class SystemEventsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Scheduler.reconcile(context)
    }
}

/** The "Mark done" button on a notification. */
class DoneReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(Scheduler.EXTRA_ID, -1L)
        if (id <= 0L) return
        ReminderStore.init(context)
        ReminderStore.complete(id, LocalDate.now())
        Scheduler.cancelNotifications(context, id)
        Scheduler.reconcile(context)
    }
}

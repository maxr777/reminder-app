package app.everyndays

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.everyndays.data.Reminder
import app.everyndays.data.ReminderStore
import app.everyndays.notify.Scheduler
import app.everyndays.ui.AppTheme
import app.everyndays.ui.DetailsScreen
import app.everyndays.ui.EditScreen
import app.everyndays.ui.ListScreen
import app.everyndays.ui.Palette
import kotlinx.coroutines.launch
import java.time.LocalDate

class MainActivity : ComponentActivity() {
    /** Set when a notification is tapped: which reminder to open. */
    private val openRequest = mutableStateOf<Long?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        ReminderStore.init(this)
        if (savedInstanceState == null) openRequest.value = reminderIdFrom(intent)
        setContent {
            AppTheme {
                App(openRequest)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openRequest.value = reminderIdFrom(intent)
    }

    private fun reminderIdFrom(intent: Intent?): Long? =
        intent?.getLongExtra(Scheduler.EXTRA_ID, -1L)?.takeIf { it > 0L }
}

private fun notificationsEnabled(ctx: Context): Boolean =
    NotificationManagerCompat.from(ctx).areNotificationsEnabled()

private fun parentOf(route: String): String = when {
    route.startsWith("edit:") && route != "edit:new" -> "details:" + route.removePrefix("edit:")
    else -> "list"
}

@Composable
private fun App(openRequest: MutableState<Long?>) {
    val ctx = LocalContext.current
    val items by ReminderStore.items.collectAsState()
    var today by remember { mutableStateOf(LocalDate.now()) }
    var route by rememberSaveable { mutableStateOf("list") }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var notificationsOn by remember { mutableStateOf(notificationsEnabled(ctx)) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        notificationsOn = notificationsEnabled(ctx)
        Scheduler.reconcile(ctx)
    }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 && !notificationsOn) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // Each time the app comes to the front: refresh "today", permissions and notifications.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                today = LocalDate.now()
                notificationsOn = notificationsEnabled(ctx)
                Scheduler.reconcile(ctx)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(openRequest.value) {
        openRequest.value?.let {
            route = "details:$it"
            openRequest.value = null
        }
    }

    fun markDone(r: Reminder) {
        val now = LocalDate.now()
        val updated = ReminderStore.markDone(r.id, now) ?: return
        Scheduler.cancelNotification(ctx, r.id)
        Scheduler.reconcile(ctx)
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            val result = snackbar.showSnackbar(
                message = "Done. Next: ${Format.commaDate(updated.nextDue, now)}",
                actionLabel = "Undo",
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) {
                ReminderStore.upsert(r.copy(notifiedFor = null))
                Scheduler.reconcile(ctx)
            }
        }
    }

    BackHandler(enabled = route != "list") { route = parentOf(route) }

    Crossfade(targetState = route, label = "screen") { current ->
        when {
            current.startsWith("details:") -> {
                val id = current.removePrefix("details:").toLongOrNull()
                val r = items.find { it.id == id }
                if (r == null) {
                    LaunchedEffect(current) { if (route == current) route = "list" }
                } else {
                    DetailsScreen(
                        r = r,
                        today = today,
                        snackbar = snackbar,
                        onBack = { route = "list" },
                        onEdit = { route = "edit:${r.id}" },
                        onDelete = {
                            ReminderStore.delete(r.id)
                            Scheduler.cancelNotification(ctx, r.id)
                            Scheduler.reconcile(ctx)
                            route = "list"
                        },
                        onDone = { markDone(r) },
                    )
                }
            }

            current.startsWith("edit:") -> {
                val id = current.removePrefix("edit:").toLongOrNull()
                val existing = id?.let { i -> items.find { it.id == i } }
                EditScreen(
                    existing = existing,
                    today = today,
                    defaultColor = items.size % Palette.size,
                    onClose = { route = parentOf(current) },
                    onSave = { reminder ->
                        val saved = ReminderStore.upsert(reminder)
                        Scheduler.reconcile(ctx)
                        route = if (existing == null) "list" else "details:${saved.id}"
                    },
                )
            }

            else -> ListScreen(
                items = items,
                today = today,
                snackbar = snackbar,
                notificationsOff = !notificationsOn,
                onEnableNotifications = {
                    ctx.startActivity(
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
                    )
                },
                onOpen = { route = "details:$it" },
                onAdd = { route = "edit:new" },
                onDone = { markDone(it) },
            )
        }
    }
}

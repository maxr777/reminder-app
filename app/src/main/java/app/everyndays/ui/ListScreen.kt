package app.everyndays.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.everyndays.Format
import app.everyndays.data.Reminder
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate

@Composable
fun ListScreen(
    items: List<Reminder>,
    today: LocalDate,
    snackbar: SnackbarHostState,
    notificationsOff: Boolean,
    onEnableNotifications: () -> Unit,
    onOpen: (Long) -> Unit,
    onAdd: () -> Unit,
    onDone: (Reminder) -> Unit,
    onBackup: () -> Unit,
    onRestore: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    var showFinished by rememberSaveable { mutableStateOf(false) }

    val active = items.filter { it.isActive }
        .sortedWith(compareBy<Reminder>({ it.nextDue }, { it.time }, { it.name.lowercase() }))
    val overdue = active.filter { it.nextDue < today }
    val dueToday = active.filter { it.nextDue == today }
    val tomorrow = active.filter { it.nextDue == today.plusDays(1) }
    val thisWeek = active.filter { it.nextDue > today.plusDays(1) && it.nextDue < today.plusDays(7) }
    val later = active.filter { it.nextDue >= today.plusDays(7) }
    val paused = items.filter { it.paused && !it.finished }.sortedBy { it.name.lowercase() }
    val finished = items.filter { it.finished }.sortedByDescending { it.finishedOn }

    Scaffold(
        containerColor = colors.background,
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAdd,
                shape = RoundedCornerShape(20.dp),
                containerColor = colors.primary,
                contentColor = colors.onPrimary,
                modifier = Modifier.size(64.dp),
            ) {
                Icon(Icons.Filled.Add, contentDescription = "New reminder", modifier = Modifier.size(28.dp))
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + 104.dp,
            ),
        ) {
            item(key = "header") { Header(today, onBackup, onRestore) }
            if (notificationsOff) {
                item(key = "banner") { NotificationsBanner(onEnableNotifications) }
            }
            if (items.isEmpty()) {
                item(key = "empty") { EmptyState() }
            }
            section("Overdue", colors.error, overdue, today, onOpen, onDone)
            section("Today", colors.primary, dueToday, today, onOpen, onDone)
            section("Tomorrow", colors.onSurfaceVariant, tomorrow, today, onOpen, onDone)
            section("This week", colors.onSurfaceVariant, thisWeek, today, onOpen, onDone)
            section("Later", colors.onSurfaceVariant, later, today, onOpen, onDone)
            section("Paused", colors.onSurfaceVariant, paused, today, onOpen, onDone)
            if (finished.isNotEmpty()) {
                item(key = "finished-toggle") {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp)
                            .clickable { showFinished = !showFinished }
                            .heightIn(min = 48.dp)
                            .padding(horizontal = 24.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "FINISHED (${finished.size})",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.8.sp,
                            color = colors.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(
                            if (showFinished) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                            contentDescription = if (showFinished) "Hide finished" else "Show finished",
                            tint = colors.onSurfaceVariant,
                        )
                    }
                }
                if (showFinished) {
                    items(finished, key = { it.id }) { r ->
                        ReminderRow(r, today, onOpen = { onOpen(r.id) }, onDone = {})
                    }
                }
            }
        }
    }
}

private fun LazyListScope.section(
    title: String,
    color: Color,
    list: List<Reminder>,
    today: LocalDate,
    onOpen: (Long) -> Unit,
    onDone: (Reminder) -> Unit,
) {
    if (list.isEmpty()) return
    item(key = "section-$title") {
        Text(
            text = title.uppercase(),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.8.sp,
            color = color,
            modifier = Modifier.padding(start = 24.dp, top = 20.dp, bottom = 2.dp),
        )
    }
    items(list, key = { it.id }) { r ->
        ReminderRow(r, today, onOpen = { onOpen(r.id) }, onDone = { onDone(r) })
    }
}

@Composable
private fun Header(today: LocalDate, onBackup: () -> Unit, onRestore: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier.padding(start = 24.dp, end = 8.dp, top = 24.dp, bottom = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f).padding(top = 8.dp)) {
            Text(
                "Reminders",
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.5).sp,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.size(4.dp))
            Text(
                Format.longDate(today, today),
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box {
            IconButton(onClick = { menu = true }) {
                Icon(Icons.Filled.MoreVert, contentDescription = "More")
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text("Back up to a file") },
                    onClick = {
                        menu = false
                        onBackup()
                    },
                )
                DropdownMenuItem(
                    text = { Text("Restore from a file") },
                    onClick = {
                        menu = false
                        onRestore()
                    },
                )
            }
        }
    }
}

@Composable
private fun ReminderRow(r: Reminder, today: LocalDate, onOpen: () -> Unit, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val diff = Format.daysBetween(today, r.nextDue)
    val tint = Palette.color(r.color)
    val scope = rememberCoroutineScope()
    var checking by remember(r.id, r.nextDue, r.finished) { mutableStateOf(false) }
    val filled = checking || r.finished

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .heightIn(min = 68.dp)
            .padding(start = 12.dp, end = 24.dp, top = 4.dp, bottom = 4.dp)
            .alpha(if (r.isActive) 1f else 0.6f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = {
                if (r.isActive && !checking) {
                    checking = true
                    scope.launch {
                        delay(350)
                        onDone()
                    }
                }
            },
            enabled = r.isActive,
            modifier = Modifier
                .size(48.dp)
                .semantics { contentDescription = if (r.finished) "${r.name}, done" else "Mark ${r.name} done" },
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(if (filled) tint else Color.Transparent)
                    .border(2.dp, tint, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (filled) {
                    Icon(Icons.Filled.Check, contentDescription = null, tint = colors.background, modifier = Modifier.size(16.dp))
                }
            }
        }
        Spacer(Modifier.width(4.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                r.name,
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium,
                color = colors.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                Format.subtitle(r, today),
                fontSize = 14.sp,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (r.isActive) {
            Spacer(Modifier.width(12.dp))
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                when {
                    diff < 0 -> Text(
                        "${Format.days(-diff)} late",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.error,
                    )
                    diff == 0 -> Text("Today", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.primary)
                    else -> Text(
                        Format.relative(r.nextDue, today),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = colors.onSurface,
                    )
                }
                val second = if (diff in 0..1) Format.time(ctx, r.time) else Format.shortDate(r.nextDue, today)
                Text(second, fontSize = 13.sp, color = colors.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun EmptyState() {
    Column(
        Modifier.padding(start = 24.dp, end = 24.dp, top = 48.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            "Nothing here yet",
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            "Tap + to add a reminder: something you do every few days, every week or month, " +
                "or just once.",
            fontSize = 15.sp,
            lineHeight = 21.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun NotificationsBanner(onEnable: () -> Unit) {
    Row(
        modifier = Modifier
            .padding(start = 16.dp, end = 16.dp, top = 12.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "Notifications are off, so you won't get reminders.",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onEnable) { Text("Turn on") }
    }
}

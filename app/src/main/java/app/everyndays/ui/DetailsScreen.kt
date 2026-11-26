package app.everyndays.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.everyndays.Format
import app.everyndays.data.Reminder
import java.time.LocalDate

@Composable
fun DetailsScreen(
    r: Reminder,
    today: LocalDate,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onDone: () -> Unit,
    onSkip: () -> Unit,
    onPause: (Boolean) -> Unit,
) {
    val ctx = LocalContext.current
    val colors = MaterialTheme.colorScheme
    var confirmDelete by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    val diff = Format.daysBetween(today, r.nextDue)
    val history = r.history.sortedByDescending { it.done }
    val recurring = r.schedule.isRecurring

    Scaffold(
        containerColor = colors.background,
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
        ) {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(start = 4.dp, end = 8.dp, top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Outlined.Edit, contentDescription = "Edit")
                    }
                    Box {
                        IconButton(onClick = { menu = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "More")
                        }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            if (recurring && r.isActive) {
                                DropdownMenuItem(
                                    text = { Text("Skip this one") },
                                    onClick = {
                                        menu = false
                                        onSkip()
                                    },
                                )
                            }
                            if (recurring && !r.finished) {
                                DropdownMenuItem(
                                    text = { Text(if (r.paused) "Resume" else "Pause") },
                                    onClick = {
                                        menu = false
                                        onPause(!r.paused)
                                    },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Delete", color = colors.error) },
                                onClick = {
                                    menu = false
                                    confirmDelete = true
                                },
                            )
                        }
                    }
                }
            }

            item {
                Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 8.dp)) {
                    Text(
                        r.name,
                        fontSize = 30.sp,
                        lineHeight = 36.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.5).sp,
                        color = colors.onBackground,
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(Palette.color(r.color))
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "${Format.schedule(r)} · at ${Format.time(ctx, r.time)}",
                            fontSize = 15.sp,
                            color = colors.onSurfaceVariant,
                        )
                    }
                    val extras = listOfNotNull(
                        Format.ending(r, today),
                        if (r.advanceMinutes > 0) "Heads-up ${Format.advanceLabel(r.advanceMinutes)} before" else null,
                    )
                    if (extras.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            extras.joinToString(" · "),
                            fontSize = 14.sp,
                            color = colors.onSurfaceVariant,
                            modifier = Modifier.padding(start = 18.dp),
                        )
                    }
                }
            }

            item {
                StatusCard(r, today, diff, onDone = onDone, onResume = { onPause(false) }, onEdit = onEdit)
            }

            if (r.note.isNotBlank()) {
                item {
                    Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 28.dp)) {
                        Text("Note", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = colors.onBackground)
                        Spacer(Modifier.height(8.dp))
                        Text(r.note, fontSize = 16.sp, lineHeight = 22.sp, color = colors.onSurface)
                    }
                }
            }

            item {
                Text(
                    "History",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.onBackground,
                    modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 28.dp, bottom = 8.dp),
                )
            }
            if (history.isEmpty()) {
                item {
                    Text(
                        "Nothing yet. Each time you mark it done, it shows up here.",
                        fontSize = 15.sp,
                        lineHeight = 21.sp,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 24.dp),
                    )
                }
            } else {
                items(history) { c ->
                    Column(Modifier.padding(horizontal = 24.dp)) {
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 52.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                Format.commaDate(c.done, today),
                                fontSize = 16.sp,
                                color = colors.onSurface,
                                modifier = Modifier.weight(1f),
                            )
                            val late = !c.skipped && Format.daysBetween(c.due, c.done) > 0
                            Text(
                                Format.completionLabel(c),
                                fontSize = 14.sp,
                                color = if (late) colors.error else colors.onSurfaceVariant,
                            )
                        }
                        HorizontalDivider(color = colors.outlineVariant)
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete “${r.name}”?") },
            text = { Text("Its history will be deleted too.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete()
                }) { Text("Delete", color = colors.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun StatusCard(
    r: Reminder,
    today: LocalDate,
    diff: Int,
    onDone: () -> Unit,
    onResume: () -> Unit,
    onEdit: () -> Unit,
) {
    val ctx = LocalContext.current
    val colors = MaterialTheme.colorScheme

    val label: String
    val title: String
    val subtitle: String?
    val button: String
    val action: () -> Unit
    val footer: String?
    when {
        r.finished -> {
            label = "FINISHED"
            title = r.finishedOn?.let { "Done ${Format.longDate(it, today)}" } ?: "Finished"
            subtitle = null
            button = "Set up again"
            action = onEdit
            footer = null
        }
        r.paused -> {
            label = "PAUSED"
            title = "Not reminding you"
            subtitle = "Resume to pick up from today."
            button = "Resume"
            action = onResume
            footer = null
        }
        else -> {
            label = when {
                diff < 0 -> "WAS DUE"
                !r.schedule.isRecurring -> "DUE"
                else -> "NEXT REMINDER"
            }
            title = Format.longDate(r.nextDue, today)
            subtitle = "${Format.relative(r.nextDue, today)} · at ${Format.time(ctx, r.time)}" +
                if (r.isCycle) " · ${Format.cycleDay(r)}" else ""
            button = if (r.schedule.isRecurring) "Done today" else "Mark done"
            action = onDone
            footer = if (!r.schedule.isRecurring) {
                null
            } else {
                val next = r.nextIfDoneOn(today)
                if (next == null) "That would be the last one" else "Next one would then be ${Format.commaDate(next, today)}"
            }
        }
    }

    Column(
        Modifier
            .padding(start = 16.dp, end = 16.dp, top = 24.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(colors.primaryContainer)
            .padding(20.dp)
    ) {
        Text(
            label,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.8.sp,
            color = colors.onPrimaryContainer,
        )
        Spacer(Modifier.height(4.dp))
        Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = colors.onSurface)
        if (subtitle != null) {
            Text(
                subtitle,
                fontSize = 15.sp,
                color = if (r.isActive && diff < 0) colors.error else colors.onPrimaryContainer,
            )
        }
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = action,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            if (r.isActive) {
                Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(button, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
        if (footer != null) {
            Spacer(Modifier.height(12.dp))
            Text(
                footer,
                fontSize = 13.sp,
                color = colors.onPrimaryContainer,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

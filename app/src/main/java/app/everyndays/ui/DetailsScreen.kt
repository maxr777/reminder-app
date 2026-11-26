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
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
) {
    val ctx = LocalContext.current
    val colors = MaterialTheme.colorScheme
    var confirmDelete by remember { mutableStateOf(false) }
    val diff = Format.daysBetween(today, r.nextDue)
    val history = r.history.sortedByDescending { it.done }

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
                    IconButton(onClick = { confirmDelete = true }) {
                        Icon(Icons.Outlined.Delete, contentDescription = "Delete")
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
                            "${Format.every(r.intervalDays)} · at ${Format.time(ctx, r.time)}",
                            fontSize = 15.sp,
                            color = colors.onSurfaceVariant,
                        )
                    }
                }
            }

            item {
                Column(
                    Modifier
                        .padding(start = 16.dp, end = 16.dp, top = 24.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(colors.primaryContainer)
                        .padding(20.dp)
                ) {
                    Text(
                        if (diff < 0) "WAS DUE" else "NEXT REMINDER",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.8.sp,
                        color = colors.onPrimaryContainer,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        Format.longDate(r.nextDue, today),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.onSurface,
                    )
                    Text(
                        Format.relative(r.nextDue, today),
                        fontSize = 15.sp,
                        color = if (diff < 0) colors.error else colors.onPrimaryContainer,
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = onDone,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    ) {
                        Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Done today", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Next one would then be ${Format.commaDate(today.plusDays(r.intervalDays.toLong()), today)}",
                        fontSize = 13.sp,
                        color = colors.onPrimaryContainer,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
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
                            val late = Format.daysBetween(c.due, c.done) > 0
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

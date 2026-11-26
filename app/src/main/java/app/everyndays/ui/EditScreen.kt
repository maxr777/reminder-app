package app.everyndays.ui

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import app.everyndays.Format
import app.everyndays.R
import app.everyndays.data.Reminder
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

private val QuickIntervals = listOf(1, 2, 3, 7, 14, 30)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditScreen(
    existing: Reminder?,
    today: LocalDate,
    defaultColor: Int,
    onClose: () -> Unit,
    onSave: (Reminder) -> Unit,
) {
    val ctx = LocalContext.current
    val colors = MaterialTheme.colorScheme

    val existingCycle = existing?.isCycle == true
    var name by rememberSaveable { mutableStateOf(existing?.name ?: "") }
    var cycle by rememberSaveable { mutableStateOf(existingCycle) }
    var interval by rememberSaveable {
        mutableIntStateOf(if (existing != null && !existingCycle) existing.offDays + 1 else 7)
    }
    var onDays by rememberSaveable { mutableIntStateOf(if (existing != null && existingCycle) existing.onDays else 2) }
    var offDays by rememberSaveable { mutableIntStateOf(if (existing != null && existingCycle) existing.offDays else 1) }
    var streak by rememberSaveable { mutableIntStateOf(existing?.streak ?: 0) }
    var due by rememberSaveable { mutableStateOf(existing?.nextDue ?: today) }
    var time by rememberSaveable { mutableStateOf(existing?.time ?: LocalTime.of(20, 0)) }
    var color by rememberSaveable { mutableIntStateOf(existing?.color ?: defaultColor) }
    var note by rememberSaveable { mutableStateOf(existing?.note ?: "") }

    var showDate by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }
    var showInterval by remember { mutableStateOf(false) }

    val nameFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        if (existing == null) nameFocus.requestFocus()
    }

    // When editing, keep the next date in step with the last time it was done.
    fun recomputeDue() {
        val last = existing?.lastDone ?: return
        due = when {
            !cycle -> last.plusDays(interval.toLong())
            streak == 0 -> last.plusDays(offDays + 1L)
            else -> last.plusDays(1)
        }
    }

    fun setInterval(n: Int) {
        interval = n.coerceIn(1, 365)
        recomputeDue()
    }

    fun setOnDays(n: Int) {
        onDays = n.coerceIn(2, 14)
        streak = streak.coerceAtMost(onDays - 1)
        recomputeDue()
    }

    fun setOffDays(n: Int) {
        offDays = n.coerceIn(1, 60)
        recomputeDue()
    }

    fun setStreak(n: Int) {
        streak = n
        recomputeDue()
    }

    fun setCycle(c: Boolean) {
        if (cycle == c) return
        cycle = c
        streak = 0
        recomputeDue()
    }

    fun save() {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        val scheduleChanged = existing == null || existing.nextDue != due || existing.time != time
        val base = existing ?: Reminder(name = trimmed, offDays = 0, time = time, nextDue = due)
        onSave(
            base.copy(
                name = trimmed,
                onDays = if (cycle) onDays else 1,
                offDays = if (cycle) offDays else interval - 1,
                streak = if (cycle) streak.coerceIn(0, onDays - 1) else 0,
                time = time,
                nextDue = due,
                color = color,
                note = note.trim(),
                notifiedFor = if (scheduleChanged) null else base.notifiedFor,
            )
        )
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .systemBarsPadding()
            .imePadding()
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 4.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = "Close")
            }
            Text(
                if (existing == null) "New reminder" else "Edit reminder",
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.onBackground,
                modifier = Modifier.weight(1f).padding(start = 4.dp),
            )
            Button(
                onClick = { save() },
                enabled = name.isNotBlank(),
                shape = RoundedCornerShape(22.dp),
                contentPadding = PaddingValues(horizontal = 20.dp),
            ) {
                Text("Save", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(26.dp),
        ) {
            // Name
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FieldLabel("Name")
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    placeholder = { Text("e.g. Vitamin D3") },
                    shape = RoundedCornerShape(14.dp),
                    textStyle = TextStyle(fontSize = 17.sp),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Done,
                    ),
                    modifier = Modifier.fillMaxWidth().focusRequester(nameFocus),
                )
            }

            // Repeat
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                FieldLabel("Repeat")
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = !cycle,
                        onClick = { setCycle(false) },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    ) { Text("Every N days") }
                    SegmentedButton(
                        selected = cycle,
                        onClick = { setCycle(true) },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    ) { Text("Days on / off") }
                }
                if (!cycle) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        OutlinedIconButton(
                            onClick = { setInterval(interval - 1) },
                            enabled = interval > 1,
                            modifier = Modifier.size(48.dp),
                        ) {
                            Icon(painterResource(R.drawable.ic_remove), contentDescription = "One day less")
                        }
                        Row(
                            Modifier
                                .widthIn(min = 104.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { showInterval = true }
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            Text(
                                "$interval",
                                fontSize = 40.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.onBackground,
                                modifier = Modifier.alignByBaseline(),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (interval == 1) "day" else "days",
                                fontSize = 17.sp,
                                color = colors.onSurfaceVariant,
                                modifier = Modifier.alignByBaseline(),
                            )
                        }
                        OutlinedIconButton(
                            onClick = { setInterval(interval + 1) },
                            enabled = interval < 365,
                            modifier = Modifier.size(48.dp),
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = "One day more")
                        }
                    }
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        QuickIntervals.forEach { n ->
                            FilterChip(
                                selected = interval == n,
                                onClick = { setInterval(n) },
                                label = { Text("$n", fontSize = 14.sp) },
                                shape = RoundedCornerShape(20.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = colors.secondaryContainer,
                                    selectedLabelColor = colors.onSecondaryContainer,
                                ),
                            )
                        }
                    }
                    Text(
                        "Counted from the day you mark it done.",
                        fontSize = 13.sp,
                        color = colors.onSurfaceVariant,
                    )
                } else {
                    CounterRow("Days on", onDays, min = 2, max = 14, onChange = { setOnDays(it) })
                    CounterRow("Days off", offDays, min = 1, max = 60, onChange = { setOffDays(it) })
                    Text("Next time is", fontSize = 14.sp, color = colors.onSurfaceVariant)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        (0 until onDays).forEach { i ->
                            FilterChip(
                                selected = streak == i,
                                onClick = { setStreak(i) },
                                label = { Text("Day ${i + 1} of $onDays", fontSize = 14.sp) },
                                shape = RoundedCornerShape(20.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = colors.secondaryContainer,
                                    selectedLabelColor = colors.onSecondaryContainer,
                                ),
                            )
                        }
                    }
                    Text(
                        "Do it $onDays days in a row, then skip ${Format.days(offDays)}. " +
                            "Each step counts from the day you mark it done.",
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        color = colors.onSurfaceVariant,
                    )
                }
            }

            // Date and time
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FieldLabel(if (existing == null) "First time" else "Next time")
                    PickerField(
                        icon = rememberVectorPainter(Icons.Outlined.DateRange),
                        text = Format.dayLabel(due, today),
                        onClick = { showDate = true },
                    )
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FieldLabel("Remind at")
                    PickerField(
                        icon = painterResource(R.drawable.ic_clock),
                        text = Format.time(ctx, time),
                        onClick = { showTime = true },
                    )
                }
            }

            // Color
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FieldLabel("Color")
                Row(Modifier.offset(x = (-6).dp)) {
                    Palette.names.forEachIndexed { i, label ->
                        val c = Palette.color(i)
                        val selected = color == i
                        Box(
                            Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .clickable { color = i }
                                .semantics {
                                    contentDescription = label
                                    this.selected = selected
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                Modifier
                                    .size(34.dp)
                                    .then(if (selected) Modifier.border(2.dp, c, CircleShape) else Modifier)
                                    .padding(5.dp)
                                    .clip(CircleShape)
                                    .background(c)
                            )
                        }
                    }
                }
            }

            // Note
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FieldLabel("Note (optional)")
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    minLines = 3,
                    placeholder = { Text("e.g. Take with a meal") },
                    shape = RoundedCornerShape(14.dp),
                    textStyle = TextStyle(fontSize = 16.sp),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    if (showInterval) {
        var text by remember { mutableStateOf(interval.toString()) }
        AlertDialog(
            onDismissRequest = { showInterval = false },
            title = { Text("Repeat every") },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { v -> text = v.filter { it.isDigit() }.take(3) },
                    singleLine = true,
                    suffix = { Text("days") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        text.toIntOrNull()?.let { if (it > 0) setInterval(it) }
                        showInterval = false
                    },
                ) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showInterval = false }) { Text("Cancel") }
            },
        )
    }

    if (showDate) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = due.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        state.selectedDateMillis?.let {
                            due = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                        }
                        showDate = false
                    },
                ) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDate = false }) { Text("Cancel") }
            },
        ) {
            DatePicker(state = state)
        }
    }

    if (showTime) {
        val state = rememberTimePickerState(
            initialHour = time.hour,
            initialMinute = time.minute,
            is24Hour = DateFormat.is24HourFormat(ctx),
        )
        Dialog(onDismissRequest = { showTime = false }) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = colors.surfaceContainerHigh,
            ) {
                Column(
                    Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        "Remind at",
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp),
                    )
                    TimePicker(state = state)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { showTime = false }) { Text("Cancel") }
                        TextButton(
                            onClick = {
                                time = LocalTime.of(state.hour, state.minute)
                                showTime = false
                            },
                        ) { Text("OK") }
                    }
                }
            }
        }
    }
}

@Composable
private fun CounterRow(label: String, value: Int, min: Int, max: Int, onChange: (Int) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 16.sp, color = colors.onSurface, modifier = Modifier.weight(1f))
        OutlinedIconButton(
            onClick = { onChange(value - 1) },
            enabled = value > min,
            modifier = Modifier.size(44.dp),
        ) {
            Icon(painterResource(R.drawable.ic_remove), contentDescription = "Fewer ${label.lowercase()}")
        }
        Text(
            "$value",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = colors.onBackground,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(56.dp),
        )
        OutlinedIconButton(
            onClick = { onChange(value + 1) },
            enabled = value < max,
            modifier = Modifier.size(44.dp),
        ) {
            Icon(Icons.Filled.Add, contentDescription = "More ${label.lowercase()}")
        }
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
private fun PickerField(icon: Painter, text: String, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(shape)
            .border(1.dp, colors.outline, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, fontSize = 16.sp, color = colors.onSurface)
    }
}

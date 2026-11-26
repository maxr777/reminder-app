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
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.Color
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
import app.everyndays.data.EndKind
import app.everyndays.data.Reminder
import app.everyndays.data.RepeatKind
import app.everyndays.data.Schedule
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.TextStyle as JavaTextStyle
import java.util.Locale

private val QuickIntervals = listOf(1, 2, 3, 7, 14, 30)

private val KindChoices = listOf(
    RepeatKind.ONCE to "Once",
    RepeatKind.DAYS to "Every N days",
    RepeatKind.CYCLE to "Days on / off",
    RepeatKind.WEEKLY to "Weekly",
    RepeatKind.MONTHLY to "Monthly",
    RepeatKind.YEARLY to "Yearly",
)

private fun maskOf(days: Set<Int>): Int = days.fold(0) { m, d -> m or (1 shl (d - 1)) }
private fun daysOf(mask: Int): Set<Int> = (1..7).filter { mask and (1 shl (it - 1)) != 0 }.toSet()

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
    val s0 = existing?.schedule

    var name by rememberSaveable { mutableStateOf(existing?.name ?: "") }
    var kind by rememberSaveable { mutableStateOf(s0?.kind ?: RepeatKind.DAYS) }
    var interval by rememberSaveable { mutableIntStateOf(s0?.takeIf { it.kind == RepeatKind.DAYS }?.every ?: 7) }
    var fromDone by rememberSaveable { mutableStateOf(s0?.fromDone ?: true) }
    var onDays by rememberSaveable { mutableIntStateOf(s0?.takeIf { it.kind == RepeatKind.CYCLE }?.onDays ?: 2) }
    var offDays by rememberSaveable { mutableIntStateOf(s0?.takeIf { it.kind == RepeatKind.CYCLE }?.offDays ?: 1) }
    var streak by rememberSaveable { mutableIntStateOf(existing?.streak ?: 0) }
    var weekdayMask by rememberSaveable {
        mutableIntStateOf(
            s0?.takeIf { it.kind == RepeatKind.WEEKLY && it.weekdays.isNotEmpty() }?.let { maskOf(it.weekdays) }
                ?: maskOf(setOf(today.dayOfWeek.value))
        )
    }
    var weeks by rememberSaveable { mutableIntStateOf(s0?.takeIf { it.kind == RepeatKind.WEEKLY }?.every ?: 1) }
    var monthDay by rememberSaveable { mutableIntStateOf(s0?.takeIf { it.kind == RepeatKind.MONTHLY }?.monthDay ?: today.dayOfMonth) }
    var months by rememberSaveable { mutableIntStateOf(s0?.takeIf { it.kind == RepeatKind.MONTHLY }?.every ?: 1) }
    var date by rememberSaveable {
        mutableStateOf(
            when {
                existing == null -> today
                existing.finished -> maxOf(existing.nextDue, today)
                else -> existing.nextDue
            }
        )
    }
    var time by rememberSaveable { mutableStateOf(existing?.time ?: LocalTime.of(20, 0)) }
    var advance by rememberSaveable { mutableIntStateOf(existing?.advanceMinutes ?: 0) }
    var endKind by rememberSaveable { mutableStateOf(existing?.endKind ?: EndKind.NEVER) }
    var endDate by rememberSaveable { mutableStateOf(existing?.endDate ?: today.plusMonths(1)) }
    var endCount by rememberSaveable { mutableIntStateOf(existing?.endCount?.takeIf { it > 0 } ?: 10) }
    var color by rememberSaveable { mutableIntStateOf(existing?.color ?: defaultColor) }
    var note by rememberSaveable { mutableStateOf(existing?.note ?: "") }

    // Which dialog is open: "date", "endDate", "time", "interval", "count", or null.
    var dialog by remember { mutableStateOf<String?>(null) }

    val nameFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        if (existing == null) nameFocus.requestFocus()
    }

    val weekdays = daysOf(weekdayMask)

    fun buildSchedule(): Schedule = Schedule(
        kind = kind,
        every = when (kind) {
            RepeatKind.DAYS -> interval
            RepeatKind.WEEKLY -> weeks
            RepeatKind.MONTHLY -> months
            else -> 1
        },
        fromDone = fromDone,
        onDays = onDays,
        offDays = offDays,
        weekdays = if (kind == RepeatKind.WEEKLY) weekdays else emptySet(),
        monthDay = monthDay,
        anchor = date,
    )

    /** The first due date the current settings give. */
    fun firstDue(): LocalDate {
        val s = buildSchedule()
        val from = if (kind == RepeatKind.YEARLY && date.isBefore(today)) today else date
        return s.firstOnOrAfter(from)
    }

    // When editing a from-done schedule, keep the next date in step with the last time it was done.
    fun recomputeDue() {
        val last = existing?.lastDone ?: return
        date = when (kind) {
            RepeatKind.DAYS -> if (fromDone) last.plusDays(interval.toLong()) else return
            RepeatKind.CYCLE -> if (streak == 0) last.plusDays(offDays + 1L) else last.plusDays(1)
            else -> return
        }
    }

    val canSave = name.isNotBlank() && (kind != RepeatKind.WEEKLY || weekdays.isNotEmpty())

    fun save() {
        val trimmed = name.trim()
        if (!canSave) return
        val schedule = buildSchedule()
        val due = firstDue()
        val scheduleChanged = existing == null || existing.nextDue != due || existing.time != time ||
            existing.finished || existing.advanceMinutes != advance
        val base = existing ?: Reminder(name = trimmed, schedule = schedule, time = time, nextDue = due)
        onSave(
            base.copy(
                name = trimmed,
                schedule = schedule,
                time = time,
                nextDue = due,
                streak = if (kind == RepeatKind.CYCLE) streak.coerceIn(0, onDays - 1) else 0,
                endKind = if (kind == RepeatKind.ONCE) EndKind.NEVER else endKind,
                endDate = if (endKind == EndKind.ON_DATE) endDate else null,
                endCount = if (endKind == EndKind.AFTER_COUNT) endCount else 0,
                advanceMinutes = advance,
                finished = false,
                color = color,
                note = note.trim(),
                notifiedFor = if (scheduleChanged) null else base.notifiedFor,
                advanceNotifiedFor = if (scheduleChanged) null else base.advanceNotifiedFor,
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
                enabled = canSave,
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
                    placeholder = { Text("e.g. Vitamin D3, Dentist") },
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
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                FieldLabel("Repeat")
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    KindChoices.forEach { (k, label) ->
                        Choice(label, selected = kind == k) {
                            if (kind != k) {
                                kind = k
                                streak = 0
                            }
                        }
                    }
                }

                when (kind) {
                    RepeatKind.ONCE -> {}

                    RepeatKind.DAYS -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            RoundButton(minus = true, enabled = interval > 1, description = "One day less") {
                                interval = (interval - 1).coerceAtLeast(1)
                                recomputeDue()
                            }
                            Row(
                                Modifier
                                    .widthIn(min = 104.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { dialog = "interval" }
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
                            RoundButton(minus = false, enabled = interval < 365, description = "One day more") {
                                interval = (interval + 1).coerceAtMost(365)
                                recomputeDue()
                            }
                        }
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            QuickIntervals.forEach { n ->
                                Choice("$n", selected = interval == n) {
                                    interval = n
                                    recomputeDue()
                                }
                            }
                        }
                        SwitchRow(
                            title = "Count from when I do it",
                            subtitle = if (fromDone) {
                                "If you're late, the next one moves too."
                            } else {
                                "Keeps a fixed rhythm, even if you're late."
                            },
                            checked = fromDone,
                            onChange = {
                                fromDone = it
                                recomputeDue()
                            },
                        )
                    }

                    RepeatKind.CYCLE -> {
                        CounterRow(
                            label = "Days on",
                            value = Format.days(onDays),
                            canDecrease = onDays > 2,
                            canIncrease = onDays < 14,
                            onDecrease = {
                                onDays -= 1
                                streak = streak.coerceAtMost(onDays - 1)
                                recomputeDue()
                            },
                            onIncrease = {
                                onDays += 1
                                recomputeDue()
                            },
                        )
                        CounterRow(
                            label = "Days off",
                            value = Format.days(offDays),
                            canDecrease = offDays > 1,
                            canIncrease = offDays < 60,
                            onDecrease = {
                                offDays -= 1
                                recomputeDue()
                            },
                            onIncrease = {
                                offDays += 1
                                recomputeDue()
                            },
                        )
                        Text("Next time is", fontSize = 14.sp, color = colors.onSurfaceVariant)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            (0 until onDays).forEach { i ->
                                Choice("Day ${i + 1} of $onDays", selected = streak == i) {
                                    streak = i
                                    recomputeDue()
                                }
                            }
                        }
                        Hint(
                            "Do it $onDays days in a row, then skip ${Format.days(offDays)}. " +
                                "Each step counts from the day you mark it done."
                        )
                    }

                    RepeatKind.WEEKLY -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            (1..7).forEach { iso ->
                                val selected = iso in weekdays
                                val full = DayOfWeek.of(iso).getDisplayName(JavaTextStyle.FULL, Locale.ENGLISH)
                                Box(
                                    Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(if (selected) colors.primary else Color.Transparent)
                                        .border(1.dp, if (selected) colors.primary else colors.outline, CircleShape)
                                        .clickable { weekdayMask = weekdayMask xor (1 shl (iso - 1)) }
                                        .semantics {
                                            contentDescription = full
                                            this.selected = selected
                                        },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        full.take(1),
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (selected) colors.onPrimary else colors.onSurface,
                                    )
                                }
                            }
                        }
                        if (weekdays.isEmpty()) {
                            Text("Pick at least one day.", fontSize = 13.sp, color = colors.error)
                        }
                        CounterRow(
                            label = "Repeat every",
                            value = if (weeks == 1) "week" else "$weeks weeks",
                            canDecrease = weeks > 1,
                            canIncrease = weeks < 8,
                            onDecrease = { weeks -= 1 },
                            onIncrease = { weeks += 1 },
                        )
                    }

                    RepeatKind.MONTHLY -> {
                        CounterRow(
                            label = "On day",
                            value = if (monthDay <= 0) "last" else Format.ordinal(monthDay),
                            canDecrease = monthDay != 1,
                            canIncrease = monthDay in 1..30,
                            onDecrease = { monthDay = if (monthDay <= 0) 31 else monthDay - 1 },
                            onIncrease = { monthDay += 1 },
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Choice("1st", selected = monthDay == 1) { monthDay = 1 }
                            Choice("15th", selected = monthDay == 15) { monthDay = 15 }
                            Choice("Last day", selected = monthDay <= 0) { monthDay = 0 }
                        }
                        CounterRow(
                            label = "Repeat every",
                            value = if (months == 1) "month" else "$months months",
                            canDecrease = months > 1,
                            canIncrease = months < 12,
                            onDecrease = { months -= 1 },
                            onIncrease = { months += 1 },
                        )
                        if (monthDay in 29..31) {
                            Hint("In shorter months it falls on the last day.")
                        }
                    }

                    RepeatKind.YEARLY -> {
                        Hint("Every year on ${Format.schedule(buildSchedule()).removePrefix("Yearly on ")}. Pick the date below.")
                    }
                }
            }

            // Date and time
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        FieldLabel(
                            when (kind) {
                                RepeatKind.ONCE, RepeatKind.YEARLY -> "Date"
                                RepeatKind.WEEKLY, RepeatKind.MONTHLY -> "Starting"
                                else -> if (existing == null) "First time" else "Next time"
                            }
                        )
                        PickerField(
                            icon = rememberVectorPainter(Icons.Outlined.DateRange),
                            text = Format.dayLabel(date, today),
                            onClick = { dialog = "date" },
                        )
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        FieldLabel("Remind at")
                        PickerField(
                            icon = painterResource(R.drawable.ic_clock),
                            text = Format.time(ctx, time),
                            onClick = { dialog = "time" },
                        )
                    }
                }
                if (kind != RepeatKind.WEEKLY || weekdays.isNotEmpty()) {
                    val first = firstDue()
                    if (first != date) {
                        Hint("First time: ${Format.commaDate(first, today)}")
                    }
                }
            }

            // Heads-up
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FieldLabel("Heads-up before")
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Format.advanceChoices.forEach { m ->
                        Choice(Format.advanceLabel(m), selected = advance == m) { advance = m }
                    }
                }
                Hint("An extra notification ahead of time, e.g. for appointments.")
            }

            // Ends
            if (kind != RepeatKind.ONCE) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    FieldLabel("Ends")
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Choice("Never", selected = endKind == EndKind.NEVER) { endKind = EndKind.NEVER }
                        Choice("On a date", selected = endKind == EndKind.ON_DATE) { endKind = EndKind.ON_DATE }
                        Choice("After a number of times", selected = endKind == EndKind.AFTER_COUNT) {
                            endKind = EndKind.AFTER_COUNT
                        }
                    }
                    when (endKind) {
                        EndKind.ON_DATE -> PickerField(
                            icon = rememberVectorPainter(Icons.Outlined.DateRange),
                            text = "Last one by ${Format.commaDate(endDate, today)}",
                            onClick = { dialog = "endDate" },
                        )
                        EndKind.AFTER_COUNT -> CounterRow(
                            label = "Times",
                            value = Format.times(endCount),
                            canDecrease = endCount > 1,
                            canIncrease = endCount < 999,
                            onDecrease = { endCount -= 1 },
                            onIncrease = { endCount += 1 },
                            onValueClick = { dialog = "count" },
                        )
                        EndKind.NEVER -> {}
                    }
                    if (endKind == EndKind.AFTER_COUNT && existing != null && existing.history.isNotEmpty()) {
                        Hint("Counts the ${Format.times(existing.history.size)} already in its history.")
                    }
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

    when (dialog) {
        "interval" -> NumberDialog(
            title = "Repeat every",
            unit = "days",
            initial = interval,
            max = 365,
            onDismiss = { dialog = null },
            onConfirm = {
                interval = it
                recomputeDue()
                dialog = null
            },
        )

        "count" -> NumberDialog(
            title = "Ends after",
            unit = "times",
            initial = endCount,
            max = 999,
            onDismiss = { dialog = null },
            onConfirm = {
                endCount = it
                dialog = null
            },
        )

        "date" -> DateDialog(
            initial = date,
            onDismiss = { dialog = null },
            onConfirm = {
                date = it
                dialog = null
            },
        )

        "endDate" -> DateDialog(
            initial = endDate,
            onDismiss = { dialog = null },
            onConfirm = {
                endDate = it
                dialog = null
            },
        )

        "time" -> TimeDialog(
            initial = time,
            is24Hour = DateFormat.is24HourFormat(ctx),
            onDismiss = { dialog = null },
            onConfirm = {
                time = it
                dialog = null
            },
        )
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
private fun Hint(text: String) {
    Text(
        text,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun Choice(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, fontSize = 14.sp) },
        shape = RoundedCornerShape(20.dp),
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = colors.secondaryContainer,
            selectedLabelColor = colors.onSecondaryContainer,
        ),
    )
}

@Composable
private fun RoundButton(minus: Boolean, enabled: Boolean, description: String, onClick: () -> Unit) {
    OutlinedIconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(48.dp)) {
        if (minus) {
            Icon(painterResource(R.drawable.ic_remove), contentDescription = description)
        } else {
            Icon(Icons.Filled.Add, contentDescription = description)
        }
    }
}

@Composable
private fun CounterRow(
    label: String,
    value: String,
    canDecrease: Boolean,
    canIncrease: Boolean,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    onValueClick: (() -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 16.sp, color = colors.onSurface, modifier = Modifier.weight(1f))
        OutlinedIconButton(onClick = onDecrease, enabled = canDecrease, modifier = Modifier.size(44.dp)) {
            Icon(painterResource(R.drawable.ic_remove), contentDescription = "Less")
        }
        Text(
            value,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            color = colors.onBackground,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .width(104.dp)
                .clip(RoundedCornerShape(10.dp))
                .then(if (onValueClick != null) Modifier.clickable(onClick = onValueClick) else Modifier)
                .padding(vertical = 10.dp),
        )
        OutlinedIconButton(onClick = onIncrease, enabled = canIncrease, modifier = Modifier.size(44.dp)) {
            Icon(Icons.Filled.Add, contentDescription = "More")
        }
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onChange(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, fontSize = 16.sp, color = colors.onSurface)
            Text(subtitle, fontSize = 13.sp, lineHeight = 18.sp, color = colors.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
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
        Text(text, fontSize = 16.sp, color = colors.onSurface, maxLines = 1)
    }
}

@Composable
private fun NumberDialog(
    title: String,
    unit: String,
    initial: Int,
    max: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    var text by remember { mutableStateOf(initial.toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { v -> text = v.filter { it.isDigit() }.take(3) },
                singleLine = true,
                suffix = { Text(unit) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val n = text.toIntOrNull()
                    if (n != null && n > 0) onConfirm(n.coerceAtMost(max)) else onDismiss()
                },
            ) { Text("OK") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateDialog(initial: LocalDate, onDismiss: () -> Unit, onConfirm: (LocalDate) -> Unit) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val ms = state.selectedDateMillis
                    if (ms != null) onConfirm(Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()) else onDismiss()
                },
            ) { Text("OK") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    ) {
        DatePicker(state = state)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeDialog(initial: LocalTime, is24Hour: Boolean, onDismiss: () -> Unit, onConfirm: (LocalTime) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val state = rememberTimePickerState(
        initialHour = initial.hour,
        initialMinute = initial.minute,
        is24Hour = is24Hour,
    )
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(28.dp), color = colors.surfaceContainerHigh) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "Remind at",
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp),
                )
                TimePicker(state = state)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    TextButton(onClick = { onConfirm(LocalTime.of(state.hour, state.minute)) }) { Text("OK") }
                }
            }
        }
    }
}

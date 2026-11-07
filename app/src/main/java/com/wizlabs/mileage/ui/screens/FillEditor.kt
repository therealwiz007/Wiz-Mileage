package com.wizlabs.mileage.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.wizlabs.mileage.insights.Fmt
import com.wizlabs.mileage.insights.InsightsEngine
import com.wizlabs.mileage.model.Brand
import com.wizlabs.mileage.model.FillUp
import com.wizlabs.mileage.model.Vehicle
import com.wizlabs.mileage.model.VehicleType
import com.wizlabs.mileage.ui.components.BrandDot
import com.wizlabs.mileage.ui.components.Overline
import com.wizlabs.mileage.ui.components.SelectChip
import com.wizlabs.mileage.ui.components.WizCard
import com.wizlabs.mileage.ui.theme.Numeric
import com.wizlabs.mileage.ui.theme.Wiz
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

private fun clean(s: String, maxDecimals: Int): String {
    val t = s.replace(',', '.').filter { it.isDigit() || it == '.' }
    val dot = t.indexOf('.')
    if (dot < 0) return t.take(9)
    return (t.substring(0, dot).take(9) + "." + t.substring(dot + 1).replace(".", "").take(maxDecimals))
}

private fun plain(d: Double): String = if (d == Math.floor(d)) d.toLong().toString() else d.toString()

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FillEditor(
    vehicle: Vehicle,
    existing: FillUp?,
    allFills: List<FillUp>,
    onSave: (FillUp) -> Unit,
    onDelete: (FillUp) -> Unit,
    onClose: () -> Unit,
) {
    val others = remember(allFills, existing) { allFills.filter { it.id != existing?.id } }
    val lastByDate = remember(others) { others.maxByOrNull { it.date } }

    var date by rememberSaveable { mutableStateOf(existing?.date ?: System.currentTimeMillis()) }
    var odo by rememberSaveable { mutableStateOf(existing?.odometer?.let(::plain) ?: "") }
    var amount by rememberSaveable { mutableStateOf(existing?.amount?.let(::plain) ?: "") }
    var rsp by rememberSaveable { mutableStateOf((existing?.rsp ?: lastByDate?.rsp)?.let(::plain) ?: "") }
    var full by rememberSaveable { mutableStateOf(existing?.fullTank ?: false) }
    var brand by rememberSaveable { mutableStateOf(existing?.brand ?: lastByDate?.brand) }
    var note by rememberSaveable { mutableStateOf(existing?.note ?: "") }
    var showDate by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var tried by remember { mutableStateOf(false) }

    val odoV = odo.toDoubleOrNull()
    val amountV = amount.toDoubleOrNull()
    val rspV = rsp.toDoubleOrNull()
    val unit = vehicle.fuel.unit

    // Neighbours by date decide the valid odometer range.
    val prev = others.filter { it.date <= date }.maxByOrNull { it.date }
    val next = others.filter { it.date > date }.minByOrNull { it.date }
    val odoError = when {
        odo.isBlank() -> if (tried) "Enter the odometer reading" else null
        odoV == null -> "Not a number"
        prev != null && odoV < prev.odometer -> "Should be at least ${Fmt.km(prev.odometer)} (your reading on ${Fmt.dateShort(prev.date)})"
        next != null && odoV > next.odometer -> "Should be at most ${Fmt.km(next.odometer)} (your reading on ${Fmt.dateShort(next.date)})"
        else -> null
    }
    val amountError = when {
        amount.isBlank() -> if (tried) "Enter the amount paid" else null
        amountV == null || amountV <= 0 -> "Enter an amount above ₹0"
        else -> null
    }
    val rspError = when {
        rsp.isBlank() -> if (tried) "Enter the RSP from the pump display" else null
        rspV == null || rspV <= 0 -> "Enter a price above ₹0"
        rspV < 20 || rspV > 300 -> "That looks unusual. Check the RSP (₹ per $unit)"
        else -> null
    }
    val blocking = odoV == null || amountV == null || amountV <= 0 || rspV == null || rspV <= 0 ||
        (prev != null && odoV < prev.odometer) || (next != null && odoV > next.odometer)

    val preview = if (!blocking) FillUp(
        id = existing?.id ?: "preview", vehicleId = vehicle.id, date = date, odometer = odoV!!, amount = amountV!!,
        rsp = rspV!!, fullTank = full, brand = brand, note = note.trim(),
    ) else null
    val previewInfo = preview?.let { p ->
        InsightsEngine.perFill((others + p).sortedWith(compareBy({ it.odometer }, { it.date })))[p.id]
    }

    fun save() {
        tried = true
        if (preview == null) return
        onSave(if (existing == null) preview.copy(id = java.util.UUID.randomUUID().toString()) else preview)
    }

    Scaffold(
        containerColor = Wiz.c.bg,
        topBar = {
            TopAppBar(
                title = { Text(if (existing == null) "New fill-up" else "Edit fill-up") },
                navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, "Close") } },
                actions = {
                    if (existing != null) IconButton(onClick = { confirmDelete = true }) {
                        Icon(Icons.Rounded.DeleteOutline, "Delete", tint = Wiz.c.bad)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Wiz.c.bg),
            )
        },
        bottomBar = {
            Column(Modifier.background(Wiz.c.bg).navigationBarsPadding().imePadding().padding(16.dp)) {
                Button(
                    onClick = ::save, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Wiz.c.accent, contentColor = Wiz.c.onAccent),
                ) { Text(if (existing == null) "Save fill-up" else "Save changes", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium) }
            }
        },
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Vehicle + date
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Overline("Vehicle")
                    Text(vehicle.name, style = MaterialTheme.typography.titleMedium)
                }
                Row(
                    Modifier.clip(RoundedCornerShape(50)).background(Wiz.c.surface2)
                        .clickable { showDate = true }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.CalendarMonth, null, tint = Wiz.c.text, modifier = Modifier.padding(end = 8.dp))
                    Text(Fmt.weekday(date), color = Wiz.c.text, fontWeight = FontWeight.SemiBold)
                }
            }

            NumberField(
                label = "Odometer reading (km)", value = odo, onChange = { odo = clean(it, 1) }, error = odoError,
                hint = lastByDate?.let { "Last: ${Fmt.km(it.odometer)} on ${Fmt.dateShort(it.date)}" },
            )

            Column {
                NumberField(label = "Amount paid (₹)", value = amount, onChange = { amount = clean(it, 2) }, error = amountError, prefix = "₹ ")
                Spacer(Modifier.height(8.dp))
                val quick = if (vehicle.type in setOf(VehicleType.BIKE, VehicleType.SCOOTER)) listOf(100, 200, 300, 500, 1000) else listOf(500, 1000, 1500, 2000, 3000)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    quick.forEach { q -> SelectChip("₹" + Fmt.group(q.toLong()), amountV == q.toDouble(), { amount = q.toString() }) }
                }
            }

            NumberField(
                label = "RSP (₹ per $unit)", value = rsp, onChange = { rsp = clean(it, 2) }, error = rspError, prefix = "₹ ",
                hint = if (existing == null && lastByDate != null) "Filled in from your last fill-up. Change it if the price changed." else "Retail Selling Price shown on the pump",
            )

            // Live result
            WizCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Overline("You got")
                        Text(
                            if (amountV != null && rspV != null && rspV > 0) Fmt.dec(amountV / rspV, 2) + " $unit" else "—",
                            style = MaterialTheme.typography.headlineMedium.merge(Numeric), color = Wiz.c.accentInk,
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        previewInfo?.kmSinceLast?.takeIf { it > 0 }?.let {
                            Text("+" + Fmt.km(it), style = MaterialTheme.typography.titleSmall.merge(Numeric))
                            Text("since last fill-up", style = MaterialTheme.typography.bodySmall, color = Wiz.c.muted)
                        }
                        previewInfo?.mileage?.let {
                            Spacer(Modifier.height(4.dp))
                            Text((if (previewInfo.estimated) "≈ " else "") + Fmt.dec(it) + " km/$unit",
                                style = MaterialTheme.typography.titleSmall.merge(Numeric), color = Wiz.c.good, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Full tank
            WizCard(Modifier.fillMaxWidth(), onClick = { full = !full }, padding = 16) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Full tank", style = MaterialTheme.typography.titleMedium)
                        Text("Filled to the top? Turn on for exact mileage.", style = MaterialTheme.typography.bodySmall, color = Wiz.c.muted)
                    }
                    Switch(
                        checked = full, onCheckedChange = { full = it },
                        colors = SwitchDefaults.colors(checkedTrackColor = Wiz.c.accent, checkedThumbColor = Wiz.c.onAccent),
                    )
                }
            }

            // Brand
            Column {
                Overline("Fuel station (optional)", modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Brand.entries.forEach { b ->
                        SelectChip(b.short, brand == b, { brand = if (brand == b) null else b }, leading = { BrandDot(b, 10) })
                    }
                }
            }

            OutlinedTextField(
                value = note, onValueChange = { note = it.take(120) }, modifier = Modifier.fillMaxWidth(),
                label = { Text("Note (optional)") }, singleLine = true, shape = RoundedCornerShape(16.dp),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                colors = fieldColors(),
            )
            Spacer(Modifier.height(8.dp))
        }
    }

    if (showDate) {
        val zone = ZoneId.systemDefault()
        val localDate = Instant.ofEpochMilli(date).atZone(zone).toLocalDate()
        val state = rememberDatePickerState(
            initialSelectedDateMillis = localDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            selectableDates = object : androidx.compose.material3.SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= System.currentTimeMillis() + 86_400_000L
            },
        )
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { ms ->
                        val d = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()
                        val time = if (d == LocalDate.now(zone)) LocalTime.now(zone) else Instant.ofEpochMilli(date).atZone(zone).toLocalTime()
                        date = d.atTime(time).atZone(zone).toInstant().toEpochMilli()
                    }
                    showDate = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDate = false }) { Text("Cancel") } },
        ) { DatePicker(state) }
    }

    if (confirmDelete && existing != null) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Delete this fill-up?") },
        text = { Text("${Fmt.inr2(existing.amount)} on ${Fmt.date(existing.date)}. You can undo right after.") },
        confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete(existing) }) { Text("Delete", color = Wiz.c.bad) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
    )
}

@Composable
fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Wiz.c.accentInk, focusedLabelColor = Wiz.c.accentInk, cursorColor = Wiz.c.accentInk,
    unfocusedBorderColor = Wiz.c.line, unfocusedContainerColor = Wiz.c.surface, focusedContainerColor = Wiz.c.surface,
)

@Composable
private fun NumberField(label: String, value: String, onChange: (String) -> Unit, error: String?, hint: String? = null, prefix: String? = null) {
    OutlinedTextField(
        value = value, onValueChange = onChange, modifier = Modifier.fillMaxWidth(),
        label = { Text(label) }, singleLine = true, isError = error != null,
        prefix = prefix?.let { { Text(it) } },
        textStyle = MaterialTheme.typography.titleLarge.merge(Numeric),
        supportingText = (error ?: hint)?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
        shape = RoundedCornerShape(16.dp), colors = fieldColors(),
    )
}

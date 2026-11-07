package com.wizlabs.mileage.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.wizlabs.mileage.model.FuelType
import com.wizlabs.mileage.model.Vehicle
import com.wizlabs.mileage.model.VehicleType
import com.wizlabs.mileage.ui.components.Overline
import com.wizlabs.mileage.ui.components.SelectChip
import com.wizlabs.mileage.ui.components.icon
import com.wizlabs.mileage.ui.theme.Wiz

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun VehicleEditor(
    existing: Vehicle?,
    fillCount: Int,
    firstRun: Boolean,
    onSave: (Vehicle) -> Unit,
    onDelete: (Vehicle) -> Unit,
    onClose: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(existing?.name ?: "") }
    var type by rememberSaveable { mutableStateOf(existing?.type ?: VehicleType.BIKE) }
    var fuel by rememberSaveable { mutableStateOf(existing?.fuel ?: FuelType.PETROL) }
    var reg by rememberSaveable { mutableStateOf(existing?.registration ?: "") }
    var tank by rememberSaveable { mutableStateOf(existing?.tankCapacity?.let { if (it == Math.floor(it)) it.toLong().toString() else it.toString() } ?: "") }
    var tried by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    val nameError = if (tried && name.isBlank()) "Give it a name, like “Activa” or “Swift”" else null
    val tankV = tank.toDoubleOrNull()

    fun save() {
        tried = true
        if (name.isBlank()) return
        val v = (existing ?: Vehicle(name = "", type = type)).copy(
            name = name.trim(), type = type, fuel = fuel, registration = reg.trim().uppercase(),
            tankCapacity = tankV?.takeIf { it > 0 },
        )
        onSave(v)
    }

    Scaffold(
        containerColor = Wiz.c.bg,
        topBar = {
            TopAppBar(
                title = { Text(if (existing == null) (if (firstRun) "Your first vehicle" else "Add vehicle") else "Edit vehicle") },
                navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, "Close") } },
                actions = {
                    if (existing != null) IconButton(onClick = { confirmDelete = true }) {
                        Icon(Icons.Rounded.DeleteOutline, "Delete vehicle", tint = Wiz.c.bad)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Wiz.c.bg),
            )
        },
        bottomBar = {
            Column(Modifier.navigationBarsPadding().imePadding().padding(16.dp)) {
                Button(
                    onClick = ::save, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Wiz.c.accent, contentColor = Wiz.c.onAccent),
                ) { Text(if (existing == null) "Add vehicle" else "Save changes", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium) }
            }
        },
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Column {
                Overline("Type", modifier = Modifier.padding(start = 4.dp, bottom = 10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    VehicleType.entries.forEach { t ->
                        val sel = t == type
                        Surface(
                            onClick = { type = t }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(18.dp),
                            color = if (sel) Wiz.c.accent else Wiz.c.surface,
                            border = BorderStroke(1.dp, if (sel) Wiz.c.accent else Wiz.c.line),
                        ) {
                            Column(Modifier.padding(vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(t.icon(), null, tint = if (sel) Wiz.c.onAccent else Wiz.c.text, modifier = Modifier.size(26.dp))
                                Spacer(Modifier.height(4.dp))
                                Text(t.label, style = MaterialTheme.typography.labelMedium, color = if (sel) Wiz.c.onAccent else Wiz.c.text, maxLines = 1)
                            }
                        }
                    }
                }
            }

            OutlinedTextField(
                value = name, onValueChange = { name = it.take(30) }, modifier = Modifier.fillMaxWidth(),
                label = { Text("Name") }, placeholder = { Text(if (type == VehicleType.CAR || type == VehicleType.SUV) "e.g. Swift" else "e.g. Activa") },
                singleLine = true, isError = nameError != null, supportingText = nameError?.let { { Text(it) } },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                shape = RoundedCornerShape(16.dp), colors = fieldColors(),
            )

            Column {
                Overline("Fuel", modifier = Modifier.padding(start = 4.dp, bottom = 10.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FuelType.entries.forEach { f -> SelectChip(f.label, f == fuel, { fuel = f }) }
                }
            }

            OutlinedTextField(
                value = reg, onValueChange = { reg = it.uppercase().take(14) }, modifier = Modifier.fillMaxWidth(),
                label = { Text("Registration number (optional)") }, placeholder = { Text("MH 12 AB 1234") }, singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Next),
                shape = RoundedCornerShape(16.dp), colors = fieldColors(),
            )

            OutlinedTextField(
                value = tank, onValueChange = { s -> tank = s.filter { it.isDigit() || it == '.' }.take(5) }, modifier = Modifier.fillMaxWidth(),
                label = { Text("Tank capacity in ${fuel.unit} (optional)") }, singleLine = true,
                supportingText = { Text("Used for full-tank range and to catch typos") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                shape = RoundedCornerShape(16.dp), colors = fieldColors(),
            )
            Spacer(Modifier.height(8.dp))
        }
    }

    if (confirmDelete && existing != null) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Delete ${existing.name}?") },
        text = {
            Text(
                if (fillCount > 0) "This also deletes its $fillCount fill-up${if (fillCount == 1) "" else "s"}. This can't be undone, so export a CSV first if you want a copy."
                else "This vehicle has no fill-ups.",
            )
        },
        confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete(existing) }) { Text("Delete", color = Wiz.c.bad) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
    )
}

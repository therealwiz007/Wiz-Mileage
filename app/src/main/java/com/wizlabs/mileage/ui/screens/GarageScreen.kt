package com.wizlabs.mileage.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.wizlabs.mileage.insights.Fmt
import com.wizlabs.mileage.model.AppData
import com.wizlabs.mileage.model.ThemeMode
import com.wizlabs.mileage.model.Vehicle
import com.wizlabs.mileage.ui.components.IconBadge
import com.wizlabs.mileage.ui.components.Overline
import com.wizlabs.mileage.ui.components.Pill
import com.wizlabs.mileage.ui.components.SectionHeader
import com.wizlabs.mileage.ui.components.SelectChip
import com.wizlabs.mileage.ui.components.WizCard
import com.wizlabs.mileage.ui.components.icon
import com.wizlabs.mileage.ui.theme.Numeric
import com.wizlabs.mileage.ui.theme.Wiz

@Composable
fun GarageScreen(
    data: AppData,
    version: String,
    contentPadding: PaddingValues,
    onSelect: (Vehicle) -> Unit,
    onEdit: (Vehicle) -> Unit,
    onAddVehicle: () -> Unit,
    onTheme: (ThemeMode) -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onShare: () -> Unit,
    onReceive: () -> Unit,
    onPrivacy: () -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(
            start = 16.dp, end = 16.dp,
            top = contentPadding.calculateTopPadding() + 4.dp,
            bottom = contentPadding.calculateBottomPadding() + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { SectionHeader("Vehicles", "Add", onAddVehicle) }
        data.vehicles.forEach { v ->
            item(key = v.id) {
                val fills = data.fills.filter { it.vehicleId == v.id }
                val selected = v.id == data.selectedVehicle?.id
                WizCard(Modifier.fillMaxWidth(), onClick = { onSelect(v) }, padding = 16) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(
                            v.type.icon(),
                            tint = if (selected) Wiz.c.onAccent else Wiz.c.text,
                            bg = if (selected) Wiz.c.accent else Wiz.c.surface2,
                        )
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(v.name, style = MaterialTheme.typography.titleMedium)
                                if (selected) { Spacer(Modifier.width(8.dp)); Pill("ACTIVE", Wiz.c.accentInk) }
                            }
                            Text(
                                listOf(v.type.label, v.fuel.label, v.registration).filter { it.isNotBlank() }.joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall, color = Wiz.c.muted,
                            )
                            Text(
                                "${fills.size} fill-up${if (fills.size == 1) "" else "s"} · ${Fmt.inr(fills.sumOf { it.amount })} total",
                                style = MaterialTheme.typography.bodySmall.merge(Numeric), color = Wiz.c.muted,
                            )
                        }
                        Text("Edit", color = Wiz.c.accentInk, style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.clickable { onEdit(v) }.padding(8.dp))
                    }
                }
            }
        }
        item {
            WizCard(Modifier.fillMaxWidth(), onClick = onAddVehicle, padding = 16) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(Icons.Rounded.Add, tint = Wiz.c.text, bg = Wiz.c.surface2)
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text("Add a vehicle", style = MaterialTheme.typography.titleMedium)
                        Text("Bike, scooter, car… switch between them from the top bar", style = MaterialTheme.typography.bodySmall, color = Wiz.c.muted)
                    }
                }
            }
        }

        item { SectionHeader("Share with another phone") }
        item {
            WizCard(Modifier.fillMaxWidth(), padding = 4) {
                ActionRow(Icons.Rounded.QrCode2, "Share logs", "Show a QR code or send a share code. No internet needed.", onShare)
                HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = Wiz.c.line)
                ActionRow(Icons.Rounded.QrCodeScanner, "Receive logs", "Scan a QR code or paste a code from another phone.", onReceive)
            }
        }

        item { SectionHeader("Appearance") }
        item {
            WizCard(Modifier.fillMaxWidth(), padding = 16) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeMode.entries.forEach { t -> SelectChip(t.label, data.theme == t, { onTheme(t) }) }
                }
            }
        }

        item { SectionHeader("Your data") }
        item {
            WizCard(Modifier.fillMaxWidth(), padding = 4) {
                ActionRow(Icons.Rounded.FileDownload, "Export to CSV", "All vehicles and fill-ups. Opens in Excel or Google Sheets, and works as a backup.", onExport)
                HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = Wiz.c.line)
                ActionRow(Icons.Rounded.FileUpload, "Import CSV", "Restore a backup or bring in old records. Duplicates are skipped.", onImport)
            }
        }
        item {
            WizCard(Modifier.fillMaxWidth(), padding = 16) {
                Row(verticalAlignment = Alignment.Top) {
                    Icon(Icons.Rounded.Lock, null, tint = Wiz.c.good, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Private by design", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Everything stays on this phone. No account, no ads, no tracking, and the app has no internet permission. " +
                                "Android's own device backup can include your log. The camera is used only to scan share codes.",
                            style = MaterialTheme.typography.bodySmall, color = Wiz.c.muted,
                        )
                        Text(
                            "Read the privacy policy", color = Wiz.c.accentInk, style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(top = 8.dp).clickable(onClick = onPrivacy),
                        )
                    }
                }
            }
        }
        item {
            Column(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Overline("Wiz Mileage · v$version")
                Spacer(Modifier.height(2.dp))
                Text("by Wiz Labs", style = MaterialTheme.typography.bodySmall, color = Wiz.c.muted)
            }
        }
    }
}

@Composable
private fun ActionRow(icon: ImageVector, title: String, sub: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = Wiz.c.accentInk)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = Wiz.c.muted)
        }
        Icon(Icons.Rounded.ChevronRight, null, tint = Wiz.c.muted)
    }
}

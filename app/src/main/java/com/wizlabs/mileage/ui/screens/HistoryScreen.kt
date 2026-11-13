package com.wizlabs.mileage.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import com.wizlabs.mileage.ui.components.EmptyArt
import com.wizlabs.mileage.ui.components.EmptyState
import com.wizlabs.mileage.ui.motion.enterItem
import com.wizlabs.mileage.ui.motion.rememberEntrance
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wizlabs.mileage.insights.Fmt
import com.wizlabs.mileage.insights.InsightsEngine
import com.wizlabs.mileage.model.FillUp
import com.wizlabs.mileage.model.Vehicle
import com.wizlabs.mileage.ui.components.BrandDot
import com.wizlabs.mileage.ui.components.Pill
import com.wizlabs.mileage.ui.components.WizCard
import com.wizlabs.mileage.ui.theme.Numeric
import com.wizlabs.mileage.ui.theme.Wiz
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

@Composable
fun HistoryScreen(
    vehicle: Vehicle,
    fills: List<FillUp>,
    contentPadding: PaddingValues,
    onOpenFill: (FillUp) -> Unit,
) {
    val info = remember(fills) { InsightsEngine.perFill(fills) }
    val groups = remember(fills) {
        val zone = ZoneId.systemDefault()
        fills.sortedByDescending { it.date }.groupBy { YearMonth.from(Instant.ofEpochMilli(it.date).atZone(zone)) }
    }
    if (fills.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(contentPadding).padding(32.dp), contentAlignment = Alignment.Center) {
            EmptyState(
                "No fill-ups yet",
                "Every fill-up for ${vehicle.name} shows up here, grouped by month.",
                art = EmptyArt.RECEIPT,
            )
        }
        return
    }
    val entrance = rememberEntrance(vehicle.id)
    var n = 0
    LazyColumn(
        contentPadding = PaddingValues(
            start = 16.dp, end = 16.dp,
            top = contentPadding.calculateTopPadding() + 4.dp,
            bottom = contentPadding.calculateBottomPadding() + 96.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        groups.forEach { (_, list) ->
            enterItem(entrance, "h" + list.first().id, n++) {
                Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 10.dp), verticalAlignment = Alignment.Bottom) {
                    Text(Fmt.month(list.first().date), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Text(
                        "${Fmt.inr(list.sumOf { it.amount })}  ·  ${Fmt.dec(list.sumOf { it.quantity })} ${vehicle.fuel.unit}",
                        style = MaterialTheme.typography.bodySmall.merge(Numeric), color = Wiz.c.muted,
                    )
                }
            }
            enterItem(entrance, "c" + list.first().id, n++) {
                WizCard(Modifier.fillMaxWidth(), padding = 4) {
                    list.forEachIndexed { i, f ->
                        FillRow(f, vehicle, info[f.id], onClick = { onOpenFill(f) }, divider = i < list.lastIndex)
                    }
                }
            }
        }
    }
}

@Composable
fun FillRow(f: FillUp, vehicle: Vehicle, info: InsightsEngine.FillInfo?, onClick: () -> Unit, divider: Boolean) {
    val unit = vehicle.fuel.unit
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BrandDot(f.brand, 9)
                    Spacer(Modifier.width(8.dp))
                    Text(Fmt.weekday(f.date), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    f.brand?.let {
                        Spacer(Modifier.width(6.dp))
                        Text(it.short, style = MaterialTheme.typography.bodySmall, color = Wiz.c.muted)
                    }
                    if (f.fullTank) { Spacer(Modifier.width(8.dp)); Pill("FULL", Wiz.c.accentInk) }
                }
                Spacer(Modifier.height(3.dp))
                val parts = buildList {
                    add(Fmt.km(f.odometer))
                    info?.kmSinceLast?.takeIf { it > 0 }?.let { add("+" + Fmt.km(it)) }
                    if (f.note.isNotBlank()) add(f.note)
                }
                Text(parts.joinToString("  ·  "), style = MaterialTheme.typography.bodySmall.merge(Numeric), color = Wiz.c.muted,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(Fmt.inr2(f.amount).removeSuffix(".00"), style = MaterialTheme.typography.titleMedium.merge(Numeric))
                Text(
                    "${Fmt.dec(f.quantity, 2)} $unit @ ${Fmt.inr2(f.rsp)}",
                    style = MaterialTheme.typography.bodySmall.merge(Numeric), color = Wiz.c.muted,
                )
                info?.mileage?.let {
                    Text(
                        (if (info.estimated) "≈ " else "") + Fmt.dec(it) + " km/$unit",
                        style = MaterialTheme.typography.labelMedium.merge(Numeric), color = Wiz.c.accentInk, fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
        if (divider) HorizontalDivider(Modifier.padding(horizontal = 14.dp), color = Wiz.c.line)
    }
}

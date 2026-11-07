package com.wizlabs.mileage.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import com.wizlabs.mileage.insights.Fmt
import com.wizlabs.mileage.insights.Insights
import com.wizlabs.mileage.insights.InsightsEngine
import com.wizlabs.mileage.insights.Period
import com.wizlabs.mileage.model.AppData
import com.wizlabs.mileage.model.Vehicle
import com.wizlabs.mileage.ui.components.BarChart
import com.wizlabs.mileage.ui.components.BrandDot
import com.wizlabs.mileage.ui.components.IconBadge
import com.wizlabs.mileage.ui.components.LineChart
import com.wizlabs.mileage.ui.components.Overline
import com.wizlabs.mileage.ui.components.SectionHeader
import com.wizlabs.mileage.ui.components.SelectChip
import com.wizlabs.mileage.ui.components.StatTile
import com.wizlabs.mileage.ui.components.TipCard
import com.wizlabs.mileage.ui.components.WizCard
import com.wizlabs.mileage.ui.components.colour
import com.wizlabs.mileage.ui.components.icon
import com.wizlabs.mileage.ui.theme.Numeric
import com.wizlabs.mileage.ui.theme.Wiz
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun InsightsScreen(data: AppData, vehicle: Vehicle, contentPadding: PaddingValues) {
    var period by rememberSaveable { mutableStateOf(Period.ALL) }
    val fills = remember(data, vehicle) { data.fillsFor(vehicle.id) }
    val ins = remember(fills, period, vehicle) { InsightsEngine.compute(fills, period, vehicle) }
    val unit = vehicle.fuel.unit

    if (fills.size < 2) {
        Column(
            Modifier.fillMaxSize().padding(contentPadding).padding(32.dp),
            verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(Icons.Rounded.Insights, null, tint = Wiz.c.accentInk, modifier = Modifier.size(40.dp))
            Spacer(Modifier.height(12.dp))
            Text("Insights unlock at 2 fill-ups", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(6.dp))
            Text(
                "Mileage, cost per km, monthly spend, price history, brand comparison and smart tips appear " +
                    "as soon as ${vehicle.name} has two fill-ups.",
                color = Wiz.c.muted, style = MaterialTheme.typography.bodyMedium,
            )
        }
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(
            start = 16.dp, end = 16.dp,
            top = contentPadding.calculateTopPadding() + 4.dp,
            bottom = contentPadding.calculateBottomPadding() + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Period.entries.forEach { p -> SelectChip(p.label, p == period, { period = p }) }
            }
        }

        item { SummaryGrid(ins, unit) }

        if (ins.fillCount == 0) {
            item {
                WizCard(Modifier.fillMaxWidth()) {
                    Text("No fill-ups in this period", style = MaterialTheme.typography.titleMedium)
                    Text("Pick a longer period above.", color = Wiz.c.muted)
                }
            }
        }

        if (ins.segments.isNotEmpty()) item {
            WizCard(Modifier.fillMaxWidth()) {
                Overline("Mileage · km/$unit" + if (ins.mileageEstimated) " · estimated" else "")
                Spacer(Modifier.height(10.dp))
                LineChart(
                    values = ins.segments.map { it.mileage },
                    labels = ins.segments.map { Fmt.dateShort(it.endDate) },
                    format = { Fmt.dec(it) + " km/$unit" },
                    average = ins.avgMileage,
                )
                if (ins.bestMileage != null && ins.worstMileage != null) {
                    Spacer(Modifier.height(14.dp))
                    HorizontalDivider(color = Wiz.c.line)
                    Spacer(Modifier.height(12.dp))
                    Row {
                        MiniStat("Best", Fmt.dec(ins.bestMileage.mileage) + " km/$unit", Fmt.dateShort(ins.bestMileage.endDate), Modifier.weight(1f))
                        MiniStat("Worst", Fmt.dec(ins.worstMileage.mileage) + " km/$unit", Fmt.dateShort(ins.worstMileage.endDate), Modifier.weight(1f))
                        MiniStat("Spread", Fmt.pct((ins.bestMileage.mileage - ins.worstMileage.mileage) / ins.worstMileage.mileage), "best vs worst", Modifier.weight(1f))
                    }
                }
            }
        }

        if (ins.monthly.isNotEmpty()) item {
            WizCard(Modifier.fillMaxWidth()) {
                Overline("Monthly fuel spend")
                Spacer(Modifier.height(10.dp))
                BarChart(
                    values = ins.monthly.map { it.spend },
                    labels = ins.monthly.map { it.month.month.getDisplayName(TextStyle.NARROW, Locale.ENGLISH) },
                    readoutLabels = ins.monthly.map {
                        it.month.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH) + " " + it.month.year +
                            " · ${it.fills} fill-up${if (it.fills == 1) "" else "s"} · ${Fmt.dec(it.fuel)} $unit"
                    },
                    format = { Fmt.inr(it) },
                )
                ins.yearlyProjection?.let {
                    Spacer(Modifier.height(10.dp))
                    Text("On pace for about ${Fmt.inr(it)} a year", style = MaterialTheme.typography.bodySmall, color = Wiz.c.muted)
                }
            }
        }

        if (ins.rspPoints.size >= 2) item {
            WizCard(Modifier.fillMaxWidth()) {
                Overline("RSP you paid · ₹/$unit")
                Spacer(Modifier.height(10.dp))
                LineChart(
                    values = ins.rspPoints.map { it.second },
                    labels = ins.rspPoints.map { Fmt.dateShort(it.first) },
                    format = { Fmt.inr2(it) + "/$unit" },
                    color = Wiz.c.accent2, height = 140,
                )
                Spacer(Modifier.height(14.dp))
                Row {
                    MiniStat("Lowest", ins.minRsp?.let { Fmt.inr2(it) } ?: "—", null, Modifier.weight(1f))
                    MiniStat("Average", ins.avgRsp?.let { Fmt.inr2(it) } ?: "—", null, Modifier.weight(1f))
                    MiniStat("Highest", ins.maxRsp?.let { Fmt.inr2(it) } ?: "—", null, Modifier.weight(1f))
                }
            }
        }

        val named = ins.brands.filter { it.brand != null }
        if (named.isNotEmpty()) item {
            WizCard(Modifier.fillMaxWidth()) {
                Overline("Fuel brands")
                Spacer(Modifier.height(12.dp))
                val total = ins.brands.sumOf { it.spend }.takeIf { it > 0 } ?: 1.0
                // Share bar
                Row(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(50))) {
                    ins.brands.forEach { b ->
                        Box(Modifier.weight((b.spend / total).toFloat().coerceAtLeast(0.01f)).fillMaxHeight()
                            .background(b.brand?.colour ?: Wiz.c.surface2))
                    }
                }
                Spacer(Modifier.height(12.dp))
                ins.brands.forEach { b ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        BrandDot(b.brand, 12)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(b.brand?.full ?: "Brand not set", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "${b.fills} fill-up${if (b.fills == 1) "" else "s"} · ${Fmt.pct(b.spend / total)} of spend · avg ${Fmt.inr2(b.avgRsp)}",
                                style = MaterialTheme.typography.bodySmall, color = Wiz.c.muted,
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(b.mileage?.let { Fmt.dec(it) } ?: "—", style = MaterialTheme.typography.titleMedium.merge(Numeric))
                            Text("km/$unit", style = MaterialTheme.typography.bodySmall, color = Wiz.c.muted)
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "Mileage per brand uses the fuel that was in the tank for each stretch. It needs a few tanks per brand to mean much.",
                    style = MaterialTheme.typography.bodySmall, color = Wiz.c.muted,
                )
            }
        }

        if (ins.tips.isNotEmpty()) {
            item { SectionHeader("Smart insights") }
            ins.tips.forEach { t -> item { TipCard(t) } }
        }

        if (data.vehicles.size > 1) {
            item { SectionHeader("Compare vehicles") }
            item { CompareVehicles(data, period) }
        }
    }
}

@Composable
private fun SummaryGrid(i: Insights, unit: String) {
    val tiles = listOf(
        Triple("Spent", Fmt.inr(i.totalSpend), "${i.fillCount} fill-up${if (i.fillCount == 1) "" else "s"}"),
        Triple("Driven", Fmt.km(i.distance), i.kmPerDay?.let { Fmt.dec(it) + " km a day" }),
        Triple("Mileage", i.avgMileage?.let { Fmt.dec(it) + " km/$unit" } ?: "—", if (i.mileageEstimated) "estimated" else "full-to-full"),
        Triple("Cost per km", i.costPerKm?.let { Fmt.inr2(it) } ?: "—", i.costPerKm?.let { "${Fmt.inr(it * 100)} per 100 km" }),
        Triple("Fuel", Fmt.dec(i.totalFuel) + " $unit", i.avgRsp?.let { "avg ${Fmt.inr2(it)}/$unit" }),
        Triple("Per visit", i.avgFill?.let { Fmt.inr(it) } ?: "—", i.avgDaysBetween?.let { "every ${Fmt.dec(it, 0)} days" }),
    )
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        tiles.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { (l, v, s) -> StatTile(l, v, Modifier.weight(1f), s) }
            }
        }
    }
}

@Composable
private fun MiniStat(label: String, value: String, sub: String?, modifier: Modifier) {
    Column(modifier) {
        Overline(label)
        Text(value, style = MaterialTheme.typography.titleSmall.merge(Numeric), fontWeight = FontWeight.Bold, maxLines = 1)
        if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall, color = Wiz.c.muted, maxLines = 1)
    }
}

@Composable
private fun CompareVehicles(data: AppData, period: Period) {
    val rows = remember(data, period) {
        data.vehicles.map { v -> v to InsightsEngine.compute(data.fillsFor(v.id), period, v) }
    }
    val maxSpend = rows.maxOfOrNull { it.second.totalSpend }?.takeIf { it > 0 } ?: 1.0
    WizCard(Modifier.fillMaxWidth()) {
        rows.forEachIndexed { idx, (v, i) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(v.type.icon(), size = 40)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(v.name, style = MaterialTheme.typography.titleSmall)
                    Text(
                        listOfNotNull(
                            i.avgMileage?.let { Fmt.dec(it) + " km/${v.fuel.unit}" },
                            i.costPerKm?.let { Fmt.inr2(it) + "/km" },
                            Fmt.km(i.distance),
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall.merge(Numeric), color = Wiz.c.muted,
                    )
                }
                Text(Fmt.inr(i.totalSpend), style = MaterialTheme.typography.titleMedium.merge(Numeric))
            }
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)).background(Wiz.c.surface2)) {
                Box(Modifier.fillMaxWidth((i.totalSpend / maxSpend).toFloat()).fillMaxHeight()
                    .clip(RoundedCornerShape(50)).background(Wiz.c.accent))
            }
            if (idx < rows.lastIndex) Spacer(Modifier.height(16.dp))
        }
        val withCpk = rows.filter { it.second.costPerKm != null }
        if (withCpk.size >= 2) {
            val cheap = withCpk.minBy { it.second.costPerKm!! }
            val dear = withCpk.maxBy { it.second.costPerKm!! }
            if (cheap.first.id != dear.first.id) {
                Spacer(Modifier.height(14.dp))
                Text(
                    "${cheap.first.name} costs ${Fmt.inr2(cheap.second.costPerKm!!)} per km, ${dear.first.name} ${Fmt.inr2(dear.second.costPerKm!!)}. " +
                        "A 20 km trip is ${Fmt.inr((dear.second.costPerKm!! - cheap.second.costPerKm!!) * 20)} cheaper on the ${cheap.first.name}.",
                    style = MaterialTheme.typography.bodySmall, color = Wiz.c.muted,
                )
            }
        }
    }
}

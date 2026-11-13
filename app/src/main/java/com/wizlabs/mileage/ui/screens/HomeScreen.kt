package com.wizlabs.mileage.ui.screens

import androidx.compose.foundation.background
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.graphicsLayer
import com.wizlabs.mileage.ui.components.CountUp
import com.wizlabs.mileage.ui.components.EmptyArt
import com.wizlabs.mileage.ui.components.EmptyState
import com.wizlabs.mileage.ui.components.FuelDrop
import com.wizlabs.mileage.ui.motion.enterItem
import com.wizlabs.mileage.ui.motion.rememberAmbientClock
import com.wizlabs.mileage.ui.motion.rememberEntrance
import kotlin.math.sin
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.LocalGasStation
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.TrendingDown
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wizlabs.mileage.insights.Fmt
import com.wizlabs.mileage.insights.Insights
import com.wizlabs.mileage.insights.InsightsEngine
import com.wizlabs.mileage.insights.Period
import com.wizlabs.mileage.model.FillUp
import com.wizlabs.mileage.model.Vehicle
import com.wizlabs.mileage.model.VehicleType
import com.wizlabs.mileage.ui.components.ActivityHeatmap
import com.wizlabs.mileage.ui.components.BrandDot
import com.wizlabs.mileage.ui.components.Cockpit
import com.wizlabs.mileage.ui.components.Donut
import com.wizlabs.mileage.ui.components.MileageGauge
import com.wizlabs.mileage.ui.components.Overline
import com.wizlabs.mileage.ui.components.Ring
import com.wizlabs.mileage.ui.components.SectionHeader
import com.wizlabs.mileage.ui.components.Slice
import com.wizlabs.mileage.ui.components.Sparkline
import com.wizlabs.mileage.ui.components.TipCard
import com.wizlabs.mileage.ui.components.WizCard
import com.wizlabs.mileage.ui.components.animatedValue
import com.wizlabs.mileage.ui.components.colour
import com.wizlabs.mileage.ui.theme.Numeric
import com.wizlabs.mileage.ui.theme.Wiz
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.ceil
import kotlin.math.max

private data class Record(val icon: ImageVector, val label: String, val value: String, val sub: String)

@Composable
fun HomeScreen(
    vehicle: Vehicle,
    fills: List<FillUp>,
    contentPadding: PaddingValues,
    onAdd: () -> Unit,
    onOpenFill: (FillUp) -> Unit,
    onSeeInsights: () -> Unit,
    onSeeHistory: () -> Unit,
) {
    val ins = remember(fills, vehicle) { InsightsEngine.compute(fills, Period.ALL, vehicle) }
    val info = remember(fills) { InsightsEngine.perFill(fills) }
    val unit = vehicle.fuel.unit
    val zone = remember { ZoneId.systemDefault() }
    val today = remember { LocalDate.now(zone) }

    val entrance = rememberEntrance(vehicle.id)
    var n = 0
    LazyColumn(
        contentPadding = PaddingValues(
            start = 16.dp, end = 16.dp,
            top = contentPadding.calculateTopPadding() + 4.dp,
            bottom = contentPadding.calculateBottomPadding() + 96.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        enterItem(entrance, "cockpit", n++) { CockpitCard(vehicle, ins, fills) }

        if (fills.isEmpty()) {
            enterItem(entrance, "empty", n++) { EmptyHome(vehicle, onAdd) }
            return@LazyColumn
        }

        // Month ring + cost per km
        enterItem(entrance, "month", n++) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                WizCard(Modifier.weight(1f), padding = 16) {
                    Overline("This month")
                    Spacer(Modifier.height(10.dp))
                    val ref = if (ins.lastMonthSpend > 0) ins.lastMonthSpend else null
                    val p = ref?.let { (ins.thisMonthSpend / it).toFloat() } ?: if (ins.thisMonthSpend > 0) 1f else 0f
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Ring(p, Wiz.c.accent, Modifier.size(64.dp), thickness = 8) {
                            Text(if (ref != null) Fmt.pct(p.toDouble()) else "—", style = MaterialTheme.typography.labelLarge.merge(Numeric), fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(Fmt.inr(animatedValue(ins.thisMonthSpend)), style = MaterialTheme.typography.titleLarge.merge(Numeric), maxLines = 1)
                            Text(
                                ref?.let { "of ${Fmt.inr(it)} last month" } ?: "first month",
                                style = MaterialTheme.typography.bodySmall, color = Wiz.c.muted, maxLines = 2,
                            )
                        }
                    }
                }
                WizCard(Modifier.weight(1f), padding = 16) {
                    Overline("Cost per km")
                    Spacer(Modifier.height(6.dp))
                    Text(ins.costPerKm?.let { Fmt.inr2(animatedValue(it)) } ?: "—", style = MaterialTheme.typography.titleLarge.merge(Numeric))
                    Text(ins.costPerKm?.let { "${Fmt.inr(it * 100)} per 100 km" } ?: "needs 2 fill-ups", style = MaterialTheme.typography.bodySmall, color = Wiz.c.muted)
                    Spacer(Modifier.height(8.dp))
                    Sparkline(ins.segments.takeLast(12).map { it.cost / it.distance }, Wiz.c.accent2, Modifier.fillMaxWidth().height(30.dp))
                }
            }
        }

        // KPI strip
        enterItem(entrance, "kpi", n++) {
            val kpis = buildList {
                ins.kmPerDay?.let { add(Triple("Per day", Fmt.km(it), "≈ ${Fmt.inr((ins.costPerKm ?: 0.0) * it)} in fuel")) }
                add(Triple("Per visit", ins.avgFill?.let { Fmt.inr(it) } ?: "—", ins.avgDaysBetween?.let { "every ${Fmt.dec(it, 0)} days" } ?: ""))
                add(Triple("Fuel bought", Fmt.dec(ins.totalFuel) + " $unit", "${ins.fillCount} fill-ups"))
                ins.rangeKm?.let { add(Triple("Tank range", Fmt.km(it), "full ${Fmt.dec(vehicle.tankCapacity ?: 0.0)} $unit")) }
                ins.yearlyProjection?.let { add(Triple("Yearly pace", Fmt.inr(it), "at current driving")) }
                add(Triple("Driven", Fmt.km(ins.distance), "since first log"))
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                kpis.forEach { (l, v, s) ->
                    WizCard(Modifier.widthIn(min = 132.dp), padding = 14) {
                        Overline(l)
                        Spacer(Modifier.height(4.dp))
                        Text(v, style = MaterialTheme.typography.titleMedium.merge(Numeric), maxLines = 1)
                        if (s.isNotBlank()) Text(s, style = MaterialTheme.typography.bodySmall, color = Wiz.c.muted, maxLines = 1)
                    }
                }
            }
        }

        // Insight carousel
        if (ins.tips.isNotEmpty()) {
            enterItem(entrance, "foryouH", n++) { SectionHeader("For you", "All insights", onSeeInsights) }
            enterItem(entrance, "foryou", n++) {
                val pager = rememberPagerState { ins.tips.size }
                Column {
                    HorizontalPager(pager, pageSpacing = 10.dp) { p -> TipCard(ins.tips[p]) }
                    if (ins.tips.size > 1) Row(
                        Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.Center,
                    ) {
                        repeat(ins.tips.size) { i ->
                            val sel = i == pager.currentPage
                            val w by animateDpAsState(if (sel) 18.dp else 6.dp, spring(dampingRatio = 0.6f, stiffness = 500f), label = "dot")
                            val c by animateColorAsState(if (sel) Wiz.c.accentInk else Wiz.c.line, label = "dotC")
                            Box(
                                Modifier.padding(horizontal = 3.dp).height(6.dp).width(w)
                                    .clip(CircleShape).background(c),
                            )
                        }
                    }
                }
            }
        }

        // Activity heatmap
        enterItem(entrance, "heat", n++) {
            val byDay = remember(fills) {
                fills.groupBy { Instant.ofEpochMilli(it.date).atZone(zone).toLocalDate() }.mapValues { e -> e.value.sumOf { it.amount } }
            }
            WizCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Overline("Fill-up activity", modifier = Modifier.weight(1f))
                    val recent = fills.count { it.date >= System.currentTimeMillis() - 119L * 86_400_000L }
                    Text("$recent in 17 weeks", style = MaterialTheme.typography.bodySmall, color = Wiz.c.muted)
                }
                Spacer(Modifier.height(10.dp))
                ActivityHeatmap(byDay, today)
            }
        }

        // Brand donut
        val named = ins.brands.filter { it.brand != null }
        if (named.isNotEmpty()) enterItem(entrance, "brands", n++) {
            WizCard(Modifier.fillMaxWidth()) {
                Overline("Where you fill up")
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val slices = ins.brands.map { Slice(it.brand?.short ?: "Other", it.spend, it.brand?.colour ?: Wiz.c.line) }
                    val top = ins.brands.first()
                    Donut(slices, Modifier.size(124.dp)) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(Fmt.pct(top.spend / max(ins.totalSpend, 1.0)), style = MaterialTheme.typography.titleLarge.merge(Numeric))
                            Text(top.brand?.short ?: "Not set", style = MaterialTheme.typography.labelMedium, color = Wiz.c.muted)
                        }
                    }
                    Spacer(Modifier.width(18.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ins.brands.take(5).forEach { b ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                BrandDot(b.brand, 10)
                                Spacer(Modifier.width(8.dp))
                                Text(b.brand?.short ?: "Not set", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1)
                                Text(b.mileage?.let { Fmt.dec(it) + " km/$unit" } ?: "${b.fills}×",
                                    style = MaterialTheme.typography.bodySmall.merge(Numeric), color = Wiz.c.muted)
                            }
                        }
                    }
                }
            }
        }

        // Records
        val records = records(fills, ins, info, unit)
        if (records.isNotEmpty()) {
            enterItem(entrance, "recordsH", n++) { SectionHeader("Personal records") }
            enterItem(entrance, "records", n++) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    records.forEach { r ->
                        WizCard(Modifier.width(158.dp), padding = 14) {
                            Box(
                                Modifier.size(34.dp).clip(RoundedCornerShape(11.dp))
                                    .background(Brush.linearGradient(listOf(Wiz.c.accent, Wiz.c.accent2))),
                                contentAlignment = Alignment.Center,
                            ) { Icon(r.icon, null, tint = Wiz.c.onAccent, modifier = Modifier.size(20.dp)) }
                            Spacer(Modifier.height(10.dp))
                            Overline(r.label)
                            Text(r.value, style = MaterialTheme.typography.titleMedium.merge(Numeric), maxLines = 1)
                            Text(r.sub, style = MaterialTheme.typography.bodySmall, color = Wiz.c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }

        enterItem(entrance, "recentH", n++) { SectionHeader("Recent fill-ups", "History", onSeeHistory) }
        val recent = fills.sortedByDescending { it.date }.take(4)
        enterItem(entrance, "recent", n++) {
            WizCard(Modifier.fillMaxWidth(), padding = 4) {
                recent.forEachIndexed { i, f ->
                    FillRow(f, vehicle, info[f.id], onClick = { onOpenFill(f) }, divider = i < recent.lastIndex)
                }
            }
        }
    }
}

private fun records(fills: List<FillUp>, ins: Insights, info: Map<String, InsightsEngine.FillInfo>, unit: String): List<Record> {
    if (fills.size < 2) return emptyList()
    val out = mutableListOf<Record>()
    InsightsEngine.segments(fills.sortedWith(compareBy({ it.odometer }, { it.date }))).maxByOrNull { it.mileage }?.let {
        out += Record(Icons.Rounded.EmojiEvents, "Best tank", Fmt.dec(it.mileage) + " km/$unit", Fmt.date(it.endDate))
    }
    fills.maxByOrNull { info[it.id]?.kmSinceLast ?: 0.0 }?.let { f ->
        info[f.id]?.kmSinceLast?.takeIf { it > 0 }?.let { out += Record(Icons.Rounded.Route, "Longest stretch", Fmt.km(it), "before ${Fmt.dateShort(f.date)}") }
    }
    fills.minByOrNull { it.rsp }?.let { out += Record(Icons.Rounded.Savings, "Cheapest RSP", Fmt.inr2(it.rsp), (it.brand?.short?.let { b -> "$b · " } ?: "") + Fmt.dateShort(it.date)) }
    fills.maxByOrNull { it.amount }?.let { out += Record(Icons.Rounded.LocalGasStation, "Biggest fill-up", Fmt.inr(it.amount), Fmt.dec(it.quantity, 1) + " $unit · " + Fmt.dateShort(it.date)) }
    return out
}

@Composable
private fun CockpitCard(vehicle: Vehicle, ins: Insights, fills: List<FillUp>) {
    val unit = vehicle.fuel.unit
    val fillCount = fills.size
    val twoWheeler = vehicle.type == VehicleType.BIKE || vehicle.type == VehicleType.SCOOTER
    val peak = listOfNotNull(ins.avgMileage, ins.bestMileage?.mileage, ins.lastMileage).maxOrNull()
    // Scale ends on a number that splits into four whole quarters (e.g. 32 → 0/8/16/24/32, 100 → 0/25/50/75/100).
    val max = peak?.let { p -> val x = p * 1.25; val step = if (x > 40) 20.0 else if (x > 16) 8.0 else 4.0; ceil(x / step) * step }
        ?: if (twoWheeler) 80.0 else 32.0
    val last = remember(fills) { fills.maxByOrNull { it.date } }
    val clock = rememberAmbientClock()
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp))
            .background(Brush.verticalGradient(listOf(Cockpit.top, Cockpit.bottom))),
    ) {
        // Soft yellow glow behind the gauge that breathes slowly (layer phase only).
        Box(
            Modifier.align(Alignment.TopCenter).padding(top = 36.dp).size(240.dp)
                .graphicsLayer { val k = 0.92f + 0.08f * sin(clock.value * 0.9f); scaleX = k; scaleY = k; alpha = 0.75f + 0.25f * k }
                .background(Brush.radialGradient(listOf(Cockpit.yellow.copy(alpha = 0.15f), Color.Transparent))),
        )
        Column(Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Overline("Average mileage", Cockpit.muted, Modifier.weight(1f))
                val ch = ins.mileageChange
                AnimatedVisibility(
                    ch != null && ins.segments.size >= 2,
                    enter = fadeIn() + scaleIn(spring(dampingRatio = 0.5f, stiffness = 400f), initialScale = 0.6f),
                    exit = fadeOut(),
                ) {
                    val up = (ch ?: 0.0) >= 0
                    val c = if (up) Cockpit.green else Cockpit.red
                    Row(
                        Modifier.clip(RoundedCornerShape(50)).background(c.copy(alpha = 0.16f)).padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(if (up) Icons.Rounded.TrendingUp else Icons.Rounded.TrendingDown, null, tint = c, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("${Fmt.pct(ch ?: 0.0)} last tank", color = c, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    }
                }
            }
            MileageGauge(
                value = ins.avgMileage, max = max, unitLabel = "km/$unit",
                caption = when {
                    ins.avgMileage == null && fillCount == 0 -> "Log fill-ups to start the gauge"
                    ins.avgMileage == null -> "One more fill-up to see mileage"
                    ins.mileageEstimated -> "Estimated · mark Full tank for exact"
                    else -> "Exact · ${ins.segments.size} full-to-full tanks"
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            )
            Spacer(Modifier.height(14.dp))
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color.White.copy(alpha = 0.05f)).padding(vertical = 12.dp),
            ) {
                CockpitStat("Odometer", ins.latestOdometer, { Fmt.km(it) }, Modifier.weight(1f))
                CockpitStat("Last tank", ins.lastMileage, { Fmt.dec(it) }, Modifier.weight(1f))
                CockpitStat("RSP", ins.latestRsp, { Fmt.inr2(it) }, Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
            // Fuel strip: a drop that fills to the last fill-up, and the running spend.
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color.White.copy(alpha = 0.05f))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val level = when {
                    last == null -> 0f
                    vehicle.tankCapacity != null && vehicle.tankCapacity > 0 -> (last.quantity / vehicle.tankCapacity).toFloat()
                    last.fullTank -> 1f
                    else -> 0.62f
                }
                FuelDrop(level, Modifier.width(38.dp), empty = last == null)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Overline("Last fill-up", Cockpit.muted)
                    Text(
                        last?.let { Fmt.dec(it.quantity, 2) + " $unit · " + Fmt.inr(it.amount) } ?: "Nothing yet",
                        style = MaterialTheme.typography.titleSmall.merge(Numeric), fontWeight = FontWeight.Bold, color = Cockpit.text, maxLines = 1,
                    )
                    Text(
                        last?.let { (it.brand?.short?.let { b -> "$b · " } ?: "") + Fmt.dateShort(it.date) } ?: "Tap Add fill-up",
                        style = MaterialTheme.typography.bodySmall, color = Cockpit.muted, maxLines = 1,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Overline("Total spent", Cockpit.muted)
                    CountUp(ins.totalSpend, { Fmt.inr(it) }, MaterialTheme.typography.titleMedium, color = Cockpit.yellow, fontWeight = FontWeight.Black)
                    Text(Fmt.dec(ins.totalFuel) + " $unit", style = MaterialTheme.typography.bodySmall.merge(Numeric), color = Cockpit.muted, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun CockpitStat(label: String, value: Double?, format: (Double) -> String, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        if (value == null) Text("—", style = MaterialTheme.typography.titleSmall.merge(Numeric), fontWeight = FontWeight.Bold, color = Cockpit.text)
        else CountUp(value, format, MaterialTheme.typography.titleSmall, color = Cockpit.text, fontWeight = FontWeight.Bold)
        Overline(label, Cockpit.muted)
    }
}

@Composable
private fun EmptyHome(vehicle: Vehicle, onAdd: () -> Unit) {
    WizCard(Modifier.fillMaxWidth(), padding = 24) {
        EmptyState(
            "Log your first fill-up",
            "Type the odometer reading, the amount you paid and the RSP. That's it. " +
                "After two fill-ups ${vehicle.name}'s dashboard comes alive: mileage gauge, cost per km, spending ring, records and smart tips.",
            Modifier.fillMaxWidth(), EmptyArt.PUMP, "Add fill-up", onAdd,
        )
    }
}

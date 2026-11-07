package com.wizlabs.mileage.insights

import com.wizlabs.mileage.model.Brand
import com.wizlabs.mileage.model.FillUp
import com.wizlabs.mileage.model.Vehicle
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.max

enum class Period(val label: String, val days: Int?) {
    M1("1M", 30), M3("3M", 91), M6("6M", 182), Y1("1Y", 365), ALL("All", null)
}

/**
 * One stretch of driving with a known amount of fuel.
 * Exact (full tank to full tank): fuel = everything added after the opening full tank up to and including
 * the closing full tank. Estimated (no full tanks marked): the fuel of one fill-up over the distance to the next.
 */
data class Segment(
    val startDate: Long,
    val endDate: Long,
    val distance: Double,
    val fuel: Double,
    val cost: Double,
    val brand: Brand?,
    val estimated: Boolean,
) {
    val mileage: Double get() = distance / fuel
}

data class MonthStat(val month: YearMonth, val spend: Double, val fuel: Double, val fills: Int)

data class BrandStat(
    val brand: Brand?,
    val fills: Int,
    val spend: Double,
    val fuel: Double,
    val mileage: Double?,
    val tanks: Int,
) {
    val avgRsp: Double get() = if (fuel > 0) spend / fuel else 0.0
}

enum class TipKind { WARN, GOOD, INFO }

data class Tip(val kind: TipKind, val title: String, val body: String)

data class Insights(
    val fillCount: Int,
    val totalSpend: Double,
    val totalFuel: Double,
    val distance: Double,
    val avgMileage: Double?,
    val mileageEstimated: Boolean,
    val lastMileage: Double?,
    val mileageChange: Double?,
    val bestMileage: Segment?,
    val worstMileage: Segment?,
    val costPerKm: Double?,
    val kmPerDay: Double?,
    val avgFill: Double?,
    val avgDaysBetween: Double?,
    val latestRsp: Double?,
    val minRsp: Double?,
    val maxRsp: Double?,
    val avgRsp: Double?,
    val thisMonthSpend: Double,
    val lastMonthSpend: Double,
    val projectedMonthSpend: Double?,
    val yearlyProjection: Double?,
    val rangeKm: Double?,
    val latestOdometer: Double?,
    val segments: List<Segment>,
    val rspPoints: List<Pair<Long, Double>>,
    val monthly: List<MonthStat>,
    val brands: List<BrandStat>,
    val tips: List<Tip>,
)

object InsightsEngine {
    private const val DAY = 86_400_000L

    fun segments(sorted: List<FillUp>): List<Segment> {
        val fullCount = sorted.count { it.fullTank }
        val out = mutableListOf<Segment>()
        if (fullCount >= 2) {
            var start = -1
            for (i in sorted.indices) {
                if (!sorted[i].fullTank) continue
                if (start >= 0) {
                    val part = sorted.subList(start + 1, i + 1)
                    val s = sorted[start]
                    val seg = Segment(
                        startDate = s.date, endDate = sorted[i].date,
                        distance = sorted[i].odometer - s.odometer,
                        fuel = part.sumOf { it.quantity }, cost = part.sumOf { it.amount },
                        brand = s.brand, estimated = false,
                    )
                    if (seg.distance > 0 && seg.fuel > 0) out += seg
                }
                start = i
            }
        } else {
            for (i in 0 until sorted.size - 1) {
                val a = sorted[i]; val b = sorted[i + 1]
                val seg = Segment(a.date, b.date, b.odometer - a.odometer, a.quantity, a.amount, a.brand, estimated = true)
                if (seg.distance > 0 && seg.fuel > 0) out += seg
            }
        }
        return out
    }

    /** Per fill-up readout for lists: km since the previous fill-up and the mileage of the tank it closes. */
    data class FillInfo(val kmSinceLast: Double?, val mileage: Double?, val estimated: Boolean)

    fun perFill(sorted: List<FillUp>): Map<String, FillInfo> {
        val exact = sorted.count { it.fullTank } >= 2
        val out = HashMap<String, FillInfo>()
        var lastFull = -1
        for (i in sorted.indices) {
            val f = sorted[i]
            val km = if (i > 0) f.odometer - sorted[i - 1].odometer else null
            var mileage: Double? = null
            if (exact) {
                if (f.fullTank && lastFull >= 0) {
                    val fuel = sorted.subList(lastFull + 1, i + 1).sumOf { it.quantity }
                    val d = f.odometer - sorted[lastFull].odometer
                    if (fuel > 0 && d > 0) mileage = d / fuel
                }
            } else if (i > 0 && km != null && km > 0 && sorted[i - 1].quantity > 0) {
                mileage = km / sorted[i - 1].quantity
            }
            if (f.fullTank) lastFull = i
            out[f.id] = FillInfo(km, mileage, !exact)
        }
        return out
    }

    fun compute(
        fills: List<FillUp>,
        period: Period,
        vehicle: Vehicle? = null,
        now: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): Insights {
        val all = fills.sortedWith(compareBy({ it.odometer }, { it.date }))
        val unit = vehicle?.fuel?.unit ?: "L"
        val allSegs = segments(all)
        val cutoff = period.days?.let { now - it * DAY } ?: Long.MIN_VALUE
        val inP = all.filter { it.date >= cutoff }
        val segs = allSegs.filter { it.endDate >= cutoff }

        val totalSpend = inP.sumOf { it.amount }
        val totalFuel = inP.sumOf { it.quantity }
        val prev = inP.firstOrNull()?.let { first -> all.lastOrNull { it.date < cutoff && it.odometer <= first.odometer } }
        val distance = if (inP.isEmpty()) 0.0 else inP.maxOf { it.odometer } - (prev?.odometer ?: inP.minOf { it.odometer })

        val segDist = segs.sumOf { it.distance }
        val segFuel = segs.sumOf { it.fuel }
        val avgMileage = if (segFuel > 0) segDist / segFuel else null
        val costPerKm = if (segDist > 0) segs.sumOf { it.cost } / segDist else null

        val lastSeg = allSegs.lastOrNull()
        val earlier = allSegs.dropLast(1).takeLast(5)
        val earlierAvg = if (earlier.isNotEmpty()) earlier.sumOf { it.distance } / earlier.sumOf { it.fuel } else null
        val mileageChange = if (lastSeg != null && earlierAvg != null && earlierAvg > 0) (lastSeg.mileage - earlierAvg) / earlierAvg else null

        val byDate = inP.sortedBy { it.date }
        val spanStart = prev?.date ?: byDate.firstOrNull()?.date
        val spanDays = if (spanStart != null && byDate.isNotEmpty()) (byDate.last().date - spanStart).toDouble() / DAY else 0.0
        val kmPerDay = if (spanDays >= 3 && distance > 0) distance / spanDays else null
        val avgDaysBetween = if (byDate.size >= 2) (byDate.last().date - byDate.first().date).toDouble() / DAY / (byDate.size - 1) else null

        val rsps = inP.map { it.rsp }
        val latest = all.maxByOrNull { it.date }

        val nowDate = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val thisYm = YearMonth.from(nowDate)
        fun ym(ms: Long) = YearMonth.from(Instant.ofEpochMilli(ms).atZone(zone))
        val thisMonthSpend = all.filter { ym(it.date) == thisYm }.sumOf { it.amount }
        val lastMonthSpend = all.filter { ym(it.date) == thisYm.minusMonths(1) }.sumOf { it.amount }
        val projected = if (nowDate.dayOfMonth >= 10 && thisMonthSpend > 0) thisMonthSpend / nowDate.dayOfMonth * thisYm.lengthOfMonth() else null

        val firstYm = all.minByOrNull { it.date }?.let { ym(it.date) }
        val monthly = if (firstYm == null) emptyList() else {
            var m = maxOf(firstYm, thisYm.minusMonths(11))
            val list = mutableListOf<MonthStat>()
            while (!m.isAfter(thisYm)) {
                val f = all.filter { ym(it.date) == m }
                list += MonthStat(m, f.sumOf { it.amount }, f.sumOf { it.quantity }, f.size)
                m = m.plusMonths(1)
            }
            list
        }

        val recent = all.filter { it.date >= now - 90 * DAY }
        val recentSpanDays = recent.minOfOrNull { it.date }?.let { (now - it).toDouble() / DAY } ?: 0.0
        val yearly = if (recentSpanDays >= 30) recent.sumOf { it.amount } / max(recentSpanDays, 30.0) * 365 else null

        val brands = inP.groupBy { it.brand }.map { (b, list) ->
            val bs = segs.filter { it.brand == b }
            val f = bs.sumOf { it.fuel }
            BrandStat(b, list.size, list.sumOf { it.amount }, list.sumOf { it.quantity },
                if (f > 0) bs.sumOf { it.distance } / f else null, bs.size)
        }.sortedByDescending { it.spend }

        val avgM = avgMileage ?: allSegs.takeIf { it.isNotEmpty() }?.let { s -> s.sumOf { it.distance } / s.sumOf { it.fuel } }
        val range = vehicle?.tankCapacity?.takeIf { it > 0 }?.let { cap -> avgM?.let { it * cap } }

        val base = Insights(
            fillCount = inP.size, totalSpend = totalSpend, totalFuel = totalFuel, distance = distance,
            avgMileage = avgMileage, mileageEstimated = segs.any { it.estimated },
            lastMileage = lastSeg?.mileage, mileageChange = mileageChange,
            bestMileage = segs.takeIf { it.size >= 2 }?.maxByOrNull { it.mileage },
            worstMileage = segs.takeIf { it.size >= 2 }?.minByOrNull { it.mileage },
            costPerKm = costPerKm, kmPerDay = kmPerDay,
            avgFill = if (inP.isNotEmpty()) totalSpend / inP.size else null,
            avgDaysBetween = avgDaysBetween,
            latestRsp = latest?.rsp, minRsp = rsps.minOrNull(), maxRsp = rsps.maxOrNull(),
            avgRsp = if (totalFuel > 0) totalSpend / totalFuel else null,
            thisMonthSpend = thisMonthSpend, lastMonthSpend = lastMonthSpend,
            projectedMonthSpend = projected, yearlyProjection = yearly, rangeKm = range,
            latestOdometer = all.maxOfOrNull { it.odometer },
            segments = segs, rspPoints = byDate.map { it.date to it.rsp },
            monthly = monthly, brands = brands, tips = emptyList(),
        )
        return base.copy(tips = tips(base, all, allSegs, vehicle, unit, earlierAvg))
    }

    private fun tips(i: Insights, all: List<FillUp>, allSegs: List<Segment>, vehicle: Vehicle?, unit: String, earlierAvg: Double?): List<Tip> {
        if (all.isEmpty()) return emptyList()
        val tips = mutableListOf<Tip>()
        val mu = "km/$unit"

        // Mileage change on the latest tank.
        val ch = i.mileageChange
        if (ch != null && allSegs.size >= 3 && i.lastMileage != null && earlierAvg != null) {
            if (ch <= -0.10) tips += Tip(TipKind.WARN, "Mileage down ${Fmt.pct(ch)}",
                "Your last tank gave ${Fmt.dec(i.lastMileage)} $mu against your usual ${Fmt.dec(earlierAvg)}. " +
                    "Check tyre pressure, the air filter and chain or brakes, and how much you idle in traffic.")
            else if (ch >= 0.08) tips += Tip(TipKind.GOOD, "Mileage up ${Fmt.pct(ch)}",
                "Your last tank gave ${Fmt.dec(i.lastMileage)} $mu, better than your usual ${Fmt.dec(earlierAvg)}. Whatever you changed, keep it up.")
        }

        // Data quality: a fill-up bigger than the tank.
        val cap = vehicle?.tankCapacity
        if (cap != null && cap > 0) {
            all.lastOrNull { it.quantity > cap * 1.05 }?.let { f ->
                tips += Tip(TipKind.WARN, "Check one entry",
                    "On ${Fmt.date(f.date)} the app works out ${Fmt.dec(f.quantity, 2)} $unit, but your tank holds ${Fmt.dec(cap)} $unit. " +
                        "The amount or RSP for that fill-up may be wrong.")
            }
        }

        // Spending pace this month.
        val proj = i.projectedMonthSpend
        if (proj != null && i.lastMonthSpend > 0) {
            val d = (proj - i.lastMonthSpend) / i.lastMonthSpend
            when {
                d >= 0.15 -> tips += Tip(TipKind.WARN, "On track to spend ${Fmt.pct(d)} more",
                    "At this pace you'll spend about ${Fmt.inr(proj)} on fuel this month, against ${Fmt.inr(i.lastMonthSpend)} last month.")
                d <= -0.15 -> tips += Tip(TipKind.GOOD, "Spending ${Fmt.pct(d)} less",
                    "This month is heading for about ${Fmt.inr(proj)}, down from ${Fmt.inr(i.lastMonthSpend)} last month.")
                else -> tips += Tip(TipKind.INFO, "Spending is steady",
                    "About ${Fmt.inr(proj)} expected this month, close to last month's ${Fmt.inr(i.lastMonthSpend)}.")
            }
        }

        // Best brand for mileage.
        val ranked = i.brands.filter { it.brand != null && it.mileage != null && it.tanks >= 2 }.sortedByDescending { it.mileage }
        if (ranked.size >= 2) {
            val best = ranked.first(); val worst = ranked.last()
            val gain = (best.mileage!! - worst.mileage!!) / worst.mileage
            if (gain >= 0.02) tips += Tip(TipKind.GOOD, "Best mileage with ${best.brand!!.short}",
                "${Fmt.dec(best.mileage)} $mu on ${best.brand.short} fuel against ${Fmt.dec(worst.mileage)} on ${worst.brand!!.short}, " +
                    "${Fmt.pct(gain)} better. Based on ${best.tanks + worst.tanks} tanks, so treat it as a trend, not proof.")
        }

        // RSP change.
        val byDate = all.sortedBy { it.date }
        val last = byDate.last()
        byDate.dropLast(1).lastOrNull { abs(it.rsp - last.rsp) >= 0.01 }?.let { before ->
            val diff = last.rsp - before.rsp
            val m = i.avgMileage ?: allSegs.takeIf { it.isNotEmpty() }?.let { s -> s.sumOf { it.distance } / s.sumOf { it.fuel } }
            val extra = if (m != null) " ₹1,000 now takes you about ${Fmt.km(1000 / last.rsp * m)}, against ${Fmt.km(1000 / before.rsp * m)} before." else ""
            tips += Tip(if (diff > 0) TipKind.WARN else TipKind.GOOD,
                "Fuel price ${if (diff > 0) "up" else "down"} ${Fmt.inr2(abs(diff))}/$unit",
                "You paid ${Fmt.inr2(last.rsp)}/$unit on ${Fmt.dateShort(last.date)}, against ${Fmt.inr2(before.rsp)} on ${Fmt.dateShort(before.date)}.$extra")
        }

        // Exact mileage nudge.
        if (all.count { it.fullTank } < 2 && all.size >= 2) tips += Tip(TipKind.INFO, "Get exact mileage",
            "Turn on Full tank when you fill to the top. Two full-tank fill-ups give an exact km/$unit instead of an estimate.")

        // Daily running cost.
        if (i.costPerKm != null && i.kmPerDay != null) tips += Tip(TipKind.INFO, "${Fmt.inr2(i.costPerKm)} per km",
            "You drive about ${Fmt.km(i.kmPerDay)} a day, which costs about ${Fmt.inr(i.costPerKm * i.kmPerDay)} a day or ${Fmt.inr(i.costPerKm * i.kmPerDay * 30)} a month in fuel.")

        // Fill rhythm.
        if (i.avgDaysBetween != null && i.avgFill != null && i.fillCount >= 3) {
            val days = i.avgDaysBetween
            val every = if (days < 1.5) "almost every day" else "every ${Fmt.dec(days, 0)} days"
            tips += Tip(TipKind.INFO, "You fill up $every",
                "On average ${Fmt.inr(i.avgFill)} a visit, about ${Fmt.dec(i.totalFuel / i.fillCount)} $unit.")
        }

        i.rangeKm?.let { tips += Tip(TipKind.INFO, "Full-tank range ≈ ${Fmt.km(it)}",
            "Your ${Fmt.dec(cap ?: 0.0)} $unit tank at your average mileage.") }

        i.yearlyProjection?.let { tips += Tip(TipKind.INFO, "About ${Fmt.inr(it)} a year",
            "What fuel will cost this vehicle over a year if you keep driving like the last three months.") }

        return tips.sortedBy { it.kind.ordinal }
    }
}

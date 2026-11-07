package com.wizlabs.mileage.insights

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

/** Indian-style number formatting (1,23,456) without depending on Android ICU, so it is unit-testable. */
object Fmt {
    fun group(n: Long): String {
        val neg = n < 0
        val s = abs(n).toString()
        val out = if (s.length <= 3) s else {
            val last3 = s.takeLast(3)
            val rest = s.dropLast(3)
            rest.reversed().chunked(2).joinToString(",").reversed() + "," + last3
        }
        return if (neg) "-$out" else out
    }

    /** ₹1,23,456 (no paise). */
    fun inr(x: Double): String = "₹" + group(x.roundToLong())

    /** ₹94.72 with paise, grouped. */
    fun inr2(x: Double): String {
        val cents = (abs(x) * 100).roundToLong()
        val s = "₹" + group(cents / 100) + "." + (cents % 100).toString().padStart(2, '0')
        return if (x < 0) "-$s" else s
    }

    fun dec(x: Double, places: Int = 1): String {
        if (places == 0) return group(x.roundToLong())
        val formatted = String.format(Locale.US, "%.${places}f", abs(x))
        val (w, f) = formatted.split(".")
        val sign = if (x < 0 && formatted.any { it in '1'..'9' }) "-" else ""
        return sign + group(w.toLong()) + "." + f
    }

    fun km(x: Double): String = group(x.roundToLong()) + " km"

    fun pct(x: Double): String = (abs(x) * 100).roundToLong().toString() + "%"

    private val DAY = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)
    private val DAY_SHORT = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
    private val WEEKDAY = DateTimeFormatter.ofPattern("EEE, d MMM", Locale.ENGLISH)
    private val MONTH = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)

    fun date(ms: Long, zone: ZoneId = ZoneId.systemDefault()): String = DAY.format(Instant.ofEpochMilli(ms).atZone(zone))
    fun dateShort(ms: Long, zone: ZoneId = ZoneId.systemDefault()): String = DAY_SHORT.format(Instant.ofEpochMilli(ms).atZone(zone))
    fun weekday(ms: Long, zone: ZoneId = ZoneId.systemDefault()): String = WEEKDAY.format(Instant.ofEpochMilli(ms).atZone(zone))
    fun month(ms: Long, zone: ZoneId = ZoneId.systemDefault()): String = MONTH.format(Instant.ofEpochMilli(ms).atZone(zone))
}

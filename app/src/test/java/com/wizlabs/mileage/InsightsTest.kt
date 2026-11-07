package com.wizlabs.mileage

import com.wizlabs.mileage.data.Csv
import com.wizlabs.mileage.insights.Fmt
import com.wizlabs.mileage.insights.InsightsEngine
import com.wizlabs.mileage.insights.Period
import com.wizlabs.mileage.model.AppData
import com.wizlabs.mileage.model.Brand
import com.wizlabs.mileage.model.FillUp
import com.wizlabs.mileage.model.Vehicle
import com.wizlabs.mileage.model.VehicleType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class InsightsTest {
    private val zone = ZoneOffset.UTC
    private val v = Vehicle(id = "v", name = "Activa", type = VehicleType.SCOOTER, tankCapacity = 5.3)
    private fun day(d: Int) = LocalDate.of(2026, 10, d).atTime(10, 0).toInstant(zone).toEpochMilli()
    private fun fill(d: Int, odo: Double, amount: Double, rsp: Double = 100.0, full: Boolean = false, brand: Brand? = null) =
        FillUp(id = "f$d", vehicleId = "v", date = day(d), odometer = odo, amount = amount, rsp = rsp, fullTank = full, brand = brand)

    @Test fun indianGrouping() {
        assertEquals("999", Fmt.group(999))
        assertEquals("1,000", Fmt.group(1000))
        assertEquals("1,23,456", Fmt.group(123456))
        assertEquals("12,34,56,789", Fmt.group(123456789))
        assertEquals("₹94.72", Fmt.inr2(94.72))
        assertEquals("₹1,23,456", Fmt.inr(123456.4))
        assertEquals("1,234.5", Fmt.dec(1234.5))
        assertEquals("-0.5", Fmt.dec(-0.5))
    }

    @Test fun fullToFullMileageCountsPartialFillsBetween() {
        val fills = listOf(
            fill(1, 1000.0, 400.0, full = true),   // opening full tank (fuel not counted)
            fill(5, 1100.0, 200.0),                // 2 L partial
            fill(9, 1250.0, 300.0, full = true),   // 3 L closes: 250 km / 5 L = 50
        )
        val i = InsightsEngine.compute(fills, Period.ALL, v, now = day(10), zone = zone)
        assertEquals(1, i.segments.size)
        assertEquals(50.0, i.avgMileage!!, 1e-9)
        assertEquals(false, i.mileageEstimated)
        assertEquals(2.0, i.costPerKm!!, 1e-9) // ₹500 / 250 km
        val per = InsightsEngine.perFill(fills.sortedBy { it.odometer })
        assertEquals(50.0, per["f9"]!!.mileage!!, 1e-9)
        assertNull(per["f5"]!!.mileage)
    }

    @Test fun estimatedMileageWithoutFullTanks() {
        val fills = listOf(fill(1, 1000.0, 300.0), fill(4, 1150.0, 300.0), fill(8, 1300.0, 300.0))
        val i = InsightsEngine.compute(fills, Period.ALL, v, now = day(10), zone = zone)
        assertTrue(i.mileageEstimated)
        assertEquals(50.0, i.avgMileage!!, 1e-9) // 300 km / 6 L
        assertTrue(i.tips.any { it.title == "Get exact mileage" })
    }

    @Test fun mileageDropTipAndBrandRanking() {
        val fills = listOf(
            fill(1, 1000.0, 500.0, full = true, brand = Brand.HPCL),
            fill(3, 1250.0, 500.0, full = true, brand = Brand.HPCL), // 250/5 = 50 (HPCL)
            fill(5, 1500.0, 500.0, full = true, brand = Brand.BPCL), // 50 (HPCL)
            fill(7, 1700.0, 500.0, full = true, brand = Brand.BPCL), // 40 (BPCL)
            fill(9, 1900.0, 500.0, full = true, brand = Brand.BPCL), // 40 (BPCL)
        )
        val i = InsightsEngine.compute(fills, Period.ALL, v, now = day(10), zone = zone)
        assertEquals(4, i.segments.size)
        val hp = i.brands.first { it.brand == Brand.HPCL }
        assertEquals(50.0, hp.mileage!!, 1e-9)
        assertTrue(i.tips.any { it.title.startsWith("Best mileage with HPCL") })
        assertNotNull(i.mileageChange)
    }

    @Test fun oversizedFillIsFlagged() {
        val fills = listOf(fill(1, 1000.0, 300.0), fill(3, 1100.0, 900.0)) // 9 L in a 5.3 L tank
        val i = InsightsEngine.compute(fills, Period.ALL, v, now = day(10), zone = zone)
        assertTrue(i.tips.any { it.title == "Check one entry" })
    }

    @Test fun csvRoundTripSkipsDuplicates() {
        val d = AppData(vehicles = listOf(v), fills = listOf(fill(1, 1000.0, 300.0, brand = Brand.SHELL), fill(2, 1040.0, 200.0)), selectedVehicleId = "v")
        val csv = Csv.export(d, zone)
        val fresh = Csv.import(csv, AppData(), zone)
        assertEquals(2, fresh.added); assertEquals(1, fresh.newVehicles)
        assertEquals(Brand.SHELL, fresh.data.fills.first { it.odometer == 1000.0 }.brand)
        val again = Csv.import(csv, d, zone)
        assertEquals(0, again.added); assertEquals(2, again.skipped)
    }

    @Test fun csvQuotedFields() {
        val rows = Csv.parse("a,\"b, c\",\"say \"\"hi\"\"\"\r\n1,2,3\n")
        assertEquals(listOf("a", "b, c", "say \"hi\""), rows[0])
        assertEquals(listOf("1", "2", "3"), rows[1])
    }
}

package com.wizlabs.mileage

import com.wizlabs.mileage.data.ShareCodec
import com.wizlabs.mileage.model.AppData
import com.wizlabs.mileage.model.Brand
import com.wizlabs.mileage.model.FillUp
import com.wizlabs.mileage.model.FuelType
import com.wizlabs.mileage.model.Vehicle
import com.wizlabs.mileage.model.VehicleType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ShareCodecTest {
    private val v = Vehicle(id = "v", name = "Swift ✓", type = VehicleType.CAR, fuel = FuelType.DIESEL, registration = "MH14CD5678", tankCapacity = 37.5)

    private fun sample(n: Int): List<FillUp> {
        val r = Random(3)
        var t = 1_780_000_000_000L; var odo = 42_110.0
        return (0 until n).map { i ->
            t += r.nextLong(3, 12) * 86_400_000L + r.nextLong(0, 3_600_000L)
            odo += r.nextInt(80, 400) + 0.5 * (i % 2)
            FillUp(
                vehicleId = "v", date = t / 60_000L * 60_000L, odometer = odo, amount = listOf(1500.0, 2000.0, 2345.67)[i % 3],
                rsp = 94.72 + (i / 10) * 0.35, fullTank = i % 3 == 0, brand = Brand.entries.getOrNull(i % 7),
                note = if (i == 4) "Highway, \"tolls\" | ₹" else "",
            )
        }
    }

    @Test fun roundTripIsLossless() {
        val fills = sample(60)
        val code = ShareCodec.encode(listOf(v), fills)
        assertTrue(code.startsWith("WZM1:"))
        val b = ShareCodec.decode("Here's my log 👉 $code 👈 thanks")
        assertEquals(1, b.vehicles.size)
        val got = b.vehicles[0]
        assertEquals(v.copy(id = got.id, createdAt = got.createdAt), got)
        assertEquals(fills.size, b.fills.size)
        fills.zip(b.fills).forEach { (a, c) ->
            assertEquals(a.copy(id = c.id, vehicleId = c.vehicleId), c)
        }
    }

    @Test fun codeIsCompact() {
        val code = ShareCodec.encode(listOf(v), sample(150))
        // ~2 years of fill-ups should still fit a few QR frames.
        assertTrue("length ${code.length}", code.length < 2500)
    }

    @Test fun damagedCodeIsRejected() {
        val code = ShareCodec.encode(listOf(v), sample(10))
        val broken = code.substring(0, code.length - 6) + (if (code[code.length - 6] == 'A') "B" else "A") + code.takeLast(5)
        try { ShareCodec.decode(broken); throw AssertionError("should fail") } catch (_: ShareCodec.InvalidCode) {}
        try { ShareCodec.decode("hello"); throw AssertionError("should fail") } catch (_: ShareCodec.InvalidCode) {}
    }

    @Test fun qrFramesReassembleInAnyOrder() {
        val code = ShareCodec.encode(listOf(v), sample(150))
        val frames = ShareCodec.qrFrames(code)
        assertTrue(frames.size >= 2)
        val c = ShareCodec.FrameCollector()
        frames.reversed().forEach { c.offer(it); c.offer(it) }
        assertEquals(code, c.code)
        val partial = ShareCodec.FrameCollector(); partial.offer(frames[0])
        assertNull(partial.code)
    }

    @Test fun mergeSkipsDuplicatesAndMatchesByName() {
        val fills = sample(8)
        val mine = AppData(vehicles = listOf(v.copy(name = "swift ✓")), fills = fills.take(5), selectedVehicleId = "v")
        val (out, plan) = ShareCodec.merge(mine, ShareCodec.decode(ShareCodec.encode(listOf(v), fills)))
        assertEquals(3, plan.newFills); assertEquals(5, plan.duplicates)
        assertEquals(1, out.vehicles.size); assertEquals(8, out.fills.size)
        val (fresh, plan2) = ShareCodec.merge(AppData(), ShareCodec.decode(ShareCodec.encode(listOf(v), fills)))
        assertEquals(listOf("Swift ✓"), plan2.newVehicles); assertEquals(8, fresh.fills.size)
    }
}

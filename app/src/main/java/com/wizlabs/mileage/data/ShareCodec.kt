package com.wizlabs.mileage.data

import com.wizlabs.mileage.model.AppData
import com.wizlabs.mileage.model.Brand
import com.wizlabs.mileage.model.FillUp
import com.wizlabs.mileage.model.FuelType
import com.wizlabs.mileage.model.Vehicle
import com.wizlabs.mileage.model.VehicleType
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.CRC32
import java.util.zip.Deflater
import java.util.zip.Inflater
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Offline share codes. A log is packed into a compact binary form (deltas + varints), compressed,
 * checksummed and written as URL-safe Base64 behind a "WZM1:" prefix. The same text travels as a
 * pasteable code (WhatsApp, SMS…) or as one or more QR frames.
 */
object ShareCodec {
    const val PREFIX = "WZM1:"
    private const val FORMAT = 1
    /** Characters of payload per QR frame. Small enough to scan reliably off a phone screen. */
    const val QR_CHUNK = 700

    data class Bundle(val vehicles: List<Vehicle>, val fills: List<FillUp>)

    // ---------- encode ----------

    fun encode(vehicles: List<Vehicle>, fills: List<FillUp>): String {
        val w = Writer()
        w.byte(FORMAT)
        w.varint(vehicles.size.toLong())
        for (v in vehicles) {
            w.str(v.name); w.byte(v.type.ordinal); w.byte(v.fuel.ordinal); w.str(v.registration)
            w.varint(v.tankCapacity?.let { (it * 10).roundToLong() } ?: 0L)
            val list = fills.filter { it.vehicleId == v.id }.sortedBy { it.date }
            w.varint(list.size.toLong())
            var lastMin = 0L; var lastOdo = 0L; var lastRsp = 0L
            for (f in list) {
                val min = f.date / 60_000L
                val odo = (f.odometer * 10).roundToLong()
                val rsp = (f.rsp * 100).roundToLong()
                w.zigzag(min - lastMin); w.zigzag(odo - lastOdo)
                w.varint((f.amount * 100).roundToLong()); w.zigzag(rsp - lastRsp)
                val brand = f.brand?.let { it.ordinal + 1 } ?: 0
                w.byte((if (f.fullTank) 1 else 0) or (brand shl 1) or (if (f.note.isNotBlank()) 0x10 else 0))
                if (f.note.isNotBlank()) w.str(f.note)
                lastMin = min; lastOdo = odo; lastRsp = rsp
            }
        }
        val raw = w.bytes()
        val crc = CRC32().apply { update(raw) }.value
        val body = raw + byteArrayOf((crc shr 24).toByte(), (crc shr 16).toByte(), (crc shr 8).toByte(), crc.toByte())
        return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(deflate(body))
    }

    // ---------- decode ----------

    class InvalidCode(msg: String) : Exception(msg)

    /** Accepts a code with surrounding text (e.g. a forwarded WhatsApp message). */
    fun decode(text: String): Bundle {
        val start = text.indexOf(PREFIX)
        if (start < 0) throw InvalidCode("This isn't a Wiz Mileage share code.")
        val b64 = text.substring(start + PREFIX.length).takeWhile { it.isLetterOrDigit() || it == '-' || it == '_' }
        val body = try { inflate(Base64.getUrlDecoder().decode(b64)) } catch (e: Exception) {
            throw InvalidCode("The code is incomplete or damaged. Copy the whole code and try again.")
        }
        if (body.size < 5) throw InvalidCode("The code is incomplete.")
        val raw = body.copyOfRange(0, body.size - 4)
        val crc = CRC32().apply { update(raw) }.value
        val got = body.takeLast(4).fold(0L) { acc, b -> (acc shl 8) or (b.toLong() and 0xFF) }
        if (crc != got) throw InvalidCode("The code is damaged. Copy the whole code and try again.")

        val r = Reader(raw)
        if (r.byte() != FORMAT) throw InvalidCode("This code comes from a newer version of the app. Update Wiz Mileage.")
        val vehicles = mutableListOf<Vehicle>(); val fills = mutableListOf<FillUp>()
        repeat(r.varint().toInt()) {
            val v = Vehicle(
                name = r.str(),
                type = VehicleType.entries.getOrElse(r.byte()) { VehicleType.OTHER },
                fuel = FuelType.entries.getOrElse(r.byte()) { FuelType.PETROL },
                registration = r.str(),
                tankCapacity = r.varint().takeIf { it > 0 }?.let { it / 10.0 },
            )
            vehicles += v
            var min = 0L; var odo = 0L; var rsp = 0L
            repeat(r.varint().toInt()) {
                min += r.zigzag(); odo += r.zigzag()
                val amount = r.varint() / 100.0
                rsp += r.zigzag()
                val flags = r.byte()
                val note = if (flags and 0x10 != 0) r.str() else ""
                val brandIdx = (flags shr 1) and 0x7
                fills += FillUp(
                    vehicleId = v.id, date = min * 60_000L, odometer = odo / 10.0, amount = amount, rsp = rsp / 100.0,
                    fullTank = flags and 1 != 0, brand = if (brandIdx == 0) null else Brand.entries.getOrNull(brandIdx - 1), note = note,
                )
            }
        }
        return Bundle(vehicles, fills)
    }

    // ---------- merge ----------

    data class MergePlan(val newFills: Int, val duplicates: Int, val newVehicles: List<String>, val mergedInto: List<String>)

    /** Vehicles match by name (case-insensitive). Fill-ups already present (same odometer and amount within a day) are skipped. */
    fun merge(existing: AppData, b: Bundle): Pair<AppData, MergePlan> {
        val vehicles = existing.vehicles.toMutableList()
        val fills = existing.fills.toMutableList()
        var added = 0; var dup = 0
        val newV = mutableListOf<String>(); val merged = mutableListOf<String>()
        for (v in b.vehicles) {
            val match = vehicles.firstOrNull { it.name.equals(v.name, ignoreCase = true) }
            val target = match ?: v.also { vehicles += it; newV += it.name }
            if (match != null) merged += match.name
            for (f in b.fills.filter { it.vehicleId == v.id }) {
                val isDup = fills.any {
                    it.vehicleId == target.id && abs(it.odometer - f.odometer) < 0.05 &&
                        abs(it.amount - f.amount) < 0.005 && abs(it.date - f.date) < 86_400_000L
                }
                if (isDup) dup++ else { fills += f.copy(vehicleId = target.id); added++ }
            }
        }
        val sel = existing.selectedVehicleId ?: vehicles.firstOrNull()?.id
        return existing.copy(vehicles = vehicles, fills = fills, selectedVehicleId = sel) to MergePlan(added, dup, newV, merged)
    }

    // ---------- QR framing ----------

    /** Splits a code into QR frames: "WZMQ|<id>|<index>|<total>|<chunk>". One frame when it fits. */
    fun qrFrames(code: String): List<String> {
        val payload = code.removePrefix(PREFIX)
        val chunks = payload.chunked(QR_CHUNK)
        val id = (CRC32().apply { update(payload.toByteArray()) }.value % 1_000_000).toString().padStart(6, '0')
        return chunks.mapIndexed { i, c -> "WZMQ|$id|${i + 1}|${chunks.size}|$c" }
    }

    /** Collects scanned QR frames; [code] becomes non-null once every frame of one set has been seen. */
    class FrameCollector {
        private var id: String? = null
        private val parts = sortedMapOf<Int, String>()
        var total = 0; private set
        val have: Int get() = parts.size

        /** Returns true when the text was a frame we hadn't seen. */
        fun offer(text: String): Boolean {
            if (text.startsWith(PREFIX)) { id = "single"; total = 1; parts.clear(); parts[1] = text.removePrefix(PREFIX); return true }
            val p = text.split('|', limit = 5)
            if (p.size != 5 || p[0] != "WZMQ") return false
            val idx = p[2].toIntOrNull() ?: return false
            val n = p[3].toIntOrNull() ?: return false
            if (id != p[1]) { id = p[1]; parts.clear(); total = n }
            if (parts.containsKey(idx)) return false
            parts[idx] = p[4]
            return true
        }

        val code: String? get() = if (total > 0 && parts.size == total) PREFIX + parts.values.joinToString("") else null
    }

    // ---------- bytes ----------

    private fun deflate(b: ByteArray): ByteArray {
        val d = Deflater(Deflater.BEST_COMPRESSION, true); d.setInput(b); d.finish()
        val out = ByteArrayOutputStream(); val buf = ByteArray(4096)
        while (!d.finished()) out.write(buf, 0, d.deflate(buf))
        d.end(); return out.toByteArray()
    }

    private fun inflate(b: ByteArray): ByteArray {
        val i = Inflater(true); i.setInput(b)
        val out = ByteArrayOutputStream(); val buf = ByteArray(4096)
        while (!i.finished()) {
            val n = i.inflate(buf)
            if (n == 0 && (i.needsInput() || i.needsDictionary())) break
            out.write(buf, 0, n)
            if (out.size() > 5_000_000) throw IllegalStateException("too large")
        }
        val ok = i.finished(); i.end()
        if (!ok) throw IllegalStateException("truncated")
        return out.toByteArray()
    }

    private class Writer {
        private val out = ByteArrayOutputStream()
        fun byte(v: Int) = out.write(v and 0xFF)
        fun varint(v: Long) { var x = v; while (x and 0x7FL.inv() != 0L) { out.write(((x and 0x7F) or 0x80).toInt()); x = x ushr 7 }; out.write(x.toInt()) }
        fun zigzag(v: Long) = varint((v shl 1) xor (v shr 63))
        fun str(s: String) { val b = s.toByteArray(Charsets.UTF_8); varint(b.size.toLong()); out.write(b) }
        fun bytes(): ByteArray = out.toByteArray()
    }

    private class Reader(private val b: ByteArray) {
        private var p = 0
        fun byte(): Int { if (p >= b.size) throw InvalidCode("The code is incomplete."); return b[p++].toInt() and 0xFF }
        fun varint(): Long { var r = 0L; var s = 0; while (true) { val x = byte(); r = r or ((x and 0x7F).toLong() shl s); if (x and 0x80 == 0) return r; s += 7; if (s > 63) throw InvalidCode("The code is damaged.") } }
        fun zigzag(): Long { val v = varint(); return (v ushr 1) xor -(v and 1) }
        fun str(): String { val n = varint().toInt(); if (n < 0 || p + n > b.size) throw InvalidCode("The code is damaged."); val s = String(b, p, n, Charsets.UTF_8); p += n; return s }
    }
}

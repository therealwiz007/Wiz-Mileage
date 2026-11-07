package com.wizlabs.mileage.data

import com.wizlabs.mileage.model.AppData
import com.wizlabs.mileage.model.Brand
import com.wizlabs.mileage.model.FillUp
import com.wizlabs.mileage.model.FuelType
import com.wizlabs.mileage.model.Vehicle
import com.wizlabs.mileage.model.VehicleType
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs

/** CSV export (opens in Excel / Google Sheets) that doubles as a backup the app can import again. */
object Csv {
    private val HEADER = listOf(
        "vehicle", "vehicle_type", "fuel", "registration", "date", "odometer_km",
        "amount_inr", "rsp_inr_per_unit", "quantity", "unit", "full_tank", "brand", "note",
    )
    private val DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

    fun export(data: AppData, zone: ZoneId = ZoneId.systemDefault()): String {
        val sb = StringBuilder()
        sb.append(HEADER.joinToString(",")).append("\r\n")
        for (v in data.vehicles) {
            for (f in data.fillsFor(v.id)) {
                val row = listOf(
                    v.name, v.type.label, v.fuel.label, v.registration,
                    DATE_FMT.format(LocalDateTime.ofInstant(Instant.ofEpochMilli(f.date), zone)),
                    num(f.odometer), num(f.amount), num(f.rsp), "%.3f".format(java.util.Locale.US, f.quantity),
                    v.fuel.unit, if (f.fullTank) "yes" else "no", f.brand?.short ?: "", f.note,
                )
                sb.append(row.joinToString(",") { quote(it) }).append("\r\n")
            }
        }
        return sb.toString()
    }

    data class ImportResult(val data: AppData, val added: Int, val skipped: Int, val newVehicles: Int)

    /** Merges rows into [existing]. Vehicles are matched by name; exact duplicate fill-ups are skipped. */
    fun import(text: String, existing: AppData, zone: ZoneId = ZoneId.systemDefault()): ImportResult {
        val rows = parse(text.removePrefix("\uFEFF")).filter { r -> r.any { it.isNotBlank() } }
        if (rows.isEmpty()) throw IllegalArgumentException("The file is empty.")
        val head = rows.first().map { it.trim().lowercase() }
        fun col(vararg names: String) = names.firstNotNullOfOrNull { n -> head.indexOf(n).takeIf { it >= 0 } }
        val cVehicle = col("vehicle") ?: throw IllegalArgumentException("Missing a 'vehicle' column.")
        val cDate = col("date") ?: throw IllegalArgumentException("Missing a 'date' column.")
        val cOdo = col("odometer_km", "odometer") ?: throw IllegalArgumentException("Missing an 'odometer_km' column.")
        val cAmount = col("amount_inr", "amount") ?: throw IllegalArgumentException("Missing an 'amount_inr' column.")
        val cRsp = col("rsp_inr_per_unit", "rsp") ?: throw IllegalArgumentException("Missing an 'rsp' column.")
        val cType = col("vehicle_type"); val cFuel = col("fuel"); val cReg = col("registration")
        val cFull = col("full_tank"); val cBrand = col("brand"); val cNote = col("note")

        val vehicles = existing.vehicles.toMutableList()
        val fills = existing.fills.toMutableList()
        var added = 0; var skipped = 0; var newVehicles = 0

        for (r in rows.drop(1)) {
            fun cell(i: Int?) = i?.let { r.getOrNull(it) }?.trim().orEmpty()
            val name = cell(cVehicle)
            val odo = cell(cOdo).replace(",", "").toDoubleOrNull()
            val amount = cell(cAmount).replace(",", "").replace("₹", "").toDoubleOrNull()
            val rsp = cell(cRsp).replace(",", "").replace("₹", "").toDoubleOrNull()
            val date = parseDate(cell(cDate), zone)
            if (name.isBlank() || odo == null || amount == null || rsp == null || rsp <= 0 || date == null) {
                skipped++; continue
            }
            var v = vehicles.firstOrNull { it.name.equals(name, ignoreCase = true) }
            if (v == null) {
                v = Vehicle(
                    name = name,
                    type = VehicleType.entries.firstOrNull { it.label.equals(cell(cType), true) } ?: VehicleType.OTHER,
                    fuel = FuelType.entries.firstOrNull { it.label.equals(cell(cFuel), true) } ?: FuelType.PETROL,
                    registration = cell(cReg),
                )
                vehicles += v; newVehicles++
            }
            val dup = fills.any {
                it.vehicleId == v.id && abs(it.odometer - odo) < 0.01 && abs(it.amount - amount) < 0.01 &&
                    abs(it.date - date) < 24 * 3600_000L
            }
            if (dup) { skipped++; continue }
            fills += FillUp(
                vehicleId = v.id, date = date, odometer = odo, amount = amount, rsp = rsp,
                fullTank = cell(cFull).lowercase() in setOf("yes", "y", "true", "1", "full"),
                brand = Brand.parse(cell(cBrand)), note = cell(cNote),
            )
            added++
        }
        val sel = existing.selectedVehicleId ?: vehicles.firstOrNull()?.id
        return ImportResult(existing.copy(vehicles = vehicles, fills = fills, selectedVehicleId = sel), added, skipped, newVehicles)
    }

    private fun parseDate(s: String, zone: ZoneId): Long? {
        val patterns = listOf("yyyy-MM-dd HH:mm", "yyyy-MM-dd'T'HH:mm", "yyyy-MM-dd HH:mm:ss")
        for (p in patterns) runCatching {
            return LocalDateTime.parse(s, DateTimeFormatter.ofPattern(p)).atZone(zone).toInstant().toEpochMilli()
        }
        for (p in listOf("yyyy-MM-dd", "dd/MM/yyyy", "dd-MM-yyyy", "d/M/yyyy")) runCatching {
            return LocalDate.parse(s, DateTimeFormatter.ofPattern(p)).atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
        }
        return null
    }

    private fun num(d: Double): String =
        if (d == Math.floor(d)) d.toLong().toString() else "%.2f".format(java.util.Locale.US, d)

    private fun quote(s: String): String =
        if (s.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + s.replace("\"", "\"\"") + "\"" else s

    /** RFC 4180 style parser: quoted fields, escaped quotes, newlines inside quotes. */
    fun parse(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val field = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < text.length && text[i + 1] == '"') { field.append('"'); i++ } else inQuotes = false
                } else field.append(c)
            } else when (c) {
                '"' -> inQuotes = true
                ',' -> { row += field.toString(); field.clear() }
                '\r' -> {}
                '\n' -> { row += field.toString(); field.clear(); rows += row; row = mutableListOf() }
                else -> field.append(c)
            }
            i++
        }
        if (field.isNotEmpty() || row.isNotEmpty()) { row += field.toString(); rows += row }
        return rows
    }
}

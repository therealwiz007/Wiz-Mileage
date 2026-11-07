package com.wizlabs.mileage.data

import com.wizlabs.mileage.model.AppData
import com.wizlabs.mileage.model.Brand
import com.wizlabs.mileage.model.FillUp
import com.wizlabs.mileage.model.FuelType
import com.wizlabs.mileage.model.ThemeMode
import com.wizlabs.mileage.model.Vehicle
import com.wizlabs.mileage.model.VehicleType
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Stores everything in one small JSON file in the app's private storage.
 * Writes go to a temp file first and are then renamed, so a crash mid-write never
 * leaves a half-written file. A file that cannot be read is kept aside, never overwritten.
 */
class Store(private val file: File) {

    fun load(): AppData {
        if (!file.exists()) return AppData()
        return try {
            decode(file.readText())
        } catch (e: Exception) {
            file.copyTo(File(file.parentFile, "mileage_log.unreadable-${System.currentTimeMillis()}.json"), overwrite = true)
            AppData()
        }
    }

    @Synchronized
    fun save(data: AppData) {
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(encode(data))
        if (!tmp.renameTo(file)) {
            file.delete()
            tmp.renameTo(file)
        }
    }

    companion object {
        private const val VERSION = 1

        fun encode(d: AppData): String {
            val root = JSONObject()
            root.put("version", VERSION)
            root.put("selectedVehicleId", d.selectedVehicleId ?: JSONObject.NULL)
            root.put("theme", d.theme.name)
            root.put("vehicles", JSONArray().apply {
                d.vehicles.forEach { v ->
                    put(JSONObject().apply {
                        put("id", v.id); put("name", v.name); put("type", v.type.name)
                        put("fuel", v.fuel.name); put("registration", v.registration)
                        put("tankCapacity", v.tankCapacity ?: JSONObject.NULL)
                        put("createdAt", v.createdAt)
                    })
                }
            })
            root.put("fills", JSONArray().apply {
                d.fills.forEach { f ->
                    put(JSONObject().apply {
                        put("id", f.id); put("vehicleId", f.vehicleId); put("date", f.date)
                        put("odometer", f.odometer); put("amount", f.amount); put("rsp", f.rsp)
                        put("fullTank", f.fullTank); put("brand", f.brand?.name ?: JSONObject.NULL)
                        put("note", f.note)
                    })
                }
            })
            return root.toString()
        }

        fun decode(text: String): AppData {
            val root = JSONObject(text)
            val vehicles = root.optJSONArray("vehicles")?.let { arr ->
                (0 until arr.length()).map { i ->
                    val o = arr.getJSONObject(i)
                    Vehicle(
                        id = o.getString("id"),
                        name = o.getString("name"),
                        type = enumOr(o.optString("type"), VehicleType.OTHER),
                        fuel = enumOr(o.optString("fuel"), FuelType.PETROL),
                        registration = o.optString("registration", ""),
                        tankCapacity = if (o.isNull("tankCapacity")) null else o.optDouble("tankCapacity"),
                        createdAt = o.optLong("createdAt", 0L),
                    )
                }
            } ?: emptyList()
            val fills = root.optJSONArray("fills")?.let { arr ->
                (0 until arr.length()).map { i ->
                    val o = arr.getJSONObject(i)
                    FillUp(
                        id = o.getString("id"),
                        vehicleId = o.getString("vehicleId"),
                        date = o.getLong("date"),
                        odometer = o.getDouble("odometer"),
                        amount = o.getDouble("amount"),
                        rsp = o.getDouble("rsp"),
                        fullTank = o.optBoolean("fullTank", false),
                        brand = if (o.isNull("brand")) null else Brand.parse(o.optString("brand")),
                        note = o.optString("note", ""),
                    )
                }
            } ?: emptyList()
            return AppData(
                vehicles = vehicles,
                fills = fills,
                selectedVehicleId = if (root.isNull("selectedVehicleId")) null else root.optString("selectedVehicleId"),
                theme = enumOr(root.optString("theme"), ThemeMode.SYSTEM),
            )
        }

        private inline fun <reified T : Enum<T>> enumOr(name: String?, fallback: T): T =
            enumValues<T>().firstOrNull { it.name == name } ?: fallback
    }
}

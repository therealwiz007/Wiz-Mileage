package com.wizlabs.mileage.model

import java.util.UUID

enum class VehicleType(val label: String) {
    BIKE("Bike"), SCOOTER("Scooter"), CAR("Car"), SUV("SUV"), OTHER("Other")
}

enum class FuelType(val label: String, val unit: String) {
    PETROL("Petrol", "L"),
    PREMIUM("Premium petrol", "L"),
    DIESEL("Diesel", "L"),
    CNG("CNG", "kg");
}

/** Optional fuel retailer. Colours are only used for chart series and small dots. */
enum class Brand(val short: String, val full: String, val color: Long) {
    IOCL("IOCL", "IndianOil", 0xFFF37022),
    BPCL("BPCL", "Bharat Petroleum", 0xFF2F7DE1),
    HPCL("HPCL", "Hindustan Petroleum", 0xFFE5383B),
    NAYARA("Nayara", "Nayara Energy", 0xFF2BB5C8),
    JIO_BP("Jio-bp", "Jio-bp", 0xFF2DBE60),
    SHELL("Shell", "Shell", 0xFFF2B705);

    companion object {
        fun parse(s: String?): Brand? {
            if (s.isNullOrBlank()) return null
            val k = s.trim().lowercase().replace("-", "").replace("_", "").replace(" ", "")
            return entries.firstOrNull {
                it.name.lowercase().replace("_", "") == k ||
                    it.short.lowercase().replace("-", "") == k ||
                    it.full.lowercase().replace("-", "").replace(" ", "") == k
            }
        }
    }
}

data class Vehicle(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val type: VehicleType,
    val fuel: FuelType = FuelType.PETROL,
    val registration: String = "",
    val tankCapacity: Double? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

data class FillUp(
    val id: String = UUID.randomUUID().toString(),
    val vehicleId: String,
    /** Epoch millis. */
    val date: Long,
    val odometer: Double,
    /** Money paid, in rupees. */
    val amount: Double,
    /** Retail Selling Price per litre (or per kg for CNG). */
    val rsp: Double,
    val fullTank: Boolean = false,
    val brand: Brand? = null,
    val note: String = "",
) {
    val quantity: Double get() = if (rsp > 0) amount / rsp else 0.0
}

enum class ThemeMode(val label: String) { SYSTEM("System"), DARK("Dark"), LIGHT("Light") }

data class AppData(
    val vehicles: List<Vehicle> = emptyList(),
    val fills: List<FillUp> = emptyList(),
    val selectedVehicleId: String? = null,
    val theme: ThemeMode = ThemeMode.SYSTEM,
) {
    val selectedVehicle: Vehicle?
        get() = vehicles.firstOrNull { it.id == selectedVehicleId } ?: vehicles.firstOrNull()

    fun fillsFor(vehicleId: String): List<FillUp> =
        fills.filter { it.vehicleId == vehicleId }.sortedWith(compareBy({ it.odometer }, { it.date }))
}

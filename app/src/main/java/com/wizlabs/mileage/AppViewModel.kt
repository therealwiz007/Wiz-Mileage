package com.wizlabs.mileage

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wizlabs.mileage.data.Csv
import com.wizlabs.mileage.data.Store
import com.wizlabs.mileage.model.AppData
import com.wizlabs.mileage.model.FillUp
import com.wizlabs.mileage.model.ThemeMode
import com.wizlabs.mileage.model.Vehicle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val store = Store(File(app.filesDir, "mileage_log.json"))

    @OptIn(ExperimentalCoroutinesApi::class)
    private val writer = Dispatchers.IO.limitedParallelism(1)

    private val _data = MutableStateFlow(store.load())
    val data: StateFlow<AppData> = _data.asStateFlow()

    private fun update(change: (AppData) -> AppData) {
        val next = change(_data.value)
        _data.value = next
        viewModelScope.launch(writer) { store.save(next) }
    }

    fun selectVehicle(id: String) = update { it.copy(selectedVehicleId = id) }

    fun saveVehicle(v: Vehicle) = update { d ->
        val exists = d.vehicles.any { it.id == v.id }
        d.copy(
            vehicles = if (exists) d.vehicles.map { if (it.id == v.id) v else it } else d.vehicles + v,
            selectedVehicleId = if (exists) d.selectedVehicleId else v.id,
        )
    }

    fun deleteVehicle(id: String) = update { d ->
        val left = d.vehicles.filterNot { it.id == id }
        d.copy(
            vehicles = left,
            fills = d.fills.filterNot { it.vehicleId == id },
            selectedVehicleId = if (d.selectedVehicleId == id) left.firstOrNull()?.id else d.selectedVehicleId,
        )
    }

    fun saveFill(f: FillUp) = update { d ->
        val exists = d.fills.any { it.id == f.id }
        d.copy(fills = if (exists) d.fills.map { if (it.id == f.id) f else it } else d.fills + f)
    }

    fun deleteFill(id: String): FillUp? {
        val f = _data.value.fills.firstOrNull { it.id == id } ?: return null
        update { d -> d.copy(fills = d.fills.filterNot { it.id == id }) }
        return f
    }

    /** Merges a received share code into the log and switches to the first vehicle it touched. */
    fun importShared(b: com.wizlabs.mileage.data.ShareCodec.Bundle): com.wizlabs.mileage.data.ShareCodec.MergePlan {
        val (next, plan) = com.wizlabs.mileage.data.ShareCodec.merge(_data.value, b)
        val first = b.vehicles.firstOrNull()?.let { v -> next.vehicles.firstOrNull { it.name.equals(v.name, ignoreCase = true) } }
        update { next.copy(selectedVehicleId = first?.id ?: next.selectedVehicleId) }
        return plan
    }

    fun setTheme(t: ThemeMode) = update { it.copy(theme = t) }

    suspend fun exportCsv(uri: Uri): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val d = _data.value
            val text = "\uFEFF" + Csv.export(d)
            getApplication<Application>().contentResolver.openOutputStream(uri, "wt")!!.use {
                it.write(text.toByteArray(Charsets.UTF_8))
            }
            d.fills.size
        }
    }

    suspend fun importCsv(uri: Uri): Result<Csv.ImportResult> = withContext(Dispatchers.IO) {
        runCatching {
            val text = getApplication<Application>().contentResolver.openInputStream(uri)!!.use {
                it.readBytes().toString(Charsets.UTF_8)
            }
            Csv.import(text, _data.value)
        }
    }.onSuccess { r -> update { r.data } }
}

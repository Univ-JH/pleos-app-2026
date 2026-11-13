package io.github.univjh.pleosapp.vehicle

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.listener.DistanceDrivenListener
import ai.pleos.playground.vehicle.listener.EvBatteryLevelListener
import ai.pleos.playground.vehicle.listener.EvBatteryStateOfChargeListener
import ai.pleos.playground.vehicle.listener.RangeRemainingListener
import android.car.Car
import android.car.VehicleGear
import android.car.VehiclePropertyIds
import android.car.drivingstate.CarUxRestrictionsManager
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/** Live values read from the car. A null means the car has not reported that value. */
data class VehicleState(
    val connected: Boolean = false,
    val socPercent: Int? = null,
    val capacityKwh: Double? = null,
    val energyKwh: Double? = null,
    val rangeKm: Double? = null,
    val odometerKm: Double? = null,
    val portOpen: Boolean = false,
    val parked: Boolean = false,
    /** The car asks apps to stay out of the driver's way (moving, or not in park). */
    val driving: Boolean = false,
    /** Charge limit the car reports; null when the app may not read it. */
    val limitPercent: Int? = null,
    val outsideTempC: Double? = null,
)

sealed interface LimitResult {
    /** The car reported the new limit back. */
    data class Verified(val percent: Int) : LimitResult
    /** The request was accepted but the app has no permission to read the limit back. */
    data class Requested(val percent: Int) : LimitResult
    data class Failed(val reason: String) : LimitResult
}

/**
 * Reads battery, charge port, gear and odometer from the car and writes the charge limit.
 *
 * Pleos Vehicle SDK supplies the battery and odometer values and the limit setter. The Android Car
 * API supplies charge port, gear, outside temperature and the limit read-back, which the Pleos SDK
 * does not offer. See docs/qa/SPIKE.md for what was checked on the emulator.
 */
class EnergySource(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state = MutableStateFlow(VehicleState())
    val state: StateFlow<VehicleState> = _state

    private var job: Job? = null
    private var vehicle: Vehicle? = null
    private var car: Car? = null
    private var manager: CarPropertyManager? = null
    private var uxManager: CarUxRestrictionsManager? = null

    private val carProps = listOf(
        VehiclePropertyIds.GEAR_SELECTION,
        VehiclePropertyIds.EV_CHARGE_PORT_OPEN,
        VehiclePropertyIds.ENV_OUTSIDE_TEMPERATURE,
        VehiclePropertyIds.EV_CHARGE_PERCENT_LIMIT,
    )

    private val carCallback = object : CarPropertyManager.CarPropertyEventCallback {
        override fun onChangeEvent(value: CarPropertyValue<*>) = accept(value)
        override fun onErrorEvent(propertyId: Int, areaId: Int) {}
    }
    private val uxListener = CarUxRestrictionsManager.OnUxRestrictionsChangedListener { r ->
        _state.update { it.copy(driving = r.isRequiresDistractionOptimization) }
    }
    private val socListener = object : EvBatteryStateOfChargeListener {
        override fun onEvBatteryStateOfChargeUpdated(value: Int?) { _state.update { it.copy(socPercent = value ?: it.socPercent) } }
        override fun onFailed(e: Exception) {}
    }
    private val levelListener = object : EvBatteryLevelListener {
        override fun onEvBatteryLevelUpdated(level: Float?) { _state.update { it.copy(energyKwh = level?.div(1000.0) ?: it.energyKwh) } }
        override fun onFailed(e: Exception) {}
    }
    private val rangeListener = object : RangeRemainingListener {
        override fun onRangeRemainingChanged(rangeRemaining: Float?) { _state.update { it.copy(rangeKm = rangeRemaining?.div(1000.0) ?: it.rangeKm) } }
        override fun onFailed(e: Exception) {}
    }
    private val odometerListener = object : DistanceDrivenListener {
        override fun onDistanceDrivenUpdated(value: Float?) { _state.update { it.copy(odometerKm = value?.toDouble() ?: it.odometerKm) } }
        override fun onFailed(e: Exception) {}
    }

    @Synchronized fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            while (isActive) {
                try {
                    connect()
                    _state.update { it.copy(connected = true) }
                    // Listeners carry changes; this slow poll only covers a value the car never pushed.
                    while (isActive) { readPleos(); delay(5_000) }
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    Log.w(TAG, "Vehicle connection failed", e)
                    disconnect()
                    _state.update { it.copy(connected = false) }
                    delay(3_000)
                }
            }
        }
    }

    /** Call again after the driver grants a permission so newly readable values are picked up. */
    fun refreshPermissions() { scope.launch { subscribeCar() } }

    private suspend fun connect() {
        val sdk = Vehicle(context).also { it.initialize() }
        vehicle = sdk
        delay(1_500) // The SDK binds its service asynchronously and offers no ready callback.
        sdk.getEvBattery().registerEvBatteryStateOfCharge(socListener)
        sdk.getEvBattery().registerEvBatteryLevel(levelListener)
        sdk.getOdometer().registerRangeRemainingListener(rangeListener)
        sdk.getOdometer().registerDistanceDriven(odometerListener)
        car = Car.createCar(context)
        manager = car!!.getCarManager(Car.PROPERTY_SERVICE) as CarPropertyManager
        uxManager = (car!!.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as CarUxRestrictionsManager).also {
            it.registerListener(uxListener)
            uxListener.onUxRestrictionsChanged(it.currentCarUxRestrictions)
        }
        subscribeCar()
    }

    private fun subscribeCar() {
        val m = manager ?: return
        for (id in carProps) {
            // A property the app may not read throws; the matching state field simply stays null.
            runCatching { m.getProperty<Any>(id, 0)?.let(::accept) }
            runCatching { m.registerCallback(carCallback, id, CarPropertyManager.SENSOR_RATE_ONCHANGE) }
        }
    }

    private fun accept(value: CarPropertyValue<*>) {
        if (value.status != CarPropertyValue.STATUS_AVAILABLE) return
        _state.update {
            when (value.propertyId) {
                VehiclePropertyIds.GEAR_SELECTION -> it.copy(parked = value.value == VehicleGear.GEAR_PARK)
                VehiclePropertyIds.EV_CHARGE_PORT_OPEN -> it.copy(portOpen = value.value == true)
                VehiclePropertyIds.ENV_OUTSIDE_TEMPERATURE -> it.copy(outsideTempC = (value.value as? Float)?.toDouble())
                VehiclePropertyIds.EV_CHARGE_PERCENT_LIMIT -> it.copy(limitPercent = (value.value as? Float)?.toInt())
                else -> it
            }
        }
    }

    private fun readPleos() {
        val sdk = vehicle ?: return
        val ev = sdk.getEvBattery()
        ev.getEvBatteryStateOfCharge({ v -> v?.let { soc -> _state.update { it.copy(socPercent = soc) } } }, {})
        ev.getEvBatteryLevel({ v -> v?.let { wh -> _state.update { it.copy(energyKwh = wh / 1000.0) } } }, {})
        ev.getEvBatteryCapacity({ v -> v?.let { wh -> _state.update { it.copy(capacityKwh = wh / 1000.0) } } }, {})
        sdk.getOdometer().getRangeRemainingDistance({ v -> v?.let { m -> _state.update { it.copy(rangeKm = m / 1000.0) } } }, {})
        sdk.getOdometer().getCurrentDistanceDriven({ v -> v?.let { km -> _state.update { it.copy(odometerKm = km.toDouble()) } } }, {})
    }

    /**
     * Asks the car to stop charging at [percent] and checks that the car took it.
     * The SDK reports success even for a value the car ignores, so the limit is read back.
     */
    suspend fun setChargeLimit(percent: Int): LimitResult {
        val sdk = vehicle ?: return LimitResult.Failed("차량에 연결되지 않았어요")
        val error = suspendCoroutine<Exception?> { cont ->
            runCatching {
                sdk.getEvBattery().setEvChargePercentLimit(percent.toFloat(), { cont.resume(null) }, { cont.resume(it) })
            }.onFailure { cont.resume(it as? Exception ?: Exception(it)) }
        }
        if (error != null) {
            Log.w(TAG, "Charge limit rejected", error)
            return LimitResult.Failed("차가 충전 한도를 받지 않았어요")
        }
        val readable = runCatching { manager?.getProperty<Float>(VehiclePropertyIds.EV_CHARGE_PERCENT_LIMIT, 0) }.getOrNull() != null
        if (!readable) return LimitResult.Requested(percent)
        // The read-back arrives as a property event shortly after the write.
        val confirmed = withTimeoutOrNull(2_500) { state.first { it.limitPercent == percent } }
        if (confirmed == null) {
            val now = runCatching { manager?.getProperty<Float>(VehiclePropertyIds.EV_CHARGE_PERCENT_LIMIT, 0)?.value?.toInt() }.getOrNull()
            if (now != percent) return LimitResult.Failed("차의 충전 한도가 ${now ?: "?"}%로 남아 있어요")
            _state.update { it.copy(limitPercent = percent) }
        }
        return LimitResult.Verified(percent)
    }

    private fun disconnect() {
        runCatching { manager?.unregisterCallback(carCallback) }
        runCatching { uxManager?.unregisterListener() }
        runCatching { car?.disconnect() }
        runCatching { vehicle?.release() }
        manager = null; uxManager = null; car = null; vehicle = null
    }

    private companion object { const val TAG = "ChargeVehicle" }
}

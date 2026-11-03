package io.github.univjh.pleosapp.vehicle

import android.car.Car
import android.car.VehicleGear
import android.car.VehiclePropertyIds
import android.car.drivingstate.CarUxRestrictions
import android.car.drivingstate.CarUxRestrictionsManager
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.os.SystemClock
import android.util.Log
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.api.model.capability.AccessType
import ai.pleos.playground.vehicle.listener.TelltaleInfoListener
import ai.pleos.playground.vehicle.listener.SeatBeltNotBuckledWarningListener
import ai.pleos.playground.vehicle.api.model.telltale.Telltales
import ai.pleos.playground.vehicle.constant.telltale.TelltaleType
import ai.pleos.playground.vehicle.constant.telltale.TelltaleState
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import ai.pleos.playground.navi.data.DestinationArrivedInfo
import io.github.univjh.pleosapp.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import java.util.concurrent.ConcurrentHashMap

interface VehicleSignalSource { val frames: Flow<VehicleFrame> }
interface RoadContextSource { val arrivals: Flow<String> }
interface PerceptionSource { val available: Boolean }
data class SourceStatus(
    val frame: VehicleFrame? = null,
    val uxAllowed: Boolean = false,
    val message: String = "차량 연결을 준비하고 있어요",
    val beltAvailable: Boolean = false,
    val beltMessage: String = "안전벨트 지원 확인 중",
    val parkedConfirmed: Boolean = false,
)

/** Subscribes at 10 Hz. A repeated or stale speed sample never becomes a fresh observation. */
class PleosSignalSource(private val context: Context) : VehicleSignalSource {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val stream = MutableSharedFlow<VehicleFrame>(extraBufferCapacity = 32, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    override val frames = stream.asSharedFlow()
    val status = MutableStateFlow(SourceStatus())
    private var job: Job? = null
    private var car: Car? = null
    private var manager: CarPropertyManager? = null
    private var uxManager: CarUxRestrictionsManager? = null
    private var vehicle: Vehicle? = null
    private var telltaleListener: TelltaleInfoListener? = null
    @Volatile private var usingTelltales = false
    @Volatile private var nextCapabilityCheck = 0L
    private val parkGate = ParkedGate()
    private val belts = ConcurrentHashMap<String, Pair<Boolean, Long>>()
    private val listeners = ConcurrentHashMap<SeatArea, SeatBeltNotBuckledWarningListener>()
    @Volatile private var areas: List<SeatArea> = emptyList()
    @Volatile private var beltReady = false
    @Volatile private var checking = false
    @Volatile private var generation = 0
    @Volatile private var beltMessage = "안전벨트 지원 확인 중"
    @Volatile private var speed: CarPropertyValue<*>? = null
    @Volatile private var gear: CarPropertyValue<*>? = null
    @Volatile private var uxAllowed = false
    private val propertyListener = object : CarPropertyManager.CarPropertyEventCallback {
        override fun onChangeEvent(value: CarPropertyValue<*>) {
            when (value.propertyId) {
                VehiclePropertyIds.PERF_VEHICLE_SPEED -> speed = value
                VehiclePropertyIds.GEAR_SELECTION -> gear = value
            }
        }
        override fun onErrorEvent(propertyId: Int, areaId: Int) {
            if (propertyId == VehiclePropertyIds.PERF_VEHICLE_SPEED) speed = null
            if (propertyId == VehiclePropertyIds.GEAR_SELECTION) gear = null
        }
    }
    private val uxListener = CarUxRestrictionsManager.OnUxRestrictionsChangedListener { restrictions ->
        uxAllowed = !restrictions.isRequiresDistractionOptimization && restrictions.activeRestrictions == CarUxRestrictions.UX_RESTRICTIONS_BASELINE
        // Revoke access immediately instead of waiting for the next speed frame.
        status.value = status.value.copy(uxAllowed = uxAllowed)
    }
    @Synchronized fun start() {
        Log.d("CoachVehicle", "start: active=${job?.isActive}")
        if (job?.isActive == true) return
        val previous = job
        job = scope.launch {
            previous?.join()
            var lastBeltCheck = 0L
            var lastDiagnostic = 0L
            var lastFrameTime = 0L
            try {
                while (isActive) {
                    var now = SystemClock.elapsedRealtime()
                    try {
                        if (car?.isConnected != true) connect()
                        if (now - lastBeltCheck >= 1_000) {
                            if (!beltReady && !checking && now >= nextCapabilityCheck) checkBelts()
                            val token = generation
                            areas.forEach { area ->
                                vehicle?.getSafety()?.hasSeatBeltNotBuckledWarning(area,
                                    { value -> if (token == generation) {
                                        if (value == null) belts.remove(seatName(area))
                                        else belts[seatName(area)] = value to SystemClock.elapsedRealtime()
                                    } }, { if (token == generation) belts.remove(seatName(area)) })
                            }
                            if (usingTelltales) vehicle?.getSafety()?.getTelltalesInfo(
                                { value -> if (token == generation) updateTelltales(value) },
                                { if (token == generation) belts.remove(UNKNOWN_SEAT) })
                            lastBeltCheck = now
                        }
                        val s = speed; val g = gear
                        now = SystemClock.elapsedRealtime()
                        val speedValue = (s?.value as? Float)?.toDouble()
                        val gearValue = when (g?.value as? Int) {
                            VehicleGear.GEAR_PARK -> Gear.PARK; VehicleGear.GEAR_DRIVE -> Gear.DRIVE
                            VehicleGear.GEAR_REVERSE -> Gear.REVERSE; VehicleGear.GEAR_NEUTRAL -> Gear.NEUTRAL
                            else -> Gear.UNKNOWN
                        }
                        val age = s?.let { now - it.timestamp / 1_000_000 } ?: Long.MAX_VALUE
                        val valid = speedValue?.isFinite() == true && s?.status == CarPropertyValue.STATUS_AVAILABLE &&
                            g?.status == CarPropertyValue.STATUS_AVAILABLE && gearValue != Gear.UNKNOWN && age in 0..250
                        val frame = VehicleFrame(now, System.currentTimeMillis(), speedValue, gearValue,
                            belts.filterValues { now - it.second in 0..2_500 }.mapValues { it.value.first }, valid,
                            speedSampleTimeMs = s?.timestamp?.div(1_000_000) ?: now)
                        val parked = parkGate.accept(frame)
                        if (io.github.univjh.pleosapp.BuildConfig.DEBUG && (now-lastDiagnostic >= 1000 || !valid)) {
                            Log.d("CoachTelemetry", "valid=$valid dt=${now-lastFrameTime} age=$age gear=$gearValue speed=$speedValue ux=$uxAllowed parked=$parked belts=${frame.beltWarnings}")
                            lastDiagnostic = now
                        }
                        lastFrameTime = now
                        status.value = SourceStatus(frame, uxAllowed,
                            if (valid) "차량 신호 연결됨" else "유효한 속도·기어 신호를 기다리고 있어요", beltReady, beltMessage, parked)
                        stream.emit(frame)
                        delay(100)
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        Log.w("CoachVehicle", "Vehicle connection failed", e)
                        val frame = VehicleFrame(now, System.currentTimeMillis(), null, Gear.UNKNOWN, valid = false)
                        parkGate.accept(frame)
                        status.value = SourceStatus(frame, message = "차량 연결 또는 권한 확인이 필요해요", beltMessage = beltMessage)
                        stream.emit(frame); disconnect(); delay(2_000)
                    }
                }
            } finally { disconnect() }
        }
    }
    private fun connect() {
        Log.d("CoachVehicle", "Connecting car")
        car = Car.createCar(context)
        Log.d("CoachVehicle", "Car connected")
        manager = car!!.getCarManager(Car.PROPERTY_SERVICE) as CarPropertyManager
        Log.d("CoachVehicle", "Property manager ready")
        val required = setOf(VehiclePropertyIds.PERF_VEHICLE_SPEED, VehiclePropertyIds.GEAR_SELECTION)
        check(manager!!.getPropertyList(android.util.ArraySet<Int>().apply { addAll(required) }).size == required.size) { "Speed/gear capability or read permission missing" }
        Log.d("CoachVehicle", "Capabilities ready")
        speed = manager!!.getProperty<Float>(VehiclePropertyIds.PERF_VEHICLE_SPEED, 0)
        Log.d("CoachVehicle", "Speed read")
        gear = manager!!.getProperty<Int>(VehiclePropertyIds.GEAR_SELECTION, 0)
        Log.d("CoachVehicle", "Gear read")
        check(manager!!.registerCallback(propertyListener, VehiclePropertyIds.PERF_VEHICLE_SPEED, 10f))
        check(manager!!.registerCallback(propertyListener, VehiclePropertyIds.GEAR_SELECTION, CarPropertyManager.SENSOR_RATE_ONCHANGE))
        Log.d("CoachVehicle", "Subscribed properties")
        uxManager = car!!.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as CarUxRestrictionsManager
        uxManager!!.registerListener(uxListener)
        uxListener.onUxRestrictionsChanged(uxManager!!.currentCarUxRestrictions)
        Log.d("CoachVehicle", "Properties and UX connected")
        vehicle = Vehicle(context).also { it.initialize() }
        Log.d("CoachVehicle", "Vehicle initialized")
    }
    private fun checkBelts() {
        val sdk = vehicle ?: return
        val token = generation
        Log.d("CoachVehicle", "Checking seatbelt capability")
        nextCapabilityCheck = SystemClock.elapsedRealtime() + 10_000
        checking = true
        sdk.getSafety().checkSeatNotBuckledWarningCapabilities({ config, supportedAreas ->
            if (token == generation) {
                checking = false
                beltReady = config.accessType in setOf(AccessType.READ, AccessType.READ_WRITE) && supportedAreas.isNotEmpty()
                beltMessage = if (beltReady) "안전벨트 경고 연결됨" else "이 환경은 안전벨트 경고를 제공하지 않아요"
                areas = if (beltReady) supportedAreas else emptyList()
                if (!beltReady) checkTelltales(token)
                areas.forEach { area -> if (!listeners.containsKey(area)) {
                    val listener = object : SeatBeltNotBuckledWarningListener {
                        override fun onSeatBeltNotBuckledWarningUpdated(value: Boolean?) {
                            if (token == generation) {
                                if (value == null) belts.remove(seatName(area))
                                else belts[seatName(area)] = value to SystemClock.elapsedRealtime()
                            }
                        }
                        override fun onFailed(exception: Exception) { if (token == generation) belts.remove(seatName(area)) }
                    }
                    listeners[area] = listener
                    sdk.getSafety().registerSeatBeltNotBuckledWarning(area, listener)
                } }
            }
        }, { error -> if (token == generation) {
            Log.d("CoachVehicle", "Seat-specific warning unavailable: ${error.message}")
            checking = false; checkTelltales(token)
        } })
    }
    private fun checkTelltales(token: Int) {
        val sdk = vehicle ?: return
        checking = true
        // Vehicle 2.3.2 runtime requires PRIVATE_CAR_SAFETY even for this documented getter.
        // Both permissions are declared; a denied/unsupported read remains unavailable.
        sdk.getSafety().getTelltalesInfo({ value -> if (token == generation) {
            checking = false; usingTelltales = true; beltReady = true
            beltMessage = "안전벨트 경고등 조회 지원 · 좌석 정보 없음"
            updateTelltales(value)
            if (telltaleListener == null) {
                val listener = object : TelltaleInfoListener {
                    override fun onTelltaleInfoUpdated(value: Telltales?) { if (token == generation) updateTelltales(value) }
                    override fun onFailed(exception: Exception) { if (token == generation) belts.remove(UNKNOWN_SEAT) }
                }
                telltaleListener = listener
                sdk.getSafety().registerTelltales(listener)
            }
        } }, { error -> if (token == generation) {
            Log.d("CoachVehicle", "Public telltale read unavailable: ${error.message}")
            checking = false; beltReady = false; beltMessage = "안전벨트 경고 지원 확인 대기"
        } })
    }
    private fun updateTelltales(value: Telltales?) {
        if (io.github.univjh.pleosapp.BuildConfig.DEBUG) Log.d("CoachTelltale", value.toString())
        val belt = value?.telltales?.firstOrNull { it.type == TelltaleType.SEAT_BELT }
        if (belt == null) belts.remove(UNKNOWN_SEAT)
        else belts[UNKNOWN_SEAT] = (belt.state in setOf(TelltaleState.ON, TelltaleState.BLINK)) to SystemClock.elapsedRealtime()
    }
    private fun seatName(area: SeatArea) = area.values.joinToString(" · ") {
        when (it.name) {
            "ROW_1_LEFT" -> "앞좌석 왼쪽"; "ROW_1_RIGHT" -> "앞좌석 오른쪽"; "ROW_1_CENTER" -> "앞좌석 가운데"
            "ROW_2_LEFT" -> "뒷좌석 왼쪽"; "ROW_2_RIGHT" -> "뒷좌석 오른쪽"; "ROW_2_CENTER" -> "뒷좌석 가운데"
            else -> "차량 제공 좌석 ${it.name}"
        }
    }.ifEmpty { "좌석 정보 없음" }
    private fun disconnect() {
        generation++
        listeners.forEach { (area, listener) -> runCatching { vehicle?.getSafety()?.unregisterSeatBeltNotBuckledWarning(area, listener) } }
        listeners.clear(); belts.clear(); areas = emptyList(); beltReady = false; checking = false
        telltaleListener?.let { runCatching { vehicle?.getSafety()?.unregisterTelltales(it) } }; telltaleListener = null
        usingTelltales = false; nextCapabilityCheck = 0
        runCatching { vehicle?.release() }; vehicle = null
        runCatching { manager?.unregisterCallback(propertyListener) }; manager = null
        runCatching { uxManager?.unregisterListener() }; uxManager = null
        runCatching { car?.disconnect() }; car = null
        speed = null; gear = null; uxAllowed = false; parkGate.reset()
    }
    @Synchronized fun stop() { job?.cancel(); status.value = SourceStatus(message = "차량 확인 대기 중") }
    private companion object { const val UNKNOWN_SEAT = "차량 경고 · 좌석 정보 없음" }
}
class ReplaySignalSource(private val recording: List<VehicleFrame>) : VehicleSignalSource {
    override val frames = flow { recording.forEach { emit(it.copy(simulated = true)) } }
}

/** Optional P3 adapter. P1 never starts navigation just to detect a trip ending. */
class NaviRoadContextSource(context: Context) : RoadContextSource {
    private val sdk = NaviHelper(context)
    private val stream = MutableSharedFlow<String>(extraBufferCapacity = 1)
    override val arrivals = stream.asSharedFlow()
    private val listener = object : NaviHelperEventListener {
        override fun onDestinationArrived(destinationArrivedInfo: DestinationArrivedInfo) { stream.tryEmit(destinationArrivedInfo.name) }
    }
    fun start() { sdk.initialize({ sdk.addListener(listener) }, { }) }
    fun stop() { sdk.removeListener(listener); sdk.release() }
}

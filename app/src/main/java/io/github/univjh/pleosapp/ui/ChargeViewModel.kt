package io.github.univjh.pleosapp.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.univjh.pleosapp.ChargeApp
import io.github.univjh.pleosapp.data.ChargeDecisionEntity
import io.github.univjh.pleosapp.data.UserSettings
import io.github.univjh.pleosapp.domain.Advice
import io.github.univjh.pleosapp.domain.Advisor
import io.github.univjh.pleosapp.domain.ChargePlanner
import io.github.univjh.pleosapp.domain.OdometerDay
import io.github.univjh.pleosapp.domain.TripForecaster
import io.github.univjh.pleosapp.domain.TripMode
import io.github.univjh.pleosapp.vehicle.LimitResult
import io.github.univjh.pleosapp.vehicle.VehicleState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime

sealed interface SetState {
    data object Idle : SetState
    data object Working : SetState
    data class Done(val percent: Int, val verified: Boolean, val auto: Boolean) : SetState
    data class Error(val reason: String) : SetState
}

/** The recommendation card as it is on screen right now. */
data class CardState(
    /** Opened by the charge port; only then may the countdown set the limit by itself. */
    val fromPlugMoment: Boolean,
    val mode: TripMode = TripMode.USUAL,
    /** Limit the driver nudged to, or null to follow the recommendation. */
    val chosenPercent: Int? = null,
    val set: SetState = SetState.Idle,
)

class ChargeViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as ChargeApp
    private val dao = app.database.dao()

    val vehicle: StateFlow<VehicleState> = app.energy.state
    val settings: StateFlow<UserSettings?> = app.settings.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val lastDecision: StateFlow<ChargeDecisionEntity?> = dao.latestDecision()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _card = MutableStateFlow<CardState?>(null)
    val card: StateFlow<CardState?> = _card

    private val records = dao.odometerDays().map { days ->
        TripForecaster.dailyDistances(days.map { OdometerDay(LocalDate.parse(it.date), it.firstKm, it.lastKm) })
    }
    private val mode = _card.map { it?.mode ?: TripMode.USUAL }

    /** Null until the car has reported charge and capacity and first launch is finished. */
    val advice: StateFlow<Advice?> = combine(vehicle, settings, records, app.settings.weather, mode) { v, s, r, w, m ->
        val soc = v.socPercent; val capacity = v.capacityKwh; val usual = s?.usualKm
        if (soc == null || capacity == null || usual == null) return@combine null
        val now = LocalDateTime.now()
        val (date, _) = Advisor.drivingDate(now)
        val forecast = w?.takeIf { it.forDate == date.toString() }
        Advisor.advise(now, soc, capacity, v.rangeKm, v.energyKwh, r, usual, s.reservePercent, m,
            forecast?.minTempC, forecast?.source, v.outsideTempC)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** A higher limit the driver set by hand that the countdown must leave alone. */
    val manualToKeep: StateFlow<Int?> = combine(dao.latestDecision(), vehicle, advice) { _, v, a ->
        val manual = dao.latestManual()
        if (a == null) null
        else Advisor.manualToKeep(manual?.targetPercent, manual?.odometerKm, manual?.timeMs, System.currentTimeMillis(), v.odometerKm, a.plan.targetPercent)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        viewModelScope.launch { app.plugMoments.collect { openCard(fromPlugMoment = true) } }
    }

    fun openCard(fromPlugMoment: Boolean) {
        app.refreshWeather()
        _card.value = CardState(fromPlugMoment)
    }
    fun closeCard() { _card.value = null }
    fun chooseMode(mode: TripMode) { _card.update { it?.copy(mode = mode, chosenPercent = null, set = SetState.Idle) } }

    /** Moves the limit by one 5% step from wherever it stands. */
    fun nudge(steps: Int) {
        val base = _card.value?.chosenPercent ?: advice.value?.plan?.targetPercent ?: return
        _card.update { it?.copy(chosenPercent = ChargePlanner.snap(base + steps * 5), set = SetState.Idle) }
    }

    /** Sends the limit to the car. [auto] marks the countdown; anything else is the driver's own choice. */
    fun confirm(auto: Boolean) {
        val card = _card.value ?: return
        val a = advice.value ?: return
        if (card.set is SetState.Working || card.set is SetState.Done) return
        if (auto && manualToKeep.value != null) return
        val percent = card.chosenPercent ?: a.plan.targetPercent
        _card.update { it?.copy(set = SetState.Working) }
        viewModelScope.launch {
            when (val result = app.energy.setChargeLimit(percent)) {
                is LimitResult.Failed -> _card.update { it?.copy(set = SetState.Error(result.reason)) }
                else -> {
                    val verified = result is LimitResult.Verified
                    dao.insert(ChargeDecisionEntity(timeMs = System.currentTimeMillis(), targetPercent = percent,
                        socPercent = a.socPercent, expectedKm = a.forecast.km, minTempC = a.temp.planTempC,
                        manual = !auto, odometerKm = vehicle.value.odometerKm, verified = verified))
                    _card.update { it?.copy(set = SetState.Done(percent, verified, auto)) }
                    app.speaker.say("충전 한도를 ${percent}퍼센트로 맞췄어요")
                }
            }
        }
    }

    fun finishFirstLaunch(usualKm: Int) = viewModelScope.launch { app.settings.setUsualKm(usualKm) }
    fun setUsualKm(km: Int) = viewModelScope.launch { app.settings.setUsualKm(km) }
    fun setReserve(percent: Int) = viewModelScope.launch { app.settings.setReserve(percent) }
    fun setAutoApply(on: Boolean) = viewModelScope.launch { app.settings.setAutoApply(on) }
}

package io.github.univjh.pleosapp.ui

import android.app.Application
import android.content.Intent
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.univjh.pleosapp.CoachApplication
import io.github.univjh.pleosapp.data.*
import io.github.univjh.pleosapp.domain.canReview
import io.github.univjh.pleosapp.service.RecordingService
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class CoachViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as CoachApplication
    val source = app.source.status.asStateFlow()
    val recording = app.recorderStatus.asStateFlow()
    val running = app.serviceRunning.asStateFlow()
    val consent = app.settings.consent.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val goal = app.settings.goal.stateIn(viewModelScope, SharingStarted.Eagerly, 1)
    val trips = app.repository.dao.trips().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val events = app.repository.dao.events().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val feedback = app.repository.dao.feedback().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val message = MutableStateFlow<String?>(null)
    init {
        viewModelScope.launch {
            // A crash during consent withdrawal can leave a checkpoint without a service.
            // Do not recover or delete a trip owned by a running recorder.
            if (app.recordingMutex.tryLock()) {
                try {
                    if (!app.serviceRunning.value) app.repository.recover()
                    app.repository.maintenance(System.currentTimeMillis())
                } finally { app.recordingMutex.unlock() }
            }
        }
    }
    private fun allowed(): Boolean {
        val s = source.value
        return s.parkedConfirmed && canReview(s.frame, s.uxAllowed) && s.frame?.let { SystemClock.elapsedRealtime() - it.monotonicMs in 0..500 } == true
    }
    fun setConsent(value: Boolean) {
        if (!allowed()) return
        viewModelScope.launch {
            app.settings.setConsent(value)
            if (value) startRecording() else app.stopService(Intent(app, RecordingService::class.java))
        }
    }
    fun startRecording() {
        runCatching { app.startForegroundService(Intent(app, RecordingService::class.java)) }
            .onFailure { message.value = "자동 기록을 시작하지 못했어요. 차량 권한과 알림을 확인해 주세요." }
    }
    fun setGoal(id: Int) { if (allowed()) viewModelScope.launch { app.settings.setGoal(id) } }
    fun saveFeedback(id: String, reason: String, include: Boolean) {
        if (allowed()) viewModelScope.launch { app.repository.dao.saveFeedback(EventFeedback(id, reason, include)) }
    }
    suspend fun evidence(id: String) = app.repository.dao.evidence(id)
    fun deleteTrip(id: String) { if (allowed()) viewModelScope.launch { app.repository.dao.deleteTrip(id) } }
    fun deleteAll() {
        if (!allowed()) return
        viewModelScope.launch {
            if (running.value || trips.value.any { it.status == "RECORDING" }) {
                message.value = "자동 기록을 끄고 주행 저장이 끝나면 전체 삭제할 수 있어요."
            } else app.repository.dao.deleteFinishedTrips()
        }
    }
}

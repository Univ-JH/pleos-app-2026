package io.github.univjh.pleosapp

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.DoorArea
import ai.pleos.playground.vehicle.api.model.capability.AccessType
import ai.pleos.playground.vehicle.constant.area.DoorValue
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.univjh.pleosapp.ui.theme.PleosAppTheme

/** Debug-only, read-only check of the real Pleos Vehicle Service. */
class VehicleSmokeActivity : ComponentActivity() {
    private val handler = Handler(Looper.getMainLooper())
    private val area = DoorArea(DoorValue.ROW_1_LEFT)
    private var vehicle: Vehicle? = null
    private var requestId = 0
    private var timeout: Runnable? = null
    private var busy by mutableStateOf(false)
    private var status by mutableStateOf("확인 준비 중")
    private var capability by mutableStateOf("지원 범위: 아직 확인하지 않음")
    private var value by mutableStateOf("현재 값: 아직 수신하지 않음")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PleosAppTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                            .verticalScroll(rememberScrollState())
                            .padding(32.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Text("Vehicle SDK 확인", style = MaterialTheme.typography.headlineLarge)
                        Text("앞줄 왼쪽 문 · ROW_1_LEFT", style = MaterialTheme.typography.titleLarge)
                        Text(status)
                        Text(capability)
                        Text(value, style = MaterialTheme.typography.headlineMedium)
                        Button(onClick = ::readDoor, enabled = !busy) {
                            Text("다시 읽기")
                        }
                        Text("개발용 조회 화면 · 차량 제어 권한 없음")
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        readDoor()
    }

    private fun readDoor() {
        if (busy) return
        val id = ++requestId
        busy = true
        status = "차량 서비스 응답 확인 중"
        capability = "지원 범위: 확인 중"
        value = "현재 값: 확인 중"
        timeout = Runnable {
            if (id == requestId && busy) {
                finishRead("응답 시간 초과 · Pleos 에뮬레이터와 서비스를 확인하세요.")
            }
        }.also { handler.postDelayed(it, 10_000) }

        try {
            val session = vehicle ?: Vehicle(applicationContext).also {
                vehicle = it
                it.initialize()
                // initialize() returning alone does not prove a service connection.
                Log.i(TAG, "SDK initialize returned; awaiting capability and read callbacks")
            }
            val door = session.getDoor()
            door.checkDoorPositionCapabilities(
                onComplete = { config, areas ->
                    deliver(id) {
                        val bounds = areas[area]
                        capability = "접근: ${config.accessType} · 범위: ${bounds?.minValue}..${bounds?.maxValue}"
                        Log.i(TAG, "CAPABILITY area=ROW_1_LEFT access=${config.accessType} min=${bounds?.minValue} max=${bounds?.maxValue}")
                        if (config.accessType != AccessType.READ && config.accessType != AccessType.READ_WRITE) {
                            finishRead("이 환경에서는 문 상태를 읽을 수 없습니다.")
                        } else if (!areas.containsKey(area)) {
                            finishRead("이 환경에서는 앞줄 왼쪽 문을 지원하지 않습니다.")
                        } else {
                            door.getCurrentDoorPosition(
                                area = area,
                                onComplete = { position ->
                                    deliver(id) {
                                        value = when {
                                            position == null -> "현재 값: 알 수 없음 (null)"
                                            bounds?.minValue == 0 && bounds.maxValue == 1 -> when (position) {
                                                0 -> "문 닫힘 · 원시 값: 0"
                                                1 -> "문 열림 · 원시 값: 1"
                                                else -> "현재 원시 값: $position"
                                            }
                                            else -> "현재 원시 값: $position"
                                        }
                                        Log.i(TAG, "READ area=ROW_1_LEFT position=$position")
                                        finishRead(if (position == null) "서비스 응답 수신 · 현재 데이터 없음" else "차량 데이터 수신 성공")
                                    }
                                },
                                onFailed = { error -> deliver(id) { fail(error) } },
                            )
                        }
                    }
                },
                onFailed = { error -> deliver(id) { fail(error) } },
            )
        } catch (error: Exception) {
            fail(error)
            releaseVehicle()
        }
    }

    // SDK callbacks may arrive on a Binder thread or after the activity stops.
    private fun deliver(id: Int, action: () -> Unit) {
        handler.post {
            if (id == requestId && busy) {
                try {
                    action()
                } catch (error: Exception) {
                    fail(error)
                }
            }
        }
    }

    private fun fail(error: Exception) {
        Log.e(TAG, "Vehicle read failed", error)
        finishRead("조회 실패: ${error.javaClass.simpleName}: ${error.message}")
    }

    private fun finishRead(message: String) {
        timeout?.let(handler::removeCallbacks)
        timeout = null
        status = message
        busy = false
        Log.i(TAG, message)
    }

    override fun onStop() {
        requestId++
        timeout?.let(handler::removeCallbacks)
        timeout = null
        busy = false
        releaseVehicle()
        super.onStop()
    }

    private fun releaseVehicle() {
        val session = vehicle
        vehicle = null
        try {
            session?.release()
            if (session != null) Log.i(TAG, "Vehicle session released")
        } catch (error: Exception) {
            Log.w(TAG, "Vehicle release failed", error)
        }
    }

    private companion object {
        const val TAG = "PleosVehicleSmoke"
    }
}

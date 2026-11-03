package io.github.univjh.pleosapp.service

import android.app.*
import android.content.*
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import io.github.univjh.pleosapp.*
import io.github.univjh.pleosapp.data.RecordingEngine
import io.github.univjh.pleosapp.domain.RecorderState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.withLock
import android.util.Log

class RecordingService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val app get() = application as CoachApplication
    private var runner: Job? = null
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("recording", "자동 주행 기록", NotificationManager.IMPORTANCE_LOW))
        nm.createNotificationChannel(NotificationChannel("reports", "주차 후 리포트", NotificationManager.IMPORTANCE_LOW))
        ServiceCompat.startForeground(this, 1, notification("주행을 자동으로 기록합니다", "recording"),
            if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0)
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (runner?.isActive != true) {
            runner = scope.launch {
                app.recordingMutex.withLock {
                    if (!app.settings.consent.first()) { stopSelf(); return@withLock }
                    val engine = RecordingEngine(app.repository)
                    var failed = false
                    var pendingReport = false
                    try {
                        app.repository.recover()
                        app.repository.maintenance(System.currentTimeMillis())
                        app.serviceRunning.value = true
                        app.settings.consent.collectLatest { enabled ->
                            if (!enabled) { stopSelf(); return@collectLatest }
                            app.source.start()
                            app.source.frames.collect { frame ->
                                val completed = engine.accept(frame)
                                app.recorderStatus.value = if (!frame.valid) "신호 중단 · 연결 복구 중" else when (engine.recorder.state) {
                                    RecorderState.WAITING -> "자동 기록 대기 중"
                                    RecorderState.RECORDING -> "주행 기록 중"
                                    RecorderState.PARK_CONFIRMING -> "주차 확인 중"
                                }
                                if (completed != null) {
                                    app.repository.maintenance(frame.wallTimeMs)
                                    pendingReport = true
                                }
                                val source = app.source.status.value
                                if (pendingReport && source.uxAllowed && source.parkedConfirmed && frame.valid) {
                                    runCatching {
                                        getSystemService(NotificationManager::class.java).notify(2,
                                            notification("주행 리포트가 준비됐어요", "reports"))
                                    }
                                    pendingReport = false
                                }
                            }
                        }
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        failed = true
                        Log.e("CoachRecorder", "Recording stopped", e)
                        stopSelf()
                    } finally {
                        withContext(NonCancellable) {
                            runCatching { engine.finishInterrupted() }
                                .onFailure { failed = true; Log.e("CoachRecorder", "Final checkpoint failed", it) }
                        }
                        app.serviceRunning.value = false
                        app.recorderStatus.value = if (failed) "저장 오류 · 기록 중단" else "자동 기록 꺼짐"
                    }
                }
            }
        }
        return START_STICKY
    }
    private fun notification(text: String, channel: String): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, channel).setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("운전 돌아보기").setContentText(text).setContentIntent(open)
            .setOngoing(channel == "recording").setSilent(true).setAutoCancel(channel != "recording").build()
    }
    override fun onDestroy() {
        runner?.cancel()
        if (!app.visible) app.source.stop()
        // The runner performs its final database write in NonCancellable before finishing.
        runner?.invokeOnCompletion { scope.cancel() } ?: scope.cancel()
        super.onDestroy()
    }
}

class RecordingBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED)) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val app = context.applicationContext as CoachApplication
                if (app.settings.consent.first()) {
                    runCatching { context.startForegroundService(Intent(context, RecordingService::class.java)) }
                        .onFailure { app.recorderStatus.value = "앱을 열어 자동 기록 상태를 확인해 주세요" }
                }
            } finally { pending.finish() }
        }
    }
}

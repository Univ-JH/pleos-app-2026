package io.github.univjh.pleosapp

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import io.github.univjh.pleosapp.ui.CoachScreen
import io.github.univjh.pleosapp.ui.theme.PleosAppTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val app get() = application as CoachApplication
    private val permissionRequest = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { connect() }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { PleosAppTheme { CoachScreen(onPermissions = ::requestPermissions) } }
        requestPermissions()
    }
    private fun requestPermissions() {
        val permissions = mutableListOf("android.car.permission.CAR_SPEED", "android.car.permission.CAR_POWERTRAIN", "pleos.car.permission.CAR_SAFETY")
        if (Build.VERSION.SDK_INT >= 33) permissions += Manifest.permission.POST_NOTIFICATIONS
        val missing = permissions.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isEmpty()) connect() else permissionRequest.launch(missing.toTypedArray())
    }
    private fun connect() {
        app.source.start()
        lifecycleScope.launch {
            if (app.settings.consent.first()) runCatching {
                startForegroundService(android.content.Intent(this@MainActivity, io.github.univjh.pleosapp.service.RecordingService::class.java))
            }.onFailure { app.recorderStatus.value = "자동 기록을 시작하지 못했어요. 권한을 확인해 주세요." }
        }
    }
    override fun onStart() { super.onStart(); app.visible = true; app.source.start() }
    override fun onStop() {
        app.visible = false
        lifecycleScope.launch { if (!app.settings.consent.first() && !app.serviceRunning.value) app.source.stop() }
        super.onStop()
    }
}

package io.github.univjh.pleosapp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import io.github.univjh.pleosapp.ui.AppRoot
import io.github.univjh.pleosapp.ui.ChargeViewModel
import io.github.univjh.pleosapp.ui.theme.PleosAppTheme

class MainActivity : ComponentActivity() {
    private val app get() = application as ChargeApp
    private val vm: ChargeViewModel by viewModels()
    private val permissionRequest = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        app.energy.refreshPermissions()
        app.refreshWeather()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { PleosAppTheme { AppRoot(vm) } }
        requestPermissions()
        showCardIfAsked(intent)
    }

    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); showCardIfAsked(intent) }

    /** The notification posted while the app was hidden leads straight to the card. */
    private fun showCardIfAsked(intent: Intent) {
        if (intent.getBooleanExtra(ChargeApp.EXTRA_SHOW_CARD, false)) {
            intent.removeExtra(ChargeApp.EXTRA_SHOW_CARD)
            vm.openCard(fromPlugMoment = true)
        }
    }

    private fun requestPermissions() {
        // CAR_ENERGY lets the app read the charge limit back; location is only for the weather forecast.
        val wanted = mutableListOf("android.car.permission.CAR_ENERGY", Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= 33) wanted += Manifest.permission.POST_NOTIFICATIONS
        val missing = wanted.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isEmpty()) app.refreshWeather() else permissionRequest.launch(missing.toTypedArray())
    }

    override fun onStart() { super.onStart(); app.visible = true; app.energy.start() }
    override fun onStop() { app.visible = false; super.onStop() }
}

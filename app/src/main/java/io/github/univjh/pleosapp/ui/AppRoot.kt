package io.github.univjh.pleosapp.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.univjh.pleosapp.ui.home.HomeScreen
import io.github.univjh.pleosapp.ui.plugin.PlugInCard
import io.github.univjh.pleosapp.vehicle.VehicleState

@Composable
fun AppRoot(vm: ChargeViewModel = viewModel()) {
    val vehicle by vm.vehicle.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val advice by vm.advice.collectAsStateWithLifecycle()
    val card by vm.card.collectAsStateWithLifecycle()
    val manualToKeep by vm.manualToKeep.collectAsStateWithLifecycle()
    val lastDecision by vm.lastDecision.collectAsStateWithLifecycle()
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        val s = settings ?: return@Surface
        val shown = card
        val a = advice
        // The car's centre display is far wider in dp than a phone; scale everything up so text
        // and touch targets are readable from the driver's seat instead of floating in empty space.
        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
            val base = LocalDensity.current
            val scale = (maxWidth / 1100.dp).coerceIn(1f, 1.6f)
            CompositionLocalProvider(LocalDensity provides Density(base.density * scale, base.fontScale)) {
            when {
                // While the car is moving the driver gets a status line and nothing to operate.
                vehicle.driving -> Driving(vehicle)
                s.usualKm == null -> FirstLaunch(vm::finishFirstLaunch)
                shown != null && a != null -> PlugInCard(shown, a, vehicle, s, manualToKeep, vm::chooseMode, vm::nudge, vm::confirm, vm::closeCard)
                else -> HomeScreen(vehicle, a, s, lastDecision, { vm.openCard(fromPlugMoment = false) }, vm::setUsualKm, vm::setReserve, vm::setAutoApply)
            }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FirstLaunch(onDone: (Int) -> Unit) {
    var choice by rememberSaveable { mutableStateOf(50) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("하루에 보통 얼마나 타세요?", style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Text("주행 기록이 쌓이기 전까지 이 거리로 충전량을 추천해요. 나중에 설정에서 바꿀 수 있어요.",
            style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 720.dp))
        Spacer(Modifier.height(28.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf(30 to "30 km 이하", 50 to "50 km쯤", 80 to "80 km쯤", 120 to "120 km 이상").forEach { (km, label) ->
                FilterChip(choice == km, { choice = km }, { Text(label, style = MaterialTheme.typography.titleSmall) },
                    modifier = Modifier.heightIn(min = 68.dp))
            }
        }
        Spacer(Modifier.height(32.dp))
        Button({ onDone(choice) }, Modifier.widthIn(min = 320.dp).heightIn(min = 76.dp)) {
            Text("시작하기", style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Composable
private fun Driving(vehicle: VehicleState) {
    Column(Modifier.fillMaxSize().padding(40.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(Icons.Outlined.DirectionsCar, null, Modifier.size(72.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Text(vehicle.socPercent?.let { "배터리 $it%" } ?: "주행 중", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(8.dp))
        Text("주행 중에는 조작할 수 없어요. 주차하고 충전구를 열면 추천이 떠요.",
            style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

package io.github.univjh.pleosapp.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.EvStation
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.univjh.pleosapp.data.ChargeDecisionEntity
import io.github.univjh.pleosapp.data.UserSettings
import io.github.univjh.pleosapp.domain.Advice
import io.github.univjh.pleosapp.ui.Wording
import io.github.univjh.pleosapp.vehicle.VehicleState
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** What the driver sees when opening the app by hand: the car's charge state and their own settings. */
@Composable
fun HomeScreen(
    vehicle: VehicleState, advice: Advice?, settings: UserSettings, lastDecision: ChargeDecisionEntity?,
    onOpenCard: () -> Unit, onUsualKm: (Int) -> Unit, onReserve: (Int) -> Unit, onAutoApply: (Boolean) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 900.dp
        val status: @Composable () -> Unit = { Status(vehicle, advice, lastDecision, onOpenCard) }
        val prefs: @Composable () -> Unit = { Preferences(settings, onUsualKm, onReserve, onAutoApply) }
        Column(Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 24.dp)) {
            Text("충전 플래너", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground)
            Spacer(Modifier.height(16.dp))
            if (wide) Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Column(Modifier.weight(1.1f).verticalScroll(rememberScrollState())) { status() }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) { prefs() }
            } else Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                status(); prefs()
            }
        }
    }
}

@Composable
private fun Status(vehicle: VehicleState, advice: Advice?, lastDecision: ChargeDecisionEntity?, onOpenCard: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(color = scheme.primaryContainer, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(28.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.BatteryChargingFull, null, Modifier.size(40.dp), tint = scheme.primary)
                Spacer(Modifier.width(12.dp))
                Text(if (vehicle.connected) "지금 배터리" else "차량에 연결하는 중", style = MaterialTheme.typography.titleSmall, color = scheme.onPrimaryContainer)
            }
            Text(vehicle.socPercent?.let { "$it%" } ?: "—", fontSize = 72.sp, lineHeight = 80.sp, fontWeight = FontWeight.Bold, color = scheme.onPrimaryContainer)
            val facts = listOfNotNull(
                vehicle.limitPercent?.let { "차의 충전 한도 $it%" },
                vehicle.rangeKm?.takeIf { it > 0 }?.let { "남은 거리 ${it.toInt()} km" },
                if (vehicle.portOpen) "충전구 열림" else "충전구 닫힘",
            )
            Text(facts.joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = scheme.onPrimaryContainer)
        }
    }
    Spacer(Modifier.height(16.dp))
    Surface(color = scheme.surface, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(28.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (advice == null) {
                Text("차량 값을 기다리고 있어요", style = MaterialTheme.typography.titleMedium, color = scheme.onSurface)
                Text("배터리 잔량과 용량을 읽으면 추천을 계산해요", style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
            } else {
                Text(Wording.headline(advice), style = MaterialTheme.typography.titleSmall, color = scheme.onSurfaceVariant)
                Text("${advice.plan.targetPercent}%까지", style = MaterialTheme.typography.headlineLarge, color = scheme.onSurface)
                Text(Wording.distance(advice), style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                Text(Wording.weather(advice), style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                Button(onOpenCard, Modifier.fillMaxWidth().heightIn(min = 72.dp)) {
                    Icon(Icons.Outlined.EvStation, null); Spacer(Modifier.width(10.dp))
                    Text("충전 추천 보기", style = MaterialTheme.typography.titleMedium)
                }
                Text("주차하고 충전구를 열면 이 추천이 저절로 떠요", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            }
            lastDecision?.let {
                val time = DateTimeFormatter.ofPattern("M월 d일 HH:mm").format(Instant.ofEpochMilli(it.timeMs).atZone(ZoneId.systemDefault()))
                Text("마지막 설정: $time · ${it.targetPercent}% (${if (it.manual) "직접" else "자동"})",
                    style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Preferences(settings: UserSettings, onUsualKm: (Int) -> Unit, onReserve: (Int) -> Unit, onAutoApply: (Boolean) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(color = scheme.surface, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(28.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("내 설정", style = MaterialTheme.typography.titleMedium, color = scheme.onSurface)
            Spacer(Modifier.height(4.dp))
            Text("하루 평소 주행", style = MaterialTheme.typography.titleSmall, color = scheme.onSurface)
            Text("주행 기록이 쌓이기 전까지 이 거리로 추천해요", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            Choices(listOf(30, 50, 80, 120), settings.usualKm, { "$it km" }, onUsualKm)
            Spacer(Modifier.height(10.dp))
            Text("도착 후 남길 여유", style = MaterialTheme.typography.titleSmall, color = scheme.onSurface)
            Text("예상보다 더 달려도 괜찮게 남겨 두는 양이에요", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            Choices(listOf(10, 20, 30), settings.reservePercent, { "$it%" }, onReserve)
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("충전구를 열면 자동 설정", style = MaterialTheme.typography.titleSmall, color = scheme.onSurface)
                    Text("추천 화면을 ${settings.autoDelaySec}초 동안 그대로 두면 추천값으로 맞춰요. 화면이 가려져 있으면 하지 않아요",
                        style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                }
                Spacer(Modifier.width(16.dp))
                Switch(settings.autoApply, onAutoApply)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Choices(options: List<Int>, selected: Int?, label: (Int) -> String, onSelect: (Int) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            FilterChip(selected == option, { onSelect(option) }, { Text(label(option), style = MaterialTheme.typography.labelLarge) },
                modifier = Modifier.heightIn(min = 60.dp))
        }
    }
}

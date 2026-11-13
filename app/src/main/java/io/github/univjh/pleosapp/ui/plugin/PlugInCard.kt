package io.github.univjh.pleosapp.ui.plugin

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.outlined.AcUnit
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.univjh.pleosapp.data.UserSettings
import io.github.univjh.pleosapp.domain.Advice
import io.github.univjh.pleosapp.domain.ChargePlanner
import io.github.univjh.pleosapp.domain.TempBasis
import io.github.univjh.pleosapp.domain.TripMode
import io.github.univjh.pleosapp.ui.CardState
import io.github.univjh.pleosapp.ui.SetState
import io.github.univjh.pleosapp.ui.Wording
import io.github.univjh.pleosapp.ui.components.BatteryBar
import io.github.univjh.pleosapp.vehicle.VehicleState
import kotlinx.coroutines.delay

/**
 * The moment before plugging in: one number, why, and one button.
 * When the charge port opened it by itself, the limit is set after a countdown, but only while
 * this card is really on screen and untouched.
 */
@Composable
fun PlugInCard(
    card: CardState, advice: Advice, vehicle: VehicleState, settings: UserSettings, manualToKeep: Int?,
    onMode: (TripMode) -> Unit, onNudge: (Int) -> Unit, onConfirm: (auto: Boolean) -> Unit, onClose: () -> Unit,
) {
    val target = card.chosenPercent ?: advice.plan.targetPercent
    var touched by remember { mutableStateOf(false) }
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    val onScreen = lifecycle.isAtLeast(Lifecycle.State.RESUMED) && LocalWindowInfo.current.isWindowFocused
    val mayCountDown = card.fromPlugMoment && settings.autoApply && card.set is SetState.Idle &&
        manualToKeep == null && card.chosenPercent == null && !touched && onScreen
    var remaining by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(mayCountDown, target) {
        remaining = null
        if (!mayCountDown) return@LaunchedEffect
        for (s in settings.autoDelaySec downTo 1) { remaining = s; delay(1_000) }
        remaining = null
        onConfirm(true)
    }

    BoxWithConstraints(
        Modifier.fillMaxSize().pointerInput(Unit) {
            // Any touch means the driver is deciding; the countdown must not act for them.
            awaitPointerEventScope {
                while (true) { if (awaitPointerEvent(PointerEventPass.Initial).type == PointerEventType.Press) touched = true }
            }
        },
    ) {
        val wide = maxWidth >= 900.dp
        val reasons: @Composable () -> Unit = { Recommendation(card, advice, vehicle, target) }
        val actions: @Composable () -> Unit = {
            Actions(card, advice, target, remaining, touched, manualToKeep, settings, onMode, onNudge, onConfirm, onClose)
        }
        Column(Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 24.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(Wording.headline(advice), style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                OutlinedButton(onClose, Modifier.heightIn(min = 60.dp)) {
                    Icon(Icons.Outlined.Close, null); Spacer(Modifier.width(8.dp)); Text("닫기")
                }
            }
            Spacer(Modifier.height(8.dp))
            if (wide) Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(40.dp)) {
                Column(Modifier.weight(1.15f).verticalScroll(rememberScrollState())) { reasons() }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) { actions() }
            } else Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                reasons(); actions()
            }
        }
    }
}

@Composable
private fun Recommendation(card: CardState, advice: Advice, vehicle: VehicleState, target: Int) {
    val scheme = MaterialTheme.colorScheme
    Text("${target}%까지", fontSize = 76.sp, lineHeight = 84.sp, fontWeight = FontWeight.Bold, color = scheme.onSurface)
    val limit = vehicle.limitPercent?.let { " · 차의 현재 한도 $it%" } ?: ""
    Text("지금 ${advice.socPercent}%$limit", style = MaterialTheme.typography.bodyLarge, color = scheme.onSurfaceVariant)
    if (advice.socPercent >= target) Text("이미 충분해서 더 충전하지 않아요", style = MaterialTheme.typography.bodyMedium, color = scheme.primary)
    if (card.chosenPercent != null && card.chosenPercent != advice.plan.targetPercent) {
        Text("직접 고른 값이에요. 추천은 ${advice.plan.targetPercent}%", style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
    }
    Spacer(Modifier.height(20.dp))
    BatteryBar(
        socPercent = advice.socPercent, targetPercent = target,
        neededPercent = advice.plan.neededWithReserve, capPercent = ChargePlanner.EVERYDAY_CAP,
        neededLabel = "${Wording.dayLabel(advice)} 필요 ${advice.plan.neededWithReserve}% (여유 ${advice.plan.reservePercent}% 포함)",
        capLabel = "평소 상한 ${ChargePlanner.EVERYDAY_CAP}%", modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(20.dp))
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Reason(Icons.Outlined.Route, Wording.distance(advice))
        val weatherIcon = when {
            advice.temp.basis == TempBasis.SEASON -> Icons.Outlined.Thermostat
            (advice.temp.planTempC ?: 20.0) < 5 -> Icons.Outlined.AcUnit
            (advice.temp.planTempC ?: 20.0) >= 25 -> Icons.Outlined.WbSunny
            else -> Icons.Outlined.Thermostat
        }
        Reason(weatherIcon, Wording.weather(advice))
        Reason(Icons.Outlined.BatteryChargingFull, Wording.battery(advice), warn = advice.plan.exceedsFullCharge)
    }
    Spacer(Modifier.height(12.dp))
    Text(Wording.efficiency(advice), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
}

@Composable
private fun Reason(icon: ImageVector, text: String, warn: Boolean = false) {
    val color = if (warn) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Icon(icon, null, Modifier.size(30.dp).padding(top = 3.dp), tint = if (warn) color else MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = color)
    }
}

@Composable
private fun Actions(
    card: CardState, advice: Advice, target: Int, remaining: Int?, touched: Boolean, manualToKeep: Int?, settings: UserSettings,
    onMode: (TripMode) -> Unit, onNudge: (Int) -> Unit, onConfirm: (Boolean) -> Unit, onClose: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val done = card.set as? SetState.Done
    if (done != null) {
        Surface(color = scheme.primaryContainer, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(28.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Outlined.CheckCircle, null, Modifier.size(56.dp), tint = scheme.primary)
                Text("충전 한도 ${done.percent}% 설정됨", style = MaterialTheme.typography.headlineSmall, color = scheme.onPrimaryContainer)
                Text(if (done.verified) "차에서 ${done.percent}%로 바뀐 것을 확인했어요" else "차에 ${done.percent}%로 요청했어요. 이 차는 설정값을 다시 읽을 수 없어요",
                    style = MaterialTheme.typography.bodyMedium, color = scheme.onPrimaryContainer)
                if (done.auto) Text("기다리는 동안 자동으로 설정했어요", style = MaterialTheme.typography.bodySmall, color = scheme.onPrimaryContainer)
            }
        }
        Spacer(Modifier.height(16.dp))
        Button(onClose, Modifier.fillMaxWidth().heightIn(min = 72.dp)) { Text("확인", style = MaterialTheme.typography.titleMedium) }
        return
    }

    Text("${Wording.dayLabel(advice).substringBefore("(")} 일정이 다른가요?", style = MaterialTheme.typography.titleSmall, color = scheme.onSurfaceVariant)
    Spacer(Modifier.height(10.dp))
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ModeRow("평소대로", "기록과 알려주신 거리로 계산", card.mode == TripMode.USUAL) { onMode(TripMode.USUAL) }
        ModeRow("장거리 있어요", "출발 전에 100%까지", card.mode == TripMode.LONG_TRIP) { onMode(TripMode.LONG_TRIP) }
    }
    Spacer(Modifier.height(20.dp))
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("직접 조절", style = MaterialTheme.typography.titleSmall, color = scheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        FilledTonalIconButton({ onNudge(-1) }, Modifier.size(64.dp), enabled = target > ChargePlanner.EVERYDAY_FLOOR) {
            Icon(Icons.Outlined.Remove, "5% 낮추기", Modifier.size(32.dp))
        }
        Text("$target%", style = MaterialTheme.typography.titleLarge, modifier = Modifier.width(92.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        FilledTonalIconButton({ onNudge(1) }, Modifier.size(64.dp), enabled = target < 100) {
            Icon(Icons.Outlined.Add, "5% 높이기", Modifier.size(32.dp))
        }
    }
    if (target < advice.plan.neededWithReserve) {
        Spacer(Modifier.height(8.dp))
        Notice("${Wording.dayLabel(advice)} 필요한 ${advice.plan.neededWithReserve}%보다 낮아요", error = true)
    }
    Spacer(Modifier.height(20.dp))
    val working = card.set is SetState.Working
    Button({ onConfirm(false) }, Modifier.fillMaxWidth().heightIn(min = 76.dp), enabled = !working) {
        if (working) { CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp); Spacer(Modifier.width(14.dp)); Text("차에 설정하는 중", style = MaterialTheme.typography.titleMedium) }
        else Text("${target}%로 설정", style = MaterialTheme.typography.titleLarge)
    }
    Spacer(Modifier.height(10.dp))
    val error = card.set as? SetState.Error
    when {
        error != null -> Notice("${error.reason}. 다시 눌러 주세요", error = true)
        manualToKeep != null -> Notice("직접 정하신 $manualToKeep%를 그대로 둡니다. 바꾸려면 버튼을 누르세요")
        remaining != null -> Notice("누르지 않으면 ${remaining}초 뒤 ${target}%로 설정돼요")
        card.fromPlugMoment && settings.autoApply && touched -> Notice("자동 설정을 멈췄어요. 버튼을 눌러 설정하세요")
    }
}

@Composable
private fun ModeRow(title: String, detail: String, selected: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = onClick, shape = MaterialTheme.shapes.medium,
        color = if (selected) scheme.primaryContainer else scheme.surface,
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) scheme.primary else scheme.outlineVariant),
        modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp).semantics { role = Role.RadioButton; this.selected = selected },
    ) {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = if (selected) scheme.onPrimaryContainer else scheme.onSurface)
                Text(detail, style = MaterialTheme.typography.bodySmall, color = if (selected) scheme.onPrimaryContainer else scheme.onSurfaceVariant)
            }
            if (selected) Icon(Icons.Outlined.CheckCircle, null, tint = scheme.primary, modifier = Modifier.size(30.dp))
        }
    }
}

@Composable
private fun Notice(text: String, error: Boolean = false) {
    val color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (error) Icon(Icons.Outlined.ErrorOutline, null, tint = color, modifier = Modifier.size(24.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = color)
    }
}

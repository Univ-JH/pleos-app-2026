package io.github.univjh.pleosapp.ui

import io.github.univjh.pleosapp.domain.Advice
import io.github.univjh.pleosapp.domain.EfficiencySource
import io.github.univjh.pleosapp.domain.ForecastBasis
import io.github.univjh.pleosapp.domain.PlanKind
import io.github.univjh.pleosapp.domain.TempBasis
import java.time.DayOfWeek
import kotlin.math.roundToInt

/** Sentences shown on the card. Each one names where its number comes from. */
object Wording {
    private fun weekday(d: DayOfWeek) = "월화수목금토일"[d.value - 1]

    fun dayLabel(a: Advice) = "${if (a.forToday) "오늘" else "내일"}(${weekday(a.forDate.dayOfWeek)})"

    fun headline(a: Advice) = if (a.forToday) "지금 충전 추천" else "오늘 밤 충전 추천"

    fun distance(a: Advice): String {
        val km = a.forecast.km.roundToInt()
        val basis = when (a.forecast.basis) {
            ForecastBasis.WEEKDAY_AVERAGE -> "지난 ${a.forecast.samples}주 ${weekday(a.forDate.dayOfWeek)}요일 평균"
            ForecastBasis.RECENT_AVERAGE -> "최근 ${a.forecast.samples}일 평균"
            ForecastBasis.DRIVER_ANSWER -> "알려주신 하루 평소 거리"
        }
        return "${dayLabel(a)} 예상 $km km · $basis"
    }

    fun weather(a: Advice): String {
        val lost = ((1 - a.plan.rangeRetained) * 100).roundToInt()
        val effect = if (lost >= 5) "주행거리가 약 $lost% 줄어요" else "날씨 영향은 거의 없어요"
        return when (a.temp.basis) {
            TempBasis.FORECAST -> "${dayLabel(a)} 최저 ${temp(a.temp.shownTempC)} · $effect"
            TempBasis.CAR_SENSOR -> "예보 없음 · 지금 바깥 ${temp(a.temp.shownTempC)}(차량 센서) 기준 · $effect"
            TempBasis.SEASON -> "날씨 정보 없음 · 계절 평균으로 여유 있게 잡았어요"
        }
    }

    fun battery(a: Advice): String = when {
        a.plan.exceedsFullCharge -> "100%로도 여유분이 모자라요. 가는 길에 충전이 필요해요"
        a.plan.kind == PlanKind.LONG_TRIP -> "장거리 전 완충은 괜찮아요. 완충 상태로 오래 세워두지만 마세요"
        a.plan.kind == PlanKind.NEEDS_FULL -> "${dayLabel(a)} 주행에는 80%로 부족해서 100%를 권해요"
        a.plan.kind == PlanKind.UNKNOWN_VEHICLE -> "차량 값을 다 읽지 못해 평소 상한 80%를 권해요"
        else -> "80% 아래로 유지하면 배터리가 덜 늙어요"
    }

    fun efficiency(a: Advice): String {
        val e = "%.1f km/kWh".format(a.efficiency.kmPerKwh)
        return when (a.efficiency.source) {
            EfficiencySource.CAR_SINCE_CHARGE -> "전비 $e · 차가 기록한 지난 충전 이후 전비"
            EfficiencySource.CAR_CUMULATIVE -> "전비 $e · 차가 기록한 누적 전비"
            EfficiencySource.RANGE_ENERGY -> "전비 $e · 차의 남은 주행거리 ÷ 남은 배터리로 계산"
            EfficiencySource.DEFAULT -> "차가 전비와 남은 주행거리를 알려주지 않아 전비를 ${e}로 가정했어요"
        }
    }

    fun temp(c: Double?) = c?.let { "${it.roundToInt()}°C".replace("-", "−") } ?: "?"
}

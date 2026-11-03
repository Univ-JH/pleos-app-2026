package io.github.univjh.pleosapp.domain

/** Bundled, offline coaching copy. Future scenarios are education only, never detections. */
data class Lesson(val id: Int, val title: String, val advice: String, val condition: String,
    val phase: String, val source: String, val reviewedAt: String = "2026-10-29")
private const val LAW = "https://www.law.go.kr/법령/도로교통법"
private const val GUIDE = "https://www.easylaw.go.kr/CSP/CnpClsMain.laf?ccfNo=2&cciNo=1&cnpClsNo=2&csmSeq=684"
val lessons = listOf(
    Lesson(1, "부드러운 출발", "출발 전 주변을 살피고 가속 페달을 서서히 밟아 보세요. 급한 가속만으로 위법이나 위험을 확정하지 않아요.", "정지 후 전진 출발 · 초기 실험 기준", "P1", GUIDE),
    Lesson(2, "일정한 속도 유지", "멀리 보고 앞차와 여유를 두면 가속과 감속의 반복을 줄이는 데 도움이 돼요.", "반복 가감속 신호의 품질 검증 필요", "P2", GUIDE),
    Lesson(3, "미리 준비하는 감속", "앞의 흐름을 살피고 여유 있게 감속해 보세요. 긴급 회피를 위한 제동은 필요한 행동일 수 있어요.", "전진 중 감속 · 초기 실험 기준", "P1", GUIDE),
    Lesson(4, "차분한 조향", "회전 전에 속도를 줄이고 주변을 확인하세요. 긴급 회피 여부는 조향 신호만으로 알 수 없어요.", "조향각·차속 신호 검증 필요", "P2", GUIDE),
    Lesson(5, "출발 전 안전벨트", "출발 전에 탑승자의 안전벨트를 확인하세요. 차량 경고가 나타난 좌석만 표시하며, 누가 탔는지는 판단하지 않아요.", "속도와 차량 미착용 경고가 함께 확인된 경우 · 법 제50조 참고", "P1", LAW),
    Lesson(6, "쉬어 가는 운전", "피곤하거나 집중이 흐려지면 안전한 장소에서 쉬세요. 연속 운전 시간만으로 피로를 판단하지 않아요.", "연속 운전 시간 추적 필요", "P2", GUIDE),
    Lesson(7, "제한속도 확인", "도로 표지의 제한속도와 현장 상황에 맞춰 운전하세요.", "구간·방향·시간에 맞는 제한속도 필요 · 법 제17조 참고", "P4", LAW),
    Lesson(8, "보호구역 속도 조절", "보호구역에서는 표지와 보행자를 확인하고 갑자기 나타나는 사람에 대비하세요.", "보호구역 경계와 적용 시간·속도 필요 · 법 제12조 참고", "P4", LAW),
    Lesson(9, "회전 전에 감속", "회전 직전까지 급히 달리지 않고, 주변을 확인할 여유가 있도록 미리 감속하세요.", "실제 회전 궤적과 도로 맥락 필요", "P3", GUIDE),
    Lesson(10, "회전 의사 알리기", "방향지시등으로 다른 운전자에게 회전 의사를 알리세요.", "실제 회전과 점등 상태 필요 · 법 제38조 참고", "P3", LAW),
    Lesson(11, "미리 켜는 방향지시등", "주변 운전자가 준비할 수 있도록 방향지시등을 미리 켜세요.", "회전 위치·속도·점등 시각 필요 · 법 제38조 참고", "P3", LAW),
    Lesson(12, "회전 후 방향지시등", "회전을 마친 뒤 방향지시등이 꺼졌는지 확인하세요.", "회전 종료와 점등 상태 필요", "P3", GUIDE),
    Lesson(13, "회전교차로 진입 알림", "회전교차로의 표지와 통행 방법을 미리 확인하고, 방향지시등으로 의사를 알리세요.", "회전교차로 진입 구간·차로 필요 · 법 제25조의2·제38조 참고", "P3", LAW),
    Lesson(14, "회전교차로 진출 알림", "출구를 앞두고 진출 의사를 알리고 보행자와 주변 차량을 확인하세요.", "실제 출구·차로·점등 상태 필요", "P3", GUIDE),
    Lesson(15, "적색신호에서 우회전", "적색신호의 우회전은 정지 의무와 보행자 보호를 먼저 확인해야 해요. 현장 신호와 표지를 따르세요.", "신호·정지선·보행자·전용 신호 필요", "P5", LAW),
    Lesson(16, "우회전 전용 신호", "우회전 전용 신호가 있는 곳에서는 해당 신호를 확인하세요.", "전용 신호의 존재·방향·현시 필요 · 법 제5조 참고", "P5", LAW),
    Lesson(17, "보호구역 횡단보도", "어린이 보호구역의 신호기 없는 횡단보도에서는 일시정지 의무를 확인하세요.", "보호구역·횡단보도·신호기 유무 필요 · 법 제27조 참고", "P5", LAW),
    Lesson(18, "보행자에게 양보", "횡단하는 사람과 건너려는 사람을 살피고 안전하게 지나갈 수 있도록 기다리세요.", "보행자 의도와 횡단보도 위치 필요 · 법 제27조 참고", "P5", LAW),
    Lesson(19, "회전 중인 차량에 양보", "회전교차로 진입 전 서행하며 이미 회전 중인 차량의 흐름을 확인하세요.", "주변 차량과 교차로 맥락 필요 · 법 제25조의2 참고", "P5", LAW),
    Lesson(20, "여유 있는 차간거리", "앞차가 갑자기 멈춰도 대응할 수 있도록 간격을 두세요. 날씨와 노면에 따라 더 큰 여유가 필요해요.", "앞차 거리·상대속도·노면 맥락 필요 · 법 제19조 참고", "P5", LAW),
    Lesson(21, "무리 없는 차로 변경", "옆과 뒤를 확인하고 다른 차량의 진행을 방해하지 않도록 여유 있게 차로를 바꾸세요.", "차선과 주변 차량 궤적 필요 · 법 제19조 참고", "P5", LAW),
    Lesson(22, "진로 변경 금지선", "차로를 바꾸기 전 노면 표시와 안전표지를 확인하세요.", "차선 종류와 차량 위치 필요 · 법 제14조 참고", "P5", LAW),
    Lesson(23, "교차로 안의 여유 공간", "교차로를 빠져나갈 공간이 없다면 진입하지 않고 기다리세요.", "교차로 경계와 앞쪽 정체 상황 필요 · 법 제25조 참고", "P5", LAW),
    Lesson(24, "신호와 정지선", "신호가 바뀔 때 급히 진입하지 않고, 신호와 정지선을 함께 확인하세요.", "신호 현시·정지선·차량 위치 필요 · 법 제5조 참고", "P5", LAW),
)

data class TrendTrip(val id: String, val distanceMeters: Double, val validMs: Long, val missingMs: Long)
data class Trend(val ratePer100Km: Double?, val coverage: Double?, val enough: Boolean)
fun calculateTrend(trips: List<TrendTrip>, includedEventCount: Int): Trend {
    val distance = trips.sumOf { it.distanceMeters }
    val valid = trips.sumOf { it.validMs }; val missing = trips.sumOf { it.missingMs }
    return Trend(if (distance > 0) includedEventCount / distance * 100_000 else null,
        if (valid + missing > 0) valid.toDouble() / (valid + missing) else null,
        trips.size >= 3 && distance >= 10_000 && valid + missing > 0 && valid.toDouble() / (valid + missing) >= .9)
}

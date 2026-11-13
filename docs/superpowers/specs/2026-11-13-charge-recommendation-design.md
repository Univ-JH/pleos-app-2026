# 충전 목표 추천 앱 (#6) — 설계와 구현 계획

## Context

현대차그룹 차량용 앱 공모전(Pleos Connect, Android Automotive)에 #6 "환경 기반 충전량 추천"으로 나간다. 앰비언트 계열 아이디어는 에뮬레이터 시연 한계로 뺐고, 배터리 근거집(`docs/research/battery-care.html`, 브랜치 `feat/charge-recommendation`)을 근거로 삼는다. 사용자는 기능과 UI/UX 완성도를 특히 중시한다.

일정: 접수 마감 **11/15**, 1차 발표 11/23, PT 12/3~4. 마감까지 **핵심 장면이 에뮬레이터에서 실제로 동작 + 시연 영상 + APK**를 기획서와 함께 제출하고, 나머지는 PT까지 완성한다.

## 사용자가 정한 것

- 가장 빛나야 할 장면: **충전기 꽂기 전**. 목표 충전량을 추천하고 차 충전 한도를 바로 설정.
- 다음 주행량을 아는 방법: **세 가지 모두** — 지난 주행 기록 학습(기본) · 꽂을 때 한 번 탭(평소대로 / 장거리 있어요) · 내비 목적지 고르기.
- 핵심 카드 목업(60%까지 · 이유 3줄 · 일정 탭 3개 · 자동 설정): "대체로 맞음".
- 앱을 직접 열면: **배터리 습관 리포트, 절약 효과, 내 설정**.
- 홈 목업(충전 상태 띠 · 습관 3항목 · 아낀 요금/고충전 시간)과 실패 대응: "대체로 맞음".
- 실패 기준: **틀린 조언**, **조작이 귀찮음**, **그냥 폰 앱 같음**.
- 자동 설정: **카드가 화면에 보일 때만** 15초 카운트다운. 못 봤으면 알림만 남김.
- 시연: **진짜 신호만**. 앱 안에서 흉내 내는 시연 버튼이나 가짜 데이터 없음. 에뮬레이터에서 안 되는 부분은 영상에서 빼고 기획서에 설명.

## 목표 / 비목표

- 목표: 꽂는 순간 한 화면, 한 번(또는 0번) 탭으로 "오늘 밤 몇 %까지"가 끝난다. 이유가 숫자와 출처로 납득된다. 차와 연동되는 것이 시연에서 눈에 보인다.
- 비목표: 충전 예약·요금제 최적화, 충전소 찾기, 배터리 수명 연수 예측, 주행 중 화면.
- 금지: 다음 날 필요량+여유분보다 낮은 추천, 출처 없는 효과 수치, 주행 중 조작 UI, 시뮬레이션·가짜 데이터.

## 핵심 장면 — 충전 추천 카드 (마감까지)

- 트리거(진짜 신호 우선순위): ① 주차(P) 중 충전구 열림/커넥터 연결(Android Car 속성) ② 이게 에뮬레이터에서 안 만들어지면 P 전환(Virtual Console의 실제 기어 신호) ③ 운전자가 홈의 "충전 추천 보기"를 누름. 어느 경로든 실제 차량 값(SOC·용량·전비)으로 계산.
- 카드: 큰 숫자 "60%까지", "지금 N% · 완료 예상"(충전 속도를 알 때만). 0–100% 막대(현재 → 목표, 점선 "내일 필요"·"평소 상한 80%"). 이유 3줄(예상 거리와 근거 · 날씨 영향 · 배터리 이유). 오른쪽 탭: 평소대로(기본) / 장거리 있어요 / 목적지 고르기 — 탭하면 즉시 다시 계산. 주 버튼 "60%로 설정".
- 자동 설정: 카드가 실제로 화면에 보이는 동안만 15초 카운트다운, 화면을 만지면 멈춤. 카드가 가려졌거나 앱이 백그라운드면 카운트다운 없이 알림("오늘 밤 60% 추천 · 설정")만. 운전자가 앱에서 직접 정한 값(예: 내일 장거리로 100%)은 자동 설정이 절대 낮추지 않음 — 마지막 수동 값과 시각을 저장.
- 설정 결과: 차량 음성 "충전 한도를 60%로 맞췄어요" + 카드 "설정됨". 실제 반영을 읽을 수 없으면 문구를 "차에 60%로 요청했어요"로 낮춘다(과장 금지). 실패 시 이유와 다시 시도.
- 목적지 고르기: 11/15에는 스파이크에서 NaviHelper가 경로 안내를 시작하지 않고 경로 거리를 줄 수 있을 때만 노출. 안 되면 PT까지 숨김(직선거리 추정은 쓰지 않음 — 과소추정 = 틀린 조언).

## 추천 규칙 (`ChargePlanner`, 순수 함수 + 단위 테스트)

- need% = 예상거리 ÷ 전비(km/kWh) × 기온계수 ÷ 배터리용량(kWh) × 100
- 기온계수: 근거집 기온별 주행거리 곡선(−18°C 46%, −7°C 59%, 22°C 100%, 35°C 86%)을 선형 보간한 1/잔존율. 보수적.
- 여유분 R: 기본 20%(설정 가능).
- 평소: target = max(50, need%+R)을 5% 단위 올림, 상한 80%.
- need%+R > 80 이거나 "장거리 있어요": 100% (이유: 출발 전 완충은 제조사 허용).
- 날씨를 못 받으면: 기온계수 대신 계절 보수값을 쓰고 이유 줄에 "날씨 정보 없음"을 표시.
- 불변식: target ≥ need%+R (100% 상한 제외). 테스트로 고정.

## 예상 거리 (`TripForecaster`) — 가짜 기록 없이

- 처음 실행 때 한 번만 묻기: "하루 보통 얼마나 타세요?" (30 / 50 / 80+ km 칩). 유일한 온보딩 입력.
- 실제 기록이 쌓이면 대체: 같은 요일 3회 이상 → 요일 평균, 아니면 최근 14일 중 2일 이상 → 최근 평균, 아니면 온보딩 값. 이유 줄에 어떤 근거인지 그대로 표시("지난 6주 목요일 평균" / "최근 3일 평균" / "처음에 알려주신 하루 50 km").
- 기록: 주행거리계 값을 날짜별로 저장(Room). 시연 영상의 학습 장면은 Virtual Console로 실제 주행해 쌓인 기록으로만 보여준다.

## 홈 · 리포트 · 설정

- 11/15 최소 홈: 상단 충전 상태 띠(현재 SOC · 마지막으로 정한 한도 · "충전 추천 보기"). 심사위원이 APK를 열어도 진짜 차량 값으로 카드까지 갈 수 있게 한다.
- PT까지: 배터리 습관(지난 30일: 80% 넘게 머문 시간, 급속충전 비율, 20% 아래로 내려간 날; 점선=권장 상한; "근거 보기"→출처) / 이번 달 아낀 것(급속 대신 집 충전 요금 차액 — 단가 명시, 높은 충전량 머문 시간 변화 — 비교 기준 명시). 데이터가 부족하면 수치 대신 "기록이 N일 더 필요해요".
- 설정: 집·회사 위치(날씨 위치로도 사용), 도착 후 남길 여유분, 자동 설정 켜기/대기 시간, 날씨 반영 켜기, 하루 평소 거리.
- 다음 날 아침 확인 카드(PT까지): 시동 후 P에서 "어젯밤 60% · 오늘 52 km 충분".
- 레이아웃: 넓은 창 왼쪽 레일(홈/리포트/설정), 좁은 창·큰 글자 상단 메뉴. 본문 20–22sp, 주요 버튼 ≥60dp, 라이트/다크. 주행 중에는 상태만.

## 만드는 방식 (Claude 결정)

- **작업 위치(사용자 결정)**: 전용 브랜치 `feat/charge-recommendation`에서만 작업한다. 폴더는 이미 만든 `/Users/hyeon/workspace/hyundai_app/.claude/worktrees/charge-recommendation`. 원래 작업 폴더(`feat/driving-coach`, 커밋 안 된 문서 수정 5개)는 건드리지 않는다 — 브랜치 전환·커밋·스태시 없음. 모든 빌드·커밋·푸시는 전용 폴더에서.
- main 기반의 Kotlin + Compose 뼈대 사용. 패키지 `io.github.univjh.pleosapp`.
- `feat/driving-coach`에서 **복사해 재사용**(병합 아님, `git show feat/driving-coach:<path>`):
  - `app/src/main/java/io/github/univjh/pleosapp/vehicle/VehicleSignalSource.kt` — Car/CarPropertyManager + Pleos Vehicle 초기화, UX 제한·주차 판단(`ParkedGate`).
  - `ui/theme/Theme.kt` — 의미별 색상·글꼴·간격 토큰.
  - `service/RecordingService.kt` + 부트 리시버 — 백그라운드 감시 패턴.
  - `data/CoachDatabase.kt`, `CoachRepository.kt` — Room/DataStore 구성. `gradle/libs.versions.toml`의 room·ksp·datastore·lifecycle·coroutines.
  - `scripts/qa/console_driver.py` — Virtual Console 세션 유지(`/browser/session`), 기어·속도 입력.
- 차량 API: Pleos `EvBattery`(SOC, 용량, 전비, `setEvChargePercentLimit` — `pleos.car.permission.CONTROL_CAR_ENERGY`), Android Car 속성(EV_CHARGE_PORT_OPEN/CONNECTED, EV_CHARGE_PERCENT_LIMIT 읽기 가능 여부, 기어, 주행거리계). 음성: Pleos TTS SDK가 있으면, 없으면 Android `TextToSpeech`.
- 날씨: Open-Meteo 예보(키 없음), 설정의 집 위치 기준, 마지막 예보 캐시.

## 새 파일 (예정)

`vehicle/EnergySource.kt` · `domain/ChargePlanner.kt` · `domain/TripForecaster.kt` · `weather/WeatherClient.kt` · `data/ChargeDatabase.kt`(DailyOdometer, ChargeDecision) · `data/Settings.kt` · `service/ChargeWatchService.kt` · `ui/plugin/PlugInCard.kt` · `ui/home/HomeScreen.kt` · `ui/onboarding/*` · `ui/theme/Theme.kt` · 테스트 `ChargePlannerTest`, `TripForecasterTest` · 설계 문서 `docs/superpowers/specs/2026-11-13-charge-recommendation-design.md`(이 계획의 설계 부분을 커밋).

## 일정

1. **스파이크(가장 먼저, 반나절 이내)** — 에뮬레이터에서 진짜로 되는지 확인하고 결과를 `docs/qa/SPIKE.md`에 기록:
   ① `setEvChargePercentLimit` 후 `adb shell cmd car_service get-property-value`(EV_CHARGE_PERCENT_LIMIT)로 실제 반영 확인 ② 충전구 열림/연결을 `cmd car_service inject-vhal-event`로 만들 수 있는지 ③ SOC·용량·전비·주행거리계 값과, 콘솔 주행 시 주행거리계 증가 여부 ④ TTS ⑤ 에뮬레이터 인터넷(Open-Meteo) ⑥ 앱이 백그라운드일 때 카드/알림 표시 ⑦ NaviHelper 경로 거리.
   결과에 따라 트리거 경로와 "설정됨/요청했어요" 문구를 확정.
2. `ChargePlanner`·`TripForecaster` + 테스트 → 차량 연결 → 추천 카드 UI → 최소 홈·온보딩.
3. 날씨, 음성, 끝까지 연결, 라이트/다크·큰 글자·넓은/좁은 창 확인, Virtual Console로 실제 주행 기록 쌓기, 시연 영상 녹화, 서명 APK — 11/14 안.
4. 11/15 — 기획서와 함께 제출(사용자). 에뮬레이터에서 안 된 부분은 기획서에 설명.
5. 11/16~12/3 — 리포트·절약 효과·설정, 아침 확인 카드, 목적지(안 됐다면), 다듬기.

## 검증

- `./gradlew testDebugUnitTest lintDebug assembleRelease` (규칙 불변식·예상 거리 근거 선택 테스트 포함).
- 에뮬레이터 끝까지: Virtual Console 세션 유지 → P → (가능하면) 충전구 이벤트 주입 → 카드 표시 → 탭/15초 자동(카드 보일 때만) → `get-property-value`로 한도 반영 확인 → 음성 로그. 카드를 가린 상태에서는 자동 설정이 안 일어나고 알림만 뜨는지, 수동 100%를 자동이 낮추지 않는지 확인.
- 화면 근거: 라이트/다크, 글자 1.5배, 넓은/좁은 창 스크린샷을 `docs/qa/evidence/`에, 주행 중 차단 확인.
- `adb shell screenrecord`로 시연 영상 원본.

## 빌더에게 맡기는 것

색·여백 세부값, 아이콘, 카운트다운 애니메이션, 내부 패키지 구조, Room 스키마 세부, 날씨 캐시 주기, 계절 보수값 세부.

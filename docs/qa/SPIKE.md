# 에뮬레이터 스파이크 결과

2026-11-13, Pleos Connect Emulator 2.1 (Android 14, user 빌드). 별도 시험 앱(`dev.evprobe`)으로 Vehicle SDK 2.3.2와 Android Car API를 직접 호출하고, Virtual Console(`adb forward tcp:8088`)의 값과 맞춰 봤다. 콘솔 세션(`POST /browser/session`)이 살아 있을 때만 값이 차량에 반영된다.

## 결론

충전 추천 카드의 핵심 흐름은 **모두 진짜 신호로 시연할 수 있다.** 안 되는 것은 차량 음성과 전비 API 두 가지다.

| 항목 | 결과 | 근거 |
|---|---|---|
| 충전구 열림 감지 | 됨 | 콘솔 `PUT /vehicle/ev-battery?field=charge_port_open&value=true` → 20ms 안에 Pleos `EvChargePortOpenListener`와 Car `EV_CHARGE_PORT_OPEN` 이벤트 |
| 배터리 잔량(SOC) | 됨 | Pleos `getEvBatteryStateOfCharge`·리스너. 콘솔 `field=soc` 변경이 즉시 반영 |
| 배터리 에너지·용량 | 됨 | Pleos `getEvBatteryLevel`/`getEvBatteryCapacity`, 단위 Wh (77400 = 77.4 kWh) |
| 주행거리계·남은 거리 | 됨 | Pleos `Odometer.getCurrentDistanceDriven`(km)·`getRangeRemainingDistance`(m)와 리스너. 콘솔 `/vehicle/odometer` |
| 충전 한도 설정 | 됨 | Pleos `setEvChargePercentLimit(70f)` → Car `EV_CHARGE_PERCENT_LIMIT` 이벤트 70, 다시 읽어도 70 |
| 충전 한도 읽기 | 됨(권한 필요) | Car `EV_CHARGE_PERCENT_LIMIT`. `android.car.permission.CAR_ENERGY`는 실행 중 허용이 필요한 권한 |
| 허용 한도 값 | 50~100%, 5% 단위 | `checkEvChargePercentLimitCapabilities` |
| 바깥 기온 | 됨 | Car `ENV_OUTSIDE_TEMPERATURE` (25.0) — 현재 기온만, 예보는 아님 |
| 전비 | 안 됨 | `getCumulative/SinceCharge/CurrentEvEfficiency` 모두 미지원 오류 |
| 차량 음성(Pleos TTS 3.3.1) | 안 됨 | 초기화·onReady·onStart까지 되고 `ON_DEVICE_ENGINE_ERROR`. Android 기본 TTS 엔진도 없음 |
| 날씨 예보 | 조건부 | 에뮬레이터 인터넷은 됨. Open-Meteo는 시험 시점에 호스트에서도 응답 없음, met.no는 1.6초에 응답 |
| `adb cmd car_service` 주입·조회 | 안 됨 | user 빌드라 거부. 콘솔이 대신함 |

## 주의할 점

- **콘솔의 충전 한도 표시는 앱이 바꾼 값을 따라오지 않는다.** 콘솔은 자기가 보낸 값(`source: desired`)만 보여준다. 앱이 설정한 값은 차량 속성을 다시 읽어 확인해야 한다. 반대로 콘솔에서 바꾼 한도는 앱의 Car 이벤트로 들어온다.
- SOC와 에너지(kWh)는 콘솔에서 따로 움직인다. 앱은 SOC를 기준으로 쓴다.
- `setEvChargePercentLimit`의 성공 콜백은 보낸 값을 그대로 돌려줄 뿐이다. 허용되지 않는 값(62)에도 성공이라고 답했다. 반드시 5% 단위 허용 값만 보내고 읽어서 확인한다.
- `pleos.car.permission.TTS_SERVICE`가 없으면 TTS 생성자에서 예외가 난다.
- AAOS는 사용자 10으로 앱을 실행한다. adb로 권한을 줄 때 `pm grant --user 10`.

## 설계에 반영

- 트리거는 계획의 1순위(충전구 열림)를 그대로 쓴다.
- 설정 후 Car 속성을 읽어 같으면 "설정됨", 권한이 없어 못 읽으면 "차에 요청했어요".
- 전비는 남은 거리 ÷ 남은 에너지로 구하고, 둘 중 하나가 0이면 기본값(5.0 km/kWh)을 쓰며 화면에 기본값임을 표시한다.
- 음성은 호출 코드는 넣되 실패하면 조용히 넘어간다. 에뮬레이터 시연 영상에는 음성이 없다.
- 날씨는 소스 두 개(Open-Meteo, met.no)를 순서대로 시도한다.

## 아직 확인하지 않은 것

앱이 백그라운드일 때 알림 표시, NaviHelper 경로 거리, 콘솔로 주행할 때 주행거리계가 저절로 늘어나는지.

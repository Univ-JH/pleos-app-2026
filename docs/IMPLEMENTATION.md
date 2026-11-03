# 운전 돌아보기 — 구현과 검증 범위

## 제품 동작

한국의 초보·재운전자를 위한 주행 후 코칭 앱이다. Android 사용자 프로필의 앱 전용 저장소에만 기록한다. 서버 전송, 운전자 식별, 점수 기반 순위는 없다.

1. 최초 실행 시 차량·알림 권한을 허용한다. P 및 1 km/h 미만의 유효한 신호가 30초 이어지고 `CarUxRestrictions`가 제한 없음이면 설정을 연다.
2. 자동 기록에 동의하면 `specialUse` foreground service가 상시 알림과 함께 동작한다.
3. D/R에서 주행을 시작한다. 정차나 N만으로 끝내지 않는다. P 및 1 km/h 미만이 30초 이어지면 리포트를 저장한다.
4. 앱을 닫아도 기록한다. `BOOT_COMPLETED`, `MY_PACKAGE_REPLACED`에서 동의 상태를 확인하고 복구한다. Android 강제 중지를 우회하지 않는다.
5. 주차와 UX 제한 조건을 만족할 때만 장면·학습·통계·설정을 표시한다. 화면을 강제로 열지 않는다.

## 구조

단일 app 모듈 내부의 책임을 분리했다.

- `vehicle/VehicleSignalSource.kt`: AAOS 속도·기어 10 Hz/변경 구독, Pleos Safety 좌석별 경고 지원 확인·구독·상태 조회, 공개 Telltales 조회. 별도 NaviHelper 도착 정보 어댑터는 P3용이며 자동 연결하지 않는다. `ReplaySignalSource`는 테스트 입력만 제공한다.
- `domain/Driving.kt`: Android 의존성 없는 감지기, 주행 상태기, 주차 접근 판정, 버전별 규칙.
- `data/CoachDatabase.kt`: Room의 Trip, DrivingEvent, EventFeedback, RawSample, EventEvidence. 외래키 연쇄 삭제.
- `data/CoachRepository.kt`: 단일 수집기가 주행·근거를 트랜잭션 저장한다. DataStore에 동의와 목표 저장.
- `service/RecordingService.kt`: 수집 수명주기, 알림, 부팅 복구. 서비스 간 mutex로 중복 기록 방지.
- `ui/`: ViewModel / StateFlow / Compose. 가로 폭에 맞춰 배치하고 좁은 창에서는 세로 스크롤한다.
- `domain/Lessons.kt`: 오프라인 학습 카드 24개, 출처·검토일·적용 조건 및 거리당 통계.

## 규칙 experimental-1

초기 에뮬레이터 실험값이며 법규 위반 기준 또는 검증된 위험 분류기가 아니다.

| 관측 | 조건 |
|---|---|
| 급출발 후보 | 1 km/h 이하가 2초 이어진 다음 D 출발 첫 5초 안에서 +3 m/s² 이상을 1초 관측 |
| 급감속 후보 | D, 감속 시작 속도 10 km/h 이상, −3 m/s² 이하를 1초 관측 |
| 안전벨트 경고 | 차량 미착용 경고와 5 km/h 이상 속도가 3초 함께 관측. 좌석별 신호가 없으면 지원되는 차량 전체 경고등만 사용 |

가속도는 최근 약 1초의 속도 차분으로 추정한다. 실제 샘플 시각으로 5 Hz 이상의 관측이 확보되어야 한다. 수신 시각과 차량 속도 샘플 시각은 별도로 저장하며, 250 ms를 초과한 속도 값·역전된 시각·무효 상태·누락 구간은 연속 감지를 끊는다. 반복 전달된 값으로 샘플 수를 부풀리지 않는다. 기어는 변경 시 전달되는 상태 신호이므로 속도와 같은 주기 제한을 적용하지 않는다.

같은 출발에서는 한 번만 기록한다. 급감속은 기준 아래에서 회복된 상태가 1초 이어져야 다음 장면으로 나눈다. 샘플 수나 문턱값의 짧은 흔들림으로 같은 동작을 중복 등록하지 않는다. 장면 전 최대 10초, 후 최대 5초의 근거를 저장하며 주행 종료·수집 중단 시 후속 근거는 짧을 수 있다. 모르는 좌석 또는 수신하지 못한 경고는 추정하지 않는다. 전후 사정과 위법 여부는 판단하지 않는다.

## 저장·중단·삭제

- 원시 신호 7일, 리포트·장면 근거·피드백 90일. 보관 정리는 서비스 시작/주행 완료/앱 ViewModel 생성 시 수행한다. 앱이 실행되지 않는 동안 삭제 시각을 보장하지 않는다.
- 원시 신호는 약 1초 단위로 일괄 저장한다. 전원 손실 시 마지막 약 1초는 누락될 수 있다.
- 종료 콜백을 받지 못한 `RECORDING` 행은 재시작 시 한 번 `INTERRUPTED`로 마감한다. 마지막 체크포인트는 유지하고 이후 D/R을 새 주행으로 기록한다. 전원 단절 전후를 하나의 연속 관측으로 합치지 않는다.
- 사용자 상황 설명과 통계 포함 여부는 별도 테이블이다. 통계 제외는 원본 이벤트나 근거를 지우지 않는다.
- 기록 중인 주행은 삭제하지 않는다. 전체 삭제는 자동 기록 종료 및 저장 완료 후 가능하다. 동의 철회는 기록 삭제와 별개다.
- 동의 철회 시 기록 서비스와 저장을 중단한다. 앱 화면을 열어 둔 동안에는 주차·화면 사용 제한을 확인하기 위한 비저장 상태 구독이 남으며, 화면도 닫으면 해제한다.
- 통계는 완료된 실제 SDK 주행만 사용한다. 최근 5회와 이전 5회를 비교하되 각 구간 최소 3회·10 km·유효 신호 90%를 만족해야 변화를 표시한다. 이 수치는 제품의 초기 비교 정책이다.
- 테스트 재생 입력은 `simulated=true`를 강제하고 화면에 테스트 데이터로 표시한다. 일반 사용자 화면에 가짜 주행 생성 버튼은 없다.

## 24개 상황 상태표

| 번호 | 상황 | 단계 | 코드 범위 / 추가 데이터 |
|---|---|---|---|
| 1 | 급출발 | P1 | 감지기·근거·리포트 구현 |
| 2 | 가감속 반복 | P2 | 학습 제공, 반복 패턴·신호 검증 필요 |
| 3 | 급감속 | P1 | 감지기·근거·리포트 구현 |
| 4 | 급격한 조향 | P2 | 학습 제공, 조향각 및 정상 회전 비교 필요 |
| 5 | 안전벨트 경고 | P1 | 좌석별 Safety API와 차량 경고등 대체 경로 구현. 현재 에뮬레이터는 경고등만 검증, 추가 권한의 배포 허용 여부 확인 필요 |
| 6 | 장시간 연속 운전 | P2 | 학습 제공, 휴식/주행 연속성 정책 필요 |
| 7 | 제한속도 초과 | P4 | 도로 구간·방향·시간별 제한속도 필요 |
| 8 | 보호구역 속도 | P4 | 보호구역 경계·적용 시간·제한속도 필요 |
| 9 | 회전 전 감속 | P3 | 실제 궤적과 회전 구간 필요 |
| 10 | 회전 시 미점등 | P3 | 실제 회전 및 방향지시등 필요 |
| 11 | 늦은 점등 | P3 | 점등 시각·회전 위치 필요 |
| 12 | 회전 후 미해제 | P3 | 실제 회전 종료 필요 |
| 13 | 회전교차로 진입 점등 | P3 | 교차로 구조·진입 궤적 필요 |
| 14 | 회전교차로 진출 점등 | P3 | 출구 구조·진출 궤적 필요 |
| 15 | 적색신호 우회전 | P5 | 신호·정지선·보행자 필요 |
| 16 | 우회전 전용 신호 | P5 | 전용 신호 방향·현시 필요 |
| 17 | 보호구역 무신호 횡단보도 | P5 | 횡단보도·보호구역·신호기 유무 필요 |
| 18 | 보행자 양보 | P5 | 보행자 위치·의도 필요 |
| 19 | 회전 차량 양보 | P5 | 주변 차량 궤적·우선권 맥락 필요 |
| 20 | 차간거리 | P5 | 앞차 거리·상대속도 필요 |
| 21 | 무리한 차로 변경 | P5 | 차선·옆/뒤 차량 궤적 필요 |
| 22 | 진로 변경 금지선 | P5 | 노면 표시·차량 위치 필요 |
| 23 | 꼬리물기 | P5 | 교차로 경계·출구 정체 필요 |
| 24 | 적색신호·정지선 | P5 | 신호 현시·정지선 위치 필요 |

P2~P5는 감지 기능을 활성화하지 않았다. 내비 경로와 도착 정보로 실제 회전 또는 교통 위반을 추정하지 않는다. 실제 실행 검증은 [QA 결과](qa/RESULTS.md)에 별도로 기록한다.

## 검증 실행

```sh
./gradlew assembleDebug lintDebug testDebugUnitTest
./gradlew connectedDebugAndroidTest
python3 -B -m unittest discover -s .github/scripts -p 'test_*.py'
```

실제 SDK 경로 QA:

```sh
adb forward tcp:8088 tcp:8088
# Virtual Console을 브라우저에서 열거나, 동일 endpoint를 사용하는 QA 입력기를 사용한다.
printf '%s' '{"gear":"Park","speed":0,"belt":"Off"}' > /tmp/coach-controls.json
python3 scripts/qa/console_driver.py /tmp/coach-controls.json
```

첫 터미널에서 입력기를 유지하고, 앱에서 주차 확인 후 자동 기록에 동의한 다음 별도 터미널에서 실행한다.

```sh
python3 scripts/qa/scenario.py /tmp/coach-controls.json events
python3 scripts/qa/scenario.py /tmp/coach-controls.json normal
# 각 명령이 끝난 뒤 다음 시나리오를 실행한다. 마지막 P 유지 35초를 생략하지 않는다.
python3 scripts/qa/inspect_records.py --adb "$ANDROID_HOME/platform-tools/adb" --user 10
```

`events`는 급출발·급감속·안전벨트 각 1개, `normal`은 이벤트 0개가 기대 결과다. 별도 목적지 설정 없이 동작한다. `inspect_records.py`는 debuggable APK의 DB를 읽기 전용으로 복사하므로 release APK에는 사용할 수 없다. 에뮬레이터의 현재 사용자가 다르면 `adb shell am get-current-user` 결과에 맞춘다. 입력기는 속도를 10 Hz로 게시하며 Virtual Console이 받는 정수 km/h로 변환한다.

선택적으로 실제 SDK smoke를 실행한다. 일반 계측 테스트에서는 이 항목을 건너뛴다.

```sh
./gradlew installDebug installDebugAndroidTest
adb shell am instrument -w -e class io.github.univjh.pleosapp.data.SdkSmokeTest \
  -e sdkSmoke true io.github.univjh.pleosapp.test/androidx.test.runner.AndroidJUnitRunner
```

폼 팩터 QA는 `adb emu multidisplay add 2 1080 1920 240 0` 및 `1920 1080 240 0`으로 별도 디스플레이를 만들고 `am start --display 2`로 앱을 옮겨 수행했다. 기존 task를 옮기면 원래 창 경계가 남을 수 있어 `am task resize <TASK_ID> 0 0 <WIDTH> <HEIGHT>`로 맞춘다. 스크린샷은 `adb emu screenrecord screenshot --display 2 <HOST_DIRECTORY>`로 저장한다. 종료 시 앱 task를 닫고 기본 디스플레이에서 다시 열어 창 경계를 복구한 뒤 `adb emu multidisplay del 2`로 정리한다. 기본 화면에 `wm size`만 적용하는 방법은 Pleos 분할 창 경계를 바꾸지 않으므로 사용하지 않는다.

이 파일의 gear / speed(km/h) / belt(On, Off)를 변경하면 Virtual Console → 차량 서비스 → SDK → 앱으로 전달된다. 입력기는 로컬 에뮬레이터 테스트 전용이며 앱에 포함되지 않는다. 좌석별 Safety 미착용 경고가 없는 환경에서는 공개 `getTelltalesInfo`로 SeatBelt 경고를 조회하고 좌석 정보 없음으로 저장한다. `getTelltalesInfo`는 공개 인터페이스에 CAR_SAFETY로 표기되지만 Vehicle 2.3.2 구현은 PRIVATE_CAR_SAFETY를 검사한다. 에뮬레이터에 normal 등급으로 등록된 추가 권한을 명시했다. 공모전·배포 환경에서 이 권한의 수용 여부는 별도 확인이 필요하다. 거부되면 안전벨트 기능을 비활성화한다. 개인 좌석 미착용으로 의미를 확대하지 않는다.

## Release 서명

저장소 루트의 `keystore.properties`를 다음 형식으로 준비한다. 비밀번호와 키는 커밋하지 않는다.

```properties
storeFile=coach-release.jks
storePassword=<local secret>
keyAlias=coach
keyPassword=<local secret>
```

`./gradlew assembleRelease` 후 `app/build/outputs/apk/release/app-release.apk`를 확인한다. 설정 파일이 없으면 unsigned APK이므로 제출하지 않는다. 로컬 서명 키는 별도로 백업해야 동일 앱 업데이트를 서명할 수 있다. 개발 APK와 release APK은 서명이 다르다.

## 공식 근거

- [Pleos 속도 신호 FAQ](https://document.pleos.ai/docs/pleos-only/vehicle-app-developer-guide/vehicle-app-faq)
- [Pleos 주행 규제](https://document.pleos.ai/docs/pleos-only/vehicle-app-developer-guide/driving-restriction-development-guide)
- [Virtual Console](https://document.pleos.ai/docs/pleos-only/app-test-guide/virtual-console-guide)
- [Android foreground service 유형](https://developer.android.com/develop/background-work/services/fgs/service-types)
- [Android 백그라운드 시작 제한](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start)

# P0 / P1 검증 결과

2026-10-29, macOS Apple Silicon, JDK 17, Pleos Connect Emulator 2.1.0 (Android 14 / API 34).

## 자동 검사

- `assembleDebug`, `lintDebug`, `testDebugUnitTest`, `assembleRelease`: 통과.
- 순수 Kotlin 테스트: 18개 통과. 경계값, 연속 조건 중복 방지, 5 Hz 미달, 오래된 값, 누락·역전 시간, 주차 판정, 실제 샘플 중복, 추이 계산 포함.
- Room 계측 테스트: 최종 4개 통과 (삭제 연쇄, 피드백 원본 보존, 복구/보관, 출처 혼합 방지).
- 기존 CI 변경 감지 Python 테스트: 11개 통과.
- APK 서명: apksigner v2 검증 통과. release 키는 로컬에만 생성하고 Git에서 제외.
- SDK smoke: 1개 통과. Virtual Console에서 전달한 속도 0 / P를 SDK에서 유효한 프레임으로 수신. 앱 내부 재생기를 사용하지 않음.

## 실행 중 발견한 플랫폼 차이

1. Pleos 2.1의 `android.car` 클래스는 boot classpath에 있으며 `android.car`라는 shared library는 등록되지 않음. manifest의 해당 uses-library는 optional로 선언해야 설치 가능.
2. 에뮬레이터의 좌석별 `SEAT_BELT_NOT_BUCKLED_WARNING` capability는 `ERROR_NOT_SUPPORTED_PROPERTY`를 반환함.
3. Vehicle 2.3.2 `getTelltalesInfo`의 인터페이스 RequiresPermission은 CAR_SAFETY지만 구현의 RequiresCustomPermission은 PRIVATE_CAR_SAFETY임. 실제 읽기에서 Permission 오류를 확인했으며, 에뮬레이터에 normal 등급으로 등록된 PRIVATE_CAR_SAFETY를 추가 선언함. 좌석 정보 없이 차량 SeatBelt 경고만 사용함. **공모전/마켓에서 이 권한 허용 여부와 실차 지원은 미검증**. 권한 거절/신호 부재 시 오탐 대신 기능 미지원으로 처리.
4. 처음 사용한 `-gpu swiftshader_indirect`에서 플랫폼 DrivingView의 libUnreal.so가 반복 SIGSEGV를 내고 홈 화면을 방해함. 앱의 AndroidRuntime 예외와 구분하여 로그를 보관하고 `-gpu host` 환경으로 전환.
5. 최초 에뮬레이터 실행 중 CarPropertyService 무응답을 관찰했으며 재부팅으로 복구됨. 앱은 이 구간에 유효 신호를 만들어내지 않으며 UI 접근을 차단함.

## 실제 SDK 시나리오

검증 결과와 개별 근거는 아래 표에 기록한다.

| 항목 | 결과 |
|---|---|
| 최초 동의 / foreground service | 통과 — 실제 UI 동의 후 user 10에 specialUse(0x40000000) foreground 서비스 실행 |
| SDK 급출발·급감속·안전벨트 → Room → 리포트 | 통과 — 272.3 m, 각 1개, 원시 샘플 557개·근거 425개, P 30초 후 COMPLETE. 유효 구간 누락 0 ms. [DB 근거](evidence/sdk-events.json) · [리포트](evidence/report.png) |
| 정상 주행 이벤트 0개 | 통과 — 별도 130.1 m 주행 완료, 0개. [DB 근거](evidence/normal-and-recovery.json) |
| 다른 앱에서 백그라운드 기록 | 통과 — 재부팅 후 내비게이션이 표시된 채로 정상 시나리오를 시작·종료. 코치 화면을 열지 않아도 기록 |
| 재부팅 후 동의 기반 재시작 | 통과 — 실제 재부팅 후 BOOT_COMPLETED 사유로 specialUse FGS 시작. 진행 중이던 기록은 체크포인트에서 INTERRUPTED, 다음 주행은 별도 저장. [서비스 근거](evidence/reboot-consent-on.txt) |
| 동의 철회 / 재부팅 시 비실행 | 통과 — UI 동의 철회 직후 종료. 실제 재부팅에서 수신기 호출 후 30초 뒤에도 서비스 없음. [수신기](evidence/reboot-consent-off-broadcast.txt) · [서비스](evidence/reboot-consent-off.txt) |
| 장면 근거 / 피드백 / 통계 제외 | 통과 — 속도 그래프 확인, 급감속에 ‘긴급 회피’ 및 includeInStats=false 저장. 이벤트 3개 원본은 변경 전과 동일. [DB 근거](evidence/feedback.json) |
| 주행 규제 | 통과 — 상세 장면을 연 상태에서 D·속도 입력 후 기록 상태만 표시. 탐색·설정·피드백 입력 숨김. 주차 30초와 UX 허용을 함께 확인해야 복귀 |
| 삭제 / 오프라인 학습 | 통과 — 전체 삭제 후 5개 테이블 모두 0·integrity ok. 이후 주행별 삭제는 선택 기록만 제거. 네트워크 none 상태에서 학습 목록·상세 열림 |
| 가로·세로·분할 레이아웃 | 통과 — 기본 2560×1440의 1682×1210 분할 창, 별도 1080×1920·1920×1080 / 240 dpi 디스플레이. 스크롤·학습 선택·줄바꿈 확인. [세로](evidence/portrait.png) · [가로](evidence/landscape.png) |

초기 SDK 시나리오에서 급출발이 중복 등록되는 현상을 확인해 출발 단위 latch를 추가했다. 급감속에도 회복 구간을 두었고 회귀 테스트 2개를 추가한 뒤 위 시나리오를 다시 통과했다. 초기 실패 로그와 최종 통과 로그는 로컬 artifacts에서 구분한다.

여기서 ‘차량 SDK’는 **에뮬레이터의 Virtual Console → 차량 서비스 → AAOS/Pleos SDK → 앱** 경로다. 실제 도로 주행이나 실차 검증을 의미하지 않는다. DB의 simulated=false는 앱 내부 ReplaySignalSource를 쓰지 않았다는 뜻이다. SDK 시나리오와 영상은 Debug APK로 실행했으며 서명된 Release APK는 별도 빌드 산출물이다.

## 재현 명령과 산출물

구현/실행 절차는 [IMPLEMENTATION.md](../IMPLEMENTATION.md). 로컬 `artifacts/`는 테스트 데이터·영상·패키지 폴더이며 Git에서 제외한다. `scripts/qa/console_driver.py`와 `scenario.py`는 Virtual Console HTTP 인터페이스를 통해 차량 상태를 입력한다. 앱의 ReplaySignalSource와 구별한다.

| 산출물 | 경로 |
|---|---|
| 서명된 APK | `artifacts/driving-coach-release.apk` |
| SDK 시연 영상 | `artifacts/driving-coach-demo.mp4` — 89.3초, 2560×1440, 주행 상태·주차 확인·3종 리포트 |
| 빌드·Lint·단위 테스트 | `artifacts/final-build.txt`, `app/build/reports/tests/testDebugUnitTest/`, `app/build/reports/lint-results-debug.html` |
| Room / SDK 계측 테스트 | `artifacts/room-tests.txt`, `artifacts/sdk-smoke-final.txt` |
| 최종 DB 검증 | `artifacts/final-assertions.txt`, `artifacts/final-events-result.json`, `artifacts/normal-final-result.json` |
| 서명·영상 확인 | `artifacts/release-signature.txt`, `artifacts/video-check.txt` |

APK SHA-256: `8f7c6222aa6758bfb5aef6b98722d7573d5a76645d4c81ecb8329fc96edc4ff5`.

## 제출 전 남은 확인

- `PRIVATE_CAR_SAFETY` 권한을 공모전·마켓 심사에서 허용하는지 주최 측 확인. 지원되지 않으면 현재 에뮬레이터에서 안전벨트 경고 경로를 사용할 수 없으며 3종 완성 조건을 다시 검토해야 한다.
- 실차 신호 주기·경고 제공 여부·전원 정책·OS별 자동 실행, 실제 도로에서의 오탐률은 미검증. 현재 문턱값을 안전성 평가 점수나 법규 위반 판정으로 사용하지 않는다.
- P2~P5는 학습 콘텐츠와 어댑터 경계·24개 상태표만 포함한다. 지도·인지 데이터 확보 전 감지 기능을 제공하지 않는다.
- CI와 동일한 검사는 로컬에서 통과했다. 원격 GitHub Actions 실행·PR·공모전 제출은 하지 않았다. 로컬 서명 키는 Git에서 제외되어 있으므로 별도 백업한다.

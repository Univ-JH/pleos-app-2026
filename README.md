# 운전 돌아보기 — Pleos Driving Coach

Pleos Connect app for the 2026 Hyundai Motor Group App Development Challenge.

현대자동차그룹 차량용 앱 개발 공모전을 위한 Android 프로젝트입니다.
주차 후 관측 장면·안전 지식·개선 추이를 확인하는 온디바이스 안전운전 코치입니다.
급출발·급감속·안전벨트 경고를 첫 범위로 구현했습니다. 실제 SDK 검증 상태와 남은 조건은 [QA 결과](docs/qa/RESULTS.md)를 확인하세요.

## 개발 환경

| 항목 | 설정 |
| --- | --- |
| 개발 OS | Windows / Linux / macOS |
| 언어 / UI | Kotlin 2.0.21 / Jetpack Compose · Material 3 |
| JDK | 17 |
| Gradle / Android Gradle Plugin | 8.10.2 / 8.8.2 |
| Android SDK | min 28 / compile·target 35 |
| Pleos SDK | Vehicle 2.3.2 / NaviHelper 3.0.5 |
| Application ID | `io.github.univjh.pleosapp` |

도구 버전은 Pleos 공식 샘플의 AGP·Gradle 구성을 기준으로 고정했습니다.
의존성 버전은 [`gradle/libs.versions.toml`](gradle/libs.versions.toml)에서 관리합니다.

## 시작하기

1. JDK 17과 Android SDK Platform 35, Build Tools 35.0.0을 설치합니다.
2. Android Studio에서 저장소 루트를 열고 Gradle Sync를 실행합니다.
3. SDK 경로를 자동으로 찾지 못하면 로컬 `local.properties`에 `sdk.dir`을 지정합니다.
4. 아래 명령으로 빌드와 정적 검사를 실행합니다.

macOS / Linux:

```bash
./gradlew :app:assembleDebug :app:lintDebug :app:testDebugUnitTest
```

Windows PowerShell:

```powershell
.\gradlew.bat :app:assembleDebug :app:lintDebug :app:testDebugUnitTest
```

- Debug APK: `app/build/outputs/apk/debug/app-debug.apk`
- Lint 보고서: `app/build/reports/lint-results-debug.html`
- 앱 실행: Pleos Connect AVD를 시작한 뒤 Android Studio의 Run 사용
- OS별 환경 변수·설치 명령·에뮬레이터 설정: [개발 환경 안내](docs/SETUP.md)

## 사용과 구현 범위

- 최초 권한 허용 후 P 상태를 30초 유지하고 자동 기록에 동의합니다.
- D/R에서 자동 시작, P + 1 km/h 미만 30초에서 자동 종료합니다.
- 주행 중에는 기록 상태만 표시합니다. 주차 및 차량 UX 허용 시 리포트·학습·피드백·삭제 기능을 엽니다.
- Room에 기기 내 기록, DataStore에 동의·목표를 저장합니다. 서버로 전송하지 않습니다.
- 원시 신호 7일, 리포트·근거 90일 보관 정책을 적용합니다.
- 부팅 복구, 백그라운드 foreground service, 규칙 버전, 데이터 누락 처리, 테스트 입력 분리가 포함됩니다.

[구조·규칙·24개 상황 로드맵·서명 방법](docs/IMPLEMENTATION.md) · [실제 테스트 결과](docs/qa/RESULTS.md) · [UI 미리보기·검증](docs/qa/UI.md)

## 프로젝트 구조

```text
app/src/main/java/io/github/univjh/pleosapp/
  domain/     순수 Kotlin 감지·주행 상태·학습·통계
  vehicle/    AAOS / Vehicle / NaviHelper 어댑터
  data/       Room / DataStore / 기록 엔진
  service/    foreground service / 부팅 복구
  ui/         ViewModel / Compose
app/src/test/          분석기·경계값 테스트
app/src/androidTest/   실제 Room 저장소 테스트
app/schemas/           Room 스키마 이력
scripts/qa/            로컬 에뮬레이터 입력 도구
docs/                  설정·구현·검증 결과
```

## 협업

- `main`에서 `feat/설명`, `chore/설명`, `fix/설명` 브랜치를 만들어 작업합니다.
- `main` 변경은 PR로 병합하며, 최신 `main`을 반영한 상태에서 Linux CI의 `Build and lint` 검사가 통과해야 합니다. 문서만 변경한 경우에도 이 검사는 실행되며 Android 빌드 단계만 생략합니다.
- 별도의 리뷰 승인 인원은 요구하지 않습니다. 관리자에게도 보호 규칙을 적용하며, `main` 직접 푸시·강제 푸시·삭제를 차단합니다.
- PR 전에 위의 OS별 명령으로 `:app:assembleDebug :app:lintDebug :app:testDebugUnitTest`를 실행합니다.
- `local.properties`, API 키, 서명 키와 비밀번호는 커밋하지 않습니다.
- SDK 추가·업데이트 시 버전 카탈로그와 개발 환경 문서를 함께 수정합니다.
- Release 서명은 로컬 `keystore.properties`로 설정합니다. 키·비밀번호는 Git에서 제외됩니다. CI의 Debug APK는 개발 확인용입니다.

## GitHub Actions

[Android workflow](https://github.com/Univ-JH/pleos-app-2026/actions/workflows/android.yml)는
`main` push, PR, 수동 실행 시 Linux(`ubuntu-24.04`)에서 Android APK 빌드·Lint·단위 테스트를 수행합니다.
`README.md`, `docs/`의 Markdown 및 `docs/images/`의 이미지 파일만 변경한 경우 Android 단계는 생략합니다.
그 외 파일, 코드 삭제·이동, 변경 범위를 확인할 수 없는 경우와 수동 실행은 전체 검사를 수행합니다.
Windows·macOS·Linux의 로컬 개발 안내는 [개발 환경 안내](docs/SETUP.md)에 제공합니다.
Gradle 의존성과 빌드 결과를 캐시하며, PR은 캐시를 읽기만 합니다.
Debug APK와 Lint·단위 테스트 보고서는 실행 결과의 Artifacts에서 7일간 다운로드할 수 있습니다.
캐시 문제를 조사할 때는 **Actions → Android → Run workflow → disable_cache**를 켜서 전체 검사를 실행합니다.

`main`의 보호 규칙은 GitHub Settings에서 관리합니다.
필수 검사 이름인 `Build and lint`를 변경할 때는 브랜치 보호 설정도 함께 수정해야 합니다.

## 공식 자료

- [공모전 참가 가이드](https://appdevchallenge.auto-hmg.io/guide/overview)
- [Pleos Playground](https://pleos.ai/playground)
- [Pleos Connect 개발 환경](https://document.pleos.ai/docs/connect/guide/getting-started/application-development/setup-connect-sdk-emulator)
- [Vehicle SDK](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/intro)
- [NaviHelper SDK](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/intro)

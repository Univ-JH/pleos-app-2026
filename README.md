# pleos-app-2026

Pleos Connect app for the 2026 Hyundai Motor Group App Development Challenge.

현대자동차그룹 차량용 앱 개발 공모전을 위한 Android 프로젝트입니다.
주제는 검토 중이며, 기본 화면 실행과 Pleos Vehicle 데이터 조회를 전용 에뮬레이터에서 검증했습니다.

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
./gradlew :app:assembleDebug :app:lintDebug
```

Windows PowerShell:

```powershell
.\gradlew.bat :app:assembleDebug :app:lintDebug
```

- Debug APK: `app/build/outputs/apk/debug/app-debug.apk`
- Lint 보고서: `app/build/reports/lint-results-debug.html`
- 앱 실행: Pleos Connect AVD를 시작한 뒤 Android Studio의 Run 사용
- OS별 환경 변수·설치 명령·에뮬레이터 설정: [개발 환경 안내](docs/SETUP.md)

## 현재 범위

- [x] 주제에 종속되지 않는 Compose 시작 화면과 라이트·다크 테마
- [x] Gradle Wrapper, 버전 카탈로그, Pleos Maven 저장소 및 SDK 의존성
- [x] Linux GitHub Actions Debug APK 빌드·Lint 검사 설정
- [x] Pleos 2.1.0에서 기본 앱 실행 및 앞줄 왼쪽 문 상태 조회 검증
- [ ] 앱 주제 및 핵심 사용자 시나리오 결정
- [ ] 주제에 맞는 Vehicle 기능 및 NaviHelper 연동 구현·검증
- [ ] 주행 규제 대응, 핵심 기능 테스트, 제출용 서명 APK 준비

디버그 전용 조회 화면에서 문 상태 `0 → 1 → 0` 수신을 확인했습니다.
검증 절차와 범위는 [Vehicle SDK 검증 기록](docs/VEHICLE_SMOKE_TEST.md)을 참조하세요.
조회 화면과 `CAR_DOORS` 권한은 release 빌드에 포함되지 않습니다.
NaviHelper는 의존성만 추가한 상태이며, 실차 동작은 아직 검증하지 않았습니다.

## 프로젝트 구조

```text
app/                    Android 앱, Compose 화면·테마
gradle/                 의존성 버전과 Gradle Wrapper
docs/SETUP.md           개발 환경·에뮬레이터·SDK 연동 안내
.github/workflows/      빌드와 Lint 자동 검사
```

## 협업

- `main`에서 `feat/설명`, `chore/설명`, `fix/설명` 브랜치를 만들어 작업합니다.
- `main` 변경은 PR로 병합하며, 최신 `main`을 반영한 상태에서 Linux CI의 `Build and lint` 검사가 통과해야 합니다.
- 별도의 리뷰 승인 인원은 요구하지 않습니다. 관리자에게도 보호 규칙을 적용하며, `main` 직접 푸시·강제 푸시·삭제를 차단합니다.
- PR 전에 위의 OS별 명령으로 `:app:assembleDebug :app:lintDebug`를 실행합니다.
- `local.properties`, API 키, 서명 키와 비밀번호는 커밋하지 않습니다.
- SDK 추가·업데이트 시 버전 카탈로그와 개발 환경 문서를 함께 수정합니다.
- 제출용 APK는 기능 구현 후 서명 설정과 제출 절차를 정해 준비합니다. CI의 Debug APK는 개발 확인용입니다.

## GitHub Actions

[Android workflow](https://github.com/Univ-JH/pleos-app-2026/actions/workflows/android.yml)는
`main` push, PR, 수동 실행 시 Linux(`ubuntu-latest`)에서 Android APK 빌드와 Lint를 수행합니다.
Windows·macOS·Linux의 로컬 개발 안내는 [개발 환경 안내](docs/SETUP.md)에 제공합니다.
Debug APK와 Lint 보고서는 실행 결과의 Artifacts에서 7일간 다운로드할 수 있습니다.

`main`의 보호 규칙은 GitHub Settings에서 관리합니다.
필수 검사 이름인 `Build and lint`를 변경할 때는 브랜치 보호 설정도 함께 수정해야 합니다.

## 공식 자료

- [공모전 참가 가이드](https://appdevchallenge.auto-hmg.io/guide/overview)
- [Pleos Playground](https://pleos.ai/playground)
- [Pleos Connect 개발 환경](https://document.pleos.ai/docs/connect/guide/getting-started/application-development/setup-connect-sdk-emulator)
- [Vehicle SDK](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/intro)
- [NaviHelper SDK](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/intro)

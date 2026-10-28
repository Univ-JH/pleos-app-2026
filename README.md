# pleos-app-2026

Pleos Connect app for the 2026 Hyundai Motor Group App Development Challenge.

현대자동차그룹 차량용 앱 개발 공모전을 위한 Android 프로젝트입니다.
주제는 검토 중이며, 현재는 기본 화면과 Pleos SDK 의존성을 준비한 단계입니다.

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
- [x] Windows·Linux·macOS별 GitHub Actions Debug APK 빌드·Lint 검사 설정
- [ ] 앱 주제 및 핵심 사용자 시나리오 결정
- [ ] Vehicle 데이터 조회와 NaviHelper 연동 구현·실기동 검증
- [ ] 주행 규제 대응, 핵심 기능 테스트, 제출용 서명 APK 준비

SDK 의존성을 추가한 상태이며, 차량 서비스 연결이나 API 호출 성공을 의미하지 않습니다.
차량 권한은 구현할 기능이 정해지면 필요한 항목만 추가합니다.

## 프로젝트 구조

```text
app/                    Android 앱, Compose 화면·테마
gradle/                 의존성 버전과 Gradle Wrapper
docs/SETUP.md           개발 환경·에뮬레이터·SDK 연동 안내
.github/workflows/      빌드와 Lint 자동 검사
```

## 협업

- `main`에서 `feat/설명`, `chore/설명`, `fix/설명` 브랜치를 만들어 작업합니다.
- PR 전에 위의 OS별 명령으로 `:app:assembleDebug :app:lintDebug`를 실행합니다.
- `local.properties`, API 키, 서명 키와 비밀번호는 커밋하지 않습니다.
- SDK 추가·업데이트 시 버전 카탈로그와 개발 환경 문서를 함께 수정합니다.
- 제출용 APK는 기능 구현 후 서명 설정과 제출 절차를 정해 준비합니다. CI의 Debug APK는 개발 확인용입니다.

## GitHub Actions

[Android workflow](https://github.com/Univ-JH/pleos-app-2026/actions/workflows/android.yml)는
`main` push, PR, 수동 실행 시 Windows·Linux·macOS에서 각각 빌드와 Lint를 수행합니다.
Windows는 `gradlew.bat`를 PowerShell에서 실행하고, macOS·Linux는 `gradlew`를 사용합니다.
각 OS의 Debug APK와 Lint 보고서는 실행 결과의 Artifacts에서 7일간 다운로드할 수 있습니다.

워크플로 파일과 GitHub 저장소의 브랜치 보호·보안·병합 설정은 별도 항목입니다.
저장소 관리 설정은 GitHub Settings에서 관리합니다.

## 공식 자료

- [공모전 참가 가이드](https://appdevchallenge.auto-hmg.io/guide/overview)
- [Pleos Playground](https://pleos.ai/playground)
- [Pleos Connect 개발 환경](https://document.pleos.ai/docs/connect/guide/getting-started/application-development/setup-connect-sdk-emulator)
- [Vehicle SDK](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/intro)
- [NaviHelper SDK](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/intro)

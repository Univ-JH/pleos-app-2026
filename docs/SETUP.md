# 개발 환경 설정

## 1. 로컬 도구

- [Android Studio](https://developer.android.com/studio): 프로젝트 편집과 Pleos 전용 AVD 실행
- JDK 17: Android Studio의 Gradle JDK도 17로 지정
- Android SDK Platform 35와 Build Tools 35.0.0: 앱 컴파일
- Android SDK Platform-Tools: ADB 설치·로그 확인

Android Studio가 없어도 JDK와 Android command-line tools로 APK를 빌드할 수 있습니다.
Gradle은 저장소의 Wrapper가 다운로드하므로 별도 전역 설치가 필요하지 않습니다.

## 2. 운영체제별 환경 변수

Android Studio의 SDK Manager에 표시된 **Android SDK Location**이 실제 SDK 경로입니다.
아래는 기본 경로 예시이며, 다르게 설치했다면 실제 경로로 변경합니다.

| OS | Android SDK 기본 경로 | 빌드에 사용할 Wrapper |
| --- | --- | --- |
| Windows | `%LOCALAPPDATA%\Android\Sdk` | `gradlew.bat` |
| Linux | `$HOME/Android/Sdk` | `./gradlew` |
| macOS | `$HOME/Library/Android/sdk` | `./gradlew` |

### Windows — PowerShell

Windows의 사용자 환경 변수에서 `JAVA_HOME`을 설치한 **JDK 17의 루트 폴더**로 설정합니다.
`JAVA_HOME`에는 `bin` 폴더를 포함하지 않습니다. 설정 후 새 PowerShell을 열고 다음을 실행합니다.

```powershell
$env:ANDROID_HOME = Join-Path $env:LOCALAPPDATA 'Android\Sdk'
$env:Path = "$env:JAVA_HOME\bin;$env:ANDROID_HOME\platform-tools;$env:ANDROID_HOME\emulator;$env:ANDROID_HOME\cmdline-tools\latest\bin;$env:Path"
java -version
```

`java -version`이 17인지 확인합니다. SDK 경로에 공백이 있는 경우에도 `Join-Path`와 따옴표를 유지합니다.
계속 사용할 `ANDROID_HOME`과 PATH 항목은 Windows 사용자 환경 변수에 등록합니다.

### Linux — Bash / Zsh

JDK 17을 설치하고 기본 `javac`가 17을 가리키도록 설정한 뒤 다음을 실행합니다.

```bash
javac -version
export JAVA_HOME="$(dirname "$(dirname "$(readlink -f "$(command -v javac)")")")"
export ANDROID_HOME="$HOME/Android/Sdk"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"
```

### macOS — Zsh / Bash

```bash
export JAVA_HOME="$(/usr/libexec/java_home -v 17)"
export ANDROID_HOME="$HOME/Library/Android/sdk"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"
```

macOS·Linux에서는 환경 변수 설정을 사용하는 셸의 초기화 파일에 추가하면 유지할 수 있습니다.
프로젝트에는 개인 JDK·SDK 경로를 고정하지 않습니다.

### SDK 패키지 설치

모든 OS에서 Android Studio의 SDK Manager로 Platform 35, Build Tools 35.0.0,
Platform-Tools와 Command-line Tools를 설치할 수 있습니다. 터미널을 사용한다면 다음과 같습니다.

macOS / Linux:

```bash
sdkmanager --licenses
sdkmanager "platform-tools" "platforms;android-35" "build-tools;35.0.0"
```

Windows PowerShell:

```powershell
sdkmanager.bat --licenses
sdkmanager.bat "platform-tools" "platforms;android-35" "build-tools;35.0.0"
```

라이선스 내용을 확인하고 동의한 뒤 설치합니다.

### `local.properties`

Android Studio가 SDK 경로를 자동 생성합니다. 직접 지정할 경우의 예시입니다.
Windows에서도 Java properties의 역슬래시 이스케이프를 피하도록 `/`를 사용합니다.

| OS | 예시 |
| --- | --- |
| Windows | `sdk.dir=C:/Users/YOUR_NAME/AppData/Local/Android/Sdk` |
| Linux | `sdk.dir=/home/YOUR_NAME/Android/Sdk` |
| macOS | `sdk.dir=/Users/YOUR_NAME/Library/Android/sdk` |

`ANDROID_HOME`과 `sdk.dir`을 함께 설정했다면 같은 SDK를 가리키게 합니다.
`local.properties`는 개인 PC 경로이므로 Git에서 제외합니다.

## 3. 빌드 및 설치

macOS / Linux:

```bash
./gradlew :app:assembleDebug :app:lintDebug
```

Windows PowerShell:

```powershell
.\gradlew.bat :app:assembleDebug :app:lintDebug
```

Windows 명령 프롬프트(CMD):

```bat
gradlew.bat :app:assembleDebug :app:lintDebug
```

| 결과물 | 위치 |
| --- | --- |
| Debug APK | `app/build/outputs/apk/debug/app-debug.apk` |
| Lint HTML 보고서 | `app/build/reports/lint-results-debug.html` |

AVD가 실행된 상태에서 앱을 설치합니다. Android Studio의 Run은 모든 OS에서 사용할 수 있습니다.

macOS / Linux:

```bash
./gradlew :app:installDebug
adb shell am start -n io.github.univjh.pleosapp/.MainActivity
```

Windows PowerShell:

```powershell
.\gradlew.bat :app:installDebug
adb shell am start -n io.github.univjh.pleosapp/.MainActivity
```

이 APK는 디버그 키로 서명된 개발용입니다. 제출용 release 서명은 아직 구성하지 않았습니다.

## 4. Pleos Connect Emulator

차량 API 테스트에는 Vehicle Service가 포함된 Pleos Connect 전용 AVD가 필요합니다.
일반 Android AVD에서 화면이 실행되더라도 차량 서비스의 동작까지 검증할 수는 없습니다.

1. Android Studio의 SDK Manager에서 아래 OS별 Pleos SDK Update Site를 등록합니다.
2. 호스트 아키텍처에 맞는 Pleos Connect Emulator 2.1.0 이미지를 설치합니다.
3. Device Manager에서 Automotive / Pleos Connect 장치를 생성합니다.
4. 공식 안내대로 API 34 이미지, RAM 4GB와 Cold boot를 설정합니다.

| 공식 가이드의 OS 구분 | SDK Update Site |
| --- | --- |
| macOS | `https://nexus-playground.pleos.ai/repository/raw-releases/connect-avd/2.x/crp-arm64-sys-img.xml` |
| Windows / Linux | `https://nexus-playground.pleos.ai/repository/raw-releases/connect-avd/2.x/crp-sys-img.xml` |

호스트 CPU 아키텍처와 Android Emulator 가상화 지원도 확인합니다.
위 구분은 공식 문서의 제공 범위이며, 모든 OS·CPU 조합의 에뮬레이터 지원을 의미하지 않습니다.

앱은 compile·target SDK 35로 빌드하고, Pleos AVD는 API 34를 사용합니다.
앱의 min SDK가 28이므로 해당 AVD에 설치할 수 있습니다.

설치 주소와 상세 절차는 [공식 에뮬레이터 가이드](https://document.pleos.ai/docs/connect/guide/getting-started/application-development/setup-connect-sdk-emulator)를 따릅니다.

Virtual Console로 차량 상태를 변경하려면 AVD 실행 후 다음 명령을 사용합니다.

```bash
adb forward tcp:8088 tcp:8088
```

브라우저에서 <http://127.0.0.1:8088/>를 열어 상태를 바꾸고 앱의 반응을 확인합니다.
자세한 항목은 [공식 Virtual Console 가이드](https://document.pleos.ai/docs/connect/guide/getting-started/application-development/use-vehicle-emulator)를 참조합니다.

## 5. SDK 연동을 시작할 때

현재 `app`에는 아래 SDK 의존성만 추가되어 있습니다.

| 의존성 | 버전 | 다음 구현 |
| --- | --- | --- |
| `ai.pleos.playground:Vehicle` | 2.3.2 | 필요한 도메인 선정, 권한 선언, 초기화·지원 범위 확인·조회·해제 |
| `ai.pleos.playground:NaviHelper` | 3.0.5 | 필요한 API 선정, 권한 선언, 연결 콜백·이벤트·해제 |

- 실제 기능에서 사용하는 최소 권한만 Manifest에 추가합니다.
- Vehicle은 `checkXxxCapabilities()`로 지원 여부와 접근 수준을 확인합니다.
- SDK 초기화 호출 성공, 서비스 연결 성공, 실제 데이터 수신을 구분해 처리합니다.
- 등록한 Listener를 해제하고 사용 종료 시 SDK 리소스를 정리합니다.
- 주행 중 기능 제한은 [주행 안전 가이드](https://document.pleos.ai/docs/pleos-only/vehicle-app-planning-guide/driver-distraction-guideline)를 기준으로 구현합니다.
- 위치·음성·차량 제어 등 추가 기능은 [프로젝트별 사용 가능 상태](https://document.pleos.ai/docs/pleos-only/create-my-project)를 확인한 후 연결합니다.

참고:

- [Vehicle SDK 의존성 설정](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/project-setup)
- [Vehicle 공식 샘플 가이드](https://document.pleos.ai/docs/pleos-only/tutorial/vehicle-sdk-sample-app)
- [NaviHelper SDK 의존성 설정](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/importing-sdk)
- [NaviHelper 공식 샘플](https://github.com/Pleos-Playground/pleos-sample-navi-app)

## 6. 자동 검사

`main` push, PR, 수동 실행 시 GitHub Actions가 다음을 수행합니다.

1. `ubuntu-latest`, `windows-latest`, `macos-latest`에서 각각 JDK 17과 Android SDK 준비
2. Windows는 PowerShell의 `gradlew.bat`, macOS·Linux는 `gradlew`로 `:app:assembleDebug`, `:app:lintDebug` 실행
3. OS별 Debug APK와 Lint 보고서를 서로 다른 Actions artifact로 7일 동안 보관

CI에는 에뮬레이터 기반 UI·차량 서비스 테스트가 아직 포함되어 있지 않습니다.
기능 구현 후 의미 있는 단위·연동 테스트를 추가합니다.

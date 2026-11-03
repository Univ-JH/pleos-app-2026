# UI 개선 및 확인

2026-10-31, Pleos Connect Emulator 2.1.0 / Android 14. 실제 Compose 화면을 설치해 확인했다.

## 변경 내용

- 숲색·밝은 중성 배경을 기본으로, 라이트·다크 모드의 의미별 색상과 글꼴·간격·모서리를 공통 테마로 관리한다.
- 본문 20–22sp, 보조 설명 18sp, 주요 버튼 최소 높이 60dp. 상태·핵심 수치·행동 버튼을 구분한다.
- 넓은 창에는 고정 사이드 메뉴, 좁은 창과 큰 글자 설정에는 상단 메뉴를 제공한다. 카드와 수치는 사용 가능한 너비에 따라 재배치한다.
- 홈은 기록 상태·개선 목표·최근 주행 순서로 정리했다. 리포트는 거리·장면 수·누락 비율과 우선 장면을 강조한다.
- 급출발·급감속·안전벨트를 색상뿐 아니라 아이콘과 이름으로 구별한다. 상세 그래프에는 실제 감지 시점을 점선으로 표시한다.
- 기존 차량 신호·저장·감지 규칙을 사용한다. 주행 중에는 상태만 보이고 상세·학습·설정을 차단한다.

## 확인 결과

| 확인 | 결과 / 근거 |
|---|---|
| 홈·리포트·장면·학습·설정 이동 | 통과 — 저장된 실제 에뮬레이터 SDK 기록 사용. [홈](evidence/ui-home.png), [리포트](evidence/ui-report.png), [장면](evidence/ui-event.png), [학습](evidence/ui-learning.png), [설정](evidence/ui-settings.png) |
| 기본 분할 창 | 2560×1440 화면의 1682×1210 앱 창에서 메뉴·본문·버튼 확인 |
| 세로 / 짧은 가로 창 | 별도 1080×1920, 1920×1080 / 240dpi 디스플레이에서 카드 재배치와 스크롤 확인. [세로](evidence/ui-portrait.png), [가로](evidence/ui-landscape.png) |
| 글자 배율 1.5 | 상단 메뉴·설정 본문·버튼의 줄바꿈과 스크롤 확인. [큰 글자](evidence/ui-large-font.png) |
| 다크 모드 | 홈과 리포트 확인. [다크 홈](evidence/ui-dark.png), [다크 리포트](evidence/ui-dark-report.png) |
| 주행 시 조작 차단 | 리포트를 연 상태에서 Virtual Console의 D·0km/h 입력 후 상태 화면으로 전환, 탐색·설정 버튼 없음 확인. [상태 화면](evidence/ui-driving.png) |
| 빌드·정적 검사 | `assembleDebug lintDebug testDebugUnitTest assembleRelease` 성공. Lint 오류 0, 기존 의존성 업데이트 경고만 남음 |
| 분석기 회귀 테스트 | 18개 통과, 실패·오류 0 |
| 릴리스 서명 | apksigner v2 검증 통과 |

이번 검증은 UI 변경의 에뮬레이터 확인이다. 기존 전체 SDK·저장소 검증과 플랫폼의 미확인 조건은 [P0/P1 결과](RESULTS.md)에 별도로 기록했다. TalkBack 전체 흐름이나 실차 디스플레이에서는 아직 확인하지 않았다.

## 로컬 산출물

- 새 UI 서명 APK: `artifacts/driving-coach-ui-release.apk`
- SHA-256: `2b32c85ac49d8a84fe56999a8d3607a5ac28d00d5324ab37877559508d58bce8`
- 빌드 로그: `artifacts/ui-build.txt`
- 화면·서명·주행 차단 근거: `artifacts/ui/`

기존 `driving-coach-release.apk`와 SDK 시연 영상은 이전 UI의 검증 산출물로 보존한다.

# STEP 9. 3D 아바타 작업 기록

- 브랜치: `feat/avatar-3d`
- 기준 API: 백엔드 v0.4 ([avatar-item-integration.md](avatar-item-integration.md))
- 진행 방식: 단계마다 구현 → 빌드·테스트 → 이 문서에 기록 → 커밋

## 단계 계획

| # | 작업 | 상태 |
| --- | --- | --- |
| 1 | SceneView 의존성 추가 | 완료 |
| 2 | 테스트 화면에 S3 GLB 하나 띄우기 | 예정 |
| 3 | 파츠 7개 겹치기, 카메라·조명·회전 | 예정 |
| 4 | `AvatarView` 컴포넌트로 분리 (슬롯별 교체, 로딩·실패 상태) | 예정 |
| 5 | GLB 다운로드·캐시 저장소 | 예정 |
| 6 | 2D `AvatarPreview`를 3D로 교체 | 예정 |
| 7 | 성별 필드 반영, `BODY` → `SKIN` 정리, 아바타 API 연결 | 예정 |
| 8 | 아바타 꾸미기 화면 (입어보기, 저장, 실패 시 롤백) | 예정 |
| 9 | 성별 선택 화면 (`gender == null`일 때) | 예정 |

## 공통 테스트 방법

| 종류 | 명령 / 방법 | 필요한 것 |
| --- | --- | --- |
| 빌드 + 단위 테스트 | `./gradlew assembleDebug testDebugUnitTest` | 없음 (매 단계 실행) |
| UI 테스트 | `./gradlew connectedDebugAndroidTest` | 켜진 에뮬레이터 또는 연결된 기기 |
| 화면 확인 | Android Studio에서 앱 실행 | 에뮬레이터 또는 기기. 3D 성능은 실제 기기에서 확인 |

단위 테스트 결과는 `app/build/reports/tests/testDebugUnitTest/index.html`에서 볼 수 있다.

---

## 1단계. SceneView 의존성 추가

- 날짜: 2026-11-21
- 커밋 메시지: `chore: add SceneView 4.18.0 dependency`

### 바뀐 파일

| 파일 | 내용 |
| --- | --- |
| `gradle/libs.versions.toml` | `sceneview = "4.18.0"` 버전과 `sceneview` 라이브러리 항목 추가 (버전 고정 이유 주석) |
| `app/build.gradle.kts` | `implementation(libs.sceneview)` 추가 |

### 버전 선택 이유

- 최신은 4.70.0이지만 **4.19.0부터 Kotlin 2.4 stdlib을 요구**한다.
- 프로젝트 Kotlin은 2.2.10이고, 컴파일러는 한 단계 위(2.3) 라이브러리까지만 읽을 수 있다.
- **4.18.0**은 Kotlin 2.3.21을 쓰는 마지막 버전이고, minSdk 24로 앱과 같다.
- Kotlin 자체를 올리는 방법은 공통 설정이라 다른 팀원 코드 전체에 영향을 줘서 택하지 않았다. 나중에 Kotlin을 올리면 SceneView도 같이 올릴 수 있다.

### 함께 바뀐 라이브러리 버전 (Gradle이 자동으로 올림)

| 라이브러리 | 이전 | 이후 |
| --- | --- | --- |
| Compose BOM | 2026.02.01 | 2026.05.01 (UI 1.11.2, Material3 1.4.0) |
| Kotlin stdlib | 2.2.10 | 2.3.21 |
| Lifecycle | 2.10.0 | 2.11.0 |

- 앱 용량: 3D 엔진(Filament) 네이티브 라이브러리가 4개 ABI 합계 약 26MB 늘었다. 실제 기기에는 자기 ABI 것만 설치되어 약 6~7MB다.
- 공통 파일(`libs.versions.toml`)을 바꿨으므로 PR에서 다른 팀원에게 알린다.

### 테스트 결과

| 항목 | 결과 |
| --- | --- |
| `./gradlew assembleDebug testDebugUnitTest` | 성공. 단위 테스트 332개 전부 통과 (실패 0, 오류 0) |
| UI 테스트 | 실행 못 함 (에뮬레이터 연결 없음) |

### 직접 확인하는 방법

1. `./gradlew assembleDebug testDebugUnitTest` → `BUILD SUCCESSFUL`
2. Android Studio에서 Gradle Sync가 오류 없이 끝나는지 확인
3. 앱을 실행해 로그인·홈·상점 등 기존 화면이 전처럼 보이는지 확인 (Compose 버전이 올라갔으므로)

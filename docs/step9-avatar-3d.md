# STEP 9. 3D 아바타 작업 기록

- 브랜치: `feat/avatar-3d`
- 기준 API: 백엔드 v0.4 ([avatar-item-integration.md](avatar-item-integration.md))
- 진행 방식: 단계마다 구현 → 빌드·테스트 → 이 문서에 기록 → 커밋

## 단계 계획

| # | 작업 | 상태 |
| --- | --- | --- |
| 1 | SceneView 의존성 추가 | 완료 |
| 2 | 테스트 화면에 S3 GLB 하나 띄우기 | 완료 |
| 3 | 파츠 7개 겹치기, 카메라·조명·회전 | 완료 |
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
| UI 테스트 (한 클래스만) | `./gradlew connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.dailyquest.app.debug.Avatar3dTestScreenTest"` | 위와 같음 |
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

---

## 2단계. 테스트 화면에 S3 GLB 하나 띄우기

- 날짜: 2026-11-21
- 커밋 메시지: `feat: add debug 3D test screen for avatar GLB`

### 무엇을 만들었나

디버그 빌드에만 들어가는 **"DQ 3D 테스트"** 화면. 런처에 앱 아이콘과 별도로 아이콘이 하나 더 생긴다.

- 위쪽 칩으로 파츠 5개 중 하나를 고르면 S3에서 GLB를 받아 3D로 보여준다: 남 몸체, 남 머리(3.7MB, 가장 무거움), 남 모자, 여 몸체, 여 상의
- 한 손가락 드래그로 회전, 두 손가락으로 확대·축소
- 아래 상태 줄에 파일 이름, 크기, 다운로드 시간, 모델 생성 시간, 실패 시 이유를 표시
- "다시 불러오기"로 같은 파일을 다시 받는다 (디스크 캐시 없음)
- 앱 Navigation(`Routes.kt`, `NavGraph.kt`)과 다른 팀원 화면은 건드리지 않았다. release 빌드에는 들어가지 않는다(병합된 매니페스트로 확인).

### 바뀐 파일 (모두 새 파일)

| 파일 | 내용 |
| --- | --- |
| `app/src/debug/AndroidManifest.xml` | 테스트 화면을 debug 빌드에만 런처 아이콘으로 등록 |
| `app/src/debug/java/com/dailyquest/app/debug/Avatar3dTestActivity.kt` | 테스트 화면. SceneView 하나에 모델을 바꿔 끼운다 |
| `app/src/debug/java/com/dailyquest/app/debug/GlbDownloader.kt` | OkHttp로 GLB를 받고 헤더(`glTF`, 버전 2)를 검사. 실패는 예외 대신 `Result.failure` |
| `app/src/testDebug/java/com/dailyquest/app/debug/GlbDownloaderTest.kt` | 다운로드 단위 테스트 5개 (MockWebServer) |
| `app/src/androidTest/java/com/dailyquest/app/debug/Avatar3dTestScreenTest.kt` | 테스트 화면 UI 테스트 3개 (실제 S3 사용) |

### GLB 파일 사전 점검 (PC에서 5개 내려받아 확인)

| 항목 | 결과 |
| --- | --- |
| 형식 | 모두 glTF 2.0 바이너리(GLB) |
| 확장 기능 | `KHR_materials_specular` 하나뿐. Filament가 지원하고 필수 항목도 아님 |
| 오브젝트 이름 | 파일마다 노드 1개, 이름 = `code` (예: `DQ_Boy_Hat_Cafe`) |
| 좌표 | Y축이 위, 발 사이 바닥이 원점. 남 몸체 높이 약 1.29m, 모자 꼭대기 약 1.44m |
| 뼈대·애니메이션 | **없음.** 지금 파일로는 대기·걷기 애니메이션을 할 수 없다 (P2에서 리깅 필요) |
| 삼각형 수 | 남 몸체 53,712 / 남 머리 122,064 / 남 모자 24,208 / 여 상의 29,456 |

### 진행 중 만난 문제와 해결

| 문제 | 원인 | 해결 |
| --- | --- | --- |
| 다운로드·모델 생성은 성공했는데 3D 화면이 검게만 보임 | SceneView 기본 카메라는 원점(발밑)을 비추는 구조인데, 아바타는 높이 약 1.3m | 카메라 위치를 `(0, 0.75, 2.6)`, 바라보는 점을 `(0, 0.7, 0)`으로 직접 지정 |
| 에뮬레이터에서 모델이 여전히 안 보임 | 기본 렌더링 방식(SurfaceView)은 Compose 화면 뒤에 따로 그려짐 | `SurfaceType.TextureSurface`(TextureView)로 변경 |
| 파츠를 바꾸면 화면이 비거나 모델이 안 보이고 카메라 위치가 매번 달라짐 | 파츠마다 3D 화면 전체를 새로 만들다가 화면 버퍼 할당 실패(`EGL_BAD_ALLOC`) | 3D 화면은 하나만 두고 모델만 교체 |
| 파일 이름이 긴 파츠에서 다시 `EGL_BAD_ALLOC` | 상태 글자가 두 줄로 늘면서 3D 화면 높이가 바뀜 → 화면 버퍼 재생성 | 상태 글자를 두 줄로 고정 |
| UI 테스트가 모두 시간 초과(`ComposeNotIdleException`) | 3D 화면이 매 프레임 계속 그려서 테스트 도구가 "화면이 멈출 때"를 영원히 기다림 | 테스트에서 프레임 시계를 직접 넘기도록 변경 (`mainClock.autoAdvance = false`) |
| UI 테스트 중 앱 강제 종료(Filament `PreconditionPanic`) | 모델 생성이 메인 스레드가 아닌 곳에서 실행됨. 테스트 환경에서는 다운로드 후 IO 스레드에서 이어졌다 | 모델 생성을 `withContext(Dispatchers.Main)`으로 명시 |

**4·5단계에 가져갈 교훈**
- 3D 화면은 하나만 만들고 파츠(노드)만 바꾼다. 화면 크기가 자주 바뀌지 않게 한다.
- Filament 모델 생성·삭제는 반드시 메인 스레드에서 한다.
- 3D 화면이 들어간 화면의 UI 테스트는 프레임 시계를 직접 넘겨야 한다.

### 테스트 결과

| 항목 | 결과 |
| --- | --- |
| `./gradlew assembleDebug assembleRelease testDebugUnitTest` | 성공. 단위 테스트 337개 전부 통과 (새 테스트 5개 포함) |
| 새 UI 테스트 `Avatar3dTestScreenTest` 3개 | 모두 통과 (Pixel_8 에뮬레이터, Android 15) |
| 전체 UI 테스트 `connectedDebugAndroidTest` | **78개 전부 통과** (아래 참고) |
| 에뮬레이터에서 눈으로 확인 | 파츠 5개 모두 표시, 드래그 회전 정상, 다시 불러오기 정상, `EGL_BAD_ALLOC`·앱 강제 종료 0건 |
| 인터넷 끊고 다시 불러오기 | 빨간색 "다운로드 실패: Unable to resolve host …" 표시, 앱은 꺼지지 않음 |
| release 빌드에 테스트 화면 포함 여부 | 포함 안 됨 (병합된 release 매니페스트에 없음) |

- 전체 UI 테스트 첫 실행에서 `MissionFlowTest` 도중 앱이 한 번 강제 종료됐다. 3D 엔진이 아니라 안드로이드 시스템 화면 그리기 스레드(`libhwui` RenderThread)의 충돌이었다. 이후 `MissionFlowTest` 단독 2회, 전체 UI 테스트 1회를 다시 돌렸을 때는 재현되지 않았다. 에뮬레이터의 일시적 문제로 보이지만 1단계의 Compose 버전 상승과 관련 있을 가능성도 남아 있으니, 다시 보이면 기록한다.

**에뮬레이터에서 잰 다운로드 시간** (네트워크에 따라 다름)

| 파츠 | 크기 | 다운로드 | 모델 생성 |
| --- | --- | --- | --- |
| 남 몸체 | 1.38MB | 2.1~4.4초 | 10~87ms |
| 남 머리 | 3.56MB | 1.8~3.2초 | 29~30ms |
| 남 모자 | 0.56MB | 0.3~1.7초 | 5~6ms |
| 여 몸체 | 1.27MB | 1.9초 | 11ms |
| 여 상의 | 0.71MB | 1.9초 | 10~13ms |

모델 생성은 빠르고 대부분 다운로드 시간이다. 한 벌(7개)을 처음 받을 때는 수 초가 걸릴 수 있으므로 5단계의 캐시가 중요하다.

### 직접 확인하는 방법

1. **빌드·단위 테스트:** `./gradlew assembleDebug testDebugUnitTest` → `BUILD SUCCESSFUL`
2. **화면 확인 (에뮬레이터 또는 실제 기기, 인터넷 필요)**
   1. Android Studio에서 앱을 실행한다(▶ Run). 설치되면 기기 앱 목록에 **"DQ 3D 테스트"** 아이콘이 생긴다.
   2. "DQ 3D 테스트"를 연다. Android Studio에서 바로 열려면 Run 설정의 Launch를 "Specified Activity"로 바꾸고 `com.dailyquest.app.debug.Avatar3dTestActivity`를 지정한다.
   3. 몇 초 뒤 남자 몸체가 보이고 아래 상태 줄이 초록색으로 바뀌면 성공이다.
   4. 칩을 하나씩 눌러 5개 파츠가 모두 보이는지 확인한다.
   5. 한 손가락으로 좌우 드래그하면 회전, 두 손가락으로 확대·축소된다.
   6. 기기의 인터넷을 끄고 "다시 불러오기"를 누르면 빨간색 "다운로드 실패"가 떠야 한다.
   7. **실제 기기가 있다면** 회전이 끊기지 않는지(특히 남 머리) 확인한다. 성능은 실제 기기에서만 의미가 있다.
3. **UI 테스트 (에뮬레이터 켜고, 인터넷 필요):**
   `./gradlew connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.dailyquest.app.debug.Avatar3dTestScreenTest"`

### 참고

- 앱 Navigation에 넣지 않은 디버그 전용 화면이라, 다른 팀원은 debug 빌드에서 아이콘이 하나 더 보이는 것 말고는 영향이 없다.
- `GlbDownloader`는 캐시가 없는 테스트용이다. 실제 앱에서 쓸 다운로드·캐시 저장소는 5단계에서 `main` 쪽에 따로 만든다.

---

## 3단계. 파츠 7개 겹쳐 한 벌 입히기

- 날짜: 2026-11-21
- 커밋 메시지: `feat: overlay avatar parts into a full outfit in 3D test screen`

### 무엇을 만들었나

2단계 테스트 화면에 **"한 벌"** 줄을 추가했다. 버튼을 누르면 파츠 6~7개를 **동시에** 받아 같은 원점에 겹쳐 그린다.

| 버튼 | 구성 | 확인하려는 것 |
| --- | --- | --- |
| 남 기본 | 피부 첫 톤 + 기본 눈·머리·상의·하의·신발 (6개, 모자 없음) | 성별 확정 직후 서버가 입히는 상태와 같은 모습 |
| 남 카페+모자 | 카페 테마 한 벌 + 카페 모자 (7개) | 같은 테마 한 벌, 모자와 머리 겹침 |
| 여 기본 | 여자 기본 6개 | 여자 기본 상태 |
| 여 섞어 입기 | 장미 눈, 구리색 포니테일, 베리 카디건, 꽃잎 치마, 베리 메리제인, 레몬 버킷햇 (7개) | 서로 다른 테마를 섞었을 때 메시가 뚫고 나오는지 |

그 밖의 변경:
- **배경:** 검은색 대신 앱 배경색. 3D를 투명하게(`isOpaque = false`) 그려 아래 Compose 배경이 보이게 했다.
- **FPS 표시:** 3D 화면 오른쪽 위에 1초마다 초당 프레임 수를 표시한다. 실제 기기 성능 확인용이다. FPS 글자를 따로 분리해서 값이 바뀌어도 3D 화면은 다시 구성되지 않게 했다.
- **상태 줄:** "남 기본 · 6개 · 8.28MB · 다운로드 9677ms · 모델 생성 686ms"처럼 개수와 합계를 표시한다. 하나라도 실패하면 어느 파일이 실패했는지 보여준다.
- 카메라·조명은 2단계 설정 그대로 썼다. 모자 꼭대기(약 1.44m)까지 화면 안에 들어온다.

### 바뀐 파일

| 파일 | 내용 |
| --- | --- |
| `app/src/debug/java/com/dailyquest/app/debug/TestOutfits.kt` | 새 파일. 테스트용 한 벌 4개, 슬롯 7개, 체형별 S3 주소 만들기 |
| `app/src/debug/java/com/dailyquest/app/debug/Avatar3dTestActivity.kt` | "한 벌" 줄, 동시 다운로드, 여러 모델 겹쳐 그리기, 투명 배경, FPS 표시 |
| `app/src/testDebug/java/com/dailyquest/app/debug/TestOutfitsTest.kt` | 새 파일. 한 벌 목록 단위 테스트 4개 |
| `app/src/androidTest/java/com/dailyquest/app/debug/Avatar3dTestScreenTest.kt` | 한 벌 4개를 모두 불러오는 UI 테스트 1개 추가 |

### 테스트 결과

| 항목 | 결과 |
| --- | --- |
| `./gradlew assembleDebug assembleRelease testDebugUnitTest` | 성공. 단위 테스트 341개 전부 통과 (새 테스트 4개 포함) |
| 전체 UI 테스트 `connectedDebugAndroidTest` | **79개 전부 통과** (새 테스트 `everyOutfitLoadsAllParts` 포함), 앱 강제 종료 0건 |
| 에뮬레이터에서 눈으로 확인 | 한 벌 4개 모두 파츠가 정확히 겹침(위치·크기 조정 없이). 섞어 입기를 옆으로 돌려도 옷이 뚫고 나오는 곳 없음. 카페 모자와 머리도 자연스럽게 겹침 |
| FPS (에뮬레이터) | 54~56 |
| `EGL_BAD_ALLOC` | 0건 |

**에뮬레이터에서 잰 한 벌 불러오기 시간** (동시 다운로드, 네트워크에 따라 다름)

| 한 벌 | 개수 | 합계 크기 | 다운로드 | 모델 생성 |
| --- | --- | --- | --- | --- |
| 남 기본 | 6 | 8.28MB | 9.7초 | 686ms |
| 남 카페+모자 | 7 | 4.87MB | 7.3초 | 113ms |
| 여 기본 | 6 | 6.63MB | 7.5초 | 253ms |
| 여 섞어 입기 | 7 | 5.00MB | 8.4초 | 79ms |

- **처음 한 벌을 받는 데 7~10초가 걸린다.** 에뮬레이터 네트워크가 느린 탓도 있지만, 앱에서 매번 이렇게 받을 수는 없다. 5단계의 기기 캐시가 꼭 필요하다.
- 남 기본이 가장 무겁다(8.28MB). 기본 머리 `DQ_Boy_03_Hair`(3.56MB)와 기본 상의가 크다. 3D 담당에게 Decimate로 줄여 달라고 할 근거다.

### 알게 된 점

- 서버 문서대로 **모든 파츠의 원점이 같아서 위치·크기 조정 없이 겹치기만 하면 된다.** 4단계 `AvatarView`는 슬롯별 모델을 그냥 같은 자리에 놓으면 된다.
- 테스트 화면은 파츠를 바꿀 때 이전 모델을 메모리에서 지우지 않는다(화면을 나가면 정리됨). 실제 앱용 `AvatarView`(4단계)에서는 바뀐 슬롯의 이전 모델을 지워야 한다. 지울 때도 메인 스레드에서, 화면에서 뺀 다음에 지운다.

### 직접 확인하는 방법

1. **빌드·단위 테스트:** `./gradlew assembleDebug testDebugUnitTest` → `BUILD SUCCESSFUL`
2. **화면 확인 (인터넷 필요)**
   1. 앱을 설치하고 **"DQ 3D 테스트"**를 연다.
   2. 두 번째 줄 **"한 벌"**의 버튼 4개를 하나씩 누른다. 몇 초 뒤 옷을 다 입은 캐릭터가 보이면 성공이다.
   3. 드래그로 돌려 옆·뒤에서도 옷과 몸이 맞는지, 뚫고 나오는 곳이 없는지 본다.
   4. 오른쪽 위 **FPS** 숫자를 본다. **실제 휴대폰에서** 돌리는 동안 30 이상이면 무난하고, 그보다 낮으면 파일 용량(폴리곤)을 줄여야 한다.
3. **UI 테스트 (에뮬레이터 켜고, 인터넷 필요):**
   `./gradlew connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.dailyquest.app.debug.Avatar3dTestScreenTest"`

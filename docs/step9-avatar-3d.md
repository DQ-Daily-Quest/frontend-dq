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
| 4 | `AvatarView` 컴포넌트로 분리 (슬롯별 교체, 로딩·실패 상태) | 완료 |
| 5 | GLB 다운로드·캐시 저장소 | 완료 |
| 6 | 내 아바타 데이터 연결 (서버 `gender`·착용 정보 → GLB 주소, 착용 변경 API) | 완료 |
| 7 | 상점 미리보기를 3D로 + 아이템을 누르면 입어보기 | 완료 |
| 8 | 착용 저장 (저장, 실패 시 되돌리기, 모자 외 해제 막기) | 완료 |
| 9 | 성별 선택 화면 (`gender == null`일 때, 기존 "아바타 만들기" 화면 활용) | 완료 |

> 6단계 시작 전(2026-11-22)에 계획을 바꿨다. 원래 6단계 대상인 2D `AvatarPreview`와 Mock `CustomizationStore`가 **어느 화면에서도 쓰이지 않는다**는 것을 확인했기 때문이다.
> 실제로 내 아바타가 크게 보이는 곳은 상점 "아바타" 탭 미리보기와 회원가입 "아바타 만들기" 화면이고, 둘 다 2D 그림이 고정돼 있다.
> 이 화면들에 진짜 내 아바타를 띄우려면 먼저 서버 데이터를 연결해야 해서 순서를 "데이터 → 상점 3D → 착용 저장 → 성별 선택"으로 바꿨다.
> 방·홈의 작은 얼굴(32dp)은 2D로 둔다. `AvatarView`는 하나마다 3D 엔진을 만들어 한 화면에 여러 개 띄우기에는 무겁다.

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

> **2026-11-22 정정:** 처음 기록에는 "Compose UI 1.10→1.11.2, Lifecycle 2.10.0→2.11.0"이라고 적었는데 틀렸다.
> `main`과 실제로 비교해 보니 Compose UI·Runtime·Material3·Lifecycle·Navigation은 **`main`도 이미 같은 버전**이었다
> (다른 라이브러리가 이미 끌어올려 둔 상태였는데, 의존성 트리의 `->` 표시만 보고 잘못 판단했다). 아래가 실제로 바뀐 것이다.
> 자세한 확인 과정은 맨 아래 "버전 변경 영향 확인"에 있다.

| 라이브러리 | `main` | 이후 |
| --- | --- | --- |
| Compose BOM | 2026.02.01 | 2026.05.01 |
| Compose foundation·animation | 1.10.5 | 1.11.2 (`main`은 UI 1.11.2에 foundation 1.10.5가 섞여 있었고, 이제 1.11.2로 맞춰졌다) |
| Compose material-ripple | 1.10.4 | 1.11.2 |
| fragment | 1.5.7 | 1.8.9 |
| Kotlin stdlib | 2.2.10 | 2.3.21 (컴파일러는 2.2.10 그대로) |
| Compose UI·Runtime·Material3, Lifecycle, Navigation, Activity, Core | (변화 없음) | (변화 없음) |

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

디버그 빌드에만 들어가는 **"DQ 3D 테스트"** 화면. 런처 아이콘은 없고 개발용 경로(`adb shell am start -n com.dailyquest.app/.debug.Avatar3dTestActivity` 또는 Run 설정의 Specified Activity)로만 연다.

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
   1. Android Studio에서 앱을 실행한다(▶ Run). 일반 실행은 로그인 화면으로 시작한다.
   2. "DQ 3D 테스트"는 런처에 없으므로 `adb shell am start -n com.dailyquest.app/.debug.Avatar3dTestActivity`로 열거나, Run 설정의 Launch를 "Specified Activity"로 바꾸고 `com.dailyquest.app.debug.Avatar3dTestActivity`를 지정한다.
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

---

## 4단계. `AvatarView` 컴포넌트

- 날짜: 2026-11-22
- 커밋 메시지: `feat: add reusable AvatarView with per-slot model swapping`

### 무엇을 만들었나

실제 화면(꾸미기, 상점 입어보기, 방 멤버 등)에서 쓸 **앱 본체(`main`) 컴포넌트**. 2·3단계 테스트 화면에서 얻은 설정과 교훈을 그대로 담았다.

```kotlin
AvatarView(
    parts = mapOf(AvatarPart.SKIN to skinUrl, AvatarPart.TOP to topUrl, /* ... */),  // 슬롯 → modelUrl
    modifier = Modifier.size(240.dp),
    interactive = true,            // false면 회전 없는 정면 고정
    onStatusChange = { status -> /* Loading / Ready / Failed */ },
)
```

| 동작 | 내용 |
| --- | --- |
| 바뀐 슬롯만 받기 | `parts`가 바뀌면 주소가 달라진 슬롯만 받는다. 같은 주소는 다시 받지 않는다 |
| 깜빡임 없이 교체 | 새 모델이 준비될 때까지 이전 모습을 그대로 보여주고, 다 받은 뒤 한꺼번에 바꾼다 |
| 메모리 정리 | 빠진 슬롯·교체된 슬롯의 이전 모델은 화면에서 뺀 뒤 2프레임 기다렸다가 메인 스레드에서 지운다 |
| 실패 처리 | 받지 못한 슬롯은 이전 모델을 유지하고 "아바타를 불러오지 못했어요 / 다시 시도"를 보여준다. 다시 시도하면 실패한 슬롯만 다시 받는다 |
| 로딩 표시 | 처음에는 가운데 큰 진행 표시, 옷을 바꾸는 중에는 왼쪽 위 작은 표시 |
| 3D 설정 | TextureView, 투명 배경, 원점 그대로 겹치기, 카메라 `(0, 0.75, 2.6)` → `(0, 0.7, 0)` (2·3단계 결과) |

### 구조

| 파일 | 역할 |
| --- | --- |
| `data/avatar/AvatarPart.kt` | 슬롯 7개(`SKIN`~`HAT`). 서버 `avatar` 키(소문자)와 상점 `category`(대문자) 모두 변환 |
| `data/avatar/AvatarModelFetcher.kt` | GLB 가져오기 인터페이스 + 캐시 없는 HTTP 구현(`HttpAvatarModelFetcher`) + GLB 헤더 검사. 2단계 디버그용 `GlbDownloader`를 본체로 옮겼다 |
| `ui/avatar/AvatarPartsLoader.kt` | 어떤 슬롯을 받고 어떤 모델을 지울지 정하는 핵심 로직. **3D 엔진 없이 단위 테스트**할 수 있게 모델 타입을 일반화(`<T>`) |
| `ui/avatar/AvatarView.kt` | 위 로직 + SceneView. Filament 모델 생성·삭제를 메인 스레드로 고정 |

- 다운로드 방식은 `AvatarModelFetcher` 인터페이스 뒤에 숨겼다. 5단계에서 캐시 구현을 만들어 기본값만 바꾸면 된다.
- 테스트 화면(`Avatar3dTestActivity`)도 이제 `AvatarView`로 그린다. 새 **"바꾸기"** 줄(모자 바꾸기·상의 바꾸기·모자 벗기·고정 보기)과, 실제로 받은 파일 수를 세는 상태 줄로 위 동작을 눈으로 확인할 수 있다. 2단계의 "다시 불러오기" 버튼은 없앴다(실패 시 `AvatarView`의 "다시 시도"가 대신한다).
- `AvatarPart`는 기존 Mock(`CustomizationStore`의 `AvatarSlot`, `BODY` 포함)과 별개다. 7단계에서 Mock을 서버 기준으로 정리할 때 하나로 합친다.

### 바뀐 파일

| 파일 | 변경 |
| --- | --- |
| `app/src/main/java/com/dailyquest/app/data/avatar/AvatarPart.kt` | 새 파일 |
| `app/src/main/java/com/dailyquest/app/data/avatar/AvatarModelFetcher.kt` | 새 파일 |
| `app/src/main/java/com/dailyquest/app/ui/avatar/AvatarPartsLoader.kt` | 새 파일 |
| `app/src/main/java/com/dailyquest/app/ui/avatar/AvatarView.kt` | 새 파일 |
| `app/src/debug/java/com/dailyquest/app/debug/Avatar3dTestActivity.kt` | `AvatarView` 사용, "바꾸기" 줄, 받은 파일 수 표시 |
| `app/src/debug/java/com/dailyquest/app/debug/TestOutfits.kt` | `AvatarPart` 사용, 파츠 하나도 같은 형식으로, 모자·상의 교체 후보 |
| `app/src/debug/java/com/dailyquest/app/debug/GlbDownloader.kt` | **삭제** (본체 `HttpAvatarModelFetcher`로 이동) |
| `app/src/testDebug/java/com/dailyquest/app/debug/GlbDownloaderTest.kt` | **삭제** (본체 테스트로 이동) |
| `app/src/test/java/com/dailyquest/app/ui/avatar/AvatarPartsLoaderTest.kt` | 새 파일. 단위 테스트 12개 |
| `app/src/test/java/com/dailyquest/app/data/avatar/HttpAvatarModelFetcherTest.kt` | 새 파일. 단위 테스트 7개 (옮긴 5개 + 인증 헤더 안 보냄 + 슬롯 키 변환) |
| `app/src/testDebug/java/com/dailyquest/app/debug/TestOutfitsTest.kt` | 새 목록에 맞게 수정 (4개) |
| `app/src/androidTest/java/com/dailyquest/app/ui/avatar/AvatarViewTest.kt` | 새 파일. 인터넷 없이 도는 실패·다시 시도 UI 테스트 2개 |
| `app/src/androidTest/java/com/dailyquest/app/debug/Avatar3dTestScreenTest.kt` | 교체 동작 UI 테스트로 재작성 (6개) |

### `AvatarPartsLoader` 단위 테스트가 확인하는 것

- 처음에는 모든 슬롯을 받는다 / 슬롯 하나를 바꾸면 그 파일만 받고 이전 모델을 지운다
- 모자를 쓰면 모자만 받는다 / 모자를 벗으면 아무것도 받지 않고 모자 모델만 지운다
- 같은 구성이면 아무것도 받거나 지우지 않는다
- 받지 못한 슬롯은 이전 모델을 유지하고 실패로 알린다 / 처음부터 일부 실패하면 나머지는 보인다
- 다시 시도하면 실패한 슬롯만 받는다 / 모델 생성이 실패해도 실패로 알린다
- 여러 슬롯을 바꿀 때 모두 받은 뒤 한꺼번에 바꾼다(중간에 일부만 바뀐 모습이 보이지 않는다)

### 진행 중 만난 문제와 해결

| 문제 | 원인 | 해결 |
| --- | --- | --- |
| SceneView의 `ModelNode`가 화면에서 빠져도 모델 메모리가 남음 | 소스 확인 결과 노드만 지우고 모델(asset)은 지우지 않는다 | `AvatarView`가 교체·제거된 모델을 직접 `destroyModel` |
| 같은 결과 상태가 연달아 오면 `onStatusChange`가 불리지 않아 테스트 화면이 "불러오는 중"에 멈출 수 있음 | Compose `snapshotFlow`는 값이 같으면 알리지 않는다 | `AvatarPartsLoader.completedSyncs`를 두어 불러오기가 끝날 때마다 반드시 알림 |
| UI 테스트가 10분 넘게 멈춤 | 화면 밖에 걸친 칩을 `performScrollTo()`로 누르려 했는데, 스크롤 애니메이션이 프레임 시계를 기다리고 테스트는 시계를 멈춰 둬서 서로 기다림 | 클릭 동작을 직접 실행(`performSemanticsAction(OnClick)`) |
| 위 테스트가 멈춘 동안 화면에 모델이 안 보임 | 버그가 아니었다. 테스트가 시계를 멈춰 화면이 갱신되지 않았을 뿐. 직접 같은 순서로 눌러 정상 표시를 확인 | — |

### 테스트 결과

| 항목 | 결과 |
| --- | --- |
| `./gradlew assembleDebug assembleRelease testDebugUnitTest` | 성공. 단위 테스트 355개 전부 통과 (새 `AvatarPartsLoaderTest` 12개, `HttpAvatarModelFetcherTest` 7개 포함) |
| 새 UI 테스트 (`Avatar3dTestScreenTest` 6개 + `AvatarViewTest` 2개) | 8개 모두 통과 |
| 전체 UI 테스트 `connectedDebugAndroidTest` | **83개 전부 통과** (다른 팀원 화면 포함), 앱 강제 종료 0건 |
| 에뮬레이터에서 눈으로 확인 | 모자 바꾸기 → 모자 파일 1개(0.52MB)만 받음. 고정 보기에서 드래그해도 돌아가지 않음. 남 모자 → 여 몸체 전환 정상 |
| 인터넷 끊고 한 벌 바꾸기 → 다시 켜고 "다시 시도" | 끊겼을 때 "아바타를 불러오지 못했어요 / 다시 시도"와 실패 슬롯 6개 표시, 앱은 꺼지지 않음. 다시 시도하자 6개를 모두 받아 정상 표시 |
| 메모리 (모자 바꾸기 반복) | 기준 229.3MB → 16번 더 바꿈 227.2MB → 32번 더 바꿈 227.5MB (TOTAL PSS). **늘지 않음 = 교체된 모델이 지워진다** |

- **알아 둘 동작:** 받지 못한 슬롯은 이전 모델이 남는다(설계대로). 테스트 화면에서 남자 몸체를 띄운 채 인터넷을 끊고 "여 기본"을 누르면 남자 몸체가 그대로 보인다. 실제 앱에서는 성별이 바뀌지 않으므로 같은 체형 안에서 이전 옷이 남는 정도다.

### 직접 확인하는 방법

1. **빌드·단위 테스트:** `./gradlew assembleDebug testDebugUnitTest` → `BUILD SUCCESSFUL`
2. **화면 확인 (인터넷 필요)** — "DQ 3D 테스트"를 연다.
   1. "한 벌 → 남 기본"을 누르고 준비될 때까지 기다린다.
   2. "바꾸기 → 모자 바꾸기"를 누른다. 상태 줄이 **"파츠 7개 · 받은 파일 1개"**면 모자만 받은 것이다. 여러 번 누르면 모자가 차례로 바뀐다.
   3. "상의 바꾸기"를 누르면 상의만 바뀌고 **"받은 파일 1개"**가 나온다.
   4. "모자 벗기"를 누르면 **"파츠 6개 · 받은 파일 0개"**가 나온다.
   5. "고정 보기"를 켜고 드래그해 본다. 돌아가지 않으면 정상이다.
   6. 기기 인터넷을 끄고 다른 한 벌을 누르면 캐릭터 아래에 **"아바타를 불러오지 못했어요 / 다시 시도"**가 나온다. 인터넷을 켜고 "다시 시도"를 누르면 나머지가 채워진다.
3. **UI 테스트 (에뮬레이터 켜고, 인터넷 필요):**
   `./gradlew connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.dailyquest.app.debug.Avatar3dTestScreenTest,com.dailyquest.app.ui.avatar.AvatarViewTest"`

---

## 5단계. GLB 디스크 캐시

- 날짜: 2026-11-22
- 커밋 메시지: `feat: cache avatar GLB files on disk`

### 무엇을 만들었나

한 번 받은 GLB를 기기 캐시 폴더(`cache/avatar_models/`)에 저장하고, 같은 주소는 다시 받지 않는 `CachingAvatarModelFetcher`. `AvatarView`의 기본 다운로더를 이것으로 바꿨으므로 **화면 쪽 코드는 그대로** 캐시가 적용된다.

| 규칙 | 내용 | 이유 |
| --- | --- | --- |
| 유효기간 없음 | 저장된 파일이 있으면 서버에 묻지 않고 바로 쓴다 | 백엔드 v0.4: 파일이 바뀌면 주소도 바뀐다(`Cache-Control: immutable`) |
| 파일 이름 | 주소의 SHA-256 + `.glb` | 주소에 `?v=2` 같은 쿼리나 특수문자가 있어도 안전 |
| 용량 상한 100MB | 넘으면 가장 오래 안 쓴 파일부터 지운다 | 남자 파츠 57개 전체가 약 44MB. 한 체형 전체가 들어간다 |
| 동시 요청 합치기 | 같은 주소를 동시에 요청하면 한 번만 받는다 | 여러 화면이 같은 몸체를 그릴 때 |
| 깨진 파일 | 저장된 파일이 GLB가 아니면 지우고 다시 받는다. 저장은 임시 파일 → 이름 바꾸기 | 저장 중 앱이 꺼져도 반쯤 쓴 파일이 남지 않는다 |
| 저장 실패 | 저장 공간 부족 등으로 저장하지 못해도 받은 내용은 그대로 쓴다 | 캐시는 있으면 좋은 것이지 필수가 아니다 |
| 받기 실패 | 아무것도 저장하지 않고 실패를 돌려준다. 다음에 다시 받으러 간다 | |
| 로그아웃 | 지우지 않는다 | 공개 에셋이라 사용자와 관계없다 |
| 폴더 위치 | 앱 `cacheDir` | 시스템이 저장 공간이 부족할 때 지울 수 있다. 지워지면 다시 받으면 된다 |

### 바뀐 파일

| 파일 | 변경 |
| --- | --- |
| `app/src/main/java/com/dailyquest/app/data/avatar/CachingAvatarModelFetcher.kt` | 새 파일. 캐시 다운로더 + 앱 공용 인스턴스(`AvatarModels`) |
| `app/src/main/java/com/dailyquest/app/ui/avatar/AvatarView.kt` | 기본 다운로더를 `AvatarModels.fetcher(context)`로 변경 |
| `app/src/test/java/com/dailyquest/app/data/avatar/CachingAvatarModelFetcherTest.kt` | 새 파일. 단위 테스트 10개 |
| `app/src/debug/java/com/dailyquest/app/debug/Avatar3dTestActivity.kt` | 앱과 같은 캐시 폴더 사용, 상태 줄에 "받은 파일 / 캐시" 구분, 왼쪽 위 캐시 크기 표시, "캐시 비우기" 버튼 |
| `app/src/androidTest/java/com/dailyquest/app/debug/Avatar3dTestScreenTest.kt` | 테스트마다 화면이 뜨기 전에 캐시를 비우는 규칙, 캐시 UI 테스트 2개 추가 |

### 캐시 단위 테스트가 확인하는 것

- 처음에는 받고 두 번째부터는 저장된 파일을 쓴다 / **새 인스턴스(앱 재시작)에서도** 저장된 파일을 쓴다
- 주소가 다르면(파일을 고쳐 주소가 바뀐 경우, 쿼리가 붙은 경우) 따로 받는다
- 저장된 파일이 깨졌으면 지우고 다시 받는다 / 받지 못하면 저장하지 않고, 다음에 다시 받는다
- 같은 주소 동시 요청 5개 → 네트워크 1번
- 용량을 넘으면 가장 오래 안 쓴 파일부터 지운다(최근에 쓴 파일은 남는다)
- 임시 파일이 남지 않는다 / 저장 폴더를 만들 수 없어도 받은 내용은 돌려준다 / 비우면 다시 받는다

### 테스트 결과

| 항목 | 결과 |
| --- | --- |
| `./gradlew assembleDebug assembleRelease testDebugUnitTest` | 성공. 단위 테스트 365개 전부 통과 (새 캐시 테스트 10개 포함) |
| 3D UI 테스트 (`Avatar3dTestScreenTest` 8개 + `AvatarViewTest` 2개) | 10개 모두 통과 (새 `reselectedOutfitComesFromCache`, `clearingCacheDownloadsAgain` 포함) |
| 전체 UI 테스트 `connectedDebugAndroidTest` | **85개 전부 통과** (다른 팀원 화면 포함), 앱 강제 종료 0건 |

**에뮬레이터에서 직접 확인** (캐시를 비우고 시작)

| 순서 | 상태 줄 | 의미 |
| --- | --- | --- |
| 1. 첫 실행, 남 몸체 | 받은 파일 1개 1.38MB · 캐시 0개 | 처음이라 받음 |
| 2. 남 기본 | 받은 파일 5개 6.90MB · 캐시 0개 · **7,364ms** | 몸체는 이미 보이므로 나머지 5개만 받음 |
| 3. 앱 강제 종료 후 다시 실행, 남 몸체 | 받은 파일 0개 · 캐시 1개 | **앱을 다시 켜도 캐시가 남아 있다** |
| 4. 남 기본 | 받은 파일 0개 · 캐시 5개 · **177ms** | 7.4초 → 0.2초 |
| 5. 인터넷 끄고 앱 재시작, 남 기본 | 받은 파일 0개 · 캐시 5개 · 223ms, 캐릭터 정상 표시 | **인터넷 없이도 받아 둔 옷은 보인다** |
| 6. 인터넷 끈 채 여 기본(받은 적 없음) | 실패: skin, eyes, hair, top, bottom, shoes | 받은 적 없는 파일은 당연히 실패, 앱은 꺼지지 않음 |

- 저장 폴더에는 SHA-256 이름의 `.glb` 6개(8.3MB)만 있었고 임시 파일은 없었다.
- 3번의 시간(3.4초)은 앱 시작부터 잰 값이라 3D 엔진 준비 시간이 포함된다. 캐시에서 읽는 시간은 4·5번(0.2초)이 실제에 가깝다.

### 직접 확인하는 방법

1. **빌드·단위 테스트:** `./gradlew assembleDebug testDebugUnitTest` → `BUILD SUCCESSFUL`
2. **화면 확인** — "DQ 3D 테스트"를 연다.
   1. "캐시 비우기"를 누른다. 왼쪽 위가 "캐시 0개"가 된다.
   2. "한 벌 → 남 기본"을 누른다. 상태 줄 둘째 줄에 **"받은 파일 5개 · 캐시 0개"**처럼 받은 파일이 나온다.
   3. "파츠 → 남 모자"를 누른 뒤 다시 "남 기본"을 누른다. **"받은 파일 0개 · 캐시 6개"**가 나오고 거의 바로 뜬다.
   4. 앱을 완전히 종료(최근 앱 화면에서 밀어서 닫기)하고 다시 열어 "남 기본"을 누른다. 여전히 **"받은 파일 0개"**다.
   5. 기기 인터넷을 끄고 앱을 다시 열어 "남 기본"을 누른다. 인터넷 없이도 캐릭터가 보인다.
3. **UI 테스트 (에뮬레이터 켜고, 인터넷 필요):**
   `./gradlew connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.dailyquest.app.debug.Avatar3dTestScreenTest,com.dailyquest.app.ui.avatar.AvatarViewTest"`

---

## 6단계. 내 아바타 데이터 연결

- 날짜: 2026-11-22
- 커밋 메시지: `feat: load and equip my avatar from server`

### 무엇을 만들었나

서버에서 **내 체형과 입고 있는 옷**을 받아 `AvatarView`가 바로 쓸 수 있는 "슬롯 → GLB 주소"로 바꾸고, 착용을 바꾸는 저장소 `MyAvatarRepository`. 화면은 아직 바뀌지 않는다(7단계에서 상점에 붙인다).

| 기능 | 서버 요청 | 내용 |
| --- | --- | --- |
| `load()` | `GET /users/me` + `GET /users/me/items?type=AVATAR` (동시에) | `/users/me`의 `avatar`에는 아이템 **ID만** 있다. 착용 아이템은 모두 보유 아이템이므로 보유 목록에서 GLB 주소를 찾는다 |
| `equip(changes)` | `PATCH /users/me` `{"avatar":{"hat":50}}` | 바꿀 슬롯만 보낸다. `null`이면 해제(`{"avatar":{"hat":null}}`). 응답의 내 정보(코인 등)로 세션도 갱신 |
| `MyAvatar` | — | `gender`(MALE/FEMALE/null), 슬롯별 착용 아이템, `modelParts`(→ `AvatarView`), `needsGender`(→ 9단계 성별 선택) |
| `AvatarErrorMessages` | — | `ITEM_NOT_OWNED`, `ITEM_GENDER_MISMATCH`, `ITEM_NOT_FOUND`, `GENDER_ALREADY_SET`를 한국어 안내로 |

**설계에서 신경 쓴 점**
- 착용 저장(PATCH)은 성공했는데 그 뒤 보유 목록 조회만 실패하면, 실패로 돌려주지 않고 **마지막으로 받은 보유 목록**으로 대신한다. 실패로 알리면 화면이 이미 저장된 착용을 되돌려 버리기 때문이다. 기억한 목록은 같은 계정일 때만 쓴다.
- 서버가 `gender`를 보내지 않거나(구버전) 모르는 값이면 "성별 없음"으로 본다.
- 보유 목록에 없는 착용 ID(서버 데이터 불일치)는 그 슬롯만 건너뛴다.
- GLB 주소는 기존 `ApiConfig.resolveFileUrl`로 정리한다(지금은 S3 전체 주소라 그대로, 예전 `/files/...` 상대 경로도 처리).

### 바뀐 파일

| 파일 | 변경 | 비고 |
| --- | --- | --- |
| `app/src/main/java/com/dailyquest/app/data/avatar/MyAvatarRepository.kt` | 새 파일 | |
| `app/src/main/java/com/dailyquest/app/data/remote/dto/UserDtos.kt` | `UserMeResponse.gender` 필드, `UpdateAvatarRequest` 추가 | **팀원 파일, 추가만** |
| `app/src/main/java/com/dailyquest/app/data/remote/DqApi.kt` | `updateAvatar` (PATCH /users/me) 추가 | **팀원 파일, 추가만** |
| `app/src/main/java/com/dailyquest/app/data/remote/ApiError.kt` | 오류 코드 3개 추가 | **팀원 파일, 추가만** |
| `app/src/main/java/com/dailyquest/app/data/auth/AppContainer.kt` | `myAvatarRepository` 한 줄 | **팀원 파일, 추가만** |
| `app/src/androidTest/java/com/dailyquest/app/TestDQApplication.kt` | 가짜 API에 `updateAvatar` 추가 | **팀원 파일.** 안 넣으면 UI 테스트가 컴파일되지 않는다 |
| `app/src/test/java/com/dailyquest/app/data/avatar/MyAvatarRepositoryTest.kt` | 새 파일. 단위 테스트 13개 | |
| `app/src/main/java/com/dailyquest/app/ui/avatar/AvatarView.kt` | **앱 강제 종료 수정** (아래 "진행 중 만난 문제") | |
| `app/src/debug/java/com/dailyquest/app/debug/Avatar3dTestActivity.kt` | 상태 줄 시간을 "표시까지"(받기 + 모델 생성 + 텍스처 준비)로 변경, 개수 세기를 여러 스레드에서 안전하게 | |

- 기존 동작은 바뀌지 않는다. 기존 닉네임 변경(`updateMe`)과 사용자 정보 파싱은 그대로다.
- **알아 둘 점:** `DqApi`에 함수를 추가하면 UI 테스트의 가짜 API(`TestDQApplication.FakeApi`)에도 같이 추가해야 한다. 이번에 빠뜨려서 UI 테스트 컴파일이 한 번 깨졌다(`compileDebugAndroidTestKotlin`으로 확인 후 수정).

### 진행 중 만난 문제: 3D 엔진 강제 종료 (4단계부터 있던 버그)

이번 단계 전체 UI 테스트에서 `Avatar3dTestScreenTest.everyPartAndOutfitLoads` 도중 앱이 강제 종료됐다. 4·5단계에서는 통과했던 테스트다.

| 항목 | 내용 |
| --- | --- |
| 증상 | `PreconditionPanic in setImage:554 — Texture is not created yet. It may be in the process of asynchronous loading`. 메인 스레드, `ModelLoader.createModelInstance` → `ResourceLoader.asyncBeginLoad` |
| 원인 | SceneView의 `createModelInstance()`는 모델을 만들자마자 **원본 데이터를 지운다**. 그런데 텍스처(옷 그림)는 그 뒤에도 백그라운드에서 만든다. Filament는 텍스처를 원본 데이터의 **메모리 주소**로 구분해 재사용하므로, 다음 모델의 원본 데이터가 방금 비워진 같은 주소에 들어오면 "아직 만드는 중인 텍스처"를 다시 쓰려다 앱이 꺼진다 |
| 가끔만 나는 이유 | 메모리 주소가 우연히 겹칠 때만 난다. 그래서 4·5단계에서는 통과했다. 이번 단계와는 관계없이 4단계부터 숨어 있던 버그다 |
| 해결 | `AvatarView`가 모델을 만들 때 원본 데이터를 **텍스처를 다 만들 때까지 들고 있다가** 지운다(`createModelSafely`: `createModel(releaseSourceData = false)` → 진행률 1.0까지 대기(최대 5초) → `releaseSourceData()`). 주소가 겹치지 않고, 모델도 하나씩 차례로 만들어진다 |
| 확인 | 3D UI 테스트(10개)를 **3번 연속** 돌려 30개 모두 통과, 강제 종료 0건 |

**대가: 화면에 다 뜨는 시간이 조금 늘었다** (캐시에서 꺼낼 때, 에뮬레이터)

| | 남 기본 | 여 기본 |
| --- | --- | --- |
| 예전 방식 | 0.69초 | 0.50초 |
| 고친 방식 | 1.41초 | 0.65초 |

예전 방식의 "준비됨"은 텍스처가 아직 덜 입혀진 상태일 수 있었고, 고친 방식은 옷 그림까지 다 입혀진 뒤에 "준비됨"이 뜬다. 앱이 꺼지지 않는 것이 더 중요해서 고친 방식으로 정했다. 텍스처 용량을 줄이면(3D 담당 요청) 이 시간도 줄어든다.

**테스트 화면 개수 세기 버그 (테스트 화면만 해당)**

| 항목 | 내용 |
| --- | --- |
| 증상 | `reselectedOutfitComesFromCache`가 "캐시 6개"를 기다렸는데 "캐시 5개"로 실패 |
| 원인 | 테스트 화면의 `CountingFetcher`가 파츠 6개를 **동시에(여러 스레드에서)** 셀 때 `count++`가 겹쳐 하나가 씹혔다. 캐시를 붙인 뒤 파일을 아주 빨리 꺼내게 되면서 드러났다 |
| 해결 | 세는 부분을 `synchronized`로 한 번에 하나씩만 실행. 앱 본체 코드와는 관계없다 |

**처음 받을 때(캐시 없음) 화면에 다 뜨기까지:** 남 기본 12.8초, 여 기본 8.2초, 모자 하나 바꾸기 2.7초 (에뮬레이터 네트워크 기준). 대부분 다운로드 시간이다.

### 단위 테스트가 확인하는 것 (MockWebServer, backend v0.4 응답 형식)

- 불러오면 슬롯별 ID를 GLB 주소로 바꾼다(남자 기본 6개, 모자 없음) / 보유 목록은 `type=AVATAR`로만 요청한다
- 성별 없는 계정은 `needsGender`이고 착용이 비어 있다 / `gender`가 없는 구버전 응답도 성별 없음으로 본다
- 두 요청 중 하나라도 실패하면 불러오기 실패
- 모자를 쓰면 `{"avatar":{"hat":50}}`만 보낸다 / 벗으면 `{"avatar":{"hat":null}}`로 `null`이 실제로 나간다
- 착용 저장 응답으로 세션의 코인이 갱신된다
- 저장은 됐는데 보유 목록 조회만 실패하면 마지막 목록으로 대신해 성공을 돌려준다
- `ITEM_GENDER_MISMATCH`(409), `ITEM_NOT_OWNED`(403) 안내 문구
- 보유 목록에 없는 착용 ID는 건너뛰고, 상대 경로는 전체 주소로 바꾼다 / 모르는 성별 값은 null

### 테스트 결과

| 항목 | 결과 |
| --- | --- |
| `./gradlew assembleDebug assembleRelease testDebugUnitTest` | 성공. 단위 테스트 378개 전부 통과 (새 `MyAvatarRepositoryTest` 13개 포함) |
| `./gradlew compileDebugAndroidTestKotlin` | 처음엔 실패(가짜 API에 새 함수 없음) → 추가 후 성공 |
| 3D UI 테스트 3번 연속 (강제 종료 수정 후) | 30개 모두 통과, 강제 종료 0건 |
| 전체 UI 테스트 `connectedDebugAndroidTest` (첫 실행) | 1개 실패 — 위 3D 엔진 강제 종료 |
| 전체 UI 테스트 (강제 종료 수정 후) | 1개 실패 — 테스트 화면의 개수 세기 버그(아래) |
| 3D UI 테스트 2번 연속 (개수 세기 수정 후) | 20개 모두 통과 |
| 전체 UI 테스트 (최종) | **85개 전부 통과** (다른 팀원 화면 포함), 앱 강제 종료 0건 |
| 실제 서버로 확인 | 하지 않음. 로그인 계정이 필요해서 서버 문서(api.md v0.4)와 백엔드 코드의 응답 형식으로 만든 가짜 서버로만 확인했다. 7단계에서 화면에 붙이면 실제 계정으로 확인한다 |

### 직접 확인하는 방법

이번 단계는 화면 변화가 없어서 테스트로 확인한다.

1. `./gradlew testDebugUnitTest` → `BUILD SUCCESSFUL`
2. 이 클래스만 보려면: `./gradlew testDebugUnitTest --tests "com.dailyquest.app.data.avatar.MyAvatarRepositoryTest"`
3. 결과 보고서: `app/build/reports/tests/testDebugUnitTest/index.html` → `com.dailyquest.app.data.avatar` → `MyAvatarRepositoryTest` 13개 통과
4. UI 테스트가 컴파일되는지: `./gradlew compileDebugAndroidTestKotlin`

---

## 7단계. 상점에 3D 미리보기 + 입어보기

- 날짜: 2026-11-22
- 커밋 메시지: `feat: add 3D avatar preview and try-on to shop`

### 무엇을 바꿨나

상점(`AvatarShopContent`, 하단 탭 "쇼핑")의 **아바타 탭**에 내 아바타 3D 미리보기를 넣고, 아이템을 누르면 바로 입어 보게 했다. (기존 상점에는 미리보기가 없었다. 2D 아바타 그림이 있던 화면은 쓰이지 않는 예전 디자인 `B5ShopScreen`이다.)

| 동작 | 바뀌기 전 | 바뀐 뒤 (아바타 탭) |
| --- | --- | --- |
| 위쪽 | 탭·카테고리·필터만 | + 내 아바타 3D 미리보기(높이 200dp 고정) |
| 아이템 누르기 | 바로 상세·구매 창 | **미리보기에 입혀 보기** + 카드 초록 테두리. 같은 아이템을 다시 누르면 벗기 |
| 구매 | 상세 창의 구매 버튼 | 아래 **"N 코인으로 구매"** 버튼 → 기존 상세·구매 창(구매 확인·코인 부족 처리 그대로) |
| 보유 중인 아이템 | 상세 창 | 아래 버튼이 "보유 중인 아이템이에요"(비활성). 착용은 8단계 |
| 되돌리기 | — | 미리보기 아래 "입어보는 중: 이름 · **원래대로**" |
| 여러 슬롯 | — | 상의와 모자처럼 다른 슬롯은 함께 입어 볼 수 있고, 같은 슬롯은 바꿔 입는다 |

- **가구 탭은 그대로**다(미리보기 없음, 누르면 바로 상세 창).
- 입어보기는 서버에 저장하지 않는다. 상점을 나가면 사라진다.
- 미리보기 상태 안내: 불러오는 중(진행 표시) / 실패("다시 시도") / **성별을 정하지 않은 계정("캐릭터를 먼저 만들어 주세요", 9단계에서 성별 선택으로 연결)**.
- 3D 모델 주소가 없는 아이템도 입어보기 표시·구매 버튼은 동작하고, 모습만 바뀌지 않는다.

### UI 테스트에서 3D 끄기

3D 화면은 매 프레임 계속 그려서, "화면이 멈출 때까지 기다리는" 기존 Compose UI 테스트(팀원 상점 테스트 등)가 시간 초과로 실패한다(2단계에서 겪은 문제). 그래서 `AppContainer`에 `avatar3dEnabled` 스위치를 두고 **UI 테스트 앱(`TestDQApplication`)에서만 끈다.** 꺼져 있으면 같은 자리에 빈 상자("3D 미리보기 (테스트 환경)")를 그린다. 실제 앱은 항상 켜져 있다.

### 바뀐 파일

| 파일 | 변경 | 비고 |
| --- | --- | --- |
| `app/src/main/java/com/dailyquest/app/ui/avatar/MyAvatarViewModel.kt` | 새 파일. 내 아바타 불러오기, 입어보기 상태, 계정 전환 처리 | |
| `app/src/main/java/com/dailyquest/app/ui/avatar/MyAvatarPreview.kt` | 새 파일. 미리보기 상자(3D/빈 상자, 안내, "원래대로") | |
| `app/src/main/java/com/dailyquest/app/ui/screens/AvatarShopContent.kt` | 미리보기, 카드 누르면 입어보기, 선택 테두리, 아래 구매 버튼 | **팀원 파일.** 가구 탭 흐름·구매 창·코인 처리는 그대로 |
| `app/src/main/java/com/dailyquest/app/data/auth/AppContainer.kt` | `avatar3dEnabled` 옵션(기본 true) | **팀원 파일, 추가만** |
| `app/src/androidTest/java/com/dailyquest/app/TestDQApplication.kt` | `avatar3dEnabled = false` | **팀원 파일, 2줄** |
| `app/src/androidTest/java/com/dailyquest/app/ShopFlowTest.kt` | `openItem()`이 카드를 누른 뒤 아래 구매 버튼을 누르도록 | **팀원 테스트.** 흐름이 바뀐 만큼만 수정, 검사 내용은 그대로 |
| `app/src/test/java/com/dailyquest/app/ui/avatar/MyAvatarViewModelTest.kt` | 새 파일. 단위 테스트 11개 | |
| `app/src/androidTest/java/com/dailyquest/app/ShopTryOnTest.kt` | 새 파일. 입어보기 UI 테스트 7개 | |

### 테스트가 확인하는 것

**단위 (`MyAvatarViewModelTest` 11개)**
- 로그인하면 불러오고, 로그인 전에는 불러오지 않는다 / 실패는 안내 문구, 다시 시도로 회복
- 입어 보면 그 슬롯만 덮어쓴다 / 없던 모자 슬롯은 늘어난다 / 같은 아이템을 다시 누르면 벗는다 / 같은 슬롯 다른 아이템은 바꿔 입는다 / 원래대로
- 3D 모델이 없는 아이템은 이름만 보이고 모습은 그대로
- 다른 계정으로 바뀌면 이전 모습·입어보기를 지우고 다시 불러온다 / 불러오는 사이 계정이 바뀌면 이전 응답을 버린다

**UI (`ShopTryOnTest` 7개, 가짜 서버)**
- 아바타 탭에 미리보기가 있다(테스트 계정은 성별이 없어 "캐릭터를 먼저 만들어 주세요")
- 카드를 누르면 "입어보는 중: 민트 상의"와 "120 코인으로 구매"가 나오고 상세 창은 뜨지 않는다
- 구매 버튼을 누르면 기존 상세·구매 창이 뜬다(구매 요청은 보내지 않음)
- 같은 카드를 다시 누르면 벗는다 / 상의+모자를 입어 본 뒤 "원래대로"
- 보유 아이템은 "보유 중인 아이템이에요"(비활성)
- 가구 탭은 미리보기가 없고 누르면 바로 상세 창

### 테스트 결과

| 항목 | 결과 |
| --- | --- |
| `./gradlew assembleDebug assembleRelease testDebugUnitTest` | 성공. 단위 테스트 389개 전부 통과 (새 `MyAvatarViewModelTest` 11개 포함) |
| 상점 UI 테스트 (`ShopFlowTest` 6개 + `ShopTryOnTest` 7개) | 13개 모두 통과 (팀원 테스트는 `openItem()`만 고침) |
| 전체 UI 테스트 `connectedDebugAndroidTest` | **92개 전부 통과** (다른 팀원 화면 포함), 앱 강제 종료 0건 |
| 화면 배치 (테스트 환경 캡처) | 탭 → 카테고리 → 필터 → 미리보기 200dp("입어보는 중 · 원래대로") → 카드(고른 카드 초록 테두리) → "120 코인으로 구매" → 하단 탭바까지 한 화면에 들어감 |
| 실제 서버·실제 3D로 상점 확인 | **하지 못함.** 로그인 계정이 필요하다. 3D 표시 자체는 같은 `AvatarView`를 쓰는 테스트 화면(2~6단계)에서 확인했다 |

### 직접 확인하는 방법

1. **빌드·단위 테스트:** `./gradlew assembleDebug testDebugUnitTest` → `BUILD SUCCESSFUL`
2. **실제 앱 (로그인 필요, 성별이 정해진 계정)**
   1. 하단 "쇼핑" → "아바타" 탭. 위에 내 캐릭터가 3D로 보이고 드래그로 돌아간다.
   2. 사지 않은 옷을 누르면 미리보기 캐릭터가 그 옷을 입고, 아래에 "N 코인으로 구매"가 나온다.
   3. 같은 옷을 다시 누르면 벗는다. 모자와 상의를 차례로 누르면 둘 다 입는다. "원래대로"로 돌아간다.
   4. "N 코인으로 구매"를 누르면 기존 구매 창이 뜬다.
   5. "우리 집" 탭은 예전처럼 누르면 바로 창이 뜬다.
   - 성별을 정하지 않은 계정(소셜 가입 직후, 이 기능 이전 계정)은 "캐릭터를 먼저 만들어 주세요"가 보인다. 9단계에서 해결한다.
3. **UI 테스트:** `./gradlew connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.dailyquest.app.ShopFlowTest,com.dailyquest.app.ShopTryOnTest"`

---

## 8단계. 착용 저장

- 날짜: 2026-11-22
- 커밋 메시지: `feat: equip owned avatar items from shop`

### 무엇을 바꿨나

상점 아바타 탭에서 고른 아이템의 아래 버튼이 아이템 상태에 따라 바뀐다. 보유한 아이템은 **착용을 서버에 저장**할 수 있다.

| 고른 아이템 | 아래 버튼 | 누르면 |
| --- | --- | --- |
| 보유하지 않음 | "N 코인으로 구매" (7단계와 같음) | 기존 상세·구매 창 |
| 보유, 입고 있지 않음 | **"착용하기"** | `PATCH /users/me` `{"avatar":{"top":21}}` 저장 |
| 입고 있는 모자 | **"모자 벗기"** | `{"avatar":{"hat":null}}` 저장 |
| 입고 있는 다른 슬롯 | **"착용 중"** (비활성) | — (모자 외에는 벗을 수 없다) |
| 저장 중 | "저장 중…" (비활성) | — (저장 중에는 다른 저장을 받지 않는다) |
| 캐릭터가 없는 계정(성별 없음) | "착용하기" (비활성) | — (9단계에서 캐릭터를 만든 뒤 가능) |

- **저장 성공:** 서버가 돌려준 착용으로 바꾸고 그 슬롯의 "입어보는 중"을 끝낸다(이제 진짜 입은 옷). "착용했어요"/"벗었어요" 알림.
- **저장 실패:** 그 슬롯만 입어보기를 벗겨 **원래 입던 모습으로 되돌리고** 이유를 알린다(예: "내 캐릭터가 입을 수 없는 아이템이에요."). 다른 슬롯의 입어보기는 그대로. 다시 "착용하기"를 누를 수 있다.
- **모자 외 해제 막기:** 서버(v0.4)는 모든 슬롯 해제를 받아 주지만, 옷이 없는 캐릭터가 되지 않도록 앱에서 막는다(`REMOVABLE_PARTS = {HAT}`). 버튼으로도 막고, ViewModel에서도 요청을 보내지 않는다.
- 흐름이 이어진다: 입어 보기 → 구매 → 같은 카드의 버튼이 "착용하기"로 바뀜 → 착용.

### 바뀐 파일

| 파일 | 변경 | 비고 |
| --- | --- | --- |
| `app/src/main/java/com/dailyquest/app/ui/avatar/MyAvatarViewModel.kt` | `equip()`, `takeOff()`, 저장 중·알림 상태, `REMOVABLE_PARTS` | |
| `app/src/main/java/com/dailyquest/app/ui/screens/AvatarShopContent.kt` | 아래 버튼을 구매/착용하기/모자 벗기/착용 중으로, 알림 토스트 | **팀원 파일** |
| `app/src/androidTest/java/com/dailyquest/app/TestDQApplication.kt` | 가짜 서버가 성별·착용을 기억(`TestAvatarCalls`), 보유하지 않은 아이템 착용 시 `ITEM_NOT_OWNED` | **팀원 파일.** 기본값(성별 없음·착용 없음)은 기존과 같아 다른 테스트에 영향 없음 |
| `app/src/test/java/com/dailyquest/app/ui/avatar/MyAvatarViewModelTest.kt` | 착용 단위 테스트 9개 추가 (총 20개) | |
| `app/src/androidTest/java/com/dailyquest/app/ShopTryOnTest.kt` | 착용 UI 테스트 4개 추가, 보유 아이템 테스트를 "착용하기(비활성)"로 변경 (총 11개) | |

### 테스트가 확인하는 것

**단위 (착용 9개)**
- 착용하면 바꾼 슬롯만 저장, 서버 응답으로 바꾸고 입어보기는 끝, "착용했어요"
- 실패하면 입어보기를 벗겨 원래 모습으로, 이유 안내 / 실패해도 다른 슬롯 입어보기는 그대로
- 모자는 `null`로 벗는다 / 모자 외 6개 슬롯은 벗을 수 없고 서버에 요청하지 않는다
- 저장 중에는 다른 저장을 받지 않는다 / 아바타를 불러오기 전에는 저장하지 않는다
- 입고 있는지 판단은 서버에 저장된 착용과 비교 / 안내는 보여 준 뒤 지운다

**UI (가짜 서버)**
- 캐릭터가 없는 계정은 보유 아이템도 "착용하기"가 비활성
- 보유한 상의를 착용 → 요청 `{"top":2}` 한 번, 버튼이 "착용 중", "입어보는 중" 사라짐
- 입고 있는 머리는 "착용 중"(비활성), 요청 없음
- 입고 있는 모자 → "모자 벗기" → 요청 `{"hat":null}`, 버튼이 "착용하기"로
- 저장 실패 → "입어보는 중"이 사라지고(원래 모습) "착용하기"를 다시 누를 수 있음

**진행 중 만난 문제:** 새 UI 테스트 3개가 처음에 실패했다. 테스트에서 "이 아이템은 보유 중"으로 바꾸기 **전에** 앱이 이미 상점 목록을 받아 와서 버튼이 "구매"로 남아 있었다(앱 문제가 아님). 보유 상태를 바꾼 테스트는 상점을 연 뒤 "우리 집 → 아바타"로 탭을 오가 목록을 새로 받게 했다.

### 테스트 결과

| 항목 | 결과 |
| --- | --- |
| `./gradlew assembleDebug assembleRelease testDebugUnitTest` | 성공. 단위 테스트 398개 전부 통과 (새 착용 테스트 9개 포함) |
| 상점 UI 테스트 (`ShopFlowTest` 6개 + `ShopTryOnTest` 11개) | 17개 모두 통과 |
| 전체 UI 테스트 `connectedDebugAndroidTest` | **96개 전부 통과** (다른 팀원 화면 포함), 앱 강제 종료 0건 |
| 실제 서버로 착용 저장 | **하지 못함** (로그인 계정 필요). 요청 형식(`{"avatar":{"hat":null}}` 등)은 6단계 MockWebServer 테스트와 이번 가짜 서버 테스트로 확인 |

### 직접 확인하는 방법

1. **빌드·단위 테스트:** `./gradlew assembleDebug testDebugUnitTest` → `BUILD SUCCESSFUL`
2. **실제 앱 (로그인 필요, 성별이 정해진 계정)** — 하단 "쇼핑" → "아바타"
   1. "보유 아이템"에서 입고 있지 않은 옷을 누르면 미리보기가 그 옷을 입고 아래 버튼이 **"착용하기"**다. 누르면 "착용했어요"가 뜨고 버튼이 **"착용 중"**이 된다.
   2. 상점을 나갔다 다시 들어와도 그 옷을 입고 있다(서버에 저장됨).
   3. 입고 있는 모자를 누르면 **"모자 벗기"**. 누르면 모자가 사라진다.
   4. 입고 있는 머리·상의 등은 **"착용 중"**이고 누를 수 없다.
   5. 사지 않은 옷: 입어 보기 → "N 코인으로 구매" → 구매 → 같은 카드 버튼이 "착용하기"로 바뀐다.
3. **UI 테스트:** `./gradlew connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.dailyquest.app.ShopTryOnTest"`

---

## 9단계. 캐릭터 만들기 (성별 선택)

- 날짜: 2026-11-22
- 커밋 메시지: `feat: require character creation with gender and skin choice`

### 정한 것

- **캐릭터는 필수다.** 성별이 없는 계정은 로그인할 때마다 캐릭터 만들기 화면으로 간다. 신규 이메일 가입, 신규 소셜 가입, `gender` 없이 가입한 계정, v0.4 이전 기존 계정이 모두 해당한다. "나중에 꾸밀게요"(건너뛰기)는 없앴다.
- **성별은 로그인 뒤에 정한다**(백엔드 문서의 B안). 이메일·소셜 모두 같은 화면을 거친다. 회원가입 요청(`POST /auth/signup`)은 바꾸지 않았다.
- 시작 전에 운영 서버(`/v3/api-docs`)에 `PUT /users/me/gender`와 `/shop/items/batch`가 **배포돼 있음을 확인**했다. 배포 전이었다면 모든 사용자가 이 화면에 갇혔을 것이다.

### 무엇을 바꿨나

기존 회원가입 "아바타 만들기" 화면(`SignupAvatarScreen`, 2D 남자아이 그림 + 저장되지 않는 색 동그라미)을 진짜 **캐릭터 만들기**로 바꿨다.

| 항목 | 바뀌기 전 | 바뀐 뒤 |
| --- | --- | --- |
| 미리보기 | 2D 남자아이 그림 고정 | 고른 성별·피부의 **3D 기본 한 벌** |
| 고르기 | 피부 5·헤어 5·옷 5 색 동그라미(저장 안 됨) | **남자아이/여자아이** + **피부 3톤(아이보리·피치·브라운)** |
| "Daily Quest 시작하기" | 홈으로 이동만 | `PUT /users/me/gender` → 서버가 기본 한 벌 지급·착용 → 고른 피부가 서버 기본 톤과 다르면 피부만 바꿔 입힘 → 다음 화면 |
| 건너뛰기 | "나중에 꾸밀게요" | 없음(필수) |
| 안내 | "아직 서버에 저장되지 않아요" | "캐릭터는 한 번 정하면 바꿀 수 없어요. 머리와 옷은 상점에서 꾸밀 수 있어요." |
| 머리 | "회원가입 2/2" + 뒤로 | "캐릭터 만들기"(기존 계정도 오므로) |

- 헤어·옷은 기본 한 벌로 시작하고 상점(7·8단계)에서 꾸민다.
- **미리보기용 GLB 주소는 앱에 둔다**(`AvatarDefaults`). 성별을 정하기 전에는 서버가 아바타 아이템을 보여 주지 않기 때문이다. backend V10 시드와 같은 코드이고, S3에 16개 모두 있는 것을 확인했다. 실제 지급은 서버가 한다.
- 피부 톤은 체형마다 코드가 다르다(남 아이보리 = `DQ_Boy_01_Body`, 여 아이보리 = `DQ_Body_BoySkin` 등). 서버가 처음 입히는 피부는 체형별 첫 톤(남 아이보리, 여 피치)이다.

**`createCharacter` 동작 (MyAvatarRepository)**
1. `PUT /users/me/gender` — 이미 정해진 계정(`GENDER_ALREADY_SET`: 두 번 누름·다른 기기)이면 지금 상태(`GET /users/me`)로 그대로 진행한다.
2. 세션의 내 정보에 성별 반영 → 다음 로그인부터 바로 홈으로 간다.
3. 고른 피부가 입혀진 것과 다르면 보유 목록에서 `code`로 찾아 `PATCH {"avatar":{"skin":id}}`. **이 단계만 실패하면 캐릭터는 이미 만들어졌으므로 성공으로 본다**(피부는 상점에서 다시 바꿀 수 있다).

### 바뀐 파일

| 파일 | 변경 | 비고 |
| --- | --- | --- |
| `app/src/main/java/com/dailyquest/app/data/avatar/AvatarDefaults.kt` | 새 파일. 미리보기용 기본 한 벌, 피부 3톤 코드 | |
| `app/src/main/java/com/dailyquest/app/data/avatar/MyAvatarRepository.kt` | `createCharacter()` | |
| `app/src/main/java/com/dailyquest/app/ui/avatar/CharacterCreateViewModel.kt` | 새 파일 | |
| `app/src/main/java/com/dailyquest/app/ui/screens/AuthScreens.kt` | `SignupAvatarScreen`을 캐릭터 만들기로 교체 | **팀원 파일.** 다른 회원가입·로그인 화면은 그대로 |
| `app/src/main/java/com/dailyquest/app/ui/auth/AuthViewModel.kt` | `consumePostLoginRoute()`: 성별 없으면 항상 캐릭터 만들기 | **팀원 파일** |
| `app/src/main/java/com/dailyquest/app/data/auth/SessionManager.kt` | `UserProfile.gender` | **팀원 파일, 추가만** |
| `app/src/main/java/com/dailyquest/app/data/remote/DqApi.kt` | `setGender` (PUT /users/me/gender) | **팀원 파일, 추가만** |
| `app/src/main/java/com/dailyquest/app/data/remote/dto/UserDtos.kt` | `SetGenderRequest` | **팀원 파일, 추가만** |
| `app/src/main/java/com/dailyquest/app/data/remote/dto/InventoryDtos.kt` | `MyItemDto.code`, `gender` | **팀원 파일, 추가만** |
| `app/src/test/java/com/dailyquest/app/data/auth/SocialLoginTest.kt` | 로그인 후 화면 테스트를 새 규칙에 맞게 1개 수정, 2개 추가 | **팀원 테스트.** "신규 가입자는 아바타 → 그다음 홈"이 "캐릭터가 없으면 계속 캐릭터 만들기"로 바뀌었다 |
| `app/src/androidTest/java/com/dailyquest/app/TestDQApplication.kt` | 가짜 서버에 `setGender`, 기본 성별 MALE | **팀원 파일.** 기본 성별이 없으면 모든 UI 테스트가 캐릭터 만들기 화면에서 시작하게 되므로 MALE로 둔다 |
| `app/src/androidTest/java/com/dailyquest/app/ShopTryOnTest.kt` | 기본 성별 MALE에 맞게 2개 수정, 1개 추가 | |
| `app/src/test/java/com/dailyquest/app/data/avatar/MyAvatarRepositoryTest.kt` | 캐릭터 만들기 6개 추가 | |
| `app/src/test/java/com/dailyquest/app/ui/avatar/CharacterCreateViewModelTest.kt` | 새 파일. 7개 | |
| `app/src/androidTest/java/com/dailyquest/app/CharacterCreateTest.kt` | 새 파일. UI 4개 | |

- `NavGraph.kt`, `Routes.kt`는 바꾸지 않았다. 캐릭터 만들기 경로(`SIGNUP_AVATAR`)와 "로그인 후 갈 화면" 연결이 이미 있었다.

### 테스트가 확인하는 것

**단위**
- 로그인 후 화면: 캐릭터 없으면 계속 캐릭터 만들기 / 신규 가입 표시 + 캐릭터 있음 → 한 번 캐릭터 화면 후 홈 / 캐릭터 있는 기존 계정 → 홈
- 캐릭터 만들기: `{"gender":"MALE"}` 전송, 서버 기본 피부와 같으면 피부 요청 없음 / 다른 피부면 `code`로 찾아 `{"avatar":{"skin":3}}` / 세션에 성별 반영 / `GENDER_ALREADY_SET`이면 현재 상태로 진행 / 성별 확정 실패면 실패 + 세션 그대로 / 피부만 실패하면 성공
- 화면 상태: 처음엔 남자아이·아이보리, 미리보기 6개 / 여자아이·브라운이면 모두 `girl/` 주소, 모자 없음 / 시작하면 고른 값으로 만들고 끝 / 실패하면 안내 후 다시 시도 / 저장 중엔 다시 누르기·선택 변경 무시 / 만든 뒤 다시 보내지 않음 / 피부 코드가 서버 시드와 같음

**UI (가짜 서버, `CharacterCreateTest`)**
- 캐릭터 없는 계정은 앱을 켜면 캐릭터 만들기에서 시작하고, 건너뛰기가 없다
- 여자아이·브라운 → 시작 → 다음 화면으로 넘어가고 서버에 `FEMALE` 한 번
- 아무것도 안 고르고 시작하면 `MALE`
- 저장 실패 → 안내가 보이고 화면에 남음 → 다시 누르면 성공해서 넘어감

### 테스트 결과

| 항목 | 결과 |
| --- | --- |
| `./gradlew assembleDebug assembleRelease testDebugUnitTest` | 성공. 단위 테스트 413개 전부 통과 (새 15개 포함) |
| `CharacterCreateTest` 4개 + `ShopTryOnTest` 12개 | 16개 모두 통과 |
| 전체 UI 테스트 `connectedDebugAndroidTest` | **101개 전부 통과** (다른 팀원 화면 포함), 앱 강제 종료 0건 |
| 화면 배치 (테스트 환경 캡처, 여자아이·피치 선택) | 제목 → 미리보기 260dp → 캐릭터 칩 → 피부 동그라미 3개(선택 테두리) → 안내 → "Daily Quest 시작하기" |
| 미리보기 GLB | 16개 모두 S3에 있음(200), 피부 코드가 서버 시드와 일치 |
| 실제 서버로 캐릭터 만들기 | **하지 못함** (새 계정이 필요). 운영 서버에 API가 배포된 것은 확인 |

### 직접 확인하는 방법

1. **빌드·단위 테스트:** `./gradlew assembleDebug testDebugUnitTest` → `BUILD SUCCESSFUL`
2. **실제 앱 — 새 계정으로**
   1. 이메일로 회원가입하거나, 처음 쓰는 구글·카카오 계정으로 로그인한다.
   2. **"캐릭터 만들기"** 화면이 뜬다. 남자아이/여자아이와 피부를 바꾸면 위 3D 캐릭터가 바로 바뀐다(처음 한 번은 받는 데 몇 초).
   3. "Daily Quest 시작하기" → 홈으로 간다.
   4. 상점 → 아바타 탭에 방금 고른 캐릭터가 보인다(성별·피부 그대로).
   5. 로그아웃 후 다시 로그인하면 캐릭터 만들기 없이 바로 홈으로 간다.
3. **이 기능 이전에 만든 계정:** 로그인하면 캐릭터 만들기가 한 번 뜬다(서버가 v0.4에서 기존 계정의 성별을 비웠다).
4. **UI 테스트:** `./gradlew connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.dailyquest.app.CharacterCreateTest"`


---

## 버전 변경 영향 확인 (2026-11-22)

1단계에서 SceneView 때문에 자동으로 올라간 라이브러리 버전이 **다른 팀원이 만든 기존 코드**를 깨뜨리지 않는지 따로 확인했다. `main`을 별도 폴더(git worktree)에 꺼내 지금 브랜치와 나란히 비교했다.

| 확인 | 방법 | 결과 |
| --- | --- | --- |
| 1. 실제로 바뀐 라이브러리 | 두 쪽의 `debugRuntimeClasspath`를 전부 뽑아 비교 | 기존 라이브러리 중 버전이 바뀐 것은 Compose foundation·animation·ripple(1.10→1.11.2), BOM, fragment(1.5.7→1.8.9), Kotlin stdlib(2.2.10→2.3.21)뿐. 새로 들어온 14개는 SceneView·Filament와 그 하위 라이브러리. **빠진 라이브러리 0개** |
| 2. 컴파일 경고 | 두 쪽을 `--rerun-tasks`로 다시 컴파일해 경고 비교 | 양쪽 모두 같은 경고 13개. **새 버전 때문에 팀원 코드에 새로 생긴 경고 0개** |
| 3. 팀원 테스트 | `main`에 있는 테스트 함수 407개(단위 332 + UI 75)를 이름으로 뽑아, 깨끗하게 다시 빌드한 지금 브랜치 결과와 하나씩 대조 | **407개 모두 존재하고 모두 통과**, 실패 0, 빠짐 0 |
| 4. 화면 모양 | 같은 캡처용 임시 테스트로 `main` 빌드와 지금 빌드에서 개발용 목록의 화면 17개를 같은 순서로 찍어 픽셀 비교(상단 시계 줄 제외) | **팀원 화면 16개 + 목록 화면이 픽셀까지 완전히 같음.** 다른 것은 일부러 바꾼 상점(미리보기 상자 추가) 1개뿐. 18장이 모두 서로 다른 화면인 것도 확인(캡처 실수 아님) |

비교한 화면: 홈(우리만·공개), 방 내부, 스토리 뷰어, 미션 인증하기, 인증 확인, 미션 보내기, 회원가입 정보 입력, B1 홈, B2 방 내부, B3 미션 인증, B4 미션 보내기, B5 쇼핑, Daily Quest, 세로형 미션 인증, 세로형 인증 확인, 개발용 목록. (로그인 화면은 로그인된 테스트 앱에서 자동으로 넘어가서, 캐릭터 만들기는 일부러 바꿔서 제외)

**결론:** 라이브러리 버전 변경으로 팀원 기존 코드가 깨지거나 화면이 달라진 곳은 찾지 못했다.

**이 확인으로 알 수 없는 것**
- 실제 서버에 로그인한 상태의 동작(테스트는 가짜 서버 기준)
- 에뮬레이터(Pixel 8, Android 15) 외 다른 기기·안드로이드 버전
- 캡처는 각 화면의 첫 모습만 비교했다. 스크롤·애니메이션 중간 모습은 비교하지 않았다(기능은 UI 테스트 75개가 확인)

---

## 실서버 확인 + 3D 로딩 표시 (2026-11-24)

PR을 올리기 전에 실제 서버에 로그인해서 STEP 9 흐름을 처음으로 확인했다. 그 과정에서 찾은 문제 두 개를 고쳤고, 하나는 팀원에게 전달한다.

공용 테스트 계정은 성별을 한 번 정하면 되돌릴 수 없어서 건드리지 않고, 검증용 계정 2개를 새로 만들어 썼다(API 확인용 1개, 앱 화면 확인용 1개). 계정 정보는 저장소에 적지 않는다.

### API 확인 결과 (서버 v0.4)

| 확인 | 결과 |
| --- | --- |
| 가입 직후 `gender` | `null`, 착용 없음, 상점 아바타 아이템 0개 |
| `PUT /users/me/gender` | 기본 한 벌 9개 지급, 피부 첫 톤·눈·머리·상의·하의·신발 착용, 모자는 보유만 |
| 같은 요청 다시 | 409 `GENDER_ALREADY_SET` |
| `PATCH /users/me` 피부·모자 변경, 모자 `null` | 정상 반영 |
| 다른 성별 아이템 구매 | 409 `ITEM_GENDER_MISMATCH` |
| 안 가진 아이템 착용 | 403 `ITEM_NOT_OWNED` (다른 성별 아이템도 이 코드) |
| 코인 부족 구매 | 409 `INSUFFICIENT_COINS` |
| 상점 목록 | 내 성별 아이템만(54개) |
| `GET /shop/items/batch` | 정상, 100개 초과 시 400 `VALIDATION_ERROR` |
| 피부 코드 6개 (`AvatarDefaults`) | 서버에 모두 있음. 서버의 첫 톤도 앱 기본값과 같음 |
| GLB 파일 | S3에서 200, `modelUrl` 파일 이름 = `code` |

### 앱 화면 확인 결과 (에뮬레이터)

| 흐름 | 결과 |
| --- | --- |
| 로그인 → 캐릭터 만들기로 이동 | 정상 |
| 남자아이 + 브라운 선택 → 3D 미리보기 바뀜 | 정상 |
| 시작하기 → 서버에 `MALE` + `DQ_Boy_01_Body_DeepBrown` 저장 → 홈 | 정상 |
| 상점 3D 미리보기에 서버 저장 모습 표시 | 정상 (아래 문제 3 우회 후) |
| 기본 모자 입어보기 → 착용하기 → 서버 저장 | 정상 |
| 모자 벗기 → 서버 `null` | 정상 |
| 안 산 모자 → "100 코인으로 구매" → 코인 부족 안내 | 정상 |

### 찾은 문제 1: 3D가 뜨기 전 빈 상자 (고침)

**현상:** 상점에 들어가면 미리보기가 5~6초 동안 빈 상자로 보이다가 아바타가 나타났다. 진행 표시가 없었다.

**원인:** 진행 표시를 빼먹은 것이 아니었다. 로그에 `Skipped 367 frames`, 한 프레임 `6018ms`가 찍혔다. 3D 엔진(Filament)을 만드는 동안 메인 스레드가 멈춰 화면 자체가 그려지지 않았다. 엔진을 화면 첫 구성 때 바로 만들어서, 진행 표시가 한 번도 그려지기 전에 멈췄다.

**고친 것** (`AvatarView.kt`)
- 처음에는 진행 표시만 그리고, **두 프레임 뒤에** 3D 엔진을 만든다. 엔진을 만드는 동안 화면은 여전히 멈추지만, 멈춘 화면에 진행 표시가 보인다.
- 3D 화면이 생긴 직후 불러오기를 시작하기 전 짧은 순간에도 진행 표시를 보인다.

### 찾은 문제 2: 화면을 닫을 때 앱 강제 종료 가능성 (고침)

문제 1을 고친 뒤 테스트에서 `SIGSEGV in AssetLoader::createAsset`(네이티브 강제 종료)가 한 번 났다.

**원인:** 모델을 만드는 도중 화면이 닫히면 3D 엔진이 먼저 지워지는데, 남은 모델 만들기가 지워진 엔진을 그대로 썼다. 모델 만들기는 중간에 끊기면 모델을 잃어버려서 취소되지 않게 만들어 둔 구간이다(4단계). 원래부터 있던 경로인데, 엔진 생성이 두 프레임 늦어지면서 테스트가 끝나는 시점과 겹쳐 드러났다.

**고친 것:** 모델을 만들기 전과 텍스처를 기다리는 동안 매번 화면이 살아 있는지 확인하고, 닫혔으면 멈춘다. 화면 닫기와 모델 만들기는 둘 다 메인 스레드에서 돌아서 확인한 직후에 닫힐 수 없다.

### 찾은 문제 3: 캐릭터를 만든 직후 상점 아바타 탭이 비어 있음 (팀원 전달, 이 PR에서 고치지 않음)

**현상:** 새로 가입 → 캐릭터 만들기 → 상점으로 가면 "표시할 상품이 없어요". 탭을 바꿨다 돌아오거나 앱을 다시 켜면 정상.

**원인:** `ShopViewModel`(팀원 STEP 7 코드)은 앱 전체에 하나만 있고, **로그인 계정(userId)이 바뀔 때만** 목록을 다시 받는다. 로그인 순간에는 성별이 없어서 서버가 아바타 아이템을 0개로 주는데, 캐릭터를 만들어도 userId는 같아서 다시 받지 않는다.

**고치는 방법(제안):** 다시 받는 기준을 `userId`에서 `(userId, gender)`로 바꾼다.

### 바뀐 파일

| 파일 | 변경 |
| --- | --- |
| `ui/avatar/AvatarView.kt` | 진행 표시 먼저 그린 뒤 3D 엔진 생성, 불러오기 시작 전 진행 표시, 화면이 닫힌 뒤 모델 만들기 중단 |
| `androidTest/.../ui/avatar/AvatarViewTest.kt` | 테스트 2개 추가 |

### 테스트가 확인하는 것

| 테스트 | 확인 내용 |
| --- | --- |
| `loadingShowsOnFirstFrameBeforeSceneStarts` | 3D 엔진을 만들기 전 **첫 화면에** 이미 진행 표시가 있다. 고치기 전 코드에서는 첫 화면 상태가 "준비됨·빈 화면"이라 실패한다 |
| `loadingStaysWhileModelsAreDownloading` | 3D 화면이 시작된 뒤 다운로드가 끝나지 않는 동안 진행 표시가 계속 있고, 실패 표시는 없다 |

### 테스트 결과

| 종류 | 결과 |
| --- | --- |
| 단위 테스트 | 413개 통과 |
| 아바타 관련 UI 테스트 (`AvatarViewTest`, `Avatar3dTestScreenTest`, `ShopTryOnTest`, `CharacterCreateTest`) | 28개 통과, 강제 종료 0 |
| 전체 UI 테스트 | 103개 통과(기존 101 + 새 2), 강제 종료 0 |
| 실제 앱 화면 | 상점 진입 직후 연속 캡처: 진행 표시 → 아바타 순서로 보임, 빈 상자 없음 |

> 처음 돌릴 때 `Avatar3dTestScreenTest`가 "40초 안에 준비됨이 안 나옴"으로 여러 개 실패했다. 이때 에뮬레이터는 남은 메모리 약 170MB, 스왑 690MB 사용, 시스템 CPU 약 300%로 과부하 상태였다(다른 프로젝트 앱도 함께 실행 중). 에뮬레이터를 저장 상태 없이 다시 켜고 돌리니 28개 모두 통과했다.

### 직접 확인하는 방법

1. 앱 실행 → 성별을 정한 계정으로 로그인 → 하단 "쇼핑"
2. 미리보기 상자에 **빈 상자 대신 진행 표시**가 먼저 보이고, 그 뒤 아바타가 나타나는지 본다.
3. 홈 ↔ 쇼핑을 몇 번 오가도 앱이 꺼지지 않는지 본다.

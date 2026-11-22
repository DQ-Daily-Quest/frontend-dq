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
| 8 | 착용 저장 (저장, 실패 시 되돌리기, 모자 외 해제 막기) | 예정 |
| 9 | 성별 선택 화면 (`gender == null`일 때, 기존 "아바타 만들기" 화면 활용) | 예정 |

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

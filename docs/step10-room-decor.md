# STEP 10. 방 꾸미기 작업 기록

- 브랜치: `feat/room-decor` (STEP 9가 합쳐진 `main` 9287866에서 시작)
- 기준 API: 백엔드 연동 규칙 11장(11/24) — 16×16 방 1종 + 가구 50종, `code`로 찾기
- 에셋: `DailyQuest_Room16_AppPack_1080` (방 1080×810, 가구 50종 × 4방향 PNG, 배치 정보 `assets_manifest.json`)
- 진행 방식: 단계마다 구현 → 빌드·테스트 → 이 문서에 기록 → 보고 후 커밋

## 시작 전 상태 (2026-11-24 확인)

- 방 화면: 고정 2D 일러스트(`img_room_interior`). "방 꾸미기" 버튼은 "아직 서버와 연결되지 않았어요" 안내만 띄운다.
- 앱에는 방 꾸미기 API가 없다. 서버에는 조회·저장·배치 가능 목록 API가 있다.
- 상점 "우리 집" 탭에는 서버의 기본 방 + 가구 50종이 이미 나온다. 다만 썸네일이 잘리고 흐리다(아래 8단계).
- 칸 수, 이미지 기준점 같은 배치 정보는 서버에 없다. 앱이 에셋 팩의 정보를 직접 가지고 있어야 한다.

## 단계 계획

| # | 작업 | 내용 | 테스트 | 상태 |
| --- | --- | --- | --- | --- |
| 1 | 가구 배치 정보 넣기 | 에셋 팩의 방 격자(기준점·축)와 가구별 정보(칸 수, 방향별 이미지 크기·기준점·보정값, 러그 여부)를 앱 안 JSON으로 넣고 `code`로 찾는다 | 단위: 방 + 50종이 모두 읽히는지, 90°·270°에서 가로·세로 칸이 바뀌는지 | 완료 |
| 2 | 좌표 계산 | 칸 ↔ 방 이미지 위치, 서버 x·y(0~1) ↔ 칸 번호, 방 밖·겹침 검사, 그리는 순서(러그 먼저, 뒤쪽 먼저), **확대·이동 계산**(화면 좌표 ↔ 방 좌표, 확대 1~3배, 방이 화면 밖으로 빠지지 않게 이동 제한) | 단위: 에셋 팩 배치 예시 54개가 미리보기 이미지와 같은 위치에 오는지, 확대 상태에서 손가락 위치가 같은 칸을 가리키는지 | 완료 |
| 3 | 서버 연결 | 꾸미기 API 3개, `DecorRepository`, 기본 방은 `room_16x16`으로 찾기, 에러(`ITEM_NOT_OWNED_IN_ROOM` 등) 처리 | 단위: 가짜 서버(MockWebServer)로 응답·에러 | 완료 |
| 4 | 방 보여주기 + 두 손가락 확대 | 이미 놓인 가구(서버에 저장된 배치)를 방 위 정확한 자리에 **보이게만** 한다. 놓기·옮기기는 6단계(방과 가구에 같은 배율, 이미지 기기 캐시). **두 손가락으로 확대·축소(1~3배), 확대했을 때 한 손가락으로 이동, 두 번 탭하면 원래 크기.** 디버그 테스트 화면에서 배치 예시로 확인 | UI: 확대·이동·되돌리기, 화면 캡처를 에셋 미리보기와 비교 | 완료 |
| 5 | 방 화면에 연결 | 방 화면의 고정 일러스트를 실제 배치된 방으로 바꾼다. 저장한 적 없는 방은 기본 방. 방을 누르면 크게 보기(확대 가능) | UI: 조회 결과대로 그려지는지 | 완료 |
| 6 | 꾸미기 편집 화면 (가구 놓기) | "방 꾸미기" → 편집 화면. 사용자가 가구 목록(바닥·벽 제외, 놓을 수 있는 개수 표시)에서 골라 방에 놓기, 드래그로 옮기면 칸에 맞춰짐, 회전, 빼기, 겹치거나 방 밖이면 빨간 표시. **확대 상태에서도 편집 가능: 가구를 잡고 끌면 가구 이동, 빈 곳을 끌면 화면 이동, 두 손가락은 확대** | UI: 놓기·옮기기·회전·겹침 막기, 확대 상태에서 옮기기 | 완료 |
| 7 | 저장 | 저장 요청, 실패 시 되돌리기, 저장 안 하고 나가면 경고, 마지막 수정한 사람 표시 | UI + 단위 | 완료 |
| 8 | 상점 정리 + 실서버 확인 | ① 상점 버그 수정(캐릭터 만든 직후 아바타 목록이 빔 → `(userId, gender)`로 다시 받기, `fix:` 커밋 따로) ② 가구 썸네일이 잘리지 않게(`RemoteImage`에 맞춤 방식 옵션 추가, 기본값 그대로) ③ 실서버로 전체 흐름 확인 | UI + 실서버 | 대기 |

## 미리 정한 것

- 방은 2D로 한다. 에셋의 GLB(3D 방)는 계획서상 P2라 이번에는 쓰지 않는다.
- 확대는 1~3배. 1배는 방 전체가 화면 너비에 맞는 크기다. 3배면 가장 작은 가구(약 11dp)도 33dp 정도로 보인다.
- 같은 가구는 "방 멤버 중 그 가구를 가진 사람 수"만큼만 놓을 수 있다(서버 규칙). 목록에 남은 개수를 보여준다.
- 칸 겹침과 방 범위는 앱이 검사한다. 서버는 값 범위와 보유 여부만 검사한다.

## 다른 팀원 파일을 건드리는 곳

| 파일 | 담당 | 단계 | 이유 |
| --- | --- | --- | --- |
| `RoomScreen.kt`, `RoomParts.kt` | 홈·방 담당 | 5, 6 | 고정 일러스트 → 실제 방, "방 꾸미기" 버튼 연결 |
| `Routes.kt`, `NavGraph.kt` | 내비게이션 담당 | 6 | 편집 화면 경로 추가 |
| `ShopViewModel.kt` | 상점 담당 | 8 | 상점 버그 수정 (팀원에게 내가 고친다고 전달함) |
| `RemoteImage.kt` | 상점·인증 담당 | 8 | 맞춤 방식 옵션 추가 (기존 동작은 그대로) |

## 정해야 할 것

- 가구 썸네일 흐림: 가구 PNG가 원래 작아서(28~120px) 상점 칸에서 흐리다. 앱에서는 잘림만 고칠 수 있다. 선명하게 하려면 에셋 쪽에 상점용 512×512 썸네일을 따로 요청해야 한다(받으면 백엔드는 `imageUrl`만 바꾸면 됨).

---

## 1단계. 가구 배치 정보 넣기

서버는 가구의 이름·가격·이미지 주소만 준다. 몇 칸을 차지하는지, 그림의 어느 점을 바닥 칸에 맞추는지는 에셋 팩에만 있다. 그래서 에셋 팩 `assets_manifest.json`에서 앱에 필요한 값만 골라 앱 안에 넣었다.

### 무엇을 만들었나

- **`assets/decor/room16_catalog.json`** (약 31KB): 방 1개 + 가구 50종.
  - 방: 이미지 크기(1080×810), 16×16칸, 칸 (0, 0)의 바닥 위치와 칸 한 개만큼 움직일 때의 X·Y 방향.
  - 가구: `code`, 차지하는 칸 수, 러그인지(겹쳐 놓기 가능), 4방향 그림마다 크기·기준점(그림 아래 가운데)·바닥 보정값·그 방향의 칸 수.
  - 이름·가격·이미지 주소는 넣지 않았다(서버 값을 쓴다). 숫자는 소수 4자리로 줄였다(1080px 기준 화면 차이 없음).
- **`tools/make_room_catalog.py`**: 위 JSON을 만드는 스크립트. 에셋 팩이 바뀌면 다시 실행한다.
- **`data/decor/RoomCatalog.kt`**
  - `RoomCatalog.parse()`: JSON을 읽는다. 4방향 그림이 다 있는지, 90°·270°에서 칸 수가 서로 바뀌는지 검사하고 틀리면 바로 실패한다.
  - `RoomCatalog.furniture(code)`: 서버 아이템의 `code`로 가구 정보를 찾는다. 앱이 모르는 가구면 null(서버에 새 가구가 먼저 올라온 경우 대비).
  - `RoomCatalogs.load(context)`: 앱에서 한 번만 읽어 둔다.
  - `Rotation`: 0°·90°·180°·270°. 서버 `rotation` 값 변환(`fromDegrees`), 회전 버튼용 `next()`, 0° 이미지 주소에서 방향별 주소 만들기(`spriteUrl`, `.png` 앞에 `_r090` 등을 붙이는 서버 규칙).

### 확인한 것

- 앱 JSON의 `code` 51개(방 + 가구 50)가 **서버 `GET /shop/items?type=FURNITURE`의 51개와 정확히 같다**(앱에만 있는 것 0, 서버에만 있는 것 0).
- 저장소 스크립트로 다시 만든 결과가 앱 JSON과 바이트까지 같다.
- 빌드한 APK 안에 `assets/decor/room16_catalog.json`이 들어 있다.

### 바뀐 파일

| 파일 | 변경 |
| --- | --- |
| `app/src/main/assets/decor/room16_catalog.json` | 새 파일 |
| `app/src/main/java/com/dailyquest/app/data/decor/RoomCatalog.kt` | 새 파일 |
| `app/src/test/java/com/dailyquest/app/data/decor/RoomCatalogTest.kt` | 새 파일 (테스트 16개) |
| `tools/make_room_catalog.py` | 새 파일 |
| `docs/step10-room-decor.md` | 새 파일 (이 문서) |

다른 팀원 파일은 건드리지 않았다.

### 테스트가 확인하는 것 (`RoomCatalogTest`, 앱에 들어가는 JSON을 그대로 읽음)

| 테스트 | 확인 내용 |
| --- | --- |
| `roomIs16x16On1080Image` | 방 코드·크기·칸 수·격자 기준점이 에셋 팩 값과 같다 |
| `has50FurnitureWithUniqueCodes` | 가구 50종, 코드 중복 없음, 방은 가구 목록에 없음 |
| `findsFurnitureByServerCode` | `desk_oak`가 2×1칸, 기본 방향 그림 87×76px(에셋 README 예시와 같음). 없는 코드·null은 null |
| `everyFurnitureHasAllFourDirections` | 50종 모두 4방향 그림이 있다 |
| `quarterTurnsSwapOccupiedCells` | 50종 모두 0°·180°는 원래 칸 수, 90°·270°는 가로·세로가 바뀐다 |
| `anchorIsBottomCenterOfEachSprite` | 200장 모두 기준점이 그림 아래 가운데다 |
| `onlyRugIsFloorDecorAndCanOverlap` | 러그만 바닥 장식(겹쳐 놓기 가능), 나머지 49종은 일반 가구 |
| `everyFurnitureFitsInRoom` | 어떤 가구도 16칸보다 크지 않다 |
| `spriteUrlAddsDirectionSuffixBeforePng` | 0° 주소 → `_r090`·`_r180`·`_r270` 주소. `.png`가 아니면 그대로 |
| `rotationFromServerDegrees` | 0·90·180·270, 360→0°, -90→270°, 45→null |
| `nextRotationCyclesClockwiseOrder` | 0→90→180→270→0 |
| `validSmallFileParses` 외 4개 | 방향이 빠진 파일, 90°에서 칸 수가 안 바뀐 파일, 모르는 층, 모르는 형식 번호를 거부한다 |

### 테스트 결과

| 종류 | 결과 |
| --- | --- |
| `RoomCatalogTest` | 16개 통과 |
| 전체 단위 테스트 | 460개 통과(기존 444 + 새 16) |
| 빌드 | 성공, 새 컴파일 경고 없음 |

UI는 아직 없어서 UI 테스트는 돌리지 않았다(이번 단계는 화면 변화 없음).

### 직접 확인하는 방법

```
./gradlew testDebugUnitTest --tests "com.dailyquest.app.data.decor.*"
```
결과는 `app/build/reports/tests/testDebugUnitTest/index.html`에서 볼 수 있다. 화면에서 보이는 변화는 4단계부터 생긴다.

---

## 2단계. 좌표 계산 + 확대·이동 계산

화면 없이 숫자만 다루는 계산을 먼저 만들고 단위 테스트로 굳혔다. 4단계 이후 화면은 이 계산만 불러 쓴다.

### 좌표 두 가지

- **칸 좌표**: 바닥 격자 0~15. 칸 (0, 0)이 방 안쪽 구석. 가구 위치는 차지한 칸들의 시작 칸 (gridX, gridY).
- **방 이미지 좌표**: 방 PNG(1080×810) 위의 픽셀. 화면에서는 여기에 같은 배율을 곱하고 위치를 옮긴다.

### 무엇을 만들었나

**`data/decor/RoomLayout.kt`**

| 기능 | 하는 일 |
| --- | --- |
| `groundPx` | 칸 좌표 → 방 이미지 좌표 (바닥 위치) |
| `gridAt`, `cellAt` | 반대 방향. 손가락이 가리키는 바닥 칸을 찾는다. 방 밖이면 null |
| `spriteBounds` | 가구 그림이 그려질 사각형. 바닥 가운데 → 보정값 더하기 → 그림 안 기준점 빼기 |
| `snapStart` | 가구 가운데를 손가락에 맞췄을 때의 시작 칸 (드래그할 때 칸에 맞추기) |
| `fitsInRoom` | 돌린 방향의 칸 수로 방 안에 들어가는지 |
| `problems` | 놓을 수 없는 가구(방 밖, 다른 가구와 칸 겹침). 러그는 겹쳐도 되고 방 밖만 검사 |
| `drawOrder` | 그리는 순서. 러그 먼저, 그다음 방 안쪽(칸 가운데 X + Y가 작은 것)부터 |
| `hitTest` | 손가락 아래 가장 앞쪽 가구. 그림 사각형으로 판단(작은 가구도 누르기 쉽게) |
| `toServerRatio`, `fromServerRatio` | 칸 번호 ↔ 서버 x·y(0~1, 0.0625 단위). 칸 경계에 안 맞거나 방 밖인 값은 null(예전 32칸 값 등을 엉뚱한 칸에 놓지 않게) |

**`ui/decor/RoomViewport.kt`** (두 손가락 확대·이동)

- 1배 = 방 전체가 화면 칸 안에 들어오는 크기(가로·세로 중 먼저 닿는 쪽에 맞춤). 1~3배.
- 확대·축소할 때 두 손가락 사이에 있던 방 지점이 그 자리에 머문다.
- 방이 화면보다 크면 빈 곳이 보이지 않을 만큼만 움직인다. 작으면 가운데에 둔다.
- 두 번 탭 → 원래 크기(`reset`), 화면 크기 변경 시 배율 유지(`resized`).

### 정답 자료 (에셋 팩과 같은 위치인지)

에셋 팩이 미리보기 이미지(`preview/room_example_1080.png`)를 만든 코드(`source/package.py` 117~139줄)를 읽어 계산 방식과 그리는 순서를 확인했다. 앱 계산 방식과 같다.

`tools/make_room_layout_fixture.py`로 배치 예시 54개의 **정답 위치·크기·그리는 순서**를 매니페스트 원래 값(소수 끝까지)으로 계산해 `app/src/test/resources/decor/example_layout.json`에 넣었다(4단계에서 디버그 화면도 쓰도록 `app/src/debug/assets/decor/example_layout.json`으로 옮김). 54개에 4방향이 모두 들어 있다(0° 43, 90° 5, 180° 5, 270° 1). 앱 JSON은 소수 4자리로 줄였으므로, 그 반올림 때문에 위치가 틀어지지 않는지도 함께 확인된다.

### 바뀐 파일

| 파일 | 변경 |
| --- | --- |
| `app/src/main/java/com/dailyquest/app/data/decor/RoomLayout.kt` | 새 파일 |
| `app/src/main/java/com/dailyquest/app/ui/decor/RoomViewport.kt` | 새 파일 |
| `app/src/test/java/com/dailyquest/app/data/decor/RoomLayoutTest.kt` | 새 파일 (14개) |
| `app/src/test/java/com/dailyquest/app/ui/decor/RoomViewportTest.kt` | 새 파일 (9개) |
| `app/src/test/resources/decor/example_layout.json` | 새 파일 (정답 자료) |
| `tools/make_room_layout_fixture.py` | 새 파일 |

다른 팀원 파일은 건드리지 않았다.

### 테스트가 확인하는 것

`RoomLayoutTest`
| 테스트 | 확인 내용 |
| --- | --- |
| `exampleLayoutPositionsMatchAssetPack` | 54개 모두 그림 위치가 정답과 0.01px 안, 크기는 정확히 같음 |
| `exampleLayoutDrawOrderMatchesAssetPack` | 54개 그리는 순서가 정답과 같음 |
| `exampleLayoutHasNoProblems` | 예시 배치에 방 밖·겹침 없음 |
| `gridAtIsInverseOfGroundPx` | 칸 → 이미지 → 칸이 원래대로 |
| `cellAtFindsCellUnderFingerAndNullOutside` | 손가락 아래 칸, 방 끝 칸, 방 밖·벽 위는 null |
| `snapStartPutsFurnitureCenterUnderFinger` | 드래그 시 칸 맞춤(돌린 소파 포함, 가운데에서 조금 비껴도 같은 칸) |
| `fitsInRoomChecksRotatedSize` | 2×1 책상: 14번 칸 OK, 15번 칸 밖. 돌리면 반대 |
| `overlappingFurnitureAreBothProblems` | 겹친 두 가구 모두 표시, 안 겹친 가구는 아님 |
| `rugMayOverlapButNotLeaveRoom` | 러그 위 테이블·의자 OK, 러그가 방 밖이면 표시 |
| `outOfRoomIsProblemEvenWithoutOverlap` | 방 밖 가구 표시 |
| `rugIsDrawnFirstThenBackToFront` | 러그 → 안쪽 → 앞쪽 순 |
| `hitTestPicksFrontMostFurniture` | 겹친 곳을 누르면 앞 가구, 빈 곳은 null |
| `serverRatioRoundTripsEveryCell` | 0~15칸 모두 왕복, 연동 규칙 예시(0.4375 → 7) |
| `serverRatioRejectsValuesOffTheGrid` | 32칸 시절 반 칸·1.0·음수는 null, 소수 4자리 오차는 허용 |

`RoomViewportTest`
| 테스트 | 확인 내용 |
| --- | --- |
| `fitUsesSmallerSideAndCenters` | 1배 크기와 가운데 맞춤(정사각 화면, 방 화면 칸 358×252) |
| `zoomKeepsPointUnderFingersInPlace` | 확대해도 손가락 사이 지점이 그대로 |
| `zoomStaysBetweenOneAndThree` | 1~3배를 넘지 않음 |
| `panCannotShowEmptySpaceOutsideRoom` | 아무리 끌어도 방 밖 빈 곳이 안 보임 |
| `cannotPanWhenRoomFitsTheView` | 1배에서 방이 화면에 다 들어오면 안 움직임 |
| `resetReturnsToFit` | 두 번 탭 → 처음 상태와 같음 |
| `resizeKeepsZoomAndStaysInBounds` | 화면 크기 바뀌어도 배율 유지, 범위 안 |
| `screenAndContentRoundTrip` | 화면 ↔ 방 좌표 왕복 |
| `fingerPointsAtSameCellWhenZoomed` | 3배 확대 + 이동 상태에서 손가락이 가리키는 칸이 정확함 |

### 테스트가 정말 틀린 코드를 잡는지 확인

일부러 계산을 틀리게 바꿔 돌려 보고 원래대로 되돌렸다.

| 일부러 바꾼 것 | 실패한 테스트 |
| --- | --- |
| 보정값을 빼먹음 | `exampleLayoutPositionsMatchAssetPack` |
| 러그를 먼저 그리지 않음 | `rugIsDrawnFirstThenBackToFront`, `exampleLayoutDrawOrderMatchesAssetPack` |
| 러그도 겹침 검사 | `exampleLayoutHasNoProblems`, `rugMayOverlapButNotLeaveRoom` |

### 테스트 결과

| 종류 | 결과 |
| --- | --- |
| 새 테스트 | 23개 통과 (`RoomLayoutTest` 14, `RoomViewportTest` 9) |
| 전체 단위 테스트 | 483개 통과 |
| 빌드 | 성공, 새 컴파일 경고 없음 |

화면 변화가 없어 UI 테스트는 돌리지 않았다.

### 직접 확인하는 방법

```
./gradlew testDebugUnitTest --tests "com.dailyquest.app.data.decor.*" --tests "com.dailyquest.app.ui.decor.*"
```

---

## 3단계. 서버 연결

### 서버 규칙 확인 (backend `origin/main` 7bf9e02, `DecorService.kt`)

- `PUT /rooms/{roomId}/decorations`는 **방 전체를 요청 내용으로 바꾼다.** 요청에 없는 가구는 지워진다.
- `backgroundItemId`를 null로 보내면 **배경도 지운다.** 그래서 앱은 항상 배경을 채워 보낸다(한 번도 저장 안 한 방이면 기본 방).
- 같은 가구는 "방 멤버 중 그 가구를 가진 사람 수"까지만 놓을 수 있다. 넘으면 403 `ITEM_NOT_OWNED_IN_ROOM`이고, 서버 문구에 가구 이름과 개수가 들어 있다.
- 멤버가 아니면 403 `FORBIDDEN`, 방이 없으면 404 `ROOM_NOT_FOUND`, 판매 종료·가구 아님은 404 `ITEM_NOT_FOUND`.
- 기본 방(FLOOR)도 "배치 가능 목록"에 들어 있다. 그래서 기본 방 `itemId`는 이 목록에서 `code = room_16x16`로 찾는다(따로 `GET /users/me/items`를 부르지 않는다).

### 무엇을 만들었나

| 파일 | 내용 |
| --- | --- |
| `data/remote/dto/DecorDtos.kt` (새 파일) | 서버 응답·요청 형식 그대로 |
| `data/remote/DqApi.kt` | `getDecorations`, `saveDecorations`(PUT), `listAvailableDecor` 추가. PUT은 같은 요청을 다시 보내도 결과가 같아서 자동 재전송을 막지 않는다 |
| `data/remote/ApiError.kt` | `ITEM_NOT_OWNED_IN_ROOM` 코드 추가 |
| `data/decor/DecorRepository.kt` (새 파일) | 조회·배치 가능 목록·저장, 서버 값 ↔ 칸 좌표 변환, 오류 문구 |
| `data/auth/AppContainer.kt` | `decorRepository` 한 줄 추가 |
| `androidTest/.../TestDQApplication.kt` | FakeApi에 새 API 3개, 가짜 방 꾸미기 상태 `TestDecorCalls` (5~7단계 UI 테스트용) |

**`DecorRepository`가 하는 일**
- `load(roomId, catalog)`: 서버 배치를 칸 좌표(`GridPlacement`)로 바꾼 `RoomDecor`. 한 번도 저장 안 한 방은 `background = null`.
- `available(roomId, catalog)`: 배경(WALL·FLOOR)과 가구로 나누고 기본 방을 찾는다. 앱이 모르는 가구는 `spec = null`(놓을 수 없음).
- `save(decor, catalog, defaultBackground)`: 칸 → 서버 비율, 방향 → 각도, `z`는 앱의 그리는 순서(러그 0, 다른 기기가 z만 봐도 같은 모습이 되게).
- **앱이 그릴 수 없는 배치는 지우지 않고 그대로 둔다**(`KeptPlacement`). 앱이 모르는 새 가구, 칸에 안 맞는 위치(예전 32칸 값), 90도 단위가 아닌 방향, 방 밖으로 나간 위치. 저장할 때 받은 값 그대로 다시 보낸다. 저장이 방 전체를 바꾸는 방식이라 빼고 보내면 서버에서 지워지기 때문이다.
- `DecorErrorMessages`: 개수 초과는 서버 문구(가구 이름 포함)를 그대로, 나머지는 안내 문구.

배치 정보(`RoomCatalog`)는 앱 assets에서 읽는데 `AppContainer`에는 Context가 없다. 그래서 화면 쪽이 `RoomCatalogs.load(context)`로 읽어 저장소 함수에 넘긴다.

### 실서버 확인 (읽기만)

test02 계정의 방 9·10번을 조회했다. 서버 데이터는 바꾸지 않았다(저장 확인은 8단계).
- `GET /rooms/{id}/decorations`: 필드 `roomId`, `background`, `placements`, `updatedAt`, `updatedBy`. 두 방 모두 저장한 적 없어 `background = null`, 배치 0개.
- `GET /rooms/{id}/decorations/available`: 필드 `item`(`itemId`, `name`, `category`, `imageUrl`, `modelUrl`, `code`), `ownerCount`, `placedCount`, `owners`. 두 방 모두 기본 방(`room_16x16`, FLOOR, 보유 2명)만 있다.
- 앱 DTO의 필드 이름과 모두 같다.

### 다른 팀원 파일

| 파일 | 변경 |
| --- | --- |
| `DqApi.kt`, `ApiError.kt`, `AppContainer.kt` | 추가만 (기존 코드 변경 없음) |
| `TestDQApplication.kt` | FakeApi에 메서드 3개 추가, `TestDecorCalls` 추가 |

### 테스트가 확인하는 것 (`DecorRepositoryTest`, MockWebServer, 서버 `DecorDtos.kt` 형식)

| 테스트 | 확인 내용 |
| --- | --- |
| `loadTurnsServerRatiosIntoCells` | 0.25·0.375·90° → 칸 (4, 6)·90°, 연동 규칙 예시 (0.4375, 0.1875) → (7, 3), 배경·수정한 사람 |
| `neverSavedRoomHasNoBackground` | 저장 안 한 방은 배경 null, 배치 없음 |
| `placementsTheAppCannotDrawAreKeptNotDropped` | 모르는 가구·32칸 시절 값·45°·방 밖 4개는 버리지 않고 보관 |
| `availableSplitsBackgroundFromFurniture` | 기본 방은 배경으로, 가구만 목록에. 보유자 수·놓인 수, 모르는 가구는 spec 없음 |
| `saveSendsRatiosRotationDrawOrderAndDefaultBackground` | 보낸 요청: 기본 방 ID, 칸 → 0.4375·0.1875, 270°, z(러그 0·의자 1), 보관한 배치는 받은 값 그대로 |
| `saveKeepsCurrentBackgroundOverDefault` | 이미 배경이 있으면 그 배경을 보냄 |
| `saveReturnsWhatServerStored` | 저장 응답을 다시 칸 좌표로 |
| `everyCellAndDirectionSurvivesSaveAndLoad` | 16칸 × 4방향 모두 저장(소수 4자리) → 조회 후 같은 칸·방향 |
| `tooManyCopiesShowsServerMessageWithItemName` | 개수 초과 시 서버 문구 그대로 |
| `otherErrorsHaveFriendlyMessages` | 멤버 아님·방 없음·판매 종료 문구 |
| `networkFailureIsReportedNotThrown` | 서버가 꺼져 있으면 앱이 멈추지 않고 네트워크 오류로 알림 |

### 테스트 결과

| 종류 | 결과 |
| --- | --- |
| `DecorRepositoryTest` | 11개 통과 |
| 전체 단위 테스트 | 494개 통과(기존 483 + 새 11) |
| 빌드, UI 테스트 컴파일 | 성공, 새 컴파일 경고 없음 |

화면 변화가 없어 UI 테스트는 돌리지 않았다(FakeApi는 컴파일로 확인).

### 직접 확인하는 방법

```
./gradlew testDebugUnitTest --tests "com.dailyquest.app.data.decor.*"
```

---

## 4단계. 방 보여주기 + 두 손가락 확대

이미 놓인 가구를 방 위 정확한 자리에 **보여주기만** 한다. 놓기·옮기기는 6단계.

### 무엇을 만들었나

**`ui/decor/RoomView.kt`**
- 방 그림 위에 가구 그림을 2단계 계산(`RoomLayout`)대로 그린다. 방과 가구에 같은 배율을 쓰고, 가구 그림 왼쪽 위는 방 이미지 기준 정수 픽셀로 맞춘다(에셋 팩도 미리보기를 만들 때 반올림해서 붙였다).
- 방향은 서버 규칙대로 `_r090` 등의 그림 파일을 고른다(그림을 돌리지 않음).
- 두 손가락: 1~3배 확대·축소(+ 같이 이동). 확대한 상태에서 한 손가락: 이동. 두 번 탭: 원래 크기.
- **1배에서 한 손가락으로 끄는 동작은 가로채지 않는다.** 5단계에서 방 화면(세로 스크롤) 안에 넣어도 화면 스크롤이 막히지 않게 하기 위해서다.
- 그림을 받는 동안 진행 표시, 하나라도 실패하면 "다시 시도". 받은 그림부터 하나씩 바로 그린다.
- 확대 상태(`RoomViewState`)를 밖에 둘 수 있어, 6단계 편집 화면이 읽거나 되돌릴 수 있다.

**`ui/decor/DecorImages.kt`** (그림 캐시)
- 기기 저장: STEP 9의 GLB 디스크 캐시(`CachingAvatarModelFetcher`)를 그대로 쓰고 파일 검사만 PNG로 바꿨다. 폴더 `cacheDir/decor_images`, 최대 30MB(지금 전체 약 3MB). S3 가구 파일은 바뀌지 않으므로(1년 immutable) 서버에 다시 묻지 않는다.
- 메모리: 읽은 그림을 24MB까지 보관(방 그림 약 3.5MB, 가구는 수십 KB).

**STEP 9 파일 변경 (내 파일, 기본 동작 그대로)**
- `HttpAvatarModelFetcher`, `CachingAvatarModelFetcher`에 "받은 파일 검사" 옵션 추가. 기본값은 지금처럼 GLB라 아바타 동작은 같다(기존 STEP 9 테스트 그대로 통과). `PngFormat` 추가.

**디버그 전용 "DQ 방 테스트" 화면** (`app/src/debug`, 런처 아이콘 따로)
- 에셋 팩 배치 예시 54개를 실제 S3 그림으로 그린다. 예시 54개 / 빈 방 / 원래 크기 / 캐시 비우기.
- 상태 줄: 확대 배율, 가구 수, 기기 캐시 파일 수·크기, 다 그릴 때까지 걸린 시간.
- 배치 예시 파일은 `app/src/debug/assets/decor/example_layout.json`으로 옮겼다(단위 테스트 `RoomLayoutTest`와 같은 파일을 쓴다).

### S3 파일 확인

S3의 방 1장 + 가구 200장을 내려받아 에셋 팩 파일과 비교했다. **201개 모두 바이트까지 같다.** 그래서 에셋 팩 미리보기 이미지를 정답으로 쓸 수 있다.

### 바뀐 파일

| 파일 | 변경 |
| --- | --- |
| `app/src/main/java/com/dailyquest/app/ui/decor/RoomView.kt` | 새 파일 |
| `app/src/main/java/com/dailyquest/app/ui/decor/DecorImages.kt` | 새 파일 |
| `app/src/main/java/com/dailyquest/app/data/avatar/AvatarModelFetcher.kt` | 파일 검사 옵션, `PngFormat` 추가 (기본값 GLB 그대로) |
| `app/src/main/java/com/dailyquest/app/data/avatar/CachingAvatarModelFetcher.kt` | 파일 검사 옵션 추가 (기본값 GLB 그대로) |
| `app/src/debug/AndroidManifest.xml` | "DQ 방 테스트" 런처 추가 |
| `app/src/debug/java/com/dailyquest/app/debug/RoomDecorTestActivity.kt` | 새 파일 |
| `app/src/debug/java/com/dailyquest/app/debug/ExampleRoomLayout.kt` | 새 파일 |
| `app/src/debug/assets/decor/example_layout.json` | `app/src/test/resources/decor/`에서 옮김 |
| `app/src/test/java/com/dailyquest/app/data/decor/RoomLayoutTest.kt` | 배치 예시 파일 경로만 변경 |
| `tools/make_room_layout_fixture.py` | 출력 경로 안내만 변경 |
| `app/src/test/java/com/dailyquest/app/data/avatar/PngFileCacheTest.kt` | 새 파일 (단위 3개) |
| `app/src/androidTest/java/com/dailyquest/app/ui/decor/RoomViewTest.kt` | 새 파일 (UI 7개) |
| `app/src/androidTest/java/com/dailyquest/app/debug/RoomExampleRenderTest.kt` | 새 파일 (UI 1개, 픽셀 비교) |
| `app/src/androidTest/java/com/dailyquest/app/debug/RoomDecorTestScreenTest.kt` | 새 파일 (UI 1개) |
| `app/src/androidTest/assets/decor/room_example_1080.png` | 에셋 팩 미리보기(정답 이미지, 650KB) |

다른 팀원 파일은 건드리지 않았다. 아직 방 화면에는 연결하지 않았다(5단계).

### 테스트가 확인하는 것

`RoomViewTest` (가짜 그림, 인터넷 없이)
| 테스트 | 확인 내용 |
| --- | --- |
| `pinchZoomsAndDoubleTapReturnsToOriginalSize` | 손가락 사이를 2배로 벌리면 2배, 두 번 탭하면 1배 |
| `zoomStopsAtThreeTimes` | 크게 벌려도 3배에서 멈춤 |
| `oneFingerAtNormalSizeScrollsTheScreenNotTheRoom` | 세로 스크롤 안에 넣고 1배에서 위로 끌면 **화면이 스크롤**되고 방은 그대로 |
| `oneFingerWhenZoomedMovesTheRoomNotTheScreen` | 확대 상태에서 위로 끌면 **방이 움직이고** 화면은 스크롤되지 않음 |
| `notZoomableIgnoresPinch` | 확대를 끈 상태에서는 두 손가락에 반응하지 않음 |
| `showsLoadingUntilImagesArrive` | 그림을 받는 동안 진행 표시, 다 받으면 사라짐 |
| `failedImageShowsRetryAndRetryLoadsAgain` | 실패하면 "다시 시도", 누르면 다시 받아 그림 |

`RoomExampleRenderTest` (실제 S3 그림, 인터넷 필요)
- 배치 예시 54개를 1080×810px(1배)로 그려 화면을 찍고, 에셋 팩 미리보기와 픽셀 비교(미리보기가 불투명한 곳만, 색 차이 8 이하를 "같음"으로).
- 결과: **일치 100.00%, 평균 색 차이 0.033**(0~255 기준).
- 같은 테스트에서 가구 없이 빈 방만 그려 비교하면 일치 74.46%, 평균 차이 10.97. 비교가 가구 자리를 실제로 구분한다는 뜻이다.
- 기준: 일치 99.5% 이상, 평균 차이 1.0 이하.

`RoomDecorTestScreenTest` (실제 S3)
- 디버그 화면이 예시 54개를 다 그리면 "준비됨", 빈 방 → 다시 예시 → 원래 크기 버튼이 동작한다.

`PngFileCacheTest` (단위)
- PNG 헤더 판별, PNG 검사로 만든 다운로더는 PNG를 받고 GLB를 거절, PNG 캐시는 다시 받지 않고 깨진 파일은 다시 받음.

### 테스트가 정말 틀린 코드를 잡는지 확인

`RoomView`가 모든 가구를 0° 그림으로 그리도록 일부러 바꿔 `RoomExampleRenderTest`를 돌렸다. 일치율이 96.07%(평균 차이 1.64)로 떨어져 **실패했다**(돌린 가구 11개의 그림이 달라짐). 확인 후 원래 코드로 되돌렸다.

### 직접 측정

에뮬레이터(Pixel 8, API 35)에서 디버그 "DQ 방 테스트" 화면:
| 상황 | 다 그릴 때까지 |
| --- | --- |
| 처음(인터넷에서 53개, 1.0MB) | 약 4.0초 |
| 다음부터(기기 캐시) | 약 0.2초 |

파일이 53개인 이유: 방 1장 + 예시에 쓰인 서로 다른 가구 그림 52장(같은 가구·같은 방향은 한 번만 받는다).

### 테스트 결과

| 종류 | 결과 |
| --- | --- |
| 단위 테스트 | 497개 통과(기존 494 + 새 3) |
| 새 UI 테스트 | 9개 통과 (`RoomViewTest` 7, `RoomExampleRenderTest` 1, `RoomDecorTestScreenTest` 1) |
| 전체 UI 테스트 | 121개 통과(새 9개 포함), 강제 종료 0 |
| 빌드 | 성공, 새 컴파일 경고 없음 |

### 직접 확인하는 방법

1. 디버그 빌드 설치 → 런처의 **"DQ 방 테스트"** 아이콘
2. 방과 가구 54개가 에셋 팩 미리보기와 같은 모습인지 본다.
3. 두 손가락으로 벌려 확대(3배까지), 확대한 상태에서 한 손가락으로 끌어 이동, 두 번 탭해 원래 크기.
4. "캐시 비우기" 후 상태 줄의 시간(인터넷)과, 앱을 다시 켰을 때의 시간(기기 캐시)을 비교한다.

UI 테스트만 돌리려면:
```
./gradlew connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.dailyquest.app.ui.decor.RoomViewTest,com.dailyquest.app.debug.RoomExampleRenderTest,com.dailyquest.app.debug.RoomDecorTestScreenTest"
```

---

## 5단계. 방 화면에 연결

방 화면의 고정 일러스트(`RoomInterior`) 자리에 서버에 저장된 실제 방을 보여준다.

### 무엇을 만들었나

**`ui/decor/RoomDecorCard.kt`** (새 파일)
- 멤버: `GET /rooms/{id}/decorations`로 받은 배치를 그린다. 한 번도 저장하지 않은 방은 기본 방.
  카드 안쪽 아래 작은 띠에 "마지막으로 꾸민 사람: 닉네임" 또는 "아직 꾸미지 않은 방이에요. 가구를 놓아 보세요.".
  오른쪽 아래 "방 꾸미기" 버튼(지금은 "편집 화면은 곧 열려요" 안내, 6단계에서 편집 화면 연결).
- 비멤버: 꾸미기 API가 멤버 전용(403)이라 **부르지 않고** 기본 방만 보여준다. "방에 참여하면 멤버들이 꾸민 모습을 볼 수 있어요.".
- 앱이 모르는 가구가 있으면 "앱을 업데이트하면 보이는 가구가 N개 있어요."(3단계의 보관 규칙).
- 불러오기 실패: 안내 문구 + "다시 시도". 다시 받다가 실패해도 이전에 받은 모습은 그대로 보여준다.
- **방을 한 번 탭하면 "방 크게 보기" 창**: 두 손가락 확대·이동, 두 번 탭 되돌리기. 카드 안에서는 확대하지 않는다(방 화면이 세로 스크롤이라 손가락 동작이 겹치지 않게).
- **카드 크기는 예전 고정 일러스트와 같은 비율(358:252)이다.** 방 화면의 아래쪽(탭·미션 목록) 위치가 바뀌지 않게 하기 위해서다. 방 그림(4:3)은 그 안에 맞춰 넣어 양옆에 좁은 빈 곳이 생긴다. 안내 문구도 카드 아래가 아니라 카드 안쪽에 넣었다(아래 "기존 테스트 실패와 수정" 참고).

**`ui/decor/RoomDecorViewModel.kt`** (새 파일): 멤버 여부가 정해지거나 바뀔 때(참여 직후·나감) 다시 판단한다. 같은 상태로 다시 불려도 또 부르지 않는다.

**`RoomView` 추가**: 한 번 탭 동작(`onTap`), 화면 읽기용 설명("방, 가구 N개").

**`DecorRepository.DEFAULT_ROOM_IMAGE_URL`**: 기본 방 그림 주소. 연동 규칙 11-2의 파일 주소 규칙(`items/furniture/{code}/{code}.png`)으로 만든다. 비멤버는 배치 가능 목록도 부를 수 없어서 이 주소를 쓴다.

**`AppContainer.decorImages`** 옵션: 그림을 가져오는 곳. 앱은 기본(S3 + 기기 캐시), UI 테스트는 빈 그림을 주는 가짜를 넣어 인터넷 없이 돈다(STEP 9의 `avatar3dEnabled`와 같은 방식).

ViewModel은 카드 안에서 방 ID별로 만든다. 그래서 내비게이션 파일(`NavGraph.kt`, `Routes.kt`)은 건드리지 않았다.

### 다른 팀원 파일

| 파일 | 담당 | 변경 |
| --- | --- | --- |
| `ui/screens/RoomScreen.kt` | 홈·방 | `RoomInterior(...)` 블록 하나를 `RoomDecorCard(...)`로 교체, import 1줄 교체. 다른 곳은 그대로 |
| `data/auth/AppContainer.kt` | (공용) | `decorImages` 옵션 추가 |
| `androidTest/.../TestDQApplication.kt` | (공용) | 빈 그림 공급, `TestDecorCalls.getRequests`·`seed` 추가 |

`RoomInterior`는 Mock 방·B 흐름 화면에서 계속 쓰여 지우지 않았다.

### 바뀐 파일

| 파일 | 변경 |
| --- | --- |
| `app/src/main/java/com/dailyquest/app/ui/decor/RoomDecorCard.kt` | 새 파일 |
| `app/src/main/java/com/dailyquest/app/ui/decor/RoomDecorViewModel.kt` | 새 파일 |
| `app/src/main/java/com/dailyquest/app/ui/decor/RoomView.kt` | 한 번 탭, 화면 읽기 설명 |
| `app/src/main/java/com/dailyquest/app/data/decor/DecorRepository.kt` | 기본 방 그림 주소 상수 |
| `app/src/main/java/com/dailyquest/app/data/auth/AppContainer.kt` | `decorImages` 옵션 |
| `app/src/main/java/com/dailyquest/app/ui/screens/RoomScreen.kt` | 고정 일러스트 → 실제 방 카드 |
| `app/src/androidTest/java/com/dailyquest/app/TestDQApplication.kt` | 빈 그림, 조회 기록·미리 저장 |
| `app/src/test/java/com/dailyquest/app/ui/decor/RoomDecorViewModelTest.kt` | 새 파일 (단위 6개) |
| `app/src/androidTest/java/com/dailyquest/app/RoomDecorCardTest.kt` | 새 파일 (UI 6개) |

### 테스트가 확인하는 것

`RoomDecorViewModelTest` (단위)
| 테스트 | 확인 내용 |
| --- | --- |
| `nonMemberDoesNotCallServerAndShowsDefaultRoom` | 비멤버는 서버를 부르지 않고 기본 방 |
| `memberLoadsDecorAndUsesSavedBackground` | 멤버는 조회, 저장된 배경·마지막 수정한 사람 |
| `neverSavedRoomUsesDefaultRoomImage` | 저장 안 한 방은 기본 방 그림 |
| `failureKeepsPreviousDecorAndRetryLoadsAgain` | 실패해도 이전 모습 유지, 다시 시도로 회복 |
| `forbiddenShowsMemberOnlyMessage` | 403이면 "이 방의 멤버만…" |
| `becomingMemberLoadsAndLeavingClears` | 참여 직후 조회, 같은 상태 반복은 조회 안 함, 나가면 비움 |

`RoomDecorCardTest` (UI, 앱 전체 + 가짜 서버, 방 9101)
| 테스트 | 확인 내용 |
| --- | --- |
| `neverSavedRoomShowsDefaultRoomEmptyAndDecorateButton` | 가구 0개, "아직 꾸미지 않은 방", 방 꾸미기 버튼, 서버 조회함 |
| `savedPlacementsAreDrawnWithLastEditor` | 저장된 가구 2개(책상 90°, 러그), "마지막으로 꾸민 사람: 지원" |
| `furnitureTheAppDoesNotKnowIsCountedNotDrawn` | 모르는 가구는 그리지 않고 "앱을 업데이트하면 보이는 가구가 1개" |
| `loadFailureShowsRetryAndRetryLoads` | 실패 → 다시 시도 → 정상 |
| `tappingRoomOpensFullViewAndCloseReturns` | 방 탭 → 크게 보기 → 닫기 → 방 화면 |
| `decorateButtonTellsEditorIsComing` | 방 꾸미기 버튼 → 안내 |

### 기존 테스트 실패와 수정

처음에는 카드를 방 그림 비율(4:3)로 만들고 안내 문구를 카드 **아래**에 두었다. 그러자 카드가 예전 일러스트보다 약 60dp 커졌고, 전체 UI 테스트에서 **팀원 테스트 `MissionFlowTest.roomMissionTabShowsOnlyThatRoomsMissionsAndExpiredScope`가 실패**했다(127개 중 1개). 아래로 밀린 "마감" 버튼이 하단 메뉴 바 밑에 놓여 터치가 메뉴 바에 닿았기 때문이다. 같은 이유로 내 테스트 `loadFailureShowsRetryAndRetryLoads`의 "다시 시도" 터치도 반응이 없었다.

팀원 테스트를 고치는 대신 **카드 크기를 예전 일러스트와 같게(358:252)** 하고 안내 문구를 카드 안쪽으로 옮겼다. 방 화면의 다른 부분 위치가 STEP 10 전과 같아진다. 그 뒤 두 테스트 모두 원래대로(화면 터치) 통과한다.

"방 꾸미기" 버튼은 카드 바탕색과 같은 색(Soft)이면 버튼처럼 보이지 않아, 위쪽 "방 설정" 버튼과 같은 테두리 모양(Outline)으로 했다.

화면 확인: 임시 테스트로 방 화면(가짜 서버, 빈 그림)을 찍어 카드 크기·안내 띠·버튼 위치를 확인했다(임시 테스트 파일은 지움). 실제 방 그림이 들어간 화면은 8단계(실서버)에서 확인한다.

### 실제 화면 확인 (못 함)

test02 계정으로 방 화면을 열어 보려 했지만, 이 계정은 성별이 없어서 로그인 직후 캐릭터 만들기 화면에서 멈췄다. 성별은 한 번 정하면 되돌릴 수 없어 여기서 멈추고 앱 데이터를 지웠다(서버의 test02 성별은 그대로 없음을 확인). 검증용 계정(STEP 9에서 만든 계정)은 방이 없다. 실서버 화면 확인은 8단계에서 한다.

### 테스트 결과

| 종류 | 결과 |
| --- | --- |
| 단위 테스트 | 503개 통과(기존 497 + 새 6) |
| 새 UI 테스트 | `RoomDecorCardTest` 6개 통과 |
| 방 화면 기존 UI 테스트(`RoomFlowTest`, `MissionFlowTest`) + `RoomViewTest` | 통과 |
| 전체 UI 테스트 | 127개 통과(새 6개 포함, 카드 크기 수정 후), 강제 종료 0 |

### 직접 확인하는 방법

1. 앱에서 내가 멤버인 방에 들어간다 → 멤버 목록 아래에 실제 방(저장 안 했으면 빈 기본 방)과 "방 꾸미기" 버튼.
2. 방을 한 번 탭 → "방 크게 보기"에서 두 손가락 확대·이동, 두 번 탭, 닫기.
3. 공개방에 참여하지 않은 상태로 보면 기본 방과 "방에 참여하면…" 안내(서버 조회 없음).

---

## 6단계. 꾸미기 편집 화면 (가구 놓기)

방 화면의 "방 꾸미기" 버튼으로 여는 편집 화면. 저장은 7단계에서 연결한다(이번 단계에서는 화면 안에서만 바뀐다).

### 화면 구성

| 위치 | 내용 |
| --- | --- |
| 위 | 방. 가구를 잡고 끌면 칸에 맞춰 옮겨진다. 두 손가락 확대·축소, 확대한 뒤 빈 곳을 끌면 화면 이동 |
| 방 아래 | "가구 N개 · 겹치거나 방 밖 N개", 확대했을 때만 "원래 크기" 버튼 |
| 가운데 | 선택한 가구 이름 + "돌리기"·"빼기". 선택이 없으면 사용법 안내 |
| 아래 | "가구 넣기": 방 멤버가 가진 가구(바닥·벽 제외). 그림·이름·"남은 N개". 누르면 방 가운데 가까운 빈자리에 놓이고 선택된다 |

표시
- 선택한 가구: 바닥 칸을 초록으로 채우고 가구 위에 초록 테두리.
- 겹치거나 방 밖인 가구: 바닥 칸과 가구 그림을 빨갛게. 선택한 가구가 문제면 테두리도 빨강.

### 규칙

- **남은 개수 = 멤버 중 가진 사람 수 − 지금 놓인 수.** 앱이 그릴 수 없어 그대로 둔 배치(3단계 보관 규칙)도 놓인 수에 센다. 서버가 저장할 때 같은 규칙으로 검사하기 때문이다.
- 남은 개수가 0이면 카드를 흐리게 하고, 누르면 "○○은(는) 방 멤버가 가진 N개까지만 놓을 수 있어요." 안내.
- 앱이 모르는 가구(배치 정보 없음)는 "업데이트 필요"로 흐리게, 누르면 안내.
- 빈자리 찾기: 방 가운데에서 가까운 칸부터, 0° 먼저 보고 안 되면 다른 방향. 러그는 다른 가구 위에도 놓인다. 자리가 없으면 안내.
- 끌기: 잡은 지점과 가구 바닥 가운데의 거리를 유지하며 칸에 맞춘다. **반 칸이 넘게 움직여야 칸이 바뀐다**(살짝 떨리는 것은 무시). 방 밖·겹침이어도 일단 옮기고 빨갛게 보여준다(저장은 7단계에서 막는다).
- 선택한 가구가 다른 가구 뒤에 가려져 있어도, 겹친 곳을 누르면 선택한 가구를 먼저 잡는다.
- 돌리기: 90도씩. 돌려서 방 밖으로 나가면 방 안으로 밀어 넣는다.
- 바뀜 여부(`dirty`): 불러온 배치와 비교한다. 넣었다 빼거나 원래 자리로 돌려놓으면 "바뀌지 않음"이다(7단계 나가기 확인에 쓴다).

### 무엇을 만들었나

| 파일 | 내용 |
| --- | --- |
| `ui/decor/RoomDecorEditScreen.kt` (새 파일) | 편집 화면, 가구 목록 카드, 가구 그림(`DecorThumb`, 잘리지 않게 맞춤) |
| `ui/decor/RoomDecorEditViewModel.kt` (새 파일) | 불러오기(꾸미기 + 배치 가능 목록 동시), 넣기·선택·옮기기·돌리기·빼기, 문제 검사, 바뀜 여부 |
| `ui/decor/RoomView.kt` | 편집 모드: `RoomEditor`(선택·이동 알림), 선택·문제 표시, 편집용 손가락 동작 |
| `data/decor/RoomLayout.kt` | `findFreeSpot` (빈자리 찾기) |
| `navigation/Routes.kt` (팀원 B) | `ROOM_DECOR = "room/{roomId}/decor"`, `roomDecor(roomId)` 추가 |
| `navigation/NavGraph.kt` (팀원 B) | 편집 화면 등록 1곳, 서버 연동 경로 목록에 추가(“샘플 데이터” 표시가 안 뜨게) |
| `ui/screens/RoomScreen.kt` (홈·방) | "방 꾸미기" 버튼 → 편집 화면으로 이동 (5단계의 "곧 열려요" 안내 대신) |

### 다른 팀원 파일

| 파일 | 담당 | 변경 |
| --- | --- | --- |
| `Routes.kt` | 팀원 B | 경로 상수 1개 + 함수 1개 추가 |
| `NavGraph.kt` | 팀원 B | `composable(Routes.ROOM_DECOR)` 1곳, `SERVER_BACKED_ROUTES`에 1개, import 3줄 |
| `RoomScreen.kt` | 홈·방 | `onDecorate` 한 줄 |

### 테스트가 확인하는 것

`FreeSpotTest` (단위)
| 테스트 | 확인 내용 |
| --- | --- |
| `emptyRoomPutsFurnitureInTheMiddle` | 빈 방이면 가운데 근처, 0° |
| `skipsOccupiedCellsAndNeverOverlaps` | 책상 20개를 연달아 놓아도 겹침·방 밖 없음 |
| `rugMayGoOnTopOfFurniture` | 꽉 찬 방에도 러그는 놓임 |
| `nullWhenRoomIsFull` | 꽉 차면 null |
| `triesOtherDirectionWhenOnlyThatFits` | 1칸 너비 통로만 있으면 2×1 책상을 90°로 놓음 |

`RoomDecorEditViewModelTest` (단위)
| 테스트 | 확인 내용 |
| --- | --- |
| `loadsSavedFurnitureAndChoices` | 저장된 가구·배경·목록을 불러옴, 바뀐 것 없음, 남은 개수 |
| `loadFailureShowsErrorAndRetryWorks` | 불러오기 실패 → 다시 시도 |
| `addPlacesNearMiddleSelectsItAndCountsDown` | 넣으면 가운데 근처·선택됨·남은 개수 줄어듦·바뀜 |
| `cannotAddMoreThanOwnersHave` | 가진 사람 수를 넘으면 안 들어가고 안내 |
| `keptPlacementsCountTowardTheLimit` | 그릴 수 없는 보관 배치도 개수에 셈 |
| `unknownFurnitureCannotBeAdded` | 모르는 가구는 안내만 |
| `movingOntoAnotherFurnitureMarksBothThenMovingAwayClears` | 겹치면 둘 다 문제, 떨어뜨리면 해제, 방 밖이면 문제 |
| `rotateTurnsAndPushesBackIntoRoom` | 끝에서 돌리면 방 안으로 밀려 들어감, 4번 돌리면 원래 방향 |
| `removeDeletesSelectedAndFreesCount` | 빼면 개수 회복, 넣었다 빼면 바뀌지 않음 |
| `selectAndDeselect` | 선택·해제 |
| `movingBackToOriginalPlaceIsNotDirty` | 원래 자리로 돌려놓으면 바뀌지 않음 |

`RoomEditGesturesTest` (UI, 가구 위치를 계산해 실제로 누르고 끎)
| 테스트 | 확인 내용 |
| --- | --- |
| `pressingFurnitureSelectsIt` | 가구를 누르면 선택 |
| `draggingFurnitureMovesItCellByCell` | 책상 가운데를 3칸 오른쪽·2칸 아래로 끌면 (7,7) → (10,9), 중간 칸도 거침 |
| `smallWiggleDoesNotMoveFurniture` | 반 칸이 안 되게 움직이면 그대로 |
| `tappingEmptyFloorClearsSelection` | 빈 바닥을 누르면 선택 해제 |
| `draggingEmptyFloorAtNormalSizeMovesNothing` | 1배에서 빈 곳을 끌면 아무것도 안 움직이고 선택도 그대로 |
| `pinchZoomsWithoutMovingFurniture` | 두 손가락 확대 중에는 가구가 안 움직임 |
| `whenZoomedDraggingEmptyFloorPansAndFurnitureStillDrags` | 확대 상태: 빈 곳 끌기 = 화면 이동, 가구 끌기 = 한 칸 이동 |

`RoomDecorEditTest` (UI, 앱 전체 + 가짜 서버)
| 테스트 | 확인 내용 |
| --- | --- |
| `emptyRoomShowsTrayWithRemainingCounts` | 가구 0개, 책상·의자 카드, 기본 방은 목록에 없음, 남은 2개·1개 |
| `addRotateRemoveUpdatesRoomAndCounts` | 넣기 → 1개·선택 이름·남은 1개 → 돌리기 → 하나 더 → 남은 0개 → 빼기 → 남은 1개 |
| `cannotPlaceMoreThanMembersOwn` | 의자는 1개까지 |
| `savedFurnitureIsLoadedIntoEditor` | 저장된 가구가 편집 화면에 들어옴, 남은 개수에 반영 |
| `loadFailureShowsRetry` | 불러오기 실패 → 다시 시도 → 정상 |
| `backReturnsToRoom` | 뒤로 → 방 화면 |

`RoomDecorCardTest.decorateButtonTellsEditorIsComing` → `decorateButtonOpensEditor`로 바꿨다("곧 열려요" 안내 대신 편집 화면이 열림).

테스트를 처음 돌렸을 때 실패한 5개는 모두 테스트 쪽 문제였다(위 확인 내용은 고친 뒤 기준):
- 가구 카드는 통째로 누르는 버튼이라 안의 "남은 N개" 글자가 합쳐져 보인다 → 합치기 전 구조에서 찾도록 수정(3개).
- 편집 화면의 방에 붙인 이름표(`decorEditRoom`)가 RoomView의 기본 이름표(`roomView`)를 덮어썼다 → 테스트가 `decorEditRoom`을 찾도록 수정(1개).
- 5단계의 "곧 열려요" 안내 테스트 → 편집 화면이 열리는지로 수정(1개).

### 화면 확인

임시 테스트로 두 장면을 찍어 확인했다(임시 테스트 파일은 지움).
- 실제 S3 그림의 배치 예시에서 침대를 선택하고 테이블을 소파 위로 옮긴 모습: 침대 바닥에 초록 테두리, 겹친 소파·테이블이 빨갛게 표시된다.
- 앱 흐름의 편집 화면(가짜 서버·빈 그림): 방 → 가구 수 → 선택한 가구와 돌리기·빼기 → 가구 목록. 모르는 가구는 흐리게 "업데이트 필요".
- 가구 카드를 누를 때 물결 효과가 둥근 모서리 밖으로 번져 카드 모양으로 잘랐다.

### 테스트 결과

| 종류 | 결과 |
| --- | --- |
| 단위 테스트 | 519개 통과(기존 503 + 새 16) |
| 새 UI 테스트 | `RoomEditGesturesTest` 7개, `RoomDecorEditTest` 6개 통과 |
| 전체 UI 테스트 | 140개 통과(새 13개 포함), 강제 종료 0 |

### 직접 확인하는 방법

1. 내가 멤버인 방 → "방 꾸미기"
2. 아래 가구 목록에서 하나 누르기 → 방 가운데 근처에 놓이고 초록 테두리
3. 가구를 잡고 끌기 → 칸에 맞춰 이동. 다른 가구 위로 가면 빨갛게, "겹치거나 방 밖 N개"
4. "돌리기", "빼기", 두 손가락 확대 후 빈 곳 끌기(화면 이동)
5. 같은 가구를 가진 사람 수보다 많이 넣으려 하면 안내

---

## 7단계. 저장

### 동작

| 상황 | 동작 |
| --- | --- |
| 바뀐 것이 없음 | 오른쪽 위 "저장"이 꺼져 있다(흐림) |
| 겹치거나 방 밖인 가구가 있음 | 서버로 보내지 않는다. 첫 번째 문제 가구를 선택해 보여 주고 "겹치거나 방 밖으로 나간 가구 N개를 먼저 옮겨 주세요." (서버는 칸 겹침을 검사하지 않으므로 앱이 막는다) |
| 저장 성공 | "방을 저장했어요." → 방 화면으로 돌아간다. 방 화면은 다시 보일 때 서버에서 다시 받아 바로 반영된다 |
| 저장 실패 | 편집한 내용을 그대로 두고 안내. 개수 초과(403 `ITEM_NOT_OWNED_IN_ROOM`)는 서버 문구(가구 이름 포함) 그대로. 다시 저장할 수 있다 |
| 저장 중 | 버튼이 "저장 중…"으로 꺼지고, 다시 눌러도 한 번만 보낸다. 뒤로 가기도 응답을 기다린다 |
| 바꾼 채로 나가기(화면 위 뒤로 버튼·기기 뒤로 버튼) | "저장하지 않고 나갈까요? / 바꾼 배치가 사라져요." → "계속 꾸미기" 또는 "나가기" |
| 바꾸지 않고 나가기 | 묻지 않고 나간다 |

보내는 내용은 3단계 `DecorRepository.save` 그대로다. 배경이 없으면(한 번도 저장 안 한 방) 기본 방을 보내고, 앱이 그릴 수 없어 보관한 배치도 받은 값 그대로 함께 보낸다.

### 방 화면 반영

방 화면의 꾸민 방 카드(5단계)가 **화면이 다시 보일 때(ON_RESUME) 다시 받는다.** 편집 화면에서 저장하고 돌아온 경우뿐 아니라, 다른 멤버가 바꾼 뒤 방 화면으로 돌아와도 새 모습이 보인다. 이미 한 번 받은 멤버만 다시 받고, 받는 동안 이전 모습은 그대로 보인다.

### 바뀐 파일

| 파일 | 변경 |
| --- | --- |
| `ui/decor/RoomDecorEditViewModel.kt` | `save()`, `saving`·`saved`·`canSave`, 저장 함수 주입 |
| `ui/decor/RoomDecorEditScreen.kt` | 저장 버튼, 나가기 확인 창, 기기 뒤로 버튼 처리, 저장 후 방 화면으로 |
| `ui/decor/RoomDecorViewModel.kt` | `onResume()` (이미 받은 멤버만 다시 받기) |
| `ui/decor/RoomDecorCard.kt` | 화면이 다시 보일 때 `onResume()` |
| `test/.../ui/decor/RoomDecorSaveTest.kt` | 새 파일 (단위 7개) |
| `androidTest/.../RoomDecorSaveFlowTest.kt` | 새 파일 (UI 5개) |

다른 팀원 파일은 건드리지 않았다.

### 테스트가 확인하는 것

`RoomDecorSaveTest` (단위)
| 테스트 | 확인 내용 |
| --- | --- |
| `nothingChangedMeansSaveIsOff` | 바뀐 것이 없으면 저장 꺼짐, 눌러도 안 보냄 |
| `saveSendsLayoutKeptPlacementsAndDefaultBackground` | 보낸 내용: 방 ID, 가구 2개, 보관 배치, 배경 없으면 기본 방. 성공 후 바뀐 것 없음·안내·선택 해제 |
| `overlappingFurnitureBlocksSaveAndSelectsTheProblem` | 겹치면 안 보내고 첫 문제 가구 선택, "2개를 먼저 옮겨" |
| `failureKeepsEditsAndShowsServerMessage` | 개수 초과 실패: 서버 문구, 편집 유지, 다시 저장 가능 |
| `cannotSaveTwiceWhileSaving` | 저장 중에는 꺼짐, 두 번 눌러도 한 번만 보냄 |
| `editingAfterSaveIsDirtyAgain` | 저장 뒤 다시 바꾸면 다시 "바뀜" |
| `roomCardReloadsWhenShownAgainOnlyAfterFirstLoad` | 방 카드는 한 번 받은 뒤에만 다시 보일 때 다시 받음 |

`RoomDecorSaveFlowTest` (UI, 앱 전체 + 가짜 서버)
| 테스트 | 확인 내용 |
| --- | --- |
| `saveIsOffUntilSomethingChanges` | 처음엔 꺼짐, 가구를 넣으면 켜짐 |
| `savingSendsLayoutAndRoomShowsItAfterReturning` | 저장 → 방 화면으로, 카드에 가구 1개·"마지막으로 꾸민 사람: 테스터". 보낸 요청: 방 9101, 배경 = 기본 방 9001, 책상 1개, 0.0625 단위 위치, 0°. 다시 편집 화면을 열면 저장된 가구가 들어 있고 저장 꺼짐 |
| `saveFailureKeepsEditing` | 실패하면 편집 화면에 그대로, 다시 저장하면 성공 |
| `leavingWithChangesAsksFirst` | 바꾼 채 뒤로 → 확인 창 → "계속 꾸미기"면 그대로, "나가기"면 저장 없이 방 화면 |
| `leavingWithoutChangesDoesNotAsk` | 바꾸지 않았으면 바로 나감 |

### 화면 확인

임시 테스트로 찍어 확인했다(임시 파일은 지움): 바뀐 것이 없을 때 흐린 "저장", 가구를 넣은 뒤 켜진 "저장", 뒤로 버튼을 눌렀을 때 "저장하지 않고 나갈까요?" 창(계속 꾸미기·나가기).

### 테스트 결과

| 종류 | 결과 |
| --- | --- |
| 단위 테스트 | 526개 통과(기존 519 + 새 7) |
| 새 UI 테스트 | `RoomDecorSaveFlowTest` 5개 통과 (편집·카드 테스트와 함께 17개) |
| 전체 UI 테스트 | 145개 통과(새 5개 포함), 강제 종료 0 |

### 직접 확인하는 방법

1. 방 → "방 꾸미기" → 가구 하나 넣기 → 오른쪽 위 "저장" → 방 화면에 바로 보이는지
2. 다시 "방 꾸미기" → 가구를 다른 가구 위로 끌기 → "저장" → 안내와 함께 문제 가구가 선택되는지
3. 무언가 바꾼 뒤 뒤로 → 확인 창 → "계속 꾸미기"/"나가기"

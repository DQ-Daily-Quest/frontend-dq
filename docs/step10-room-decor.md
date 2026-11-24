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
| 4 | 방 보여주기 + 두 손가락 확대 | 이미 놓인 가구(서버에 저장된 배치)를 방 위 정확한 자리에 **보이게만** 한다. 놓기·옮기기는 6단계(방과 가구에 같은 배율, 이미지 기기 캐시). **두 손가락으로 확대·축소(1~3배), 확대했을 때 한 손가락으로 이동, 두 번 탭하면 원래 크기.** 디버그 테스트 화면에서 배치 예시로 확인 | UI: 확대·이동·되돌리기, 화면 캡처를 에셋 미리보기와 비교 | 대기 |
| 5 | 방 화면에 연결 | 방 화면의 고정 일러스트를 실제 배치된 방으로 바꾼다. 저장한 적 없는 방은 기본 방. 방을 누르면 크게 보기(확대 가능) | UI: 조회 결과대로 그려지는지 | 대기 |
| 6 | 꾸미기 편집 화면 (가구 놓기) | "방 꾸미기" → 편집 화면. 사용자가 가구 목록(바닥·벽 제외, 놓을 수 있는 개수 표시)에서 골라 방에 놓기, 드래그로 옮기면 칸에 맞춰짐, 회전, 빼기, 겹치거나 방 밖이면 빨간 표시. **확대 상태에서도 편집 가능: 가구를 잡고 끌면 가구 이동, 빈 곳을 끌면 화면 이동, 두 손가락은 확대** | UI: 놓기·옮기기·회전·겹침 막기, 확대 상태에서 옮기기 | 대기 |
| 7 | 저장 | 저장 요청, 실패 시 되돌리기, 저장 안 하고 나가면 경고, 마지막 수정한 사람 표시 | UI + 단위 | 대기 |
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

`tools/make_room_layout_fixture.py`로 배치 예시 54개의 **정답 위치·크기·그리는 순서**를 매니페스트 원래 값(소수 끝까지)으로 계산해 `app/src/test/resources/decor/example_layout.json`에 넣었다. 54개에 4방향이 모두 들어 있다(0° 43, 90° 5, 180° 5, 270° 1). 앱 JSON은 소수 4자리로 줄였으므로, 그 반올림 때문에 위치가 틀어지지 않는지도 함께 확인된다.

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

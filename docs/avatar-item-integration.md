# Daily Quest 아바타·아이템 연동 정리

2026년 11월 21일 · 진영 · **백엔드 API v0.4 기준** (backend-dq PR #25, `docs/api.md` 3.7, 7.1, 7.1.1)

> 이 문서는 백엔드에 **이미 구현된** 아바타·아이템 API를 프론트 입장에서 정리한 것이다.
> 이전 초안에서 제안했던 내용 중 백엔드가 다르게 정한 것은 맨 아래 [초안 대비 바뀐 점](#초안-대비-바뀐-점)에 있다.

## 핵심 원칙

아이템마다 3D 파일(GLB)이 하나씩 있고, 서버가 그 주소(**`modelUrl`**)를 내려준다. 앱은 착용 중인 슬롯의 `modelUrl` 6~7개를 내려받아 같은 원점에 겹쳐 그린다.

| 담당 | 하는 일 | 갖고 있는 것 |
| --- | --- | --- |
| 3D (Blender) | 파츠마다 GLB를 하나씩 내보내기, 썸네일 렌더링, 아이템 목록표 작성 | 파츠 GLB 111개 (남 57, 여 54), 썸네일 PNG 111장 |
| 백엔드 | 아이템·보유·착용·코인·성별 저장과 검증, `modelUrl`·`imageUrl` 내려주기 | DB, API, S3 |
| 앱 (프론트) | 착용 슬롯의 `modelUrl`을 내려받아 겹쳐 그리기, 상점·꾸미기 UI | 내려받은 GLB 캐시 |

- 아이템은 전부 낱개로 사고 장착한다. 사용자는 슬롯(피부, 눈, 머리, 상의, 하의, 신발, 모자)마다 하나씩 골라 자유롭게 조합한다.
- 아이템은 **체형(성별) 전용**이다. 남자 캐릭터는 남자 아이템만, 여자 캐릭터는 여자 아이템만 사고 입을 수 있다.
- 모든 GLB는 캐릭터에 입힌 위치 그대로 내보내서 원점(발 사이 바닥)이 같다. 앱은 위치나 크기를 따로 맞추지 않고 그대로 겹친다.
- 입어보기는 앱에서만 처리하고, 구매와 착용만 서버에 저장한다.

## 연동 흐름

**1. 성별 정하기와 기본 아이템 지급 (계정당 1회)**

성별은 `gender` 필드이고 값은 `MALE` | `FEMALE`이다. 에셋 폴더 기준으로 `MALE` = boy, `FEMALE` = girl이다. **한 번 정하면 바꿀 수 없다.**

| 경우 | 앱이 할 일 | API |
| --- | --- | --- |
| 이메일 가입 | 회원가입 화면에서 남/여를 고르게 하고 가입 요청에 함께 보낸다 | `POST /auth/signup`에 `gender` |
| 소셜 가입 (Google·Kakao) | 서버가 로그인 순간 계정을 만들어서 `gender`가 `null`이다. 홈으로 가기 전에 성별 선택 화면을 띄운다 | `PUT /users/me/gender` |
| `gender` 없이 가입한 계정, 기존 계정 | 위와 같다 | `PUT /users/me/gender` |

- 앱은 로그인·소셜 로그인·토큰 재발급 응답의 `user.gender`(또는 `GET /users/me`의 `gender`)가 **`null`이면 성별 선택 화면**으로 보낸다. 서버의 `isNewUser`가 아니라 `gender`로 판단해야, 선택 화면에서 앱을 꺼도 다음에 다시 그 화면이 뜬다.
- 성별이 정해지는 순간 서버가 그 체형의 **기본 한 벌**(피부 3종, 눈, 머리, 상의, 하의, 신발, 모자 = 9개)을 지급한다.
- 모자를 뺀 6개 슬롯은 바로 착용된다. **피부는 첫 번째 톤**(남 `DQ_Boy_01_Body`, 여 `DQ_Body`)이 입혀지고, 나머지 두 톤은 보유 상태라 착용 변경으로 바꿀 수 있다. **기본 모자는 지급만 하고 착용하지 않는다.**
- 피부를 가입 화면에서 고르게 하고 싶다면, 성별 확정 직후 `PATCH /users/me`로 피부만 바꿔 입히면 된다.
- 성별을 정하기 전에는 `avatar`의 모든 슬롯이 `null`이고 상점에 아바타 아이템이 나오지 않는다. 이 상태에서는 아바타·상점 화면에 들어가지 못하게 한다.

**2. 앱 실행, 내 아바타 표시**

1. 앱 → 서버: `GET /users/me` → `gender`와 `avatar`(슬롯 → `itemId`)를 받는다.
2. 앱 → 서버: `GET /users/me/items?type=AVATAR` → 보유 아이템 전체(`modelUrl`, `equipped` 포함)를 받는다. 착용 중인 아이템의 `modelUrl`을 여기서 찾는다.
3. 앱: 슬롯마다 `modelUrl`의 GLB를 내려받는다. 주소가 파일마다 고유하고 바뀌지 않으므로 **기기에 영구 캐시**한다.
4. 앱: 받은 GLB 6~7개를 같은 위치, 같은 크기로 겹쳐 그린다.

**3. 다른 사람 아바타 표시 (방 멤버, 피드 등)**

1. 앱 → 서버: `GET /users/{userId}` → 그 사람의 `gender`, `avatar`(아이템 ID 7개)를 받는다.
2. 앱 → 서버: `GET /shop/items/batch?ids=1,4,15,26,31,45` → 아이템 정보와 `modelUrl`을 한 번에 받는다. 여러 명을 그릴 때는 ID를 모아서 한 번에 요청한다(최대 100개).
3. 앱: 내 아바타와 같은 방식으로 겹쳐 그린다.

**4. 상점과 입어보기**

1. 앱 → 서버: `GET /shop/items?type=AVATAR&category=HAT` → **내 체형 아이템만** 나온다. `imageUrl`, `modelUrl`, `owned`가 들어 있다.
2. 앱: `imageUrl`로 썸네일을 표시한다.
3. 입어보기: 앱이 그 아이템의 `modelUrl`을 내려받아 그 슬롯의 모델만 바꾼다. 서버에는 저장하지 않는다.

**5. 구매**

1. 앱 → 서버: `POST /shop/items/{itemId}/purchase` (본문 없음)
2. 서버: 체형 일치, 미보유, 코인 잔액을 확인한 뒤 코인 차감 + 보유 추가 + 거래 기록을 한 트랜잭션으로 처리한다. 같은 요청이 연달아 와도 두 번째는 `ITEM_ALREADY_OWNED`라 코인이 두 번 빠지지 않는다.
3. 앱: 응답의 `remainingCoins`로 코인 표시를 갱신한다.

**6. 착용과 해제**

1. 앱: 그 슬롯의 모델을 먼저 바꾼다.
2. 앱 → 서버: `PATCH /users/me` + `{"avatar": {"hat": 107}}`
3. 서버 검증: 보유하지 않았으면 `ITEM_NOT_OWNED`(403), 체형이 다르면 `ITEM_GENDER_MISMATCH`(409), 슬롯과 카테고리가 다르면 `VALIDATION_ERROR`(400).
4. 실패하면 앱은 이전 착용 상태로 되돌린다.
5. **해제는 모자만 허용한다 (앱에서 막는다).** 서버는 모든 슬롯의 `null`을 받아 주므로, 피부·눈·머리·옷이 없는 아바타가 되지 않도록 앱이 모자 외에는 해제 버튼을 두지 않는다.

## 3D 파일

**저장 위치 (S3, 업로드 완료)**

| 파일 | 주소 |
| --- | --- |
| 남자 파츠 | `https://dq-uploads-prod.s3.ap-northeast-2.amazonaws.com/items/avatar/boy/{code}.glb` |
| 여자 파츠 | `https://dq-uploads-prod.s3.ap-northeast-2.amazonaws.com/items/avatar/girl/{code}.glb` |
| 썸네일 | 같은 폴더의 `{code}.png` (512×512, 투명 배경) |

- API는 **전체 주소**를 내려준다. 앱이 서버 주소를 붙일 필요가 없다(기존 `/files/...` 상대 경로와 다르다).
- 파일이 바뀌면 주소도 바뀐다(`Cache-Control: immutable`, 1년). 앱은 URL을 키로 영구 캐시해도 된다.
- 앱에서 주소를 직접 만들지 말고 항상 API의 `modelUrl`·`imageUrl`을 쓴다.

**내보내기 (3D 담당)**

- Blender의 Scripting 탭에서 `export_parts.py`를 실행하면, 오브젝트마다 GLB 파일이 하나씩 `.blend` 파일 옆 `glb_parts` 폴더에 생긴다.
- 파일 이름은 오브젝트 이름 그대로다. 예: `DQ_Boy_Hat_Cafe.glb`. 이 이름이 아이템의 `code`다.
- 오브젝트를 옮기지 않고 입힌 위치 그대로 내보낸다. 남자 캐릭터로 테스트했을 때 57개 모두 원래 위치와 오차 없이 같았다.
- 내보내기 설정: 선택된 오브젝트, 활성 씬, 모디파이어 적용, +Y 위쪽, 카메라·라이트·애니메이션 제외. 스크립트가 알아서 설정한다.

**파일 크기 (남자 캐릭터)**

| 항목 | 값 |
| --- | --- |
| 파일 수 | 57개 |
| 파일 하나 | 0.35MB (`DQ_Boy_Hat_Campus`) ~ 3.73MB (`DQ_Boy_03_Hair`) |
| 합계 | 43.9MB |
| 한 벌(7개) 내려받기 | 약 8~10MB |

처음 한 번은 한 벌을 다 받아야 해서 느릴 수 있다. 머리(최대 3.7MB), 기본 모자, 기본 상의처럼 큰 파일부터 Decimate로 줄여서 **파일당 1MB 이하**를 목표로 한다. 줄인 파일은 새 이름(예: `_v2`)으로 올리고 백엔드에 `model_url` 수정을 요청한다.

## 백엔드 데이터 모델 (v0.4)

**`users`**

| 컬럼 | 설명 |
| --- | --- |
| `gender` | `MALE` / `FEMALE`, nullable. `null`이면 아직 선택 전. 한 번 정하면 바꿀 수 없다 |
| `avatar_skin`, `avatar_eyes`, `avatar_hair`, `avatar_top`, `avatar_bottom`, `avatar_shoes`, `avatar_hat` | 착용 중인 아이템 ID. 해제하면 `null` |

**`items`**

| 컬럼 | 설명 |
| --- | --- |
| `id`, `name`, `type`, `category`, `price` | `type` = `AVATAR` / `FURNITURE`. 아바타 `category` = `SKIN`, `EYES`, `HAIR`, `TOP`, `BOTTOM`, `SHOES`, `HAT` |
| `code` | 에셋 고유 코드 = GLB 파일 이름 = Blender 오브젝트 이름. UNIQUE. 가구는 `null` |
| `gender` | 착용 가능한 체형. `null`이면 공용(가구 등) |
| `image_url`, `model_url` | S3 전체 주소 |
| `is_default` | 성별 확정 시 자동 지급 |
| `active` | `false`면 상점·착용·batch 조회에서 모두 빠진다 |

**`user_items`** (보유): `user_id`, `item_id`, `acquired_by`(`DEFAULT` / `SHOP`), `acquired_at`. `UNIQUE(user_id, item_id)`.

**`coin_transactions`** (코인 내역): `amount`, `balance_after`, `reason`(`MISSION_REWARD` / `STREAK_BONUS` / `PURCHASE` / `REFUND`), `ref_id`.
`REFUND`는 v0.4에서 임시 아바타 아이템을 회수하면서 산 사람에게 환불할 때 한 번 쓰였다.

## API 명세 (v0.4, 구현 완료)

| 메서드 | 경로 | 용도 |
| --- | --- | --- |
| POST | `/auth/signup` | 이메일 가입. `gender` 선택 입력 (권장) |
| POST | `/auth/login`, `/auth/oauth/{provider}`, `/auth/refresh` | 응답 `user`에 `gender` 포함 |
| PUT | `/users/me/gender` | 성별 확정, 계정당 1회 |
| GET | `/users/me` | 내 정보. `gender`, `avatar`(슬롯 → `itemId`) |
| PATCH | `/users/me` | 착용 변경. `avatar`에 바꿀 슬롯만 |
| GET | `/users/{userId}` | 공개 프로필. `gender`, `avatar` 포함 |
| GET | `/users/me/items?type=AVATAR` | 내 보유 아이템. `modelUrl`, `equipped` 포함 |
| GET | `/shop/items?type=AVATAR&category=HAT` | 상점 목록(페이지네이션). 내 체형 아이템만 |
| GET | `/shop/items/{itemId}` | 아이템 상세 |
| GET | `/shop/items/batch?ids=1,2,3` | 여러 아이템 한 번에 조회. 체형으로 거르지 않음 |
| POST | `/shop/items/{itemId}/purchase` | 구매 |

**`POST /auth/signup` 요청**

```json
{
  "email": "jinyoung@example.com",
  "password": "P@ssw0rd!",
  "nickname": "진영",
  "gender": "MALE"
}
```

`gender`를 보내면 가입과 동시에 기본 한 벌이 지급된다. 생략하면 `null`로 가입되고 `PUT /users/me/gender`로 한 번 정해야 한다. 응답에도 `gender`가 들어 있다.

**로그인 응답의 `user`** (소셜 로그인, 토큰 재발급도 같다)

```json
{ "userId": 1, "nickname": "진영", "avatarUrl": null, "coins": 0, "gender": null }
```

`gender`가 `null`이면 홈으로 가기 전에 성별 선택 화면을 띄운다.

**`PUT /users/me/gender` 요청**

```json
{ "gender": "FEMALE" }
```

응답 `200`: `GET /users/me`와 같은 내 정보(`gender`, `avatar`가 채워진 상태). 이미 정한 계정이 다시 호출하면 같은 값이어도 `GENDER_ALREADY_SET`(409).

**`GET /users/me` 응답 (아바타 부분)**

```json
{
  "userId": 12,
  "nickname": "진영",
  "gender": "MALE",
  "avatar": {
    "skin": 1, "eyes": 4, "hair": 13, "top": 22,
    "bottom": 31, "shoes": 40, "hat": null
  }
}
```

`avatar`의 값은 아이템 ID다. `modelUrl`은 들어 있지 않으므로 `/users/me/items`나 `/shop/items/batch`로 찾는다.

**`PATCH /users/me` 요청**: 바꿀 슬롯만 보낸다. 슬롯 이름은 소문자, 값은 `itemId`다.

```json
{ "avatar": { "hat": 41, "top": 26 } }
```

모자 벗기는 `{ "avatar": { "hat": null } }`이다.

**`GET /shop/items?type=AVATAR&category=TOP` 응답**

```json
{
  "content": [
    {
      "itemId": 105,
      "name": "베리 카디건",
      "type": "AVATAR",
      "category": "TOP",
      "price": 180,
      "imageUrl": "https://dq-uploads-prod.s3.ap-northeast-2.amazonaws.com/items/avatar/girl/DQ_Top_BerryCardigan.png",
      "modelUrl": "https://dq-uploads-prod.s3.ap-northeast-2.amazonaws.com/items/avatar/girl/DQ_Top_BerryCardigan.glb",
      "isDefault": false,
      "owned": false,
      "code": "DQ_Top_BerryCardigan",
      "gender": "FEMALE"
    }
  ],
  "page": 0, "size": 20, "totalElements": 1, "totalPages": 1
}
```

**`GET /shop/items/batch?ids=1,4,13` 응답**: 위 아이템 객체의 **배열**(ID 오름차순, 페이지네이션 없음). 없거나 비활성인 ID는 결과에서 빠진다. `ids`가 100개를 넘으면 `VALIDATION_ERROR`.

**`POST /shop/items/{itemId}/purchase` 응답**

```json
{ "itemId": 41, "remainingCoins": 380 }
```

**오류 코드**

| 코드 | HTTP | 언제 |
| --- | --- | --- |
| `ITEM_NOT_OWNED` | 403 | 보유하지 않은 아이템을 착용 |
| `ITEM_GENDER_MISMATCH` | 409 | 내 체형용이 아닌 아이템을 착용·구매, 또는 성별을 정하기 전에 구매 |
| `GENDER_ALREADY_SET` | 409 | 성별을 이미 정한 계정이 다시 정하려 함 |
| `VALIDATION_ERROR` | 400 | 슬롯과 카테고리 불일치, 알 수 없는 슬롯, 잘못된 `gender` 값, batch `ids` 100개 초과 |
| `INSUFFICIENT_COINS` | 409 | 코인 부족 |
| `ITEM_ALREADY_OWNED` | 409 | 이미 가진 아이템 구매 |
| `ITEM_NOT_FOUND` | 404 | 없거나 비활성인 아이템 |

## 아이템 목록 (v0.4 시드, 111종)

표의 이름이 `code`이자 GLB 파일 이름이다. ‘기본 지급’은 `is_default = true`, 가격 0이다. 상점 이름(`name`)은 서버 시드에 들어 있다(예: 카페 상의, 베리 카디건).

| 부위 | 가격 (코인) |
| --- | --- |
| 피부 | 0 (3종 모두 기본) |
| 눈 | 60 |
| 머리 | 150 |
| 상의 | 180 |
| 하의 | 120 |
| 신발 | 120 |
| 모자 | 100 |

**남자 캐릭터 (`MALE`, 57개, `items/avatar/boy/`)**

| 부위 | 기본 지급 (무료) | 상점 아이템 | 개수 |
| --- | --- | --- | --- |
| SKIN (피부) | `DQ_Boy_01_Body` (기본 착용), `DQ_Boy_01_Body_GirlSkin`, `DQ_Boy_01_Body_DeepBrown` | - | 3 |
| EYES (눈) | `DQ_Boy_02_Eyes` | `DQ_Boy_Eyes_Cafe`, `DQ_Boy_Eyes_Campus`, `DQ_Boy_Eyes_Marine`, `DQ_Boy_Eyes_Rain`, `DQ_Boy_Eyes_Sleep`, `DQ_Boy_Eyes_Snow`, `DQ_Boy_Eyes_Sport`, `DQ_Boy_Eyes_Trail` | 9 |
| HAIR (머리) | `DQ_Boy_03_Hair` | `DQ_Boy_Hair_Cafe`, `DQ_Boy_Hair_Campus`, `DQ_Boy_Hair_Marine`, `DQ_Boy_Hair_Rain`, `DQ_Boy_Hair_Sleep`, `DQ_Boy_Hair_Snow`, `DQ_Boy_Hair_Sport`, `DQ_Boy_Hair_Trail` | 9 |
| TOP (상의) | `DQ_Boy_04_Top` | `DQ_Boy_Top_Cafe`, `DQ_Boy_Top_Campus`, `DQ_Boy_Top_Marine`, `DQ_Boy_Top_Rain`, `DQ_Boy_Top_Sleep`, `DQ_Boy_Top_Snow`, `DQ_Boy_Top_Sport`, `DQ_Boy_Top_Trail` | 9 |
| BOTTOM (하의) | `DQ_Boy_05_Bottom` | `DQ_Boy_Bottom_Cafe`, `DQ_Boy_Bottom_Campus`, `DQ_Boy_Bottom_Marine`, `DQ_Boy_Bottom_Rain`, `DQ_Boy_Bottom_Sleep`, `DQ_Boy_Bottom_Snow`, `DQ_Boy_Bottom_Sport`, `DQ_Boy_Bottom_Trail` | 9 |
| SHOES (신발) | `DQ_Boy_06_Shoes` | `DQ_Boy_Shoes_Cafe`, `DQ_Boy_Shoes_Campus`, `DQ_Boy_Shoes_Marine`, `DQ_Boy_Shoes_Rain`, `DQ_Boy_Shoes_Sleep`, `DQ_Boy_Shoes_Snow`, `DQ_Boy_Shoes_Sport`, `DQ_Boy_Shoes_Trail` | 9 |
| HAT (모자) | `DQ_Boy_07_Hat` (지급만, 착용 안 함) | `DQ_Boy_Hat_Cafe`, `DQ_Boy_Hat_Campus`, `DQ_Boy_Hat_Marine`, `DQ_Boy_Hat_Rain`, `DQ_Boy_Hat_Sleep`, `DQ_Boy_Hat_Snow`, `DQ_Boy_Hat_Sport`, `DQ_Boy_Hat_Trail` | 9 |

**여자 캐릭터 (`FEMALE`, 54개, `items/avatar/girl/`)**

| 부위 | 기본 지급 (무료) | 상점 아이템 | 개수 |
| --- | --- | --- | --- |
| SKIN (피부) | `DQ_Body` (기본 착용), `DQ_Body_BoySkin`, `DQ_Body_DeepBrown` | - | 3 |
| EYES (눈) | `DQ_Eyes` | `DQ_Eyes_Cranberry`, `DQ_Eyes_Denim`, `DQ_Eyes_Drizzle`, `DQ_Eyes_Fern`, `DQ_Eyes_HollyEmerald`, `DQ_Eyes_Honey`, `DQ_Eyes_Lagoon`, `DQ_Eyes_Lilac`, `DQ_Eyes_PeachMoon`, `DQ_Eyes_Rose` | 11 |
| HAIR (머리) | `DQ_Hair` | `DQ_Hair_CocoaBraids`, `DQ_Hair_CopperPony`, `DQ_Hair_GingerCloud`, `DQ_Hair_HoneyBuns`, `DQ_Hair_InkPixie`, `DQ_Hair_SilverWaves`, `DQ_Hair_TideBob` | 8 |
| TOP (상의) | `DQ_Top` | `DQ_Top_BerryCardigan`, `DQ_Top_CloverSailor`, `DQ_Top_LemonMac`, `DQ_Top_MoonPajama`, `DQ_Top_NoelCoat`, `DQ_Top_NordicReindeer`, `DQ_Top_OrbitVarsity` | 8 |
| BOTTOM (하의) | `DQ_Bottom` | `DQ_Bottom_ChestnutCords`, `DQ_Bottom_PetalSkirt`, `DQ_Bottom_RollShorts`, `DQ_Bottom_StarlightPants`, `DQ_Bottom_TartanBell`, `DQ_Bottom_TartanCulottes`, `DQ_Bottom_WaveShorts` | 8 |
| SHOES (신발) | `DQ_Shoes` | `DQ_Shoes_AlpineShearling`, `DQ_Shoes_BellBoots`, `DQ_Shoes_BerryMaryJanes`, `DQ_Shoes_BunnySlippers`, `DQ_Shoes_CourtHighTops`, `DQ_Shoes_MapleBoots`, `DQ_Shoes_PuddleBoots` | 8 |
| HAT (모자) | `DQ_Hat` (지급만, 착용 안 함) | `DQ_Hat_CloverBeret`, `DQ_Hat_HollySanta`, `DQ_Hat_JingleAntlers`, `DQ_Hat_LemonBucket`, `DQ_Hat_LopBand`, `DQ_Hat_OrbitCap`, `DQ_Hat_RoseRibbon` | 8 |

> 여자 캐릭터 코드에는 `Girl`이 들어가 있지 않다(`DQ_Hat` 등). `code`는 UNIQUE라서, 나중에 새 아이템을 만들 때는 `DQ_Boy_…` / `DQ_Girl_…`로 시작하는 것을 권장한다.

## 남은 확인 사항

v0.4로 대부분 정해졌고, 아래만 남았다.

- **1. 해제 제한을 서버에서도 막을 것인가?** 지금은 모든 슬롯을 `null`로 해제할 수 있다. 앱에서 모자 외 해제를 막는 것으로 충분한지, 서버도 `VALIDATION_ERROR`로 막을지.
- **2. 판매 종료(계절 한정 등)는 어떻게 하는가?** `active = false`로 내리면 상점에서 빠질 뿐 아니라 **이미 산 사람도 착용할 수 없고**, `/shop/items/batch`에서도 빠져서 **다른 사람 화면에서 그 사람 아바타를 그릴 수 없다.** 판매만 끝내는 별도 플래그가 필요한지.
- **3. GLB 용량 줄이기.** 파일당 1MB 이하로 줄인 파일을 새 이름으로 올리고 `model_url`을 바꾸는 절차(누가 올리고, 누가 마이그레이션을 쓰는지).
- **4. 테마 필터(`set_code`)가 필요한가?** 지금은 없다. 상점에서 “카페 세트” 같은 묶음 필터를 보여줄 계획이 있으면 추가 요청.
- **5. 미션 보상으로 아이템을 줄 계획이 있는가?** 있으면 `acquired_by`에 `REWARD` 추가 필요.

방 가구(STEP 10)는 이미 `type = FURNITURE`(카테고리 `DESK`, `CHAIR`, `BED`, `STORAGE`, `DECO`, `WALL`, `FLOOR`)와 `GET/PUT /rooms/{roomId}/decorations`(좌표 0.0~1.0 비율, `rotation` 0~359)로 구현돼 있다. 가구는 `gender`가 `null`인 공용 아이템이라 체형 필터와 상관없이 항상 상점에 나온다.

---

## 초안 대비 바뀐 점

11월 20일 초안에서 제안한 내용 중 백엔드 v0.4가 다르게 정한 것이다. 이 문서 본문은 모두 v0.4 기준으로 고쳤다.

| 초안 제안 | v0.4 결정 |
| --- | --- |
| 성별 필드 `avatarType` (`BOY` / `GIRL`) | `gender` (`MALE` / `FEMALE`) |
| 가입 때 성별과 피부(`skinCode`)를 함께 받음 | 가입 때는 `gender`만 받음. 피부는 첫 톤이 자동 착용되고 `PATCH /users/me`로 바꿈 |
| 소셜 계정은 `PATCH /users/me`로 성별 설정 | 전용 API `PUT /users/me/gender` |
| `GET /users/me`에 `avatarModels` 추가 | 추가 안 함. `/users/me/items` 또는 `/shop/items/batch`로 `modelUrl` 조회 |
| 다른 사용자 아바타는 공개 프로필에 모델 주소 추가 | `GET /shop/items/batch?ids=`로 한 번에 조회 |
| 해제는 `hat`만 허용 (서버 검증) | 서버는 모든 슬롯 해제 허용 → 앱에서 막음 (남은 확인 사항 1) |
| 파일 위치 `/files/models/{boy,girl}/` 상대 경로 | S3 `items/avatar/{boy,girl}/` 전체 주소 |
| `set_code`, `on_sale` 컬럼 | 추가 안 함 (남은 확인 사항 2, 4) |
| 기본 모자 착용 여부 미정 | 지급만 하고 착용하지 않음 |
| 기존 계정 처리 미정 | 임시 아바타 아이템 회수·환불(`REFUND`), `gender = null`로 두고 성별 선택 시 기본 한 벌 지급 |

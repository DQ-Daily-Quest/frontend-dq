package com.dailyquest.app.data.avatar

/**
 * 아바타 착용 슬롯 7개 (backend api.md 3.1 `avatar`, 7.1 `category`).
 * - [serverKey]: `GET/PATCH /users/me`의 `avatar` 키 (소문자)
 * - 상점 아이템 `category`는 대문자(`HAT`)라서 [fromKey]는 대소문자를 가리지 않는다.
 *
 * 모든 파츠 GLB는 원점(발 사이 바닥)이 같아서 슬롯 순서와 관계없이 같은 자리에 겹쳐 그리면 된다.
 * 성별 확정 후 `hat`을 뺀 6개 슬롯은 항상 채워져 있다.
 */
enum class AvatarPart(val serverKey: String) {
    SKIN("skin"),
    EYES("eyes"),
    HAIR("hair"),
    TOP("top"),
    BOTTOM("bottom"),
    SHOES("shoes"),
    HAT("hat");

    companion object {
        fun fromKey(key: String): AvatarPart? = entries.firstOrNull { it.serverKey.equals(key, ignoreCase = true) }
    }
}

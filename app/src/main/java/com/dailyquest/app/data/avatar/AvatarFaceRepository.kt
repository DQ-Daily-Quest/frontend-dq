package com.dailyquest.app.data.avatar

import com.dailyquest.app.data.remote.ApiConfig
import com.dailyquest.app.data.remote.ApiResult
import com.dailyquest.app.data.remote.DqApi
import com.dailyquest.app.data.remote.apiCall
import com.dailyquest.app.data.remote.dto.AvatarDto
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 프로필 얼굴에 보이는 슬롯. 얼굴 카메라([com.dailyquest.app.ui.avatar.AvatarCamera.FACE])에는 하의·신발이 안 보이므로 뺀다.
 * 그래야 하의·신발만 바꿨을 때 얼굴을 다시 찍지 않는다(착용 조합 이름이 그대로).
 */
val FACE_PARTS: Set<AvatarPart> = setOf(AvatarPart.SKIN, AvatarPart.EYES, AvatarPart.HAIR, AvatarPart.HAT, AvatarPart.TOP)

/** 착용 파츠 중 얼굴에 보이는 것만. 내 아바타([MyAvatar.modelParts])처럼 이미 주소를 아는 경우에 쓴다. */
fun Map<AvatarPart, String>.faceOnly(): Map<AvatarPart, String> = filterKeys { it in FACE_PARTS }

/**
 * 한 사람의 얼굴 재료. [parts]는 얼굴 슬롯 → 3D 주소(비어 있으면 찍을 수 없음),
 * [gender]는 사진이 없을 때 보여줄 기본 그림(남·여)을 고르는 데 쓴다. 모르면 null(사람 아이콘).
 */
data class PersonFace(val gender: AvatarGender?, val parts: Map<AvatarPart, String>) {
    companion object {
        val UNKNOWN = PersonFace(null, emptyMap())
    }
}

/** 내 아바타 → 얼굴 재료. */
fun MyAvatar.toPersonFace(): PersonFace = PersonFace(gender, modelParts.faceOnly())

/** 서버 아바타(슬롯별 아이템 ID)에서 얼굴에 보이는 슬롯의 아이템 ID. 피부(몸)가 맨 앞이다(성별을 고를 때 먼저 본다). */
internal fun AvatarDto.faceItemIds(): Map<AvatarPart, Long> = buildMap {
    skin?.let { put(AvatarPart.SKIN, it) }
    eyes?.let { put(AvatarPart.EYES, it) }
    hair?.let { put(AvatarPart.HAIR, it) }
    hat?.let { put(AvatarPart.HAT, it) }
    top?.let { put(AvatarPart.TOP, it) }
}

/**
 * 다른 사람의 아바타(착용 아이템 ID)를 얼굴 재료([PersonFace])로 바꾼다 (프로필 얼굴 3·4단계).
 *
 * - 모르는 아이템 ID만 모아 `GET /shop/items/batch`로 한 번에 받는다(100개씩 나눔).
 * - 받은 아이템의 3D 주소·체형은 앱이 켜져 있는 동안 기억한다. 아이템 정보는 바뀌지 않으므로 계정이 바뀌어도 그대로 쓴다.
 * - 서버에 없는 ID(판매 종료 등)도 "없음"으로 기억해 다시 묻지 않는다. 그 슬롯은 빼고 그린다.
 * - 여러 화면이 동시에 불러도 같은 ID를 두 번 묻지 않게 한 번에 하나씩 받는다.
 * - 성별: 서버의 방 멤버·피드 작성자 정보에는 성별이 없어서, 착용 아이템의 체형(피부 먼저)으로 정한다.
 */
class AvatarFaceRepository(private val api: DqApi) {
    private val mutex = Mutex()

    /** 아이템 하나에서 기억할 것. */
    private data class ItemInfo(val modelUrl: String?, val gender: AvatarGender?)

    /** 아이템 ID → 정보. 값이 null이면 서버에 없는 아이템. */
    private val items = HashMap<Long, ItemInfo?>()

    /** 이미 아는 것만으로 얼굴 재료를 만든다. 모르는 ID가 하나라도 있으면 null(→ [face]로 받아야 한다). */
    fun cachedFace(avatar: AvatarDto?): PersonFace? {
        val ids = avatar?.faceItemIds() ?: return PersonFace.UNKNOWN
        synchronized(items) {
            if (ids.values.any { it !in items }) return null
            val infos = ids.mapValues { (_, id) -> items[id] }
            return PersonFace(
                gender = infos.values.firstNotNullOfOrNull { it?.gender },
                parts = infos.mapNotNull { (part, info) -> info?.modelUrl?.let { part to it } }.toMap(),
            )
        }
    }

    /** 한 사람의 얼굴 재료. 아바타가 없거나(null) 아무것도 안 입었으면 [PersonFace.UNKNOWN]. */
    suspend fun face(avatar: AvatarDto?): ApiResult<PersonFace> =
        when (val r = faces(listOf(avatar))) {
            is ApiResult.Success -> ApiResult.Success(r.value.single())
            is ApiResult.Failure -> r
        }

    /** 여러 사람의 얼굴 재료를 같은 순서로. 모르는 아이템은 한 번에 모아 받는다. */
    suspend fun faces(avatars: List<AvatarDto?>): ApiResult<List<PersonFace>> {
        val wanted = avatars.flatMap { it?.faceItemIds()?.values.orEmpty() }.toSet()
        val loaded = load(wanted)
        if (loaded is ApiResult.Failure) return loaded
        return ApiResult.Success(avatars.map { cachedFace(it) ?: PersonFace.UNKNOWN })
    }

    /** [ids] 중 모르는 것만 서버에서 받아 기억한다. */
    private suspend fun load(ids: Set<Long>): ApiResult<Unit> {
        if (ids.isEmpty() || synchronized(items) { items.keys.containsAll(ids) }) return ApiResult.Success(Unit)
        return mutex.withLock {
            // 기다리는 동안 다른 요청이 받아 왔을 수 있다.
            val missing = synchronized(items) { ids.filterNot { it in items } }.sorted()
            for (chunk in missing.chunked(BATCH_MAX)) {
                val r = apiCall { api.getShopItemsBatch(chunk.joinToString(",")) }
                if (r is ApiResult.Failure) return@withLock r
                val got = (r as ApiResult.Success).value.associate {
                    it.itemId to ItemInfo(ApiConfig.resolveFileUrl(it.modelUrl), AvatarGender.parse(it.gender))
                }
                synchronized(items) { chunk.forEach { id -> items[id] = got[id] } }
            }
            ApiResult.Success(Unit)
        }
    }

    companion object {
        /** 서버가 한 번에 받는 최대 ID 수 (api.md 7.1.1). */
        const val BATCH_MAX = 100
    }
}

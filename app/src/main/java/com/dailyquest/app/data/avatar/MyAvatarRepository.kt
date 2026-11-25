package com.dailyquest.app.data.avatar

import com.dailyquest.app.data.auth.AuthErrorMessages
import com.dailyquest.app.data.auth.SessionManager
import com.dailyquest.app.data.auth.toProfile
import com.dailyquest.app.data.remote.ApiConfig
import com.dailyquest.app.data.remote.ApiErrorCode
import com.dailyquest.app.data.remote.ApiException
import com.dailyquest.app.data.remote.ApiResult
import com.dailyquest.app.data.remote.DqApi
import com.dailyquest.app.data.remote.apiCall
import com.dailyquest.app.data.remote.dto.AvatarDto
import com.dailyquest.app.data.remote.dto.MyItemDto
import com.dailyquest.app.data.remote.dto.SetGenderRequest
import com.dailyquest.app.data.remote.dto.UpdateAvatarRequest
import com.dailyquest.app.data.remote.dto.UserMeResponse
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/** 아바타 체형 (backend v0.4 `gender`). 에셋 폴더 기준 MALE = boy, FEMALE = girl. */
enum class AvatarGender {
    MALE, FEMALE;

    companion object {
        /** 서버 값이 없거나 모르는 값이면 null(= 아직 성별을 정하지 않은 계정으로 다룬다). */
        fun parse(value: String?): AvatarGender? = entries.firstOrNull { it.name == value }
    }
}

/** 지금 입고 있는 아이템 하나. [modelUrl]이 없으면 3D로 그릴 수 없는 아이템이다. */
data class EquippedPart(val itemId: Long, val name: String, val modelUrl: String?, val imageUrl: String?)

/** 내 아바타: 체형과 슬롯별 착용 아이템. */
data class MyAvatar(val gender: AvatarGender?, val equipped: Map<AvatarPart, EquippedPart>) {
    /** 성별을 정하지 않은 계정. 성별 선택 화면으로 보내야 한다(STEP 9-9). */
    val needsGender: Boolean get() = gender == null

    /** [com.dailyquest.app.ui.avatar.AvatarView]에 넘길 슬롯 → GLB 주소. 주소가 없는 슬롯은 뺀다. */
    val modelParts: Map<AvatarPart, String>
        get() = equipped.mapNotNull { (part, item) -> item.modelUrl?.let { part to it } }.toMap()

    fun itemId(part: AvatarPart): Long? = equipped[part]?.itemId
}

/**
 * 내 아바타 불러오기·착용 변경 (backend api.md 3.1, 3.2, 7.3).
 *
 * 서버의 `GET /users/me` `avatar`에는 슬롯별 아이템 **ID만** 있으므로, 착용 아이템은 모두 보유 아이템이라는 점을 이용해
 * `GET /users/me/items?type=AVATAR`에서 GLB 주소를 찾는다. 두 요청은 동시에 보낸다.
 */
class MyAvatarRepository(
    private val api: DqApi,
    private val session: SessionManager,
) {
    /** 마지막으로 받은 보유 아바타 아이템(받은 계정 ID와 함께). 착용 저장 뒤 목록 조회만 실패했을 때 같은 계정이면 대신 쓴다. */
    @Volatile private var lastOwned: Pair<Long?, List<MyItemDto>> = null to emptyList()

    /** 마지막으로 받은 내 아바타(받은 계정 ID와 함께). 상점을 다시 열 때 서버 응답을 기다리지 않고 바로 그린다(아바타 속도 1단계). */
    @Volatile private var lastAvatar: Pair<Long, MyAvatar>? = null

    /** [userId] 계정의 마지막 아바타. 다른 계정이거나 아직 받은 적 없으면 null. 앱이 켜져 있는 동안만 기억한다. */
    fun cached(userId: Long): MyAvatar? = lastAvatar?.takeIf { it.first == userId }?.second

    private fun remember(userId: Long, avatar: MyAvatar): MyAvatar = avatar.also { lastAvatar = userId to it }

    suspend fun load(): ApiResult<MyAvatar> = apiCall {
        coroutineScope {
            val me = async { api.me() }
            val items = async { api.listMyItems(type = AVATAR_TYPE) }
            val user = me.await()
            val owned = items.await().also { lastOwned = user.userId to it }
            remember(user.userId, resolveMyAvatar(user, owned))
        }
    }

    /**
     * 착용을 바꾼다. [changes]에는 바꿀 슬롯만 넣고, 값이 null이면 해제한다.
     * 성공하면 서버가 돌려준 내 정보(코인 등)로 세션도 갱신한다. 요청 중 다른 계정으로 바뀌었다면 세션에는 반영하지 않는다.
     *
     * 착용 저장(PATCH)이 성공한 뒤 GLB 주소를 찾으려고 보유 목록을 다시 받는다. 이 조회만 실패하면 착용은 이미 저장됐으므로
     * 실패로 돌려주지 않고 마지막으로 받은 보유 목록으로 대신한다(화면이 저장된 착용을 되돌리지 않게).
     */
    suspend fun equip(changes: Map<AvatarPart, Long?>): ApiResult<MyAvatar> {
        val userId = session.currentUserId()
        val saved = apiCall { api.updateAvatar(UpdateAvatarRequest(changes.mapKeys { (part, _) -> part.serverKey })) }
        if (saved !is ApiResult.Success) return saved as ApiResult.Failure
        val me = saved.value
        if (userId != null) session.applyProfileIfCurrent(userId, me.toProfile())
        val owned = when (val items = apiCall { api.listMyItems(type = AVATAR_TYPE) }) {
            is ApiResult.Success -> items.value.also { lastOwned = me.userId to it }
            is ApiResult.Failure -> lastOwned.takeIf { it.first == me.userId }?.second.orEmpty()
        }
        return ApiResult.Success(remember(me.userId, resolveMyAvatar(me, owned)))
    }

    /**
     * 캐릭터 만들기 (STEP 9-9): 성별을 확정하고, 고른 피부를 입힌다.
     *
     * 1. `PUT /users/me/gender` — 서버가 그 체형의 기본 한 벌을 지급하고 피부 첫 톤·눈·머리·상의·하의·신발을 입힌다.
     *    이미 성별이 정해진 계정(`GENDER_ALREADY_SET`, 두 번 누름·다른 기기)이면 지금 상태를 받아 그대로 진행한다.
     * 2. 세션의 내 정보에 성별을 반영한다(로그인 후 다시 캐릭터 만들기로 보내지 않게).
     * 3. 고른 피부가 서버가 입힌 것과 다르면 `PATCH /users/me {"avatar":{"skin":id}}`. 피부 아이템은 보유 목록에서 `code`로 찾는다.
     *    이 단계만 실패하면 캐릭터는 이미 만들어졌으므로 실패로 돌려주지 않는다(피부는 상점에서 다시 바꿀 수 있다).
     */
    suspend fun createCharacter(gender: AvatarGender, skin: AvatarDefaults.SkinTone): ApiResult<MyAvatar> {
        val userId = session.currentUserId()
        var me = when (val created = apiCall { api.setGender(SetGenderRequest(gender.name)) }) {
            is ApiResult.Success -> created.value
            is ApiResult.Failure -> {
                if (created.error.httpCode != ApiErrorCode.GENDER_ALREADY_SET) return created
                when (val current = apiCall { api.me() }) {
                    is ApiResult.Success -> current.value
                    is ApiResult.Failure -> return current
                }
            }
        }
        if (userId != null) session.applyProfileIfCurrent(userId, me.toProfile())

        val owned = when (val items = apiCall { api.listMyItems(type = AVATAR_TYPE) }) {
            is ApiResult.Success -> items.value.also { lastOwned = me.userId to it }
            is ApiResult.Failure -> return ApiResult.Success(remember(me.userId, resolveMyAvatar(me, lastOwned.takeIf { it.first == me.userId }?.second.orEmpty())))
        }
        val actualGender = AvatarGender.parse(me.gender) ?: gender
        val skinItem = owned.firstOrNull { it.code == skin.code(actualGender) }
        if (skinItem != null && me.avatar.skin != skinItem.itemId) {
            val changed = apiCall { api.updateAvatar(UpdateAvatarRequest(mapOf(AvatarPart.SKIN.serverKey to skinItem.itemId))) }
            if (changed is ApiResult.Success) {
                me = changed.value
                if (userId != null) session.applyProfileIfCurrent(userId, me.toProfile())
            }
        }
        return ApiResult.Success(remember(me.userId, resolveMyAvatar(me, owned)))
    }

    private companion object {
        const val AVATAR_TYPE = "AVATAR"
    }
}

/** 서버 응답 두 개를 [MyAvatar]로 합친다. 보유 목록에 없는 착용 ID는(서버 불일치) 건너뛴다. */
internal fun resolveMyAvatar(me: UserMeResponse, ownedAvatarItems: List<MyItemDto>): MyAvatar {
    val byId = ownedAvatarItems.associateBy { it.itemId }
    val equipped = me.avatar.slots().mapNotNull { (part, itemId) ->
        val item = itemId?.let(byId::get) ?: return@mapNotNull null
        part to EquippedPart(
            itemId = item.itemId,
            name = item.name,
            modelUrl = ApiConfig.resolveFileUrl(item.modelUrl),
            imageUrl = ApiConfig.resolveFileUrl(item.imageUrl),
        )
    }.toMap()
    return MyAvatar(AvatarGender.parse(me.gender), equipped)
}

private fun AvatarDto.slots(): List<Pair<AvatarPart, Long?>> = listOf(
    AvatarPart.SKIN to skin, AvatarPart.EYES to eyes, AvatarPart.HAIR to hair, AvatarPart.TOP to top,
    AvatarPart.BOTTOM to bottom, AvatarPart.SHOES to shoes, AvatarPart.HAT to hat,
)

/** 아바타 착용 관련 서버 오류를 한국어 안내로 바꾼다. 분기는 message가 아니라 code로 한다. */
object AvatarErrorMessages {
    fun forApi(e: ApiException): String {
        if (e !is ApiException.Http) return AuthErrorMessages.forApi(e)
        return when (e.code) {
            ApiErrorCode.ITEM_NOT_OWNED -> "보유하지 않은 아이템이에요. 상점에서 먼저 구매해 주세요."
            ApiErrorCode.ITEM_GENDER_MISMATCH -> "내 캐릭터가 입을 수 없는 아이템이에요."
            ApiErrorCode.ITEM_NOT_FOUND -> "판매가 종료되었거나 존재하지 않는 아이템이에요."
            ApiErrorCode.GENDER_ALREADY_SET -> "캐릭터 성별은 한 번 정하면 바꿀 수 없어요."
            else -> AuthErrorMessages.forApi(e)
        }
    }
}

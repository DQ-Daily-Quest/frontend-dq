package com.dailyquest.app.data.remote.dto

import kotlinx.serialization.Serializable

/** 각 슬롯은 보유 아이템 ID(Long) 또는 null(미착용). 슬롯 의미와 3D 규격은 아직 확정되지 않았다. */
@Serializable
data class AvatarDto(
    val hair: Long? = null,
    val eyes: Long? = null,
    val skin: Long? = null,
    val top: Long? = null,
    val bottom: Long? = null,
    val shoes: Long? = null,
    val hat: Long? = null,
)

@Serializable
data class StreakDto(val current: Int = 0, val longest: Int = 0)

@Serializable
data class UserStatsDto(val roomCount: Long = 0, val completedMissionCount: Long = 0)

/** GET /users/me */
@Serializable
data class UserMeResponse(
    val userId: Long,
    val email: String? = null,
    val nickname: String,
    val coins: Int = 0,
    val avatar: AvatarDto = AvatarDto(),
    val streak: StreakDto = StreakDto(),
    val stats: UserStatsDto = UserStatsDto(),
    val createdAt: String? = null,
    /** 비밀번호가 설정된 계정인지. 소셜 전용 계정은 false, 응답에 없으면 null(알 수 없음). */
    val hasPassword: Boolean? = null,
    /** 아바타 체형 MALE | FEMALE (backend v0.4). null이면 아직 정하지 않은 계정이라 avatar 슬롯이 모두 비어 있다. */
    val gender: String? = null,
)

/** PATCH /users/me. 닉네임만 보낸다(avatar 슬롯은 STEP 9에서 다루며, 보내지 않으면 서버가 그대로 둔다). */
@Serializable
data class UpdateMeRequest(val nickname: String)

/**
 * PATCH /users/me 착용 변경 (STEP 9). 바꿀 슬롯만 보낸다. 키는 소문자 슬롯 이름, 값은 보유 아이템 ID이고 null이면 해제.
 * 앱 Json은 explicitNulls = true라서 null도 그대로 `"hat": null`로 나간다.
 */
@Serializable
data class UpdateAvatarRequest(val avatar: Map<String, Long?>)

/** PUT /users/me/gender (STEP 9-9). MALE | FEMALE, 계정당 한 번. 응답은 GET /users/me와 같다(기본 한 벌이 지급·착용된 상태). */
@Serializable
data class SetGenderRequest(val gender: String)

/** PATCH /users/me/password (성공 204, 서버가 모든 Refresh Token을 폐기한다) */
@Serializable
data class ChangePasswordRequest(val currentPassword: String, val newPassword: String)

/** DELETE /users/me 본문. 비밀번호가 있는 계정만 password가 필요하다. */
@Serializable
data class WithdrawRequest(val password: String? = null)

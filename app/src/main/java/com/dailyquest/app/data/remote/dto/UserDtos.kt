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
)

package com.dailyquest.app.data.avatar

import com.dailyquest.app.data.remote.ApiResult
import com.dailyquest.app.data.remote.DqApi
import com.dailyquest.app.data.remote.apiCall
import com.dailyquest.app.data.remote.dto.AvatarDto
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.util.concurrent.ConcurrentHashMap

/**
 * 사람(userId) → 지금 착용 아바타 (프로필 얼굴 7단계).
 *
 * 미션을 보낸 사람처럼 짧은 사용자 정보(`userId`·닉네임)만 있는 곳에서 얼굴을 그릴 때 쓴다. `GET /users/{id}`로 받는다.
 * - 여러 사람을 **동시에** 받고, 받은 착용은 기억한다. 화면에 다시 들어올 때 새로 받는다(그 사이에는 기억한 값으로 그린다).
 * - 실패한 사람은 예전 값(없으면 없음)을 그대로 둔다.
 */
class UserAvatarRepository(private val api: DqApi) {
    private val avatars = ConcurrentHashMap<Long, AvatarDto>()

    /** 기억해 둔 착용. 아직 받은 적 없으면 null. */
    fun cached(userId: Long): AvatarDto? = avatars[userId]

    /** [userIds]의 착용을 새로 받는다. 성공한 사람만 돌려준다. */
    suspend fun refresh(userIds: List<Long>): Map<Long, AvatarDto> = coroutineScope {
        userIds.distinct().map { id -> async { id to apiCall { api.getUser(id) } } }.awaitAll()
            .mapNotNull { (id, r) -> (r as? ApiResult.Success)?.value?.let { id to it.avatar } }
            .toMap()
            .also { avatars.putAll(it) }
    }

    /** 계정이 바뀌면 비운다. */
    fun clear() = avatars.clear()
}

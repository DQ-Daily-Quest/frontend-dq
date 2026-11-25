package com.dailyquest.app.data.avatar

import com.dailyquest.app.data.remote.ApiResult
import com.dailyquest.app.data.remote.dto.AvatarDto
import com.dailyquest.app.data.remote.dto.RoomMemberDto
import com.dailyquest.app.data.room.RoomRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.util.concurrent.ConcurrentHashMap

/** 지도 핀에 그릴 멤버 한 명. */
data class MapMember(val userId: Long, val nickname: String, val avatar: AvatarDto?)

/** 지도 핀 하나: 보여줄 멤버(앞에서부터 최대 [MAP_FACES_MAX]명)와 그 밖의 인원 수("+N"). */
data class MapFaces(val shown: List<MapMember>, val more: Int)

/** 지도 핀 하나에 그리는 얼굴 수. 나머지는 "+N"으로 보여준다. */
const val MAP_FACES_MAX = 3

/**
 * 지도 핀에 누구를 보여줄지 (프로필 얼굴 6단계).
 * - 다른 멤버를 먼저(서버 순서 그대로, 보통 방장이 앞), **나는 맨 뒤**. 지도는 "내 방들"이라 내 얼굴은 어느 방에나 있기 때문이다.
 * - 내 착용은 서버 멤버 목록 대신 [myAvatar](세션의 지금 착용)를 쓴다.
 * - 인원은 [memberCount](방 목록)와 받은 멤버 수 중 큰 값으로 센다. 목록을 받은 뒤 누가 들어왔을 수 있다.
 */
fun pickMapFaces(
    members: List<RoomMemberDto>,
    memberCount: Long,
    myUserId: Long?,
    myAvatar: AvatarDto?,
    max: Int = MAP_FACES_MAX,
): MapFaces {
    val (mine, others) = members.partition { it.userId == myUserId }
    val ordered = (others + mine).map { m ->
        MapMember(m.userId, m.nickname, if (m.userId == myUserId && myAvatar != null) myAvatar else m.avatar)
    }
    val shown = ordered.take(max)
    val total = maxOf(memberCount, members.size.toLong())
    return MapFaces(shown, (total - shown.size).coerceAtLeast(0).toInt())
}

/**
 * 홈 지도에 보이는 방들의 멤버 목록 (프로필 얼굴 6단계). 방 목록(`/rooms/mine`)에는 멤버가 없어서 방마다 `/rooms/{id}/members`를 부른다.
 * - 지도 한 페이지(최대 4개 방)를 **동시에** 받는다.
 * - 받은 목록은 기억해 두고, 홈에 다시 올 때 새로 받는다(그 사이에는 기억한 목록으로 그려 깜빡이지 않는다).
 *   그래서 다른 사람이 옷을 바꾸면 홈으로 돌아올 때 새 얼굴이 된다.
 * - 한 방이 실패해도 다른 방은 그린다. 실패한 방은 예전 목록(없으면 없음)을 그대로 둔다.
 */
class RoomMemberFaceRepository(private val rooms: RoomRepository) {
    private val members = ConcurrentHashMap<Long, List<RoomMemberDto>>()

    /** 기억해 둔 멤버 목록. 아직 받은 적 없으면 null. */
    fun cached(roomId: Long): List<RoomMemberDto>? = members[roomId]

    /** [roomIds]의 멤버 목록을 새로 받는다. 성공한 방만 돌려준다(실패한 방은 빠짐). */
    suspend fun refresh(roomIds: List<Long>): Map<Long, List<RoomMemberDto>> = coroutineScope {
        roomIds.distinct().map { id -> async { id to rooms.listMembers(id) } }.awaitAll()
            .mapNotNull { (id, r) -> (r as? ApiResult.Success)?.value?.let { id to it } }
            .toMap()
            .also { members.putAll(it) }
    }

    /** 계정이 바뀌면 비운다(다른 사람 방의 멤버를 보여주지 않게). */
    fun clear() = members.clear()
}

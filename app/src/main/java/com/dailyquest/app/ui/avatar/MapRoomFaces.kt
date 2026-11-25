package com.dailyquest.app.ui.avatar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dailyquest.app.data.auth.SessionState
import com.dailyquest.app.data.avatar.MAP_FACES_MAX
import com.dailyquest.app.data.avatar.MapFaces
import com.dailyquest.app.data.avatar.PersonFace
import com.dailyquest.app.data.avatar.pickMapFaces
import com.dailyquest.app.data.model.Room
import com.dailyquest.app.data.remote.dto.RoomMemberDto
import com.dailyquest.app.ui.components.Bold
import com.dailyquest.app.ui.components.Txt
import com.dailyquest.app.ui.room.LocalAppContainer
import com.dailyquest.app.ui.theme.DQColors

/**
 * 홈 지도에 보이는 방들의 핀 얼굴 (프로필 얼굴 6단계). 방 ID → 보여줄 멤버와 "+N".
 * - 기억해 둔 멤버 목록으로 바로 그리고, 화면에 들어올 때(홈에 다시 올 때) 새로 받는다.
 * - 아직 한 번도 못 받은 방은 목록에 없다(핀은 인원 수만큼 사람 아이콘).
 */
@Composable
fun rememberMapFaces(rooms: List<Room>): Map<Long, MapFaces> {
    val me = rememberMyProfile()
    val members = rememberRoomMembers(rooms.map { it.roomId })
    return rooms.mapNotNull { room ->
        val list: List<RoomMemberDto> = members[room.roomId] ?: return@mapNotNull null
        room.roomId to pickMapFaces(list, room.memberCount, me?.userId, me?.avatar)
    }.toMap()
}

/**
 * [roomIds] 방들의 멤버 목록. 기억해 둔 목록으로 바로 돌려주고, 화면에 들어올 때 새로 받는다.
 * 아직 한 번도 못 받은 방은 빠진다. 멤버만 볼 수 있으므로 **내가 참여한 방만** 넘긴다.
 */
@Composable
fun rememberRoomMembers(roomIds: List<Long>): Map<Long, List<RoomMemberDto>> {
    val repo = LocalAppContainer.current.roomMemberFaceRepository
    var members by remember(roomIds) {
        mutableStateOf(roomIds.mapNotNull { id -> repo.cached(id)?.let { id to it } }.toMap())
    }
    LaunchedEffect(roomIds) {
        if (roomIds.isEmpty()) return@LaunchedEffect
        val fresh = repo.refresh(roomIds)
        if (fresh.isNotEmpty()) members = members + fresh
    }
    return members
}

/**
 * 공개방 카드의 방장 얼굴 (프로필 얼굴 7단계). **내가 참여한 방만** 멤버 목록에서 방장을 찾아 얼굴 재료를 만든다.
 * 참여하지 않은 방은 서버가 멤버를 주지 않으므로 결과에 없다(카드에 얼굴을 띄우지 않는다, 사용자 결정 2026-11-24).
 * 참여한 방이지만 아직 받는 중이면 값이 null(사람 아이콘).
 */
@Composable
fun rememberRoomOwnerFaces(rooms: List<Room>): Map<Long, PersonFace?> {
    val me = rememberMyProfile()
    val joined = rooms.filter { it.joined }.map { it.roomId }
    val members = rememberRoomMembers(joined)
    val owners = joined.map { id ->
        val owner = members[id]?.firstOrNull { it.role == ROLE_OWNER }
        if (owner != null && owner.userId == me?.userId && me.avatar != null) me.avatar else owner?.avatar
    }
    val faces = rememberPersonFaces(owners)
    return joined.mapIndexed { i, id -> id to (if (owners[i] == null) null else faces.getOrNull(i)) }.toMap()
}

private const val ROLE_OWNER = "OWNER"

/** 세션의 내 정보(내 얼굴은 서버 목록 대신 지금 착용을 쓴다). */
@Composable
internal fun rememberMyProfile(): com.dailyquest.app.data.auth.UserProfile? {
    val session by LocalAppContainer.current.session.state.collectAsStateWithLifecycle()
    return (session as? SessionState.Authenticated)?.profile
}

/** 지도 핀 첫 얼굴의 분홍 테두리 (기존 핀 그림 `img_avatar_story_*`과 같은 색·두께). */
private val PinRing = Color(0xFFED94AB)

/**
 * 지도 핀의 얼굴 줄: 첫 사람은 분홍 테두리 44dp, 다음 사람들은 32dp로 조금씩 겹치고, 남은 인원은 "+N".
 * [faces]가 null이면(멤버 목록을 아직 못 받음) 인원 수만큼 사람 아이콘을 그린다.
 * 얼굴 정보(3D 주소)는 [personFaces]로 받는다. 화면의 모든 핀 것을 한 번에 받아 넘기면 서버 요청이 한 번이다.
 */
@Composable
fun MapRoomFaces(
    roomId: Long,
    memberCount: Long,
    faces: MapFaces?,
    personFaces: Map<Long, PersonFace?>,
    modifier: Modifier = Modifier,
) {
    val slots = faces?.shown?.size ?: memberCount.coerceIn(0, MAP_FACES_MAX.toLong()).toInt()
    val more = faces?.more ?: (memberCount - slots).coerceAtLeast(0).toInt()
    Row(modifier.testTag("mapRoomFaces-$roomId"), verticalAlignment = Alignment.CenterVertically) {
        for (i in 0 until slots) {
            val member = faces?.shown?.getOrNull(i)
            val face = member?.let { personFaces[it.userId] }
            val description = member?.let { "${it.nickname} 아바타" }
            if (i == 0) {
                Box(Modifier.size(44.dp).border(3.dp, PinRing, CircleShape).padding(3.dp)) {
                    ProfileFace(face, 38.dp, contentDescription = description)
                }
            } else {
                ProfileFace(face, 32.dp, Modifier.offset(x = (-2 - 5 * (i - 1)).dp), contentDescription = description)
            }
        }
        if (more > 0) {
            Box(
                Modifier.offset(x = (-2 - 5 * (slots - 1).coerceAtLeast(0)).dp).size(32.dp)
                    .background(DQColors.White, CircleShape).border(2.dp, DQColors.Line, CircleShape)
                    .testTag("mapRoomMore-$roomId"),
                contentAlignment = Alignment.Center,
            ) { Txt("+$more", size = 12f, weight = Bold, color = DQColors.InkGreen) }
        }
    }
}

/** 화면의 모든 핀에 보일 사람들의 얼굴 정보를 한 번에(서버 요청 한 번). userId → 얼굴 재료. */
@Composable
fun rememberMapPersonFaces(mapFaces: Map<Long, MapFaces>): Map<Long, PersonFace?> {
    val people = mapFaces.values.flatMap { it.shown }.distinctBy { it.userId }
    val faces = rememberPersonFaces(people.map { it.avatar })
    return people.mapIndexed { i, m -> m.userId to faces.getOrNull(i) }.toMap()
}

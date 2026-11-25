package com.dailyquest.app.ui.avatar

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.dailyquest.app.data.avatar.PersonFace
import com.dailyquest.app.data.remote.dto.AvatarDto
import com.dailyquest.app.ui.room.LocalAppContainer

/**
 * 사람들(userId)의 얼굴 재료 (프로필 얼굴 7단계). 미션을 보낸 사람처럼 아바타 정보가 없는 곳에서 쓴다.
 * - 착용은 기억해 둔 값으로 바로 그리고, 화면에 들어올 때 `GET /users/{id}`로 새로 받는다.
 * - 나는 세션의 지금 착용을 쓴다(조회하지 않는다).
 * - 얼굴 정보(3D 주소)는 모두 한 번에 받는다. 아직 모르는 사람은 null(사람 아이콘).
 */
@Composable
fun rememberUserFaces(userIds: List<Long>): Map<Long, PersonFace?> {
    val repo = LocalAppContainer.current.userAvatarRepository
    val me = rememberMyProfile()
    val ids = userIds.distinct()
    val others = ids.filter { it != me?.userId }
    var avatars by remember(others) { mutableStateOf(others.mapNotNull { id -> repo.cached(id)?.let { id to it } }.toMap()) }
    LaunchedEffect(others) {
        if (others.isEmpty()) return@LaunchedEffect
        val fresh = repo.refresh(others)
        if (fresh.isNotEmpty()) avatars = avatars + fresh
    }
    val list: List<AvatarDto?> = ids.map { id -> if (id == me?.userId) me.avatar else avatars[id] }
    val faces = rememberPersonFaces(list)
    return ids.mapIndexed { i, id -> id to (if (list[i] == null) null else faces.getOrNull(i)) }.toMap()
}

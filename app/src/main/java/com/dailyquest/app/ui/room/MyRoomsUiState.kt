package com.dailyquest.app.ui.room

import com.dailyquest.app.data.model.Room

/** 우리만 탭 지도에 한 번에 표시하는 방 수(MAP_ROOM_SLOTS 개수). 서버 페이지네이션과 무관한 UI 페이지 단위다. */
const val MAP_PAGE_SIZE = 4

/** 지도 핀에 그릴 임시 캐릭터 수. 실제 멤버 수보다 많거나 슬롯 이미지 수를 넘기지 않는다. */
fun mapAvatarCount(memberCount: Long, slotAvatarCount: Int): Int =
    memberCount.coerceIn(0L, slotAvatarCount.toLong()).toInt()

/**
 * 내가 참여한 방. GET /rooms/mine은 전체 배열이므로 [page]는 서버 페이지가 아닌 4개 단위 UI 페이지(0부터)다.
 * 방 순서는 서버가 준 그대로 유지한다.
 */
data class MyRoomsUiState(
    val rooms: List<Room> = emptyList(),
    val page: Int = 0,
    val loaded: Boolean = false,
    val initialLoading: Boolean = true,
    val refreshing: Boolean = false,
    val error: String? = null,
) {
    val totalRooms: Int get() = rooms.size
    val pageCount: Int get() = maxOf(1, (rooms.size + MAP_PAGE_SIZE - 1) / MAP_PAGE_SIZE)
    val currentPage: Int get() = page.coerceIn(0, pageCount - 1)
    val pageRooms: List<Room> get() = rooms.drop(currentPage * MAP_PAGE_SIZE).take(MAP_PAGE_SIZE)
    val hasPrev: Boolean get() = currentPage > 0
    val hasNext: Boolean get() = currentPage < pageCount - 1
    val isEmpty: Boolean get() = loaded && error == null && rooms.isEmpty()
}

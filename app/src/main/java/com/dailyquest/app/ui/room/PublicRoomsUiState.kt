package com.dailyquest.app.ui.room

import com.dailyquest.app.data.model.Room
import com.dailyquest.app.data.room.RoomSort

/**
 * 공개방 목록 화면 상태. [page]·[totalPages]는 서버 페이지이며 홈 "우리만" 탭의 UI 페이지와 무관하다.
 * 서버는 hasNext를 주지 않으므로 [hasNext]를 page + 1 < totalPages로 계산한다.
 */
data class PublicRoomsUiState(
    val rooms: List<Room> = emptyList(),
    /** 검색창에 입력된 원문. 서버에는 trim한 값을 보낸다. */
    val keyword: String = "",
    val sort: RoomSort = RoomSort.POPULARITY,
    val page: Int = 0,
    val totalPages: Int = 0,
    val totalElements: Long = 0,
    /** 첫 페이지 로딩(검색·정렬 변경 포함). 이때는 기존 목록을 비운다. */
    val initialLoading: Boolean = true,
    val loadingMore: Boolean = false,
    /** 기존 목록을 유지한 채 다시 불러오는 중. */
    val refreshing: Boolean = false,
    /** 첫 페이지 조회 실패 메시지. */
    val error: String? = null,
    /** 다음 페이지 조회 실패 메시지. 기존 목록은 유지된다. */
    val loadMoreError: String? = null,
    /** 한 번이라도 서버 응답을 받았는지. 최초 로딩 전의 빈 목록과 실제 빈 목록을 구분한다. */
    val loaded: Boolean = false,
) {
    val hasNext: Boolean get() = loaded && page + 1 < totalPages
    val isSearching: Boolean get() = keyword.isNotBlank()
    val isEmpty: Boolean get() = loaded && error == null && rooms.isEmpty() && !initialLoading
    val isEmptyForSearch: Boolean get() = isEmpty && isSearching
    val isEmptyOverall: Boolean get() = isEmpty && !isSearching
}

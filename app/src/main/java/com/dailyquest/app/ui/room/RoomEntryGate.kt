package com.dailyquest.app.ui.room

/**
 * 방 생성·초대코드 참여는 아직 서버와 연결되지 않은 Mock 구현이다.
 * 서버 목록과 섞이지 않도록 기본값은 비활성이며, STEP 3-5/3-6에서 실제 API로 교체할 때 제거한다.
 * 기존 화면 로직 검증용 계측 테스트만 true로 바꾼다.
 */
object RoomEntryGate {
    @Volatile
    var mockRoomActionsEnabled: Boolean = false
}

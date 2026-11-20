package com.dailyquest.app

import com.dailyquest.app.data.auth.AppContainer
import com.dailyquest.app.data.auth.InMemoryTokenStore
import com.dailyquest.app.data.auth.TokenPair
import com.dailyquest.app.data.remote.DqApi
import com.dailyquest.app.data.remote.dto.CreateRoomRequest
import com.dailyquest.app.data.remote.dto.CreateRoomResponse
import com.dailyquest.app.data.remote.dto.InviteCodeDto
import com.dailyquest.app.data.remote.dto.JoinByCodeRequest
import com.dailyquest.app.data.remote.dto.LoginRequest
import com.dailyquest.app.data.remote.dto.PageDto
import com.dailyquest.app.data.remote.dto.RoomDetailDto
import com.dailyquest.app.data.remote.dto.RoomMemberDto
import com.dailyquest.app.data.remote.dto.RoomSummaryDto
import com.dailyquest.app.data.remote.dto.TransferOwnerRequest
import com.dailyquest.app.data.remote.dto.UpdateRoomRequest
import com.dailyquest.app.data.remote.dto.OAuthLoginRequest
import com.dailyquest.app.data.remote.dto.RefreshRequest
import com.dailyquest.app.data.remote.dto.SignupRequest
import com.dailyquest.app.data.remote.dto.SignupResponse
import com.dailyquest.app.data.remote.dto.TokenResponse
import com.dailyquest.app.data.remote.dto.UserMeResponse
import com.dailyquest.app.navigation.Routes

/** 기존 Mock UI 테스트용: 가짜 토큰과 가짜 API로 로그인된 상태에서 시작하고 개발용 Catalog로 진입한다. */
class TestDQApplication : DQApplication() {
    override fun createContainer() = AppContainer(
        tokenStore = InMemoryTokenStore(TokenPair("test-access", "test-refresh")),
        apiFactory = { _, _ -> FakeApi },
        startAfterAuth = Routes.CATALOG,
    )

    private object FakeApi : DqApi {
        override suspend fun signup(body: SignupRequest): SignupResponse = error("not used")
        override suspend fun login(body: LoginRequest): TokenResponse = error("not used")
        override suspend fun oauthLogin(provider: String, body: OAuthLoginRequest): TokenResponse = error("not used")
        override suspend fun refresh(body: RefreshRequest): TokenResponse = error("not used")
        override suspend fun logout() = Unit
        override suspend fun me() = UserMeResponse(userId = 1, email = "ui-test@example.com", nickname = "uitest")

        // 방 API는 아직 화면에서 사용하지 않는다(Mock UI 유지). 호출되면 테스트가 실패하도록 한다.
        override suspend fun listPublicRooms(keyword: String?, sort: String?, page: Int, size: Int): PageDto<RoomSummaryDto> = PageDto(
            content = listOf(
                RoomSummaryDto(roomId = 9001, name = "서버 공개방 A", isPublic = true, memberCount = 3, maxMembers = 10, isHot = true),
                RoomSummaryDto(roomId = 9002, name = "서버 공개방 B", isPublic = true, memberCount = 10, maxMembers = 10, joined = true),
            ),
            page = 0, size = size, totalElements = 2, totalPages = 1,
        )
        override suspend fun listMyRooms(): List<RoomSummaryDto> = error("not used")
        override suspend fun getRoom(roomId: Long): RoomDetailDto = error("not used")
        override suspend fun listRoomMembers(roomId: Long): List<RoomMemberDto> = error("not used")
        override suspend fun createRoom(body: CreateRoomRequest): CreateRoomResponse = error("not used")
        override suspend fun joinPublicRoom(roomId: Long): RoomDetailDto = error("not used")
        override suspend fun joinRoomByCode(body: JoinByCodeRequest): RoomDetailDto = error("not used")
        override suspend fun updateRoom(roomId: Long, body: UpdateRoomRequest): RoomDetailDto = error("not used")
        override suspend fun deleteRoom(roomId: Long) = error("not used")
        override suspend fun leaveRoom(roomId: Long) = error("not used")
        override suspend fun regenerateInviteCode(roomId: Long): InviteCodeDto = error("not used")
        override suspend fun kickMember(roomId: Long, userId: Long) = error("not used")
        override suspend fun transferOwner(roomId: Long, body: TransferOwnerRequest): RoomDetailDto = error("not used")
    }
}

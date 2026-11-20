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
import com.dailyquest.app.data.remote.dto.RoomOwnerDto
import com.dailyquest.app.data.remote.dto.RoomTodayMissionDto
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.HttpException
import retrofit2.Response
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
        private val roomNames = mapOf(9001L to "서버 공개방 A", 9002L to "서버 공개방 B") +
            (1..5).associate { 9100L + it to "서버 내 방 $it" }
        override suspend fun listMyRooms(): List<RoomSummaryDto> = (1..5).map {
            RoomSummaryDto(roomId = 9100L + it, name = "서버 내 방 $it", isPublic = it % 2 == 0, memberCount = it.toLong(), maxMembers = 10, joined = true)
        }
        // 9001: 미참여 공개방, 9002: 참여 중인 공개방, 9101~9105: 내 방(홀수=비공개). 9101만 방장.
        override suspend fun getRoom(roomId: Long): RoomDetailDto {
            val isPublic = roomId == 9001L || roomId == 9002L || (roomId in 9101L..9105L && roomId % 2 == 0L)
            if (roomId == TestRoomCalls.forbiddenRoomId) throw HttpException(
                Response.error<Any>(403, """{"code":"FORBIDDEN","message":"x"}""".toResponseBody("application/json".toMediaType()))
            )
            val role = when {
                roomId == 9001L -> null
                roomId == 9101L -> "OWNER"
                roomId == 9002L || roomId in 9102L..9105L -> "MEMBER"
                else -> throw HttpException(
                    Response.error<Any>(404, """{"code":"ROOM_NOT_FOUND","message":"x"}""".toResponseBody("application/json".toMediaType()))
                )
            }
            return RoomDetailDto(
                roomId = roomId, name = roomNames[roomId] ?: "서버 방 $roomId", description = "서버 설명 $roomId",
                isPublic = isPublic, inviteCode = if (role != null && !isPublic) "SRV$roomId" else null,
                owner = RoomOwnerDto(userId = 7, nickname = "서버방장"), memberCount = 2, maxMembers = 10, myRole = role,
                todayMission = if (roomId == 9101L) RoomTodayMissionDto(1, "서버 오늘의 미션") else null,
            )
        }
        override suspend fun listRoomMembers(roomId: Long): List<RoomMemberDto> {
            TestRoomCalls.membersRequested.add(roomId)
            if (roomId == 9103L) throw java.io.IOException("offline")
            return listOf(
                RoomMemberDto(userId = 7, nickname = "서버방장", role = "OWNER"),
                RoomMemberDto(userId = 8, nickname = "서버멤버", role = "MEMBER"),
            )
        }
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

/** FakeApi가 받은 멤버 목록 요청. 비멤버 방에 멤버 API를 호출하지 않는지 검증하는 데 쓴다. */
object TestRoomCalls {
    /** 지정한 방의 상세 조회를 403으로 응답하게 한다. 테스트가 끝나면 null로 되돌린다. */
    @Volatile var forbiddenRoomId: Long? = null
    val membersRequested: MutableList<Long> = java.util.Collections.synchronizedList(mutableListOf())
}

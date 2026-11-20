package com.dailyquest.app

import com.dailyquest.app.data.auth.AppContainer
import com.dailyquest.app.data.auth.InMemoryTokenStore
import com.dailyquest.app.data.auth.TokenPair
import com.dailyquest.app.data.remote.DqApi
import com.dailyquest.app.data.remote.dto.CreateMissionRequest
import com.dailyquest.app.data.remote.dto.CreateRoomRequest
import com.dailyquest.app.data.remote.dto.MissionDetailDto
import com.dailyquest.app.data.remote.dto.MissionDto
import com.dailyquest.app.data.remote.dto.MissionRewardDto
import com.dailyquest.app.data.remote.dto.MissionRoomBriefDto
import com.dailyquest.app.data.remote.dto.MyProofDto
import com.dailyquest.app.data.remote.dto.MyRoomMissionsDto
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
                RoomSummaryDto(roomId = 9001, name = "서버 공개방 A", isPublic = true, memberCount = 3, maxMembers = 10, isHot = true, joined = 9001L in TestRoomCalls.joined),
                RoomSummaryDto(roomId = 9002, name = "서버 공개방 B", isPublic = true, memberCount = 10, maxMembers = 10, joined = 9002L !in TestRoomCalls.left),
            ).filter { it.roomId !in TestRoomCalls.gone } + TestRoomCalls.created.filter { it.isPublic && (keyword.isNullOrBlank() || it.name.contains(keyword)) }.map {
                RoomSummaryDto(roomId = it.roomId, name = it.name, isPublic = true, memberCount = 1, maxMembers = it.maxMembers, joined = true)
            },
            page = 0, size = size, totalElements = 2, totalPages = 1,
        )
        private val roomNames = mapOf(9001L to "서버 공개방 A", 9002L to "서버 공개방 B") +
            (1..5).associate { 9100L + it to "서버 내 방 $it" }
        override suspend fun listMyRooms(): List<RoomSummaryDto> = ((1..5).map {
            RoomSummaryDto(roomId = 9100L + it, name = "서버 내 방 $it", isPublic = it % 2 == 0, memberCount = it.toLong(), maxMembers = 10, joined = true)
        } + TestRoomCalls.created.map {
            RoomSummaryDto(roomId = it.roomId, name = it.name, isPublic = it.isPublic, memberCount = 1, maxMembers = it.maxMembers, joined = true)
        } + TestRoomCalls.joined.map {
            RoomSummaryDto(roomId = it, name = "서버 공개방 A", isPublic = true, memberCount = 4, maxMembers = 10, joined = true)
        }).filter { it.roomId !in TestRoomCalls.gone && it.roomId !in TestRoomCalls.left }
            .map { it.copy(name = TestRoomCalls.renamed[it.roomId] ?: it.name) }
        // 9001: 미참여 공개방, 9002: 참여 중인 공개방, 9101~9105: 내 방(홀수=비공개). 9101만 방장.
        override suspend fun getRoom(roomId: Long): RoomDetailDto {
            if (roomId in TestRoomCalls.gone) throw http(404, "ROOM_NOT_FOUND")
            val base = baseRoom(roomId)
            val isPublic = TestRoomCalls.publicOverride[roomId] ?: base.isPublic
            val role = when {
                roomId in TestRoomCalls.transferred && base.myRole == "OWNER" -> "MEMBER"
                roomId in TestRoomCalls.left -> if (isPublic) null else throw http(403, "FORBIDDEN")
                roomId in TestRoomCalls.joined && base.myRole == null -> "MEMBER"
                else -> base.myRole
            }
            val kickedHere = if (roomId == 9101L) TestRoomCalls.kicked.size else 0
            return base.copy(
                name = TestRoomCalls.renamed[roomId] ?: base.name,
                description = if (roomId in TestRoomCalls.describedAs.keys) TestRoomCalls.describedAs[roomId]?.takeIf { it.isNotBlank() } else base.description,
                isPublic = isPublic,
                maxMembers = TestRoomCalls.maxOverride[roomId] ?: base.maxMembers,
                memberCount = base.memberCount + (if (roomId in TestRoomCalls.joined && base.myRole == null) 1 else 0) - kickedHere,
                myRole = role,
                inviteCode = if (role != null && !isPublic) TestRoomCalls.inviteOverride[roomId] ?: base.inviteCode ?: "CHG$roomId" else null,
            )
        }

        private fun http(code: Int, error: String) = HttpException(
            Response.error<Any>(code, """{"code":"$error","message":"x"}""".toResponseBody("application/json".toMediaType()))
        )

        private fun baseRoom(roomId: Long): RoomDetailDto {
            val isPublic = roomId == 9001L || roomId == 9002L || (roomId in 9101L..9105L && roomId % 2 == 0L)
            if (roomId == TestRoomCalls.forbiddenRoomId) throw HttpException(
                Response.error<Any>(403, """{"code":"FORBIDDEN","message":"x"}""".toResponseBody("application/json".toMediaType()))
            )
            TestRoomCalls.created.firstOrNull { it.roomId == roomId }?.let {
                return RoomDetailDto(
                    roomId = roomId, name = it.name, description = it.description, isPublic = it.isPublic,
                    inviteCode = it.inviteCode, owner = RoomOwnerDto(userId = 1, nickname = "uitest"),
                    memberCount = 1, maxMembers = it.maxMembers, myRole = "OWNER",
                )
            }
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
            if (TestRoomCalls.created.any { it.roomId == roomId }) {
                return listOf(RoomMemberDto(userId = 1, nickname = "uitest", role = "OWNER"))
            }
            return listOf(
                RoomMemberDto(userId = 7, nickname = "서버방장", role = "OWNER"),
                RoomMemberDto(userId = 8, nickname = "서버멤버", role = "MEMBER"),
            ).filter { it.userId !in TestRoomCalls.kicked }
        }
        override suspend fun createRoom(body: CreateRoomRequest): CreateRoomResponse {
            TestRoomCalls.createCalls++
            TestRoomCalls.createFailure?.let { throw it }
            val id = 9301L + TestRoomCalls.created.size
            val code = if (body.isPublic) null else "NEW$id"
            TestRoomCalls.created.add(
                TestCreatedRoom(id, body.name.trim(), body.description, body.isPublic, body.maxMembers, code)
            )
            return CreateRoomResponse(roomId = id, name = body.name.trim(), isPublic = body.isPublic, inviteCode = code, ownerId = 1)
        }
        // ---- 미션: 서버 응답을 흉내 낸다. 실제 서버에는 어떤 요청도 가지 않는다. ----
        private fun missionDto(id: Long, type: String, content: String, creator: RoomOwnerDto? = null, assignee: RoomOwnerDto? = null,
                               due: String = "2099-01-01T00:00:00Z", proof: MyProofDto? = null) = MissionDto(
            missionId = id, type = type, content = content, reward = MissionRewardDto("COIN", if (type == "APP") 50 else 100, 0),
            createdBy = creator, assignee = assignee, date = if (type == "APP") "2025-04-17" else null, dueAt = due, myProof = proof,
            createdAt = "2025-04-17T00:00:00Z",
        )
        private val friend = RoomOwnerDto(userId = 7, nickname = "서버방장")
        private fun missionsOf(roomId: Long): List<MissionDto> {
            val base = when (roomId) {
                9101L -> listOf(
                    missionDto(8001, "APP", "서버 앱 미션 9101"),
                    missionDto(8002, "MEMBER", "서버 친구 미션", creator = friend, assignee = RoomOwnerDto(1, "uitest")),
                )
                9102L -> listOf(missionDto(8003, "APP", "서버 앱 미션 9102", proof = MyProofDto(1, "PENDING")))
                else -> emptyList()
            }
            return base + TestMissionCalls.created.filter { it.first == roomId }.map { it.second }
        }
        private fun isMyRoom(roomId: Long) = roomId in 9101L..9105L && roomId !in TestRoomCalls.gone && roomId !in TestRoomCalls.left
        override suspend fun listMyMissions(): List<MyRoomMissionsDto> {
            TestMissionCalls.mineCalls++
            TestMissionCalls.listFailure?.let { throw it }
            if (TestMissionCalls.noRooms) return emptyList()
            if (!TestMissionCalls.seeded) return (9101L..9105L).filter { isMyRoom(it) }.map { MyRoomMissionsDto(MissionRoomBriefDto(it, "서버 내 방 ${it - 9100}"), emptyList()) }
            return (9101L..9105L).filter { isMyRoom(it) }.map {
                MyRoomMissionsDto(MissionRoomBriefDto(it, "서버 내 방 ${it - 9100}"), missionsOf(it))
            }
        }
        override suspend fun listRoomMissions(roomId: Long, status: String?, type: String?, page: Int, size: Int): PageDto<MissionDto> {
            TestMissionCalls.roomCalls.add(roomId to status)
            TestMissionCalls.listFailure?.let { throw it }
            if (!isMyRoom(roomId)) throw http(403, "FORBIDDEN")
            if (!TestMissionCalls.seeded) return PageDto(content = emptyList(), page = 0, size = size, totalElements = 0, totalPages = 1)
            val items = if (status == "EXPIRED") listOf(missionDto(8100 + roomId % 100, "MEMBER", "지난 미션 $roomId", creator = friend, due = "2020-01-01T00:00:00Z"))
            else missionsOf(roomId)
            return PageDto(content = items, page = 0, size = size, totalElements = items.size.toLong(), totalPages = 1)
        }
        override suspend fun getMission(missionId: Long): MissionDetailDto {
            val m = (9101L..9105L).flatMap { missionsOf(it) }.firstOrNull { it.missionId == missionId } ?: throw http(404, "MISSION_NOT_FOUND")
            return MissionDetailDto(m.missionId, m.type, m.content, m.reward, m.createdBy, m.assignee, m.date, m.dueAt, m.myProof, m.proofCount, m.createdAt)
        }
        override suspend fun createMission(roomId: Long, body: CreateMissionRequest): MissionDto {
            TestMissionCalls.createRequests.add(roomId to body)
            TestMissionCalls.createFailure?.let { throw it }
            val assignee = body.assigneeUserId?.let { RoomOwnerDto(it, if (it == 8L) "서버멤버" else "서버방장") }
            val dto = missionDto(8500L + TestMissionCalls.created.size, "MEMBER", body.content, creator = RoomOwnerDto(1, "uitest"), assignee = assignee)
                .copy(reward = MissionRewardDto("COIN", body.rewardCoins, 0))
            TestMissionCalls.created.add(roomId to dto)
            return dto
        }
        override suspend fun joinPublicRoom(roomId: Long): RoomDetailDto {
            TestRoomCalls.calls.add("join:$roomId")
            TestRoomCalls.mutationFailure?.let { throw it }
            if (roomId == 9002L) throw http(409, "ALREADY_JOINED")
            TestRoomCalls.joined.add(roomId)
            return getRoom(roomId)
        }
        // 코드: ABC123 → 방 9001 참여, FULL01 → 정원 초과, DUP001 → 이미 참여, 그 외 → 잘못된 코드. 코드 값은 기록하지 않는다.
        override suspend fun joinRoomByCode(body: JoinByCodeRequest): RoomDetailDto {
            TestRoomCalls.calls.add("joinCode")
            TestRoomCalls.mutationFailure?.let { throw it }
            when (body.inviteCode) {
                "ABC123" -> { TestRoomCalls.joined.add(9001L); return getRoom(9001L) }
                "FULL01" -> throw http(409, "ROOM_FULL")
                "DUP001" -> throw http(409, "ALREADY_JOINED")
                else -> throw http(404, "INVALID_INVITE_CODE")
            }
        }
        override suspend fun updateRoom(roomId: Long, body: UpdateRoomRequest): RoomDetailDto {
            TestRoomCalls.calls.add("update:$roomId")
            TestRoomCalls.lastUpdate = body
            TestRoomCalls.mutationFailure?.let { throw it }
            val current = getRoom(roomId)
            body.maxMembers?.let { if (it < current.memberCount) throw http(409, "MAX_MEMBERS_BELOW_CURRENT") }
            body.name?.let { TestRoomCalls.renamed[roomId] = it }
            body.description?.let { TestRoomCalls.describedAs[roomId] = it }
            body.maxMembers?.let { TestRoomCalls.maxOverride[roomId] = it }
            body.isPublic?.let {
                TestRoomCalls.publicOverride[roomId] = it
                if (!it) TestRoomCalls.inviteOverride[roomId] = "CHG$roomId"
            }
            return getRoom(roomId)
        }
        override suspend fun deleteRoom(roomId: Long) {
            TestRoomCalls.calls.add("delete:$roomId")
            TestRoomCalls.mutationFailure?.let { throw it }
            TestRoomCalls.gone.add(roomId)
        }
        override suspend fun leaveRoom(roomId: Long) {
            TestRoomCalls.calls.add("leave:$roomId")
            TestRoomCalls.mutationFailure?.let { throw it }
            val d = getRoom(roomId)
            if (d.myRole == "OWNER" && d.memberCount > 1) throw http(409, "OWNER_CANNOT_LEAVE")
            if (d.memberCount <= 1) TestRoomCalls.gone.add(roomId) else TestRoomCalls.left.add(roomId)
        }
        override suspend fun regenerateInviteCode(roomId: Long): InviteCodeDto {
            TestRoomCalls.calls.add("regenerate:$roomId")
            TestRoomCalls.mutationFailure?.let { throw it }
            val code = "REG${TestRoomCalls.calls.size}${roomId % 100}"
            TestRoomCalls.inviteOverride[roomId] = code
            return InviteCodeDto(code)
        }
        override suspend fun kickMember(roomId: Long, userId: Long) {
            TestRoomCalls.calls.add("kick:$roomId:$userId")
            TestRoomCalls.mutationFailure?.let { throw it }
            TestRoomCalls.kicked.add(userId)
        }
        override suspend fun transferOwner(roomId: Long, body: TransferOwnerRequest): RoomDetailDto {
            TestRoomCalls.calls.add("transfer:$roomId:${body.userId}")
            TestRoomCalls.mutationFailure?.let { throw it }
            TestRoomCalls.transferred.add(roomId)
            return getRoom(roomId)
        }
    }
}

/** 미션 FakeApi 상태. 테스트 후 reset()으로 비운다. */
object TestMissionCalls {
    val created: MutableList<Pair<Long, MissionDto>> = java.util.Collections.synchronizedList(mutableListOf())
    val createRequests: MutableList<Pair<Long, CreateMissionRequest>> = java.util.Collections.synchronizedList(mutableListOf())
    val roomCalls: MutableList<Pair<Long, String?>> = java.util.Collections.synchronizedList(mutableListOf())
    @Volatile var mineCalls = 0
    @Volatile var noRooms = false
    /** false(기본)면 서버 미션이 없는 상태로 응답해 기존 방 테스트의 화면 구성을 바꾸지 않는다. */
    @Volatile var seeded = false
    @Volatile var createFailure: Throwable? = null
    @Volatile var listFailure: Throwable? = null
    fun reset() {
        created.clear(); createRequests.clear(); roomCalls.clear(); mineCalls = 0
        noRooms = false; seeded = false; createFailure = null; listFailure = null
    }
}

/** FakeApi가 받은 멤버 목록 요청. 비멤버 방에 멤버 API를 호출하지 않는지 검증하는 데 쓴다. */
class TestCreatedRoom(
    val roomId: Long, val name: String, val description: String?, val isPublic: Boolean, val maxMembers: Int, val inviteCode: String?,
)

object TestRoomCalls {
    /** createRoom으로 만들어진 방. listMyRooms·listPublicRooms·getRoom이 이를 반영한다. 테스트 후 비운다. */
    val created: MutableList<TestCreatedRoom> = java.util.Collections.synchronizedList(mutableListOf())
    @Volatile var createCalls = 0
    @Volatile var createFailure: Throwable? = null
    fun reset() {
        created.clear(); createCalls = 0; createFailure = null; forbiddenRoomId = null; membersRequested.clear()
        calls.clear(); joined.clear(); left.clear(); gone.clear(); kicked.clear(); transferred.clear()
        renamed.clear(); describedAs.clear(); maxOverride.clear(); publicOverride.clear(); inviteOverride.clear()
        mutationFailure = null; lastUpdate = null
        TestMissionCalls.reset()
    }
    /** 방 참여·관리 FakeApi 상태. 서버 응답을 흉내 내는 상태이며 테스트 후 reset()으로 비운다. */
    val calls: MutableList<String> = java.util.Collections.synchronizedList(mutableListOf())
    val joined: MutableSet<Long> = java.util.Collections.synchronizedSet(linkedSetOf())
    val left: MutableSet<Long> = java.util.Collections.synchronizedSet(linkedSetOf())
    val gone: MutableSet<Long> = java.util.Collections.synchronizedSet(linkedSetOf())
    val kicked: MutableSet<Long> = java.util.Collections.synchronizedSet(linkedSetOf())
    val transferred: MutableSet<Long> = java.util.Collections.synchronizedSet(linkedSetOf())
    val renamed: MutableMap<Long, String> = java.util.concurrent.ConcurrentHashMap()
    val describedAs: MutableMap<Long, String> = java.util.concurrent.ConcurrentHashMap()
    val maxOverride: MutableMap<Long, Int> = java.util.concurrent.ConcurrentHashMap()
    val publicOverride: MutableMap<Long, Boolean> = java.util.concurrent.ConcurrentHashMap()
    val inviteOverride: MutableMap<Long, String> = java.util.concurrent.ConcurrentHashMap()
    /** 설정하면 모든 참여·관리 요청이 이 예외로 실패한다(상태는 바뀌지 않는다). */
    @Volatile var mutationFailure: Throwable? = null
    @Volatile var lastUpdate: com.dailyquest.app.data.remote.dto.UpdateRoomRequest? = null
    /** 지정한 방의 상세 조회를 403으로 응답하게 한다. 테스트가 끝나면 null로 되돌린다. */
    @Volatile var forbiddenRoomId: Long? = null
    val membersRequested: MutableList<Long> = java.util.Collections.synchronizedList(mutableListOf())
}

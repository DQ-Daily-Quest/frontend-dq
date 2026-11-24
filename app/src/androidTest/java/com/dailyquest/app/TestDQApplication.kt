package com.dailyquest.app

import com.dailyquest.app.data.auth.AppContainer
import com.dailyquest.app.data.auth.InMemoryTokenStore
import com.dailyquest.app.data.auth.TokenPair
import com.dailyquest.app.data.remote.DqApi
import com.dailyquest.app.data.remote.dto.ApprovalDto
import com.dailyquest.app.data.remote.dto.ChangePasswordRequest
import com.dailyquest.app.data.remote.dto.SetGenderRequest
import com.dailyquest.app.data.remote.dto.UpdateAvatarRequest
import com.dailyquest.app.data.remote.dto.UpdateMeRequest
import com.dailyquest.app.data.remote.dto.WithdrawRequest
import com.dailyquest.app.data.remote.dto.CoinTransactionDto
import com.dailyquest.app.data.remote.dto.ItemDto
import com.dailyquest.app.data.remote.dto.MyItemDto
import com.dailyquest.app.data.remote.dto.PurchaseDto
import com.dailyquest.app.data.remote.dto.CreateMissionRequest
import com.dailyquest.app.data.remote.dto.PendingMissionBriefDto
import com.dailyquest.app.data.remote.dto.PendingProofDto
import com.dailyquest.app.data.remote.dto.ProofDetailDto
import com.dailyquest.app.data.remote.dto.ReactionRequest
import com.dailyquest.app.data.remote.dto.ReactionSummaryDto
import com.dailyquest.app.data.remote.dto.ProofDto
import com.dailyquest.app.data.remote.dto.ProofMissionBriefDto
import com.dailyquest.app.data.remote.dto.ProofRoomRefDto
import com.dailyquest.app.data.remote.dto.SubmitProofRequest
import com.dailyquest.app.data.remote.dto.UploadDto
import com.dailyquest.app.data.remote.dto.VoteDto
import com.dailyquest.app.data.remote.dto.CreateRoomRequest
import com.dailyquest.app.data.remote.dto.MissionDetailDto
import com.dailyquest.app.data.remote.dto.MissionDto
import com.dailyquest.app.data.remote.dto.MissionProofItemDto
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
import com.dailyquest.app.data.remote.dto.AvailableItemDto
import com.dailyquest.app.data.remote.dto.DecorItemDto
import com.dailyquest.app.data.remote.dto.DecorUserDto
import com.dailyquest.app.data.remote.dto.DecorationsDto
import com.dailyquest.app.data.remote.dto.PlacementDto
import com.dailyquest.app.data.remote.dto.SaveDecorationsRequest
import com.dailyquest.app.navigation.Routes
import com.dailyquest.app.ui.decor.DecorImageSource

/** 기존 Mock UI 테스트용: 가짜 토큰과 가짜 API로 로그인된 상태에서 시작하고 개발용 Catalog로 진입한다. */
class TestDQApplication : DQApplication() {
    override val promptsNotificationPermission = false

    override fun createContainer() = AppContainer(
        tokenStore = InMemoryTokenStore(TokenPair("test-access", "test-refresh")),
        apiFactory = { _, _ -> FakeApi },
        startAfterAuth = Routes.CATALOG,
        // 3D 아바타는 매 프레임 그려서 기존 UI 테스트의 idle 대기가 끝나지 않는다. 테스트에서는 빈 상자로 대신한다(STEP 9-7).
        avatar3dEnabled = false,
        // 방·가구 그림은 S3 대신 빈 그림을 준다. 인터넷 없이 돌게 하기 위해서다(STEP 10-5). 실제 그림은 debug.RoomExampleRenderTest가 확인한다.
        decorImages = DecorImageSource { Result.success(BLANK_DECOR_IMAGE) },
    )

    private object FakeApi : DqApi {
        override suspend fun signup(body: SignupRequest): SignupResponse = error("not used")
        override suspend fun login(body: LoginRequest): TokenResponse = error("not used")
        override suspend fun oauthLogin(provider: String, body: OAuthLoginRequest): TokenResponse = error("not used")
        override suspend fun refresh(body: RefreshRequest): TokenResponse = error("not used")
        override suspend fun logout() = Unit
        private fun meResponse() = UserMeResponse(
            userId = 1, email = "ui-test@example.com", nickname = TestProfileCalls.nickname, hasPassword = TestProfileCalls.hasPassword, coins = TestShopCalls.coins,
            streak = com.dailyquest.app.data.remote.dto.StreakDto(current = 3, longest = 8),
            stats = com.dailyquest.app.data.remote.dto.UserStatsDto(roomCount = 2, completedMissionCount = 11),
            createdAt = "2025-03-04T01:02:03Z",
            // STEP 9: 기본은 성별 MALE·착용 없음. 성별이 없으면 로그인 후 캐릭터 만들기로 가므로(9-9) 기존 테스트가 Catalog에서 시작하도록 MALE을 둔다.
            gender = TestAvatarCalls.gender,
            avatar = TestAvatarCalls.avatarDto(),
        )
        override suspend fun updateMe(body: UpdateMeRequest): UserMeResponse {
            TestProfileCalls.updates += body.nickname
            if (body.nickname == TestProfileCalls.TAKEN) throw http(409, "DUPLICATE_NICKNAME")
            TestProfileCalls.nickname = body.nickname
            return meResponse()
        }
        override suspend fun changePassword(body: ChangePasswordRequest) {
            TestProfileCalls.passwordCalls += body.newPassword
            TestProfileCalls.passwordFailure?.let { throw it }
        }
        override suspend fun withdraw(body: WithdrawRequest) {
            TestProfileCalls.withdrawCalls += (body.password ?: "<none>")
            TestProfileCalls.withdrawFailure?.let { throw it }
        }
        override suspend fun me() = meResponse()
        override suspend fun requestPasswordReset(body: com.dailyquest.app.data.remote.dto.PasswordResetRequest) {
            TestResetCalls.requests += body.email
            TestResetCalls.requestFailure?.let { throw it }
        }
        override suspend fun verifyPasswordReset(body: com.dailyquest.app.data.remote.dto.PasswordResetVerifyRequest) {
            TestResetCalls.verifies += body.email to body.code
            if (body.code != TestResetCalls.VALID_CODE) throw http(400, "INVALID_RESET_CODE")
        }
        override suspend fun confirmPasswordReset(body: com.dailyquest.app.data.remote.dto.PasswordResetConfirmRequest) {
            TestResetCalls.confirms += Triple(body.email, body.code, body.newPassword)
            TestResetCalls.confirmFailure?.let { throw it }
        }

        // STEP 9-9 성별 확정: 이미 있으면 GENDER_ALREADY_SET(409), 없으면 정하고 내 정보를 돌려준다.
        override suspend fun setGender(body: SetGenderRequest): UserMeResponse {
            TestAvatarCalls.genderRequests += body.gender
            TestAvatarCalls.genderFailure?.let { TestAvatarCalls.genderFailure = null; throw it }
            if (TestAvatarCalls.gender != null) throw http(409, "GENDER_ALREADY_SET")
            TestAvatarCalls.gender = body.gender
            return meResponse()
        }

        // STEP 9 아바타 착용 변경: 보유하지 않은 아이템이면 ITEM_NOT_OWNED, 아니면 착용을 기억하고 내 정보를 돌려준다.
        override suspend fun updateAvatar(body: UpdateAvatarRequest): UserMeResponse {
            TestAvatarCalls.requests += body.avatar
            TestAvatarCalls.failure?.let { TestAvatarCalls.failure = null; throw it }
            body.avatar.values.filterNotNull().firstOrNull { it !in TestShopCalls.owned }?.let { throw http(403, "ITEM_NOT_OWNED") }
            TestAvatarCalls.equipped.putAll(body.avatar)
            return meResponse()
        }

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
            return (base + TestMissionCalls.created.filter { it.first == roomId }.map { it.second })
                .map { if (it.missionId in TestMissionCalls.rejectedMissionIds) it.copy(proofCount = 1) else it }
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
            return MissionDetailDto(m.missionId, m.type, m.content, m.reward, m.createdBy, m.assignee, m.date, m.dueAt, m.myProof, m.proofCount, m.createdAt,
                if (m.missionId in TestMissionCalls.rejectedMissionIds) listOf(MissionProofItemDto(7700 + m.missionId, RoomOwnerDto(1, "uitest"), "REJECTED")) else emptyList())
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
        override suspend fun upload(file: okhttp3.MultipartBody.Part, purpose: String?): UploadDto {
            TestProofCalls.calls.add("upload:$purpose")
            TestProofCalls.uploadFailure?.let { throw it }
            return UploadDto("https://cdn.example/proof.jpg", "https://cdn.example/proof-thumb.jpg", "IMAGE")
        }
        override suspend fun submitProof(missionId: Long, body: SubmitProofRequest): ProofDto {
            TestProofCalls.calls.add("submit:$missionId")
            TestProofCalls.submitRequests.add(missionId to body)
            TestProofCalls.submitFailure?.let { throw it }
            return ProofDto(7900 + missionId, missionId, RoomOwnerDto(1, "uitest"), body.mediaUrl, body.thumbnailUrl, body.mediaType, body.text, "PENDING", ApprovalDto(0, 0, 1))
        }
        override suspend fun getProof(proofId: Long): ProofDetailDto {
            TestProofCalls.calls.add("detail:$proofId")
            return TestProofCalls.proofs[proofId] ?: throw http(404, "PROOF_NOT_FOUND")
        }
        override suspend fun addReaction(proofId: Long, body: ReactionRequest): List<ReactionSummaryDto> = reaction(proofId, body.emoji, true)
        override suspend fun removeReaction(proofId: Long, body: ReactionRequest): List<ReactionSummaryDto> = reaction(proofId, body.emoji, false)
        private fun reaction(proofId: Long, emoji: String, add: Boolean): List<ReactionSummaryDto> {
            TestProofCalls.calls.add("${if (add) "react" else "unreact"}:$proofId:$emoji")
            TestProofCalls.reactionFailure?.let { throw it }
            val d = TestProofCalls.proofs[proofId] ?: throw http(404, "PROOF_NOT_FOUND")
            val rest = d.reactions.filter { it.emoji != emoji }
            val cur = d.reactions.firstOrNull { it.emoji == emoji }
            val next = if (add) rest + ReactionSummaryDto(emoji, (cur?.count ?: 0) + if (cur?.reacted == true) 0 else 1, true)
            else if (cur != null && cur.count > 1) rest + ReactionSummaryDto(emoji, cur.count - 1, false) else rest
            TestProofCalls.proofs[proofId] = d.copy(reactions = next)
            return next
        }
        override suspend fun deleteProof(proofId: Long) {
            TestProofCalls.calls.add("delete:$proofId")
            TestProofCalls.deleteFailure?.let { throw it }
            TestProofCalls.proofs.remove(proofId) ?: throw http(404, "PROOF_NOT_FOUND")
        }
        override suspend fun listNotifications(unreadOnly: Boolean, page: Int, size: Int): PageDto<com.dailyquest.app.data.remote.dto.NotificationDto> {
            TestNotificationCalls.calls.add("list:$unreadOnly")
            TestNotificationCalls.listFailure?.let { throw it }
            val all = TestNotificationCalls.items.sortedByDescending { it.notificationId }.filter { !unreadOnly || !it.isRead }
            return PageDto(content = all, page = 0, size = size, totalElements = all.size.toLong(), totalPages = 1)
        }

        override suspend fun getUnreadCount(): com.dailyquest.app.data.remote.dto.UnreadCountDto =
            com.dailyquest.app.data.remote.dto.UnreadCountDto(TestNotificationCalls.items.count { !it.isRead }.toLong())

        override suspend fun markNotificationRead(notificationId: Long) {
            TestNotificationCalls.calls.add("read:$notificationId")
            TestNotificationCalls.readFailure?.let { throw it }
            val i = TestNotificationCalls.items.indexOfFirst { it.notificationId == notificationId }
            if (i < 0) throw http(404, "NOTIFICATION_NOT_FOUND")
            TestNotificationCalls.items[i] = TestNotificationCalls.items[i].copy(isRead = true)
        }

        override suspend fun markAllNotificationsRead() {
            TestNotificationCalls.calls.add("readAll")
            TestNotificationCalls.items.replaceAll { it.copy(isRead = true) }
        }

        override suspend fun updateFcmToken(body: com.dailyquest.app.data.remote.dto.FcmTokenRequest) {
            TestNotificationCalls.fcmTokens.add(body.token)
        }

        override suspend fun listFeed(roomId: Long, page: Int, size: Int): PageDto<PendingProofDto> {
            TestProofCalls.calls.add("feed:$roomId")
            TestProofCalls.feedFailure?.let { throw it }
            if (!isMyRoom(roomId)) throw http(403, "FORBIDDEN")
            val items = TestProofCalls.proofs.values.filter { it.room.roomId == roomId }.sortedByDescending { it.proofId }
                .map { PendingProofDto(it.proofId, it.user, PendingMissionBriefDto(it.mission.missionId, it.mission.content), it.mediaType, it.thumbnailUrl, it.status, false, it.createdAt) }
            return PageDto(content = items, page = 0, size = size, totalElements = items.size.toLong(), totalPages = 1)
        }
        override suspend fun listPendingProofs(roomId: Long, page: Int, size: Int): PageDto<PendingProofDto> {
            TestProofCalls.calls.add("pending:$roomId")
            if (!isMyRoom(roomId)) throw http(403, "FORBIDDEN")
            val items = TestProofCalls.proofs.values
                .filter { it.room.roomId == roomId && it.status == "PENDING" && it.approval.myVote == null && it.user.userId != 1L }
                .map { PendingProofDto(it.proofId, it.user, PendingMissionBriefDto(it.mission.missionId, it.mission.content), it.mediaType, it.thumbnailUrl, it.status, false, it.createdAt) }
            return PageDto(content = items, page = 0, size = size, totalElements = items.size.toLong(), totalPages = 1)
        }
        private fun vote(proofId: Long, choice: String): VoteDto {
            TestProofCalls.calls.add("$choice:$proofId")
            TestProofCalls.voteFailure?.let { throw it }
            val p = TestProofCalls.proofs[proofId] ?: throw http(404, "PROOF_NOT_FOUND")
            val approve = choice == "approve"
            val a = p.approval.copy(
                approved = p.approval.approved + if (approve) 1 else 0, rejected = p.approval.rejected + if (approve) 0 else 1,
                myVote = if (approve) "APPROVE" else "REJECT",
            )
            val status = if (a.approved >= a.required) "APPROVED" else if (a.rejected >= a.required) "REJECTED" else "PENDING"
            TestProofCalls.proofs[proofId] = p.copy(status = status, approval = a)
            return VoteDto(proofId, status, a, if (status == "APPROVED") MissionRewardDto("COIN", 50, 0) else null)
        }
        override suspend fun approveProof(proofId: Long): VoteDto = vote(proofId, "approve")
        override suspend fun rejectProof(proofId: Long): VoteDto = vote(proofId, "reject")
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

        override suspend fun listShopItems(type: String?, category: String?, page: Int, size: Int) = TestShopCalls.list(type, category, page, size)
        override suspend fun getShopItem(itemId: Long) = TestShopCalls.item(itemId)
        override suspend fun getShopItemsBatch(ids: String) = TestShopCalls.batch(ids)
        override suspend fun purchaseItem(itemId: Long) = TestShopCalls.purchase(itemId)
        override suspend fun listMyItems(type: String?, category: String?) = TestShopCalls.mine(type, category)
        override suspend fun listCoinTransactions(page: Int, size: Int) = TestShopCalls.transactions(page)

        override suspend fun getDecorations(roomId: Long) = TestDecorCalls.get(roomId)
        override suspend fun saveDecorations(roomId: Long, body: SaveDecorationsRequest) = TestDecorCalls.save(roomId, body)
        override suspend fun listAvailableDecor(roomId: Long) = TestDecorCalls.available(roomId)
    }
}

/**
 * STEP 10 방 꾸미기 가짜 서버 상태. 기본은 "한 번도 저장하지 않은 빈 방"과 기본 방만 가진 배치 가능 목록이다.
 * 저장하면 받은 요청을 기록하고, 배치 가능 목록에 있는 아이템으로 응답을 만들어 다음 조회에 돌려준다.
 */
private val BLANK_DECOR_IMAGE: androidx.compose.ui.graphics.ImageBitmap =
    androidx.compose.ui.graphics.ImageBitmap(4, 4)

object TestDecorCalls {
    val defaultRoom = DecorItemDto(9001, "기본 방", "FLOOR", "https://example.invalid/room_16x16.png", null, "room_16x16")

    @Volatile var availableItems: List<AvailableItemDto> = listOf(AvailableItemDto(defaultRoom, ownerCount = 1))
    private val saved: MutableMap<Long, DecorationsDto> = java.util.Collections.synchronizedMap(mutableMapOf())
    val saveRequests: MutableList<Pair<Long, SaveDecorationsRequest>> = java.util.Collections.synchronizedList(mutableListOf())
    @Volatile var failure: Throwable? = null

    /** 조회한 방 ID (요청 순서대로). 비멤버 방은 조회하지 않아야 한다. */
    val getRequests: MutableList<Long> = java.util.Collections.synchronizedList(mutableListOf())

    fun get(roomId: Long): DecorationsDto {
        getRequests += roomId
        failure?.let { throw it }
        return saved[roomId] ?: DecorationsDto(roomId)
    }

    /** 이미 저장된 방처럼 꾸민다 (테스트 준비용). */
    fun seed(roomId: Long, decorations: DecorationsDto) {
        saved[roomId] = decorations
    }

    fun available(roomId: Long): List<AvailableItemDto> {
        failure?.let { throw it }
        return availableItems
    }

    fun save(roomId: Long, body: SaveDecorationsRequest): DecorationsDto {
        failure?.let { throw it }
        saveRequests += roomId to body
        val items = availableItems.associate { it.item.itemId to it.item }
        val result = DecorationsDto(
            roomId = roomId,
            background = body.backgroundItemId?.let(items::get),
            placements = body.placements.mapIndexed { i, p ->
                PlacementDto(i + 1L, items.getValue(p.itemId), ownerId = 1, x = p.x, y = p.y, z = p.z, rotation = p.rotation)
            },
            updatedAt = "2026-11-24T00:00:00Z",
            updatedBy = DecorUserDto(1, "테스터"),
        )
        saved[roomId] = result
        return result
    }

    fun reset() {
        availableItems = listOf(AvailableItemDto(defaultRoom, ownerCount = 1))
        saved.clear()
        saveRequests.clear()
        getRequests.clear()
        failure = null
    }
}

/** 프로필·계정 관리 FakeApi 상태. 실제 서버에는 어떤 변경 요청도 보내지 않는다. */
/**
 * STEP 9 아바타 테스트용 가짜 서버 상태.
 * 기본 성별은 MALE이다. 성별이 없으면 로그인 직후 캐릭터 만들기 화면으로 가므로(9-9), 기존 테스트가 그대로 Catalog에서 시작하게 한다.
 */
object TestAvatarCalls {
    @Volatile var gender: String? = "MALE"
    val genderRequests: MutableList<String> = java.util.Collections.synchronizedList(mutableListOf())
    /** 슬롯 이름(소문자) → 아이템 ID */
    val equipped: MutableMap<String, Long?> = java.util.Collections.synchronizedMap(mutableMapOf())
    val requests: MutableList<Map<String, Long?>> = java.util.Collections.synchronizedList(mutableListOf())
    @Volatile var failure: Throwable? = null
    /** 성별 확정 요청을 한 번 실패시킨다 (STEP 9-9) */
    @Volatile var genderFailure: Throwable? = null

    fun avatarDto() = com.dailyquest.app.data.remote.dto.AvatarDto(
        hair = equipped["hair"], eyes = equipped["eyes"], skin = equipped["skin"], top = equipped["top"],
        bottom = equipped["bottom"], shoes = equipped["shoes"], hat = equipped["hat"],
    )

    fun reset() {
        gender = "MALE"; equipped.clear(); requests.clear(); genderRequests.clear(); failure = null; genderFailure = null
    }
}

object TestProfileCalls {
    const val TAKEN = "사용중닉"
    @Volatile var nickname = "uitest"
    @Volatile var hasPassword: Boolean? = true
    val updates: MutableList<String> = java.util.Collections.synchronizedList(mutableListOf())
    val passwordCalls: MutableList<String> = java.util.Collections.synchronizedList(mutableListOf())
    val withdrawCalls: MutableList<String> = java.util.Collections.synchronizedList(mutableListOf())
    @Volatile var passwordFailure: Throwable? = null
    @Volatile var withdrawFailure: Throwable? = null

    fun reset() {
        nickname = "uitest"; hasPassword = true; updates.clear(); passwordCalls.clear(); withdrawCalls.clear()
        passwordFailure = null; withdrawFailure = null
    }
}

/** 상점·인벤토리·코인 내역을 흉내 내는 가짜 서버. 서버처럼 소유·잔액·거래를 직접 갱신하며 테스트 후 reset()으로 비운다. */
object TestShopCalls {
    class Stock(val itemId: Long, val name: String, val type: String, val category: String, val price: Int, val imageUrl: String? = null, val isDefault: Boolean = false, val modelUrl: String? = null, val gender: String? = null)

    @Volatile var coins = 0
    val stock: MutableList<Stock> = java.util.Collections.synchronizedList(mutableListOf())
    val owned: MutableSet<Long> = java.util.Collections.synchronizedSet(linkedSetOf())
    val txs: MutableList<CoinTransactionDto> = java.util.Collections.synchronizedList(mutableListOf())
    val purchaseCalls: MutableList<Long> = java.util.Collections.synchronizedList(mutableListOf())
    val listCalls: MutableList<String> = java.util.Collections.synchronizedList(mutableListOf())
    /** 구매 요청을 받으면 한 번 던질 예외. [applyBeforeFailure]가 true면 서버는 처리하고 응답만 잃은 상황이다. */
    @Volatile var purchaseFailure: Throwable? = null
    @Volatile var applyBeforeFailure = false
    @Volatile var listFailure: Throwable? = null
    @Volatile var purchaseDelayMs = 0L

    fun reset() {
        coins = 0; stock.clear(); owned.clear(); txs.clear(); purchaseCalls.clear(); listCalls.clear()
        purchaseFailure = null; applyBeforeFailure = false; listFailure = null; purchaseDelayMs = 0; batchCalls.set(0); batchFailure = null
    }

    fun seedDefaults() {
        stock += Stock(1, "기본 머리", "AVATAR", "HAIR", 0, null, true)
        stock += Stock(2, "민트 상의", "AVATAR", "TOP", 120)
        stock += Stock(3, "비싼 모자", "AVATAR", "HAT", 900)
        stock += Stock(4, "나무 책상", "FURNITURE", "DESK", 80)
        owned += 1
    }

    private fun dto(s: Stock) = ItemDto(s.itemId, s.name, s.type, s.category, s.price, s.imageUrl, s.modelUrl, s.isDefault, s.itemId in owned, gender = s.gender)

    /** batch 요청 횟수 (프로필 얼굴 테스트). */
    val batchCalls = java.util.concurrent.atomic.AtomicInteger()
    @Volatile var batchFailure: Throwable? = null

    fun list(type: String?, category: String?, page: Int, size: Int): PageDto<ItemDto> {
        listCalls.add("$type:$category:$page")
        listFailure?.let { throw it }
        val all = stock.filter { (type == null || it.type == type) && (category == null || it.category == category) }.sortedBy { it.price }.map(::dto)
        return PageDto(all.drop(page * size).take(size), page, size, all.size.toLong(), if (all.isEmpty()) 0 else (all.size + size - 1) / size)
    }

    fun item(id: Long): ItemDto = dto(stock.firstOrNull { it.itemId == id } ?: throw http(404, "ITEM_NOT_FOUND"))

    fun batch(ids: String): List<ItemDto> {
        batchCalls.incrementAndGet()
        batchFailure?.let { throw it }
        val wanted = ids.split(',').mapNotNull { it.trim().toLongOrNull() }.toSet()
        return stock.filter { it.itemId in wanted }.sortedBy { it.itemId }.map(::dto)
    }

    fun purchase(id: Long): PurchaseDto {
        purchaseCalls.add(id)
        if (purchaseDelayMs > 0) Thread.sleep(purchaseDelayMs)
        val fail = purchaseFailure
        if (fail != null && !applyBeforeFailure) { purchaseFailure = null; throw fail }
        val s = stock.firstOrNull { it.itemId == id } ?: throw http(404, "ITEM_NOT_FOUND")
        if (id in owned) throw http(409, "ITEM_ALREADY_OWNED")
        if (coins < s.price) throw http(409, "INSUFFICIENT_COINS")
        coins -= s.price
        owned += id
        txs.add(0, CoinTransactionDto(txs.size + 1L, -s.price, coins, "PURCHASE", id, "2025-04-17T00:00:00Z"))
        if (fail != null) { purchaseFailure = null; throw fail }
        return PurchaseDto(id, coins)
    }

    fun mine(type: String?, category: String?): List<MyItemDto> = stock
        .filter { it.itemId in owned && (type == null || it.type == type) && (category == null || it.category == category) }
        .map { MyItemDto(it.itemId, it.name, it.type, it.category, it.price, it.imageUrl, null, it.isDefault, true, "2025-04-17T00:00:00Z", if (it.isDefault) "DEFAULT" else "SHOP", false) }

    fun transactions(page: Int): PageDto<CoinTransactionDto> = PageDto(if (page == 0) txs.toList() else emptyList(), page, 20, txs.size.toLong(), 1)

    fun http(status: Int, code: String): HttpException = HttpException(
        Response.error<Any>(status, """{"code":"$code","message":"x"}""".toResponseBody("application/json".toMediaType())),
    )
}

/** 미션 FakeApi 상태. 테스트 후 reset()으로 비운다. */
/** STEP 5 인증 FakeApi 상태. 서버 응답을 흉내 내며 reset()으로 비운다. */
object TestProofCalls {
    val calls: MutableList<String> = java.util.Collections.synchronizedList(mutableListOf())
    val submitRequests: MutableList<Pair<Long, SubmitProofRequest>> = java.util.Collections.synchronizedList(mutableListOf())
    val proofs: MutableMap<Long, ProofDetailDto> = java.util.concurrent.ConcurrentHashMap()
    @Volatile var uploadFailure: Throwable? = null
    @Volatile var submitFailure: Throwable? = null
    @Volatile var voteFailure: Throwable? = null
    @Volatile var feedFailure: Throwable? = null
    @Volatile var reactionFailure: Throwable? = null
    @Volatile var deleteFailure: Throwable? = null
    fun reset() {
        feedFailure = null; reactionFailure = null; deleteFailure = null
        calls.clear(); submitRequests.clear(); proofs.clear(); uploadFailure = null; submitFailure = null; voteFailure = null
    }
    /** 방 [roomId]의 APP 미션 [missionId]에 다른 멤버가 낸 승인 대기 인증을 시드한다. */
    fun seedPending(proofId: Long, roomId: Long, missionId: Long, type: String = "APP", required: Int = 1, user: RoomOwnerDto = RoomOwnerDto(7, "서버방장"),
        mediaType: String = "IMAGE", status: String = "PENDING") {
        proofs[proofId] = ProofDetailDto(
            proofId, missionId, user, "https://cdn.example/p$proofId" + if (mediaType == "VIDEO") ".mp4" else ".jpg",
            if (mediaType == "VIDEO") null else "https://cdn.example/p$proofId-t.jpg", mediaType, "오늘의 인증", status,
            ApprovalDto(0, 0, required, null, emptyList()), "2025-04-17T00:00:00Z",
            ProofMissionBriefDto(missionId, type, "서버 앱 미션 $roomId"), ProofRoomRefDto(roomId, "서버 내 방 ${roomId - 9100}"),
        )
    }
}

object TestMissionCalls {
    val created: MutableList<Pair<Long, MissionDto>> = java.util.Collections.synchronizedList(mutableListOf())
    val createRequests: MutableList<Pair<Long, CreateMissionRequest>> = java.util.Collections.synchronizedList(mutableListOf())
    val roomCalls: MutableList<Pair<Long, String?>> = java.util.Collections.synchronizedList(mutableListOf())
    @Volatile var mineCalls = 0
    @Volatile var noRooms = false
    /** false(기본)면 서버 미션이 없는 상태로 응답해 기존 방 테스트의 화면 구성을 바꾸지 않는다. */
    @Volatile var seeded = false
    /** 내 인증이 거절된 상태로 응답할 미션 id(서버는 상세의 proofs에만 거절 인증을 내려준다). */
    val rejectedMissionIds: MutableSet<Long> = java.util.concurrent.ConcurrentHashMap.newKeySet()
    @Volatile var createFailure: Throwable? = null
    @Volatile var listFailure: Throwable? = null
    fun reset() {
        created.clear(); createRequests.clear(); roomCalls.clear(); rejectedMissionIds.clear(); mineCalls = 0
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
        TestMissionCalls.reset(); TestProofCalls.reset(); TestShopCalls.reset(); TestProfileCalls.reset(); TestNotificationCalls.reset()
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

/** 비밀번호 재설정 API 호출 기록과 응답 설정. 실제 서버에는 요청하지 않는다. */
object TestResetCalls {
    const val VALID_CODE = "012345"
    val requests: MutableList<String> = java.util.Collections.synchronizedList(mutableListOf())
    val verifies: MutableList<Pair<String, String>> = java.util.Collections.synchronizedList(mutableListOf())
    val confirms: MutableList<Triple<String, String, String>> = java.util.Collections.synchronizedList(mutableListOf())
    @Volatile var requestFailure: Throwable? = null
    @Volatile var confirmFailure: Throwable? = null

    fun reset() {
        requests.clear(); verifies.clear(); confirms.clear(); requestFailure = null; confirmFailure = null
    }
}

/** 알림함·FCM 토큰 등록을 흉내 내는 가짜 서버 상태. */
object TestNotificationCalls {
    val calls: MutableList<String> = java.util.Collections.synchronizedList(mutableListOf())
    val fcmTokens: MutableList<String> = java.util.Collections.synchronizedList(mutableListOf())
    val items: MutableList<com.dailyquest.app.data.remote.dto.NotificationDto> = java.util.Collections.synchronizedList(mutableListOf())
    @Volatile var listFailure: Throwable? = null
    @Volatile var readFailure: Throwable? = null

    fun reset() {
        calls.clear(); fcmTokens.clear(); items.clear(); listFailure = null; readFailure = null
    }
}

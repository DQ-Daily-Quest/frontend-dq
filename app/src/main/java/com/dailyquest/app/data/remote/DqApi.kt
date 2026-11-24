package com.dailyquest.app.data.remote

import com.dailyquest.app.data.remote.dto.CoinTransactionDto
import com.dailyquest.app.data.remote.dto.CreateMissionRequest
import com.dailyquest.app.data.remote.dto.ItemDto
import com.dailyquest.app.data.remote.dto.MyItemDto
import com.dailyquest.app.data.remote.dto.PurchaseDto
import com.dailyquest.app.data.remote.dto.CreateRoomRequest
import com.dailyquest.app.data.remote.dto.CreateRoomResponse
import com.dailyquest.app.data.remote.dto.InviteCodeDto
import com.dailyquest.app.data.remote.dto.JoinByCodeRequest
import com.dailyquest.app.data.remote.dto.LoginRequest
import com.dailyquest.app.data.remote.dto.MissionDetailDto
import com.dailyquest.app.data.remote.dto.MissionDto
import com.dailyquest.app.data.remote.dto.MyRoomMissionsDto
import com.dailyquest.app.data.remote.dto.OAuthLoginRequest
import com.dailyquest.app.data.remote.dto.PageDto
import com.dailyquest.app.data.remote.dto.PendingProofDto
import com.dailyquest.app.data.remote.dto.FcmTokenRequest
import com.dailyquest.app.data.remote.dto.NotificationDto
import com.dailyquest.app.data.remote.dto.UnreadCountDto
import com.dailyquest.app.data.remote.dto.ProofDetailDto
import com.dailyquest.app.data.remote.dto.ProofDto
import com.dailyquest.app.data.remote.dto.ReactionRequest
import com.dailyquest.app.data.remote.dto.ReactionSummaryDto
import com.dailyquest.app.data.remote.dto.SubmitProofRequest
import com.dailyquest.app.data.remote.dto.UploadDto
import com.dailyquest.app.data.remote.dto.VoteDto
import com.dailyquest.app.data.remote.dto.PasswordResetConfirmRequest
import com.dailyquest.app.data.remote.dto.PasswordResetRequest
import com.dailyquest.app.data.remote.dto.PasswordResetVerifyRequest
import com.dailyquest.app.data.remote.dto.RefreshRequest
import com.dailyquest.app.data.remote.dto.RoomDetailDto
import com.dailyquest.app.data.remote.dto.RoomMemberDto
import com.dailyquest.app.data.remote.dto.RoomSummaryDto
import com.dailyquest.app.data.remote.dto.TransferOwnerRequest
import com.dailyquest.app.data.remote.dto.UpdateRoomRequest
import com.dailyquest.app.data.remote.dto.SignupRequest
import com.dailyquest.app.data.remote.dto.SignupResponse
import com.dailyquest.app.data.remote.dto.TokenResponse
import com.dailyquest.app.data.remote.dto.ChangePasswordRequest
import com.dailyquest.app.data.remote.dto.SetGenderRequest
import com.dailyquest.app.data.remote.dto.UpdateAvatarRequest
import com.dailyquest.app.data.remote.dto.UpdateMeRequest
import com.dailyquest.app.data.remote.dto.UserMeResponse
import com.dailyquest.app.data.remote.dto.WithdrawRequest
import com.dailyquest.app.data.remote.dto.AvailableItemDto
import com.dailyquest.app.data.remote.dto.DecorationsDto
import com.dailyquest.app.data.remote.dto.SaveDecorationsRequest
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import okhttp3.MultipartBody
import retrofit2.http.HTTP
import retrofit2.http.Headers
import retrofit2.http.Multipart
import retrofit2.http.Part
import retrofit2.http.PATCH
import retrofit2.http.PUT
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/** 경로는 Base URL 뒤에 붙으므로 앞에 '/'를 쓰지 않는다. */
interface DqApi {
    @Headers("$NO_AUTH_HEADER: 1")
    @POST("auth/signup")
    suspend fun signup(@Body body: SignupRequest): SignupResponse

    @Headers("$NO_AUTH_HEADER: 1")
    @POST("auth/login")
    suspend fun login(@Body body: LoginRequest): TokenResponse

    /** provider: google | kakao. 응답은 이메일 로그인과 동일하다. */
    @Headers("$NO_AUTH_HEADER: 1")
    @POST("auth/oauth/{provider}")
    suspend fun oauthLogin(@Path("provider") provider: String, @Body body: OAuthLoginRequest): TokenResponse

    @Headers("$NO_AUTH_HEADER: 1")
    @POST("auth/refresh")
    suspend fun refresh(@Body body: RefreshRequest): TokenResponse

    /**
     * 비밀번호 재설정은 로그인 없이 호출하는 공개 API다(Bearer 없음, 토큰 갱신 대상 아님).
     * 코드 요청을 자동 재전송하면 코드가 여러 번 발급될 수 있고 응답 유실 시 결과를 임의로 확정하면 안 되므로 재시도하지 않는다.
     */
    @Headers("$NO_AUTH_HEADER: 1", "X-DQ-No-Retry: true")
    @POST("auth/password-reset/request")
    suspend fun requestPasswordReset(@Body body: PasswordResetRequest)

    @Headers("$NO_AUTH_HEADER: 1", "X-DQ-No-Retry: true")
    @POST("auth/password-reset/verify")
    suspend fun verifyPasswordReset(@Body body: PasswordResetVerifyRequest)

    @Headers("$NO_AUTH_HEADER: 1", "X-DQ-No-Retry: true")
    @POST("auth/password-reset/confirm")
    suspend fun confirmPasswordReset(@Body body: PasswordResetConfirmRequest)

    /** 성공 204(body 없음). Unit 반환이면 Retrofit이 빈 본문을 허용한다. */
    @POST("auth/logout")
    suspend fun logout()

    @GET("users/me")
    suspend fun me(): UserMeResponse

    /** 닉네임 수정. 멱등하지만 응답 유실 시 결과를 재조회로 확인하므로 자동 재전송은 끈다. */
    @Headers("X-DQ-No-Retry: true")
    @PATCH("users/me")
    suspend fun updateMe(@Body body: UpdateMeRequest): UserMeResponse

    /** 성별(아바타 체형) 확정 (STEP 9-9). 계정당 한 번, 다시 부르면 GENDER_ALREADY_SET(409). */
    @PUT("users/me/gender")
    suspend fun setGender(@Body body: SetGenderRequest): UserMeResponse

    /** 아바타 착용 변경 (STEP 9). 응답은 GET /users/me와 같다. */
    @PATCH("users/me")
    suspend fun updateAvatar(@Body body: UpdateAvatarRequest): UserMeResponse

    /** 성공 204. 서버가 모든 기기의 Refresh Token을 폐기한다. */
    @Headers("X-DQ-No-Retry: true")
    @PATCH("users/me/password")
    suspend fun changePassword(@Body body: ChangePasswordRequest)

    /** 성공 204. Retrofit의 @DELETE는 본문을 허용하지 않아 @HTTP를 사용한다. */
    @Headers("X-DQ-No-Retry: true")
    @HTTP(method = "DELETE", path = "users/me", hasBody = true)
    suspend fun withdraw(@Body body: WithdrawRequest)

    // ---- 방 관리 (모두 Bearer 필요). 정적 경로(mine, join)는 {roomId} 경로와 겹치지 않게 선언한다. ----

    /** sort: popularity,desc(기본) | createdAt,desc. 서버는 size를 1~100으로 보정한다. */
    @GET("rooms")
    suspend fun listPublicRooms(
        @Query("keyword") keyword: String? = null,
        @Query("sort") sort: String? = null,
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 20,
    ): PageDto<RoomSummaryDto>

    /** 페이지네이션 없는 전체 배열. */
    @GET("rooms/mine")
    suspend fun listMyRooms(): List<RoomSummaryDto>

    @GET("rooms/{roomId}")
    suspend fun getRoom(@Path("roomId") roomId: Long): RoomDetailDto

    @GET("rooms/{roomId}/members")
    suspend fun listRoomMembers(@Path("roomId") roomId: Long): List<RoomMemberDto>

    // 방 상태 변경 요청(생성·참여·수정·삭제·탈퇴·추방·위임·초대코드 재발급)은 모두 연결 실패 자동 재전송을 끈다.
    /**
     * 성공 201. 서버에 멱등성 키가 없어 연결 실패 시 OkHttp가 자동 재전송하면 방이 중복 생성될 수 있으므로 재시도를 끈다.
     * (401 TOKEN_EXPIRED는 인증 필터가 컨트롤러 이전에 거절하므로 갱신 후 재전송해도 중복 생성되지 않는다.)
     */
    @Headers("X-DQ-No-Retry: true")
    @POST("rooms")
    suspend fun createRoom(@Body body: CreateRoomRequest): CreateRoomResponse

    @Headers("X-DQ-No-Retry: true")
    @POST("rooms/{roomId}/join")
    suspend fun joinPublicRoom(@Path("roomId") roomId: Long): RoomDetailDto

    @Headers("X-DQ-No-Retry: true")
    @POST("rooms/join")
    suspend fun joinRoomByCode(@Body body: JoinByCodeRequest): RoomDetailDto

    @Headers("X-DQ-No-Retry: true")
    @PATCH("rooms/{roomId}")
    suspend fun updateRoom(@Path("roomId") roomId: Long, @Body body: UpdateRoomRequest): RoomDetailDto

    /** 성공 204. */
    @Headers("X-DQ-No-Retry: true")
    @DELETE("rooms/{roomId}")
    suspend fun deleteRoom(@Path("roomId") roomId: Long)

    /** 성공 204. */
    @Headers("X-DQ-No-Retry: true")
    @DELETE("rooms/{roomId}/leave")
    suspend fun leaveRoom(@Path("roomId") roomId: Long)

    @Headers("X-DQ-No-Retry: true")
    @POST("rooms/{roomId}/invite-code")
    suspend fun regenerateInviteCode(@Path("roomId") roomId: Long): InviteCodeDto

    /** 성공 204. */
    @Headers("X-DQ-No-Retry: true")
    @DELETE("rooms/{roomId}/members/{userId}")
    suspend fun kickMember(@Path("roomId") roomId: Long, @Path("userId") userId: Long)

    @Headers("X-DQ-No-Retry: true")
    @POST("rooms/{roomId}/transfer-owner")
    suspend fun transferOwner(@Path("roomId") roomId: Long, @Body body: TransferOwnerRequest): RoomDetailDto

    companion object {
        /** 이 헤더가 있는 요청에는 Bearer를 붙이지 않고 토큰 갱신도 하지 않는다. 전송 전에 제거된다. */
        const val NO_AUTH_HEADER = "X-DQ-No-Auth"
    }

    // ---- 미션 ----

    /** status: ACTIVE(기본) | EXPIRED | ALL, type: APP | MEMBER. ACTIVE/ALL·전체 유형이면 서버가 오늘의 APP 미션을 지연 생성한다. */
    @GET("rooms/{roomId}/missions")
    suspend fun listRoomMissions(
        @Path("roomId") roomId: Long,
        @Query("status") status: String? = null,
        @Query("type") type: String? = null,
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 20,
    ): PageDto<MissionDto>

    /** 내 모든 방의 진행 중 미션을 방별로 묶어 반환한다. 서버가 방마다 오늘의 APP 미션을 지연 생성한다. */
    @GET("missions/mine")
    suspend fun listMyMissions(): List<MyRoomMissionsDto>

    @GET("missions/{missionId}")
    suspend fun getMission(@Path("missionId") missionId: Long): MissionDetailDto

    /** 201. 멱등성 키가 없어 응답을 잃어도 서버에는 생성됐을 수 있으므로 연결 재시도를 끈다. */
    @Headers("X-DQ-No-Retry: true")
    @POST("rooms/{roomId}/missions")
    suspend fun createMission(@Path("roomId") roomId: Long, @Body body: CreateMissionRequest): MissionDto

    // ---- 인증 ----

    /** 201. purpose는 PROOF | ROOM_THUMBNAIL(쿼리). 업로드는 멱등하지 않으므로 연결 재시도를 끈다. */
    @Headers("X-DQ-No-Retry: true")
    @Multipart
    @POST("uploads")
    suspend fun upload(@Part file: MultipartBody.Part, @Query("purpose") purpose: String? = null): UploadDto

    /** 201. 같은 미션에 PENDING/APPROVED 인증이 있으면 거부되지만 응답 유실 시 중복 방지를 위해 재시도를 끈다. */
    @Headers("X-DQ-No-Retry: true")
    @POST("missions/{missionId}/proofs")
    suspend fun submitProof(@Path("missionId") missionId: Long, @Body body: SubmitProofRequest): ProofDto

    @GET("proofs/{proofId}")
    suspend fun getProof(@Path("proofId") proofId: Long): ProofDetailDto

    @GET("rooms/{roomId}/proofs/pending")
    suspend fun listPendingProofs(
        @Path("roomId") roomId: Long,
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 20,
    ): PageDto<PendingProofDto>

    /** 최근 24시간(기본)의 방 인증 피드. 항목 형태는 승인 대기 목록과 같고 mediaUrl·리액션은 상세에서만 온다. */
    @GET("rooms/{roomId}/feed")
    suspend fun listFeed(
        @Path("roomId") roomId: Long,
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 20,
    ): PageDto<PendingProofDto>

    /** 멤버만. 이미 누른 이모지를 다시 보내도 변화가 없고, 응답은 갱신된 전체 리액션 목록이다. */
    @Headers("X-DQ-No-Retry: true")
    @POST("proofs/{proofId}/reactions")
    suspend fun addReaction(@Path("proofId") proofId: Long, @Body body: ReactionRequest): List<ReactionSummaryDto>

    @Headers("X-DQ-No-Retry: true")
    @HTTP(method = "DELETE", path = "proofs/{proofId}/reactions", hasBody = true)
    suspend fun removeReaction(@Path("proofId") proofId: Long, @Body body: ReactionRequest): List<ReactionSummaryDto>

    /** 204. 작성자 본인의 승인 대기(PENDING) 인증만 삭제할 수 있다. */
    @Headers("X-DQ-No-Retry: true")
    @DELETE("proofs/{proofId}")
    suspend fun deleteProof(@Path("proofId") proofId: Long)

    @Headers("X-DQ-No-Retry: true")
    @POST("proofs/{proofId}/approve")
    suspend fun approveProof(@Path("proofId") proofId: Long): VoteDto

    @Headers("X-DQ-No-Retry: true")
    @POST("proofs/{proofId}/reject")
    suspend fun rejectProof(@Path("proofId") proofId: Long): VoteDto

    // ---- 알림 ----

    @GET("notifications")
    suspend fun listNotifications(
        @Query("unreadOnly") unreadOnly: Boolean = false,
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 20,
    ): PageDto<NotificationDto>

    @GET("notifications/unread-count")
    suspend fun getUnreadCount(): UnreadCountDto

    /** 204. 멱등이며 없거나 남의 알림이면 404 NOTIFICATION_NOT_FOUND. */
    @PATCH("notifications/{notificationId}/read")
    suspend fun markNotificationRead(@Path("notificationId") notificationId: Long)

    /** 204. 멱등. */
    @POST("notifications/read-all")
    suspend fun markAllNotificationsRead()

    /** 204. 사용자당 토큰 하나를 덮어쓰는 멱등 요청이다. 서버에는 삭제 API가 없다. */
    @PUT("users/me/fcm-token")
    suspend fun updateFcmToken(@Body body: FcmTokenRequest)

    // ---- 상점·인벤토리·코인 내역 ----

    /** type: AVATAR | FURNITURE, category: 서버 ItemCategory. 가격 오름차순이며 서버는 size를 1~100으로 보정한다. */
    @GET("shop/items")
    suspend fun listShopItems(
        @Query("type") type: String? = null,
        @Query("category") category: String? = null,
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 20,
    ): PageDto<ItemDto>

    @GET("shop/items/{itemId}")
    suspend fun getShopItem(@Path("itemId") itemId: Long): ItemDto

    /**
     * 여러 아이템 한 번에 (api.md 7.1.1). [ids]는 쉼표로 이은 ID, 최대 100개. 체형으로 거르지 않고, 없는 ID는 결과에서 빠진다.
     * 다른 사람의 착용 아이템 ID를 3D 파일 주소로 바꿀 때 쓴다(프로필 얼굴).
     */
    @GET("shop/items/batch")
    suspend fun getShopItemsBatch(@Query("ids") ids: String): List<ItemDto>

    /** 코인 차감 + 소유권 지급이 한 번에 일어나는 상태 변경 요청이라 응답을 잃어도 자동 재전송하지 않는다. */
    @Headers("X-DQ-No-Retry: true")
    @POST("shop/items/{itemId}/purchase")
    suspend fun purchaseItem(@Path("itemId") itemId: Long): PurchaseDto

    /** 페이지네이션 없는 전체 배열(최근 획득순). */
    @GET("users/me/items")
    suspend fun listMyItems(@Query("type") type: String? = null, @Query("category") category: String? = null): List<MyItemDto>

    @GET("users/me/coins/transactions")
    suspend fun listCoinTransactions(@Query("page") page: Int = 0, @Query("size") size: Int = 20): PageDto<CoinTransactionDto>

    // ---- 방 꾸미기 (api.md 8장, STEP 10) ----

    @GET("rooms/{roomId}/decorations")
    suspend fun getDecorations(@Path("roomId") roomId: Long): DecorationsDto

    /** 방 전체를 요청 내용으로 바꾼다. 같은 요청을 다시 보내도 결과가 같아 자동 재전송을 막지 않는다. */
    @PUT("rooms/{roomId}/decorations")
    suspend fun saveDecorations(@Path("roomId") roomId: Long, @Body body: SaveDecorationsRequest): DecorationsDto

    @GET("rooms/{roomId}/decorations/available")
    suspend fun listAvailableDecor(@Path("roomId") roomId: Long): List<AvailableItemDto>
}

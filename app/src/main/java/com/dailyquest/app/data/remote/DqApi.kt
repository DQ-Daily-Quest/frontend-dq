package com.dailyquest.app.data.remote

import com.dailyquest.app.data.remote.dto.CreateRoomRequest
import com.dailyquest.app.data.remote.dto.CreateRoomResponse
import com.dailyquest.app.data.remote.dto.InviteCodeDto
import com.dailyquest.app.data.remote.dto.JoinByCodeRequest
import com.dailyquest.app.data.remote.dto.LoginRequest
import com.dailyquest.app.data.remote.dto.OAuthLoginRequest
import com.dailyquest.app.data.remote.dto.PageDto
import com.dailyquest.app.data.remote.dto.RefreshRequest
import com.dailyquest.app.data.remote.dto.RoomDetailDto
import com.dailyquest.app.data.remote.dto.RoomMemberDto
import com.dailyquest.app.data.remote.dto.RoomSummaryDto
import com.dailyquest.app.data.remote.dto.TransferOwnerRequest
import com.dailyquest.app.data.remote.dto.UpdateRoomRequest
import com.dailyquest.app.data.remote.dto.SignupRequest
import com.dailyquest.app.data.remote.dto.SignupResponse
import com.dailyquest.app.data.remote.dto.TokenResponse
import com.dailyquest.app.data.remote.dto.UserMeResponse
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.PATCH
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

    /** 성공 204(body 없음). Unit 반환이면 Retrofit이 빈 본문을 허용한다. */
    @POST("auth/logout")
    suspend fun logout()

    @GET("users/me")
    suspend fun me(): UserMeResponse

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
}

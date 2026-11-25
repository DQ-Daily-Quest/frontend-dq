package com.dailyquest.app.data.mission

import com.dailyquest.app.data.remote.ApiResult
import com.dailyquest.app.data.remote.DqApi
import com.dailyquest.app.data.remote.apiCall
import com.dailyquest.app.data.remote.dto.CreateMissionRequest
import com.dailyquest.app.data.remote.dto.UpdateMissionRequest

/** 서버가 조회하는 미션 범위. ACTIVE는 마감 전, EXPIRED는 마감 후. */
enum class MissionScope(val param: String) { ACTIVE("ACTIVE"), EXPIRED("EXPIRED"), ALL("ALL") }

/**
 * 미션 API 호출 계층. 전역 사용자 상태를 보관하지 않는다.
 * 미션 생성은 응답을 받지 못해도 서버에서 성공했을 수 있으므로 자동 재시도하지 않는다(DqApi의 No-Retry 헤더).
 * 조회 API(오늘의 미션·내 미션)는 서버가 APP 미션을 지연 생성한다는 점에 유의한다.
 */
class MissionRepository(private val api: DqApi) {

    /** 내 모든 방의 진행 중 미션. 방 묶음 순서는 서버 순서를 따른다. */
    suspend fun listMine(): ApiResult<List<RoomMissions>> = apiCall {
        api.listMyMissions().map { g ->
            RoomMissions(g.room.roomId, g.room.name, g.missions.map { it.toMission(g.room.roomId, g.room.name) })
        }
    }

    suspend fun listRoomMissions(
        roomId: Long,
        scope: MissionScope = MissionScope.ACTIVE,
        page: Int = 0,
        size: Int = DEFAULT_PAGE_SIZE,
    ): ApiResult<MissionPage> = apiCall {
        val r = api.listRoomMissions(roomId, scope.param, null, page, size)
        MissionPage(r.content.map { it.toMission(roomId) }, r.page, r.totalPages, r.totalElements)
    }

    suspend fun getMission(missionId: Long, roomIdHint: Long = 0L): ApiResult<MissionDetail> =
        apiCall { api.getMission(missionId).toDetail(roomIdHint) }

    /** [dueAtMs]가 null이면 서버 기본값(7일 뒤), [assigneeUserId]가 null이면 방 전체가 대상이다. */
    suspend fun createMission(
        roomId: Long,
        content: String,
        rewardCoins: Int,
        dueAtMs: Long?,
        assigneeUserId: Long?,
    ): ApiResult<Mission> = apiCall {
        api.createMission(
            roomId,
            CreateMissionRequest(content.trim(), rewardCoins, dueAtMs?.let { MissionTime.toIso(it) }, assigneeUserId),
        ).toMission(roomId)
    }

    /**
     * 멤버 미션 수정(PATCH). 서버는 content·dueAt만 받고 null은 바꾸지 않는다. 서버 규칙: 작성자만, APP 미션 불가,
     * 대기·승인 인증이 있으면 409 MISSION_HAS_PROOFS. 자동 재시도하지 않는다.
     */
    suspend fun updateMission(missionId: Long, roomId: Long, content: String?, dueAtMs: Long?): ApiResult<Mission> = apiCall {
        api.updateMission(missionId, UpdateMissionRequest(content?.trim(), dueAtMs?.let { MissionTime.toIso(it) })).toMission(roomId)
    }

    /** 멤버 미션 삭제(DELETE, 204). 서버 규칙: 작성자 또는 방장, APP 미션 불가, 대기·승인 인증이 있으면 409. 자동 재시도하지 않는다. */
    suspend fun deleteMission(missionId: Long): ApiResult<Unit> = apiCall { api.deleteMission(missionId) }

    /** 방장 여부(GET /rooms/{id}의 myRole). 확인하지 못하면 null이며 방장 권한을 주지 않는다. */
    suspend fun isRoomOwner(roomId: Long): Boolean? {
        if (roomId <= 0) return null
        return (apiCall { api.getRoom(roomId) } as? ApiResult.Success)?.value?.myRole?.equals("OWNER", ignoreCase = true)
    }

    companion object {
        const val DEFAULT_PAGE_SIZE = 20
    }
}

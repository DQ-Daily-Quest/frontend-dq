package com.dailyquest.app.data.mission

import com.dailyquest.app.data.remote.ApiResult
import com.dailyquest.app.data.remote.DqApi
import com.dailyquest.app.data.remote.apiCall
import com.dailyquest.app.data.remote.dto.CreateMissionRequest

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

    companion object {
        const val DEFAULT_PAGE_SIZE = 20
    }
}

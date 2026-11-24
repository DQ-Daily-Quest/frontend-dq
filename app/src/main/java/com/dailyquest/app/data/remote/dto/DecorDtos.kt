package com.dailyquest.app.data.remote.dto

import kotlinx.serialization.Serializable

// 방 꾸미기 (backend api.md 8장, 연동 규칙 11장). STEP 10-3.

/** 배치·배경·배치 가능 목록에 들어 있는 가구 요약. [code]로 앱의 배치 정보를 찾는다. */
@Serializable
data class DecorItemDto(
    val itemId: Long,
    val name: String,
    /** DESK | CHAIR | BED | STORAGE | DECO | WALL | FLOOR */
    val category: String,
    val imageUrl: String? = null,
    val modelUrl: String? = null,
    val code: String? = null,
    /** 상점·목록용 512×512 썸네일 (백엔드 11/24 추가). 없으면 null → [imageUrl]로 대신한다. 방에 그릴 때는 쓰지 않는다. */
    val thumbnailUrl: String? = null,
)

@Serializable
data class DecorUserDto(val userId: Long, val nickname: String)

/** 놓인 가구 하나. [x]·[y]는 0~1 비율(= 칸 번호 / 16), [rotation]은 0~359(앱은 0·90·180·270만 보낸다). */
@Serializable
data class PlacementDto(
    val placementId: Long,
    val item: DecorItemDto,
    val ownerId: Long,
    val x: Double,
    val y: Double,
    val z: Int = 0,
    val rotation: Int = 0,
)

/** GET·PUT /rooms/{roomId}/decorations 응답. 한 번도 저장하지 않은 방은 [background]가 null이다. */
@Serializable
data class DecorationsDto(
    val roomId: Long,
    val background: DecorItemDto? = null,
    val placements: List<PlacementDto> = emptyList(),
    val updatedAt: String? = null,
    val updatedBy: DecorUserDto? = null,
)

@Serializable
data class PlacementRequest(val itemId: Long, val x: Double, val y: Double, val z: Int, val rotation: Int)

/**
 * PUT /rooms/{roomId}/decorations. 방 전체를 이 내용으로 **바꾼다**(빠진 가구는 지워진다).
 * [backgroundItemId]를 null로 보내면 서버가 배경을 지우므로 항상 채워 보낸다.
 */
@Serializable
data class SaveDecorationsRequest(val backgroundItemId: Long?, val placements: List<PlacementRequest>)

/** GET /rooms/{roomId}/decorations/available. 같은 가구는 [ownerCount]개까지만 놓을 수 있다. */
@Serializable
data class AvailableItemDto(
    val item: DecorItemDto,
    val ownerCount: Int,
    val placedCount: Int = 0,
    val owners: List<DecorUserDto> = emptyList(),
)

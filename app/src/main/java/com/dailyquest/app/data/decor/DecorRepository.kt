package com.dailyquest.app.data.decor

import com.dailyquest.app.data.auth.AuthErrorMessages
import com.dailyquest.app.data.remote.ApiErrorCode
import com.dailyquest.app.data.remote.ApiException
import com.dailyquest.app.data.remote.ApiResult
import com.dailyquest.app.data.remote.DqApi
import com.dailyquest.app.data.remote.apiCall
import com.dailyquest.app.data.remote.dto.AvailableItemDto
import com.dailyquest.app.data.remote.dto.DecorItemDto
import com.dailyquest.app.data.remote.dto.DecorUserDto
import com.dailyquest.app.data.remote.dto.DecorationsDto
import com.dailyquest.app.data.remote.dto.PlacementDto
import com.dailyquest.app.data.remote.dto.PlacementRequest
import com.dailyquest.app.data.remote.dto.SaveDecorationsRequest

/** 서버 가구 요약. [code]로 [RoomCatalog]의 배치 정보를 찾는다. */
data class DecorItem(
    val itemId: Long,
    val name: String,
    val category: String,
    val code: String?,
    /** 방에 그리는 0° 그림. 방향별 그림 주소도 여기서 만든다. */
    val imageUrl: String?,
    /** 가구 고르기 목록용 512×512 썸네일. 없으면 null → [imageUrl] (STEP 10-8). 방에 그릴 때는 쓰지 않는다. */
    val thumbnailUrl: String? = null,
) {
    /** 가구 고르기 목록에 보여 줄 그림. */
    val listImageUrl: String? get() = thumbnailUrl ?: imageUrl

    /** 배경(방 자체)으로 쓰는 아이템. 가구 목록에서는 뺀다(연동 규칙 11-7). */
    val isBackground: Boolean get() = category in BACKGROUND_CATEGORIES

    /** [rotation] 방향 그림 주소. 이미지가 없으면 null. */
    fun spriteUrl(rotation: Rotation): String? = imageUrl?.let(rotation::spriteUrl)

    companion object {
        val BACKGROUND_CATEGORIES = setOf("WALL", "FLOOR")
    }
}

data class DecorUser(val userId: Long, val nickname: String)

/** 화면에 그릴 수 있는 가구 하나: 서버 아이템 + 칸 위치. */
data class PlacedFurniture(val item: DecorItem, val placement: GridPlacement, val ownerId: Long? = null)

/**
 * 앱이 그릴 수 없어 **건드리지 않고 그대로 두는** 배치. 저장할 때 받은 값 그대로 다시 보낸다.
 * 저장은 방 전체를 바꾸는 방식이라, 빼고 보내면 서버에서 지워지기 때문이다.
 * - 앱의 배치 정보에 없는 새 가구(서버에 먼저 올라온 경우)
 * - 칸 경계(0.0625 단위)에 맞지 않거나 방 밖인 위치(예전 32×32 방 시절 값 등)
 * - 90도 단위가 아닌 방향
 */
data class KeptPlacement(val item: DecorItem, val x: Double, val y: Double, val z: Int, val rotation: Int, val reason: String)

/** 방 하나의 꾸미기 상태. */
data class RoomDecor(
    val roomId: Long,
    /** null = 한 번도 저장하지 않은 방. 기본 방을 그린다(연동 규칙 11-4). */
    val background: DecorItem?,
    val furniture: List<PlacedFurniture>,
    val kept: List<KeptPlacement> = emptyList(),
    val updatedAt: String? = null,
    val updatedBy: DecorUser? = null,
)

/** 방에 놓을 수 있는 가구 하나. 같은 가구는 [ownerCount]개까지 놓을 수 있다(멤버 중 가진 사람 수). */
data class AvailableFurniture(
    val item: DecorItem,
    /** 앱 배치 정보. null이면 앱이 아직 모르는 가구라 놓을 수 없다. */
    val spec: FurnitureSpec?,
    val ownerCount: Int,
    /** 마지막으로 저장된 방에 놓인 개수. 편집 중 개수는 화면에서 따로 센다. */
    val placedCount: Int,
    val owners: List<DecorUser>,
)

/** 배치 가능 목록을 배경과 가구로 나눈 것. */
data class DecorChoices(
    /** 기본 방(`room_16x16`). 서버가 회원가입 때 모두에게 준다. 목록에 없으면 null. */
    val defaultBackground: DecorItem?,
    val backgrounds: List<DecorItem>,
    val furniture: List<AvailableFurniture>,
)

/**
 * 방 꾸미기 조회·저장 (api.md 8장, STEP 10-3).
 * 배치 정보([RoomCatalog])는 앱 assets에서 읽으므로 화면 쪽에서 [RoomCatalogs.load]로 받아 넘긴다.
 */
class DecorRepository(private val api: DqApi) {

    suspend fun load(roomId: Long, catalog: RoomCatalog): ApiResult<RoomDecor> =
        apiCall { api.getDecorations(roomId) }.map { it.toRoomDecor(catalog) }

    suspend fun available(roomId: Long, catalog: RoomCatalog): ApiResult<DecorChoices> =
        apiCall { api.listAvailableDecor(roomId) }.map { it.toChoices(catalog) }

    /**
     * 방 전체를 저장한다. [background]가 null이면(한 번도 저장 안 한 방) [defaultBackground]를 보낸다.
     * 둘 다 null이면 배경 없이 보낸다(서버는 배경을 비운다). [decor]의 [RoomDecor.kept]는 그대로 다시 보낸다.
     * 응답(서버가 저장한 결과)으로 바꾼 상태를 돌려준다.
     */
    suspend fun save(
        decor: RoomDecor,
        catalog: RoomCatalog,
        defaultBackground: DecorItem?,
    ): ApiResult<RoomDecor> {
        val request = buildSaveRequest(decor, catalog, defaultBackground)
        return apiCall { api.saveDecorations(decor.roomId, request) }.map { it.toRoomDecor(catalog) }
    }

    companion object {
        const val DEFAULT_ROOM_CODE = "room_16x16"

        /**
         * 기본 방 그림. 파일 주소는 `items/furniture/{code}/{code}.png` 규칙이다(연동 규칙 11-2).
         * 한 번도 저장하지 않은 방(배경 null)과, 꾸미기 API를 부를 수 없는 비멤버 화면에서 쓴다.
         */
        const val DEFAULT_ROOM_IMAGE_URL =
            "https://dq-uploads-prod.s3.ap-northeast-2.amazonaws.com/items/furniture/$DEFAULT_ROOM_CODE/$DEFAULT_ROOM_CODE.png"

        fun buildSaveRequest(decor: RoomDecor, catalog: RoomCatalog, defaultBackground: DecorItem?): SaveDecorationsRequest {
            val room = catalog.room
            val grid = decor.furniture.map { it.placement }
            // z는 그리는 순서. 다른 기기가 z만 보고 그려도 같은 모습이 되게 앱의 순서를 적는다(서버 범위 0~100).
            val rank = IntArray(grid.size)
            RoomLayout.drawOrder(grid).forEachIndexed { order, index -> rank[index] = minOf(order, MAX_Z) }
            val placed = decor.furniture.mapIndexed { i, f ->
                PlacementRequest(
                    itemId = f.item.itemId,
                    x = RoomLayout.toServerRatio(f.placement.gridX, room.columns),
                    y = RoomLayout.toServerRatio(f.placement.gridY, room.rows),
                    z = rank[i],
                    rotation = f.placement.rotation.degrees,
                )
            }
            val kept = decor.kept.map { PlacementRequest(it.item.itemId, it.x, it.y, it.z, it.rotation) }
            return SaveDecorationsRequest(
                backgroundItemId = (decor.background ?: defaultBackground)?.itemId,
                placements = placed + kept,
            )
        }

        private const val MAX_Z = 100
    }
}

// ---- 서버 응답 → 앱 모델 ----

private fun <T, R> ApiResult<T>.map(transform: (T) -> R): ApiResult<R> = when (this) {
    is ApiResult.Success -> ApiResult.Success(transform(value))
    is ApiResult.Failure -> this
}

internal fun DecorItemDto.toItem() = DecorItem(itemId, name, category, code, imageUrl, thumbnailUrl)

internal fun DecorUserDto.toUser() = DecorUser(userId, nickname)

internal fun DecorationsDto.toRoomDecor(catalog: RoomCatalog): RoomDecor {
    val placed = mutableListOf<PlacedFurniture>()
    val kept = mutableListOf<KeptPlacement>()
    placements.forEach { p ->
        when (val r = p.toPlaced(catalog)) {
            is PlacementParse.Ok -> placed += r.value
            is PlacementParse.Keep -> kept += KeptPlacement(p.item.toItem(), p.x, p.y, p.z, p.rotation, r.reason)
        }
    }
    return RoomDecor(roomId, background?.toItem(), placed, kept, updatedAt, updatedBy?.toUser())
}

private sealed interface PlacementParse {
    data class Ok(val value: PlacedFurniture) : PlacementParse
    data class Keep(val reason: String) : PlacementParse
}

private fun PlacementDto.toPlaced(catalog: RoomCatalog): PlacementParse {
    val spec = catalog.furniture(item.code) ?: return PlacementParse.Keep("앱이 모르는 가구(code=${item.code})")
    val rot = Rotation.fromDegrees(rotation) ?: return PlacementParse.Keep("90도 단위가 아닌 방향($rotation)")
    val gx = RoomLayout.fromServerRatio(x, catalog.room.columns) ?: return PlacementParse.Keep("칸에 맞지 않는 x($x)")
    val gy = RoomLayout.fromServerRatio(y, catalog.room.rows) ?: return PlacementParse.Keep("칸에 맞지 않는 y($y)")
    val grid = GridPlacement(spec, gx, gy, rot)
    if (!RoomLayout.fitsInRoom(catalog.room, grid)) return PlacementParse.Keep("방 밖으로 나감($gx, $gy, ${rot.degrees}°)")
    return PlacementParse.Ok(PlacedFurniture(item.toItem(), grid, ownerId))
}

internal fun List<AvailableItemDto>.toChoices(catalog: RoomCatalog): DecorChoices {
    val (bg, furniture) = partition { it.item.category in DecorItem.BACKGROUND_CATEGORIES }
    val backgrounds = bg.map { it.item.toItem() }
    return DecorChoices(
        defaultBackground = backgrounds.firstOrNull { it.code == DecorRepository.DEFAULT_ROOM_CODE },
        backgrounds = backgrounds,
        furniture = furniture.map {
            AvailableFurniture(it.item.toItem(), catalog.furniture(it.item.code), it.ownerCount, it.placedCount, it.owners.map { o -> o.toUser() })
        },
    )
}

/** 방 꾸미기 오류 안내 문구. */
object DecorErrorMessages {
    fun forApi(e: ApiException): String {
        if (e !is ApiException.Http) return AuthErrorMessages.forApi(e)
        return when (e.code) {
            // 서버 문구에 가구 이름과 개수가 들어 있어 그대로 보여준다.
            ApiErrorCode.ITEM_NOT_OWNED_IN_ROOM -> e.serverMessage ?: "방 멤버가 가진 개수보다 많이 놓을 수 없어요."
            ApiErrorCode.ITEM_NOT_FOUND -> "판매가 끝난 가구가 있어요. 그 가구를 빼고 다시 저장해 주세요."
            ApiErrorCode.FORBIDDEN -> "이 방의 멤버만 꾸밀 수 있어요."
            ApiErrorCode.ROOM_NOT_FOUND -> "방을 찾을 수 없어요. 삭제되었을 수 있어요."
            else -> AuthErrorMessages.forApi(e)
        }
    }
}

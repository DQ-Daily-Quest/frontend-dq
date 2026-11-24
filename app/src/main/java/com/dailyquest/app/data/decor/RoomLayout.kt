package com.dailyquest.app.data.decor

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * 방 안 가구 위치 계산 (STEP 10-2). 화면 없이 숫자만 다룬다.
 *
 * 좌표는 두 가지다.
 * - **칸 좌표**: 바닥 격자. 칸 (0, 0)이 방 안쪽 구석이고 0~15. 가구 위치는 "차지한 칸들 중 시작 칸"(gridX, gridY)으로 나타낸다.
 * - **방 이미지 좌표**: 방 PNG(1080×810) 위의 픽셀. 화면에 그릴 때는 여기에 같은 배율을 곱한다([com.dailyquest.app.ui.decor.RoomViewport]).
 *
 * 계산 방식은 에셋 팩이 미리보기 이미지를 만들 때 쓴 것과 같다(source/package.py). 단위 테스트가 배치 예시 54개로 확인한다.
 */

/** 방 이미지 위의 사각형 (픽셀). */
data class PxRect(val left: Float, val top: Float, val width: Float, val height: Float) {
    val right: Float get() = left + width
    val bottom: Float get() = top + height
    fun contains(p: PxPoint): Boolean = p.x >= left && p.x < right && p.y >= top && p.y < bottom
}

/** 바닥 칸 하나. */
data class Cell(val x: Int, val y: Int)

/** 방에 놓인 가구 하나. */
data class GridPlacement(val spec: FurnitureSpec, val gridX: Int, val gridY: Int, val rotation: Rotation) {
    val sprite: SpriteSpec get() = spec.sprite(rotation)

    /** 이 방향에서 가로(X)로 차지하는 칸 수. */
    val width: Int get() = sprite.gridWidth

    /** 이 방향에서 세로(Y)로 차지하는 칸 수. */
    val depth: Int get() = sprite.gridDepth

    val cells: Set<Cell>
        get() = buildSet { for (x in gridX until gridX + width) for (y in gridY until gridY + depth) add(Cell(x, y)) }

    /** 차지한 칸들의 가운데 (칸 좌표, 소수). */
    val centerX: Float get() = gridX + width / 2f
    val centerY: Float get() = gridY + depth / 2f
}

object RoomLayout {

    /** 칸 좌표(소수 가능)의 바닥 위치 → 방 이미지 좌표. */
    fun groundPx(room: RoomGeometry, gridX: Float, gridY: Float): PxPoint = PxPoint(
        room.gridOrigin.x + gridX * room.gridAxisX.x + gridY * room.gridAxisY.x,
        room.gridOrigin.y + gridX * room.gridAxisX.y + gridY * room.gridAxisY.y,
    )

    /**
     * 방 이미지 좌표 → 칸 좌표(소수). [groundPx]의 반대. 손가락이 가리키는 바닥 칸을 찾을 때 쓴다.
     * 두 축이 나란하지 않으므로 항상 답이 하나다.
     */
    fun gridAt(room: RoomGeometry, p: PxPoint): Pair<Float, Float> {
        val ax = room.gridAxisX
        val ay = room.gridAxisY
        val dx = p.x - room.gridOrigin.x
        val dy = p.y - room.gridOrigin.y
        val det = ax.x * ay.y - ay.x * ax.y
        val gx = (dx * ay.y - ay.x * dy) / det
        val gy = (ax.x * dy - dx * ax.y) / det
        return gx to gy
    }

    /** 손가락 아래 바닥 칸. 방 밖이면 null. */
    fun cellAt(room: RoomGeometry, p: PxPoint): Cell? {
        val (gx, gy) = gridAt(room, p)
        val cell = Cell(floor(gx).toInt(), floor(gy).toInt())
        return cell.takeIf { it.x in 0 until room.columns && it.y in 0 until room.rows }
    }

    /**
     * 가구 그림이 그려질 자리(방 이미지 좌표).
     * 바닥 가운데 → 보정값을 더해 기준점 → 그림 안 기준점을 빼서 왼쪽 위.
     */
    fun spriteBounds(room: RoomGeometry, p: GridPlacement): PxRect {
        val s = p.sprite
        val ground = groundPx(room, p.centerX, p.centerY)
        val left = ground.x + s.gridAnchorOffset.x - s.anchor.x
        val top = ground.y + s.gridAnchorOffset.y - s.anchor.y
        return PxRect(left, top, s.widthPx.toFloat(), s.heightPx.toFloat())
    }

    /**
     * 가구 가운데를 [p](방 이미지 좌표)에 맞췄을 때의 시작 칸. 드래그할 때 칸에 딱 맞추는 데 쓴다.
     * 방 밖으로 나가도 그대로 돌려준다. 막을지는 [fitsInRoom]으로 따로 판단한다(빨간 표시를 보여주기 위해).
     */
    fun snapStart(room: RoomGeometry, p: PxPoint, width: Int, depth: Int): Cell {
        val (gx, gy) = gridAt(room, p)
        return Cell((gx - width / 2f).roundToInt(), (gy - depth / 2f).roundToInt())
    }

    fun fitsInRoom(room: RoomGeometry, p: GridPlacement): Boolean =
        p.gridX >= 0 && p.gridY >= 0 && p.gridX + p.width <= room.columns && p.gridY + p.depth <= room.rows

    /**
     * 놓을 수 없는 가구의 위치(목록 순번). 방 밖으로 나갔거나, 다른 가구와 칸이 겹친 경우다.
     * 러그처럼 겹쳐도 되는 가구([FurnitureSpec.blocksPlacement] = false)는 겹침 검사에서 빠지고, 방 밖만 검사한다.
     */
    fun problems(room: RoomGeometry, placements: List<GridPlacement>): Set<Int> {
        val result = mutableSetOf<Int>()
        val owner = HashMap<Cell, Int>()
        placements.forEachIndexed { i, p ->
            if (!fitsInRoom(room, p)) result += i
            if (!p.spec.blocksPlacement) return@forEachIndexed
            p.cells.forEach { c ->
                val other = owner.putIfAbsent(c, i)
                if (other != null && other != i) {
                    result += i
                    result += other
                }
            }
        }
        return result
    }

    /**
     * 그리는 순서(목록 순번). 앞에 있는 것부터 그린다.
     * 러그 먼저, 그다음 방 안쪽(칸 가운데의 X + Y가 작은 것)부터. 같으면 목록 순서를 유지한다.
     */
    fun drawOrder(placements: List<GridPlacement>): List<Int> =
        placements.indices.sortedWith(
            compareBy<Int> { if (placements[it].spec.layer == PlacementLayer.FLOOR_DECOR) 0 else 1 }
                .thenBy { placements[it].centerX + placements[it].centerY },
        )

    /**
     * [p] 위치에 보이는 가장 앞쪽 가구(목록 순번). 없으면 null.
     * 그림의 사각형으로 판단하므로 투명한 모서리를 눌러도 잡힌다. 가구가 작아 그 편이 오히려 누르기 쉽다.
     */
    fun hitTest(room: RoomGeometry, placements: List<GridPlacement>, p: PxPoint): Int? =
        drawOrder(placements).lastOrNull { spriteBounds(room, placements[it]).contains(p) }

    // ---- 서버 값 변환 ----
    // 서버는 위치를 0~1 비율(x = gridX / 16, 0.0625 단위)로 저장한다. 연동 규칙 11-5.

    fun toServerRatio(gridIndex: Int, count: Int): Double = gridIndex.toDouble() / count

    /**
     * 서버 비율 → 칸 번호. 칸 경계(0.0625 단위)에 맞지 않거나 방 밖이면 null.
     * 예전 32×32 방 시절 값이나 다른 앱이 저장한 값이 섞여 있어도 엉뚱한 칸에 놓지 않기 위해서다.
     */
    fun fromServerRatio(ratio: Double, count: Int): Int? {
        val exact = ratio * count
        val index = exact.roundToInt()
        if (abs(exact - index) > SERVER_RATIO_TOLERANCE) return null
        return index.takeIf { it in 0 until count }
    }

    /** 서버는 소수 4자리로 저장한다. 1/16 = 0.0625라 오차가 없지만 계산 오차만큼은 허용한다. */
    private const val SERVER_RATIO_TOLERANCE = 0.01
}

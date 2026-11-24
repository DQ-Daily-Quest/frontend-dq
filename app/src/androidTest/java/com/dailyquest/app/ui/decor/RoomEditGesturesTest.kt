package com.dailyquest.app.ui.decor

import android.graphics.Bitmap
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.dailyquest.app.data.decor.Cell
import com.dailyquest.app.data.decor.DecorItem
import com.dailyquest.app.data.decor.GridPlacement
import com.dailyquest.app.data.decor.PlacedFurniture
import com.dailyquest.app.data.decor.PxPoint
import com.dailyquest.app.data.decor.RoomCatalogs
import com.dailyquest.app.data.decor.RoomLayout
import com.dailyquest.app.data.decor.Rotation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.util.Collections

/**
 * STEP 10-6 편집 모드 손가락 동작. 가구 위치는 계산(RoomLayout)으로 화면 좌표를 구해 실제로 누르고 끈다.
 * 가짜 그림을 써서 인터넷 없이 돈다.
 */
class RoomEditGesturesTest {
    @get:Rule val compose = createComposeRule()

    private val catalog = RoomCatalogs.load(InstrumentationRegistry.getInstrumentation().targetContext)
    private val room = catalog.room
    private val blank = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888).asImageBitmap()
    private fun item(code: String) = DecorItem(1, code, "DECO", code, "https://x/$code.png")

    private val placements = mutableStateListOf(
        GridPlacement(catalog.furniture("desk_oak")!!, 7, 7, Rotation.R0),
        GridPlacement(catalog.furniture("chair_sage")!!, 2, 2, Rotation.R0),
    )
    private val selected = mutableStateOf<Int?>(null)
    private val selects: MutableList<Int?> = Collections.synchronizedList(mutableListOf())
    private val moves: MutableList<Pair<Int, Cell>> = Collections.synchronizedList(mutableListOf())
    private val state = RoomViewState()

    /** 편집 화면처럼 선택·이동을 바로 반영한다. */
    private val editor = object : RoomEditor {
        override fun onSelect(index: Int?) { selects += index; selected.value = index }
        override fun onMove(index: Int, start: Cell) {
            moves += index to start
            placements[index] = placements[index].copy(gridX = start.x, gridY = start.y)
        }
    }

    private fun show() = compose.setContent {
        RoomView(
            room, "room", placements.map { PlacedFurniture(item(it.spec.code), it) }, Modifier.size(360.dp, 270.dp),
            state = state, images = { Result.success(blank) },
            editor = editor, selectedIndex = selected.value,
        )
    }

    /** 칸 좌표(소수)의 바닥 위치 → RoomView 안 화면 좌표. */
    private fun screen(gx: Float, gy: Float): Offset = compose.runOnIdle {
        val s = state.viewport!!.toScreen(RoomLayout.groundPx(room, gx, gy))
        Offset(s.x, s.y)
    }

    private fun screen(p: PxPoint): Offset = compose.runOnIdle { state.viewport!!.toScreen(p).let { Offset(it.x, it.y) } }

    @Test fun pressingFurnitureSelectsIt() {
        show()
        val chair = placements[1]
        val at = screen(chair.centerX, chair.centerY)
        compose.onNodeWithTag("roomView").performTouchInput { down(at); up() }
        compose.runOnIdle { assertEquals(1, selected.value) }
    }

    @Test fun draggingFurnitureMovesItCellByCell() {
        show()
        val desk = placements[0] // (7, 7), 2×1
        val from = screen(desk.centerX, desk.centerY)
        val to = screen(desk.centerX + 3, desk.centerY + 2) // 가운데를 3칸 오른쪽·2칸 아래로
        compose.onNodeWithTag("roomView").performTouchInput {
            down(from)
            for (i in 1..10) moveTo(from + (to - from) * (i / 10f))
            up()
        }
        compose.runOnIdle {
            assertEquals(0, selected.value)
            assertEquals(0 to Cell(10, 9), moves.last())
            assertEquals(10, placements[0].gridX)
            assertEquals(9, placements[0].gridY)
            assertTrue("중간 칸도 거쳐 간다", moves.size >= 3)
        }
    }

    @Test fun smallWiggleDoesNotMoveFurniture() {
        show()
        val desk = placements[0]
        val from = screen(desk.centerX, desk.centerY)
        val quarterCell = screen(desk.centerX + 0.25f, desk.centerY) - from
        compose.onNodeWithTag("roomView").performTouchInput { down(from); moveTo(from + quarterCell); up() }
        compose.runOnIdle {
            assertTrue("반 칸이 안 되게 움직이면 그대로", moves.all { it.second == Cell(7, 7) })
            assertEquals(7, placements[0].gridX)
        }
    }

    @Test fun tappingEmptyFloorClearsSelection() {
        show()
        selected.value = 0
        val empty = screen(1.5f, 13.5f)
        compose.onNodeWithTag("roomView").performTouchInput { down(empty); up() }
        compose.runOnIdle { assertEquals(null, selects.last()) }
    }

    @Test fun draggingEmptyFloorAtNormalSizeMovesNothing() {
        show()
        val before = compose.runOnIdle { state.viewport }
        val a = screen(1.5f, 13.5f)
        val b = screen(4.5f, 13.5f)
        compose.onNodeWithTag("roomView").performTouchInput { down(a); moveTo(b); up() }
        compose.runOnIdle {
            assertTrue(moves.isEmpty())
            assertEquals(before, state.viewport)
            assertTrue("끌었다 놓으면 선택 해제로 보지 않는다", selects.isEmpty())
        }
    }

    @Test fun pinchZoomsWithoutMovingFurniture() {
        show()
        compose.onNodeWithTag("roomView").performTouchInput {
            pinch(center - Offset(50f, 0f), center - Offset(100f, 0f), center + Offset(50f, 0f), center + Offset(100f, 0f), durationMillis = 400)
        }
        compose.runOnIdle {
            assertTrue(state.viewport!!.zoom > 1.5f)
            assertTrue(moves.isEmpty())
        }
    }

    @Test fun whenZoomedDraggingEmptyFloorPansAndFurnitureStillDrags() {
        show()
        compose.onNodeWithTag("roomView").performTouchInput {
            pinch(center - Offset(50f, 0f), center - Offset(100f, 0f), center + Offset(50f, 0f), center + Offset(100f, 0f), durationMillis = 400)
        }
        val zoomed = compose.runOnIdle { state.viewport!! }

        // 빈 곳(방 그림 위 벽 쪽 하늘)을 위로 끌면 화면이 움직인다
        val empty = Offset(20f, 20f)
        compose.onNodeWithTag("roomView").performTouchInput { down(empty); moveTo(empty + Offset(0f, -120f)); up() }
        compose.runOnIdle { assertTrue(state.viewport!!.offsetY < zoomed.offsetY) }

        // 확대 상태에서도 가구를 잡아 한 칸 옮길 수 있다
        val chair = placements[1]
        val from = screen(chair.centerX, chair.centerY)
        val to = screen(chair.centerX + 1, chair.centerY)
        compose.onNodeWithTag("roomView").performTouchInput { down(from); moveTo((from + to) / 2f); moveTo(to); up() }
        compose.runOnIdle { assertEquals(1 to Cell(3, 2), moves.last()) }
    }
}

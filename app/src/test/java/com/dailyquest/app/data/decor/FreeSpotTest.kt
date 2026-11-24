package com.dailyquest.app.data.decor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** STEP 10-6 새 가구를 놓을 빈자리 찾기. */
class FreeSpotTest {
    private val catalog = RoomCatalog.parse(File("src/main/assets/" + RoomCatalog.ASSET_PATH).readText())
    private val room = catalog.room
    private fun spec(code: String) = catalog.furniture(code)!!
    private fun place(code: String, x: Int, y: Int, r: Rotation = Rotation.R0) = GridPlacement(spec(code), x, y, r)

    @Test fun emptyRoomPutsFurnitureInTheMiddle() {
        val chair = RoomLayout.findFreeSpot(room, emptyList(), spec("chair_sage"))!!
        assertEquals(Rotation.R0, chair.rotation)
        // 1×1 의자의 가운데가 방 가운데(8, 8)에 가장 가까운 칸
        assertTrue("가운데 근처: (${chair.gridX}, ${chair.gridY})", chair.gridX in 7..8 && chair.gridY in 7..8)

        val desk = RoomLayout.findFreeSpot(room, emptyList(), spec("desk_oak"))!! // 2×1
        assertEquals(7, desk.gridX)
        assertTrue(desk.gridY in 7..8)
    }

    @Test fun skipsOccupiedCellsAndNeverOverlaps() {
        val existing = mutableListOf<GridPlacement>()
        repeat(20) {
            val p = RoomLayout.findFreeSpot(room, existing, spec("desk_oak"))!!
            existing += p
        }
        assertEquals("20개를 놓아도 겹치거나 방 밖인 것이 없다", emptySet<Int>(), RoomLayout.problems(room, existing))
    }

    @Test fun rugMayGoOnTopOfFurniture() {
        val full = buildList { for (x in 0 until 16) for (y in 0 until 16) add(place("chair_sage", x, y)) }
        val rug = RoomLayout.findFreeSpot(room, full, spec("rug_sage"))
        assertNotNull("러그는 겹쳐도 되므로 꽉 찬 방에도 놓인다", rug)
        assertTrue(RoomLayout.fitsInRoom(room, rug!!))
    }

    @Test fun nullWhenRoomIsFull() {
        val full = buildList { for (x in 0 until 16) for (y in 0 until 16) add(place("chair_sage", x, y)) }
        assertNull(RoomLayout.findFreeSpot(room, full, spec("plant_green")))
    }

    @Test fun triesOtherDirectionWhenOnlyThatFits() {
        // 1칸 너비의 세로 통로만 남긴다: 2×1 책상은 90°(1×2)로만 들어간다.
        val walls = buildList {
            for (x in 0 until 16) for (y in 0 until 16) if (x != 4) add(place("chair_sage", x, y))
        }
        val desk = RoomLayout.findFreeSpot(room, walls, spec("desk_oak"))!!
        assertEquals(4, desk.gridX)
        assertTrue(desk.rotation == Rotation.R90 || desk.rotation == Rotation.R270)
        assertEquals(emptySet<Int>(), RoomLayout.problems(room, walls + desk))
    }
}

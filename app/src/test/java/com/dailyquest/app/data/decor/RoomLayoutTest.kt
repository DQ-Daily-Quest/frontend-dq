package com.dailyquest.app.data.decor

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * STEP 10-2 칸 ↔ 방 이미지 위치 계산.
 * 정답 자료(test/resources/decor/example_layout.json)는 에셋 팩이 미리보기 이미지를 만든 방식 그대로,
 * 매니페스트 원래 값으로 계산했다(tools/make_room_layout_fixture.py).
 */
class RoomLayoutTest {
    private val catalog = RoomCatalog.parse(File("src/main/assets/" + RoomCatalog.ASSET_PATH).readText())
    private val room = catalog.room

    private fun place(code: String, x: Int, y: Int, r: Rotation = Rotation.R0) =
        GridPlacement(catalog.furniture(code)!!, x, y, r)

    private class Example(val id: String, val placement: GridPlacement, val left: Double, val top: Double, val width: Int, val height: Int)

    private val fixture: JsonObject by lazy {
        val text = javaClass.getResource("/decor/example_layout.json")!!.readText()
        Json.parseToJsonElement(text).jsonObject
    }

    private val examples: List<Example> by lazy {
        fixture["items"]!!.jsonArray.map { e ->
            val o = e.jsonObject
            fun i(k: String) = o[k]!!.jsonPrimitive.int
            fun d(k: String) = o[k]!!.jsonPrimitive.double
            Example(
                o["id"]!!.jsonPrimitive.content,
                place(o["code"]!!.jsonPrimitive.content, i("gridX"), i("gridY"), Rotation.fromDegrees(i("rotation"))!!),
                d("expectedLeftPx"), d("expectedTopPx"), i("expectedWidthPx"), i("expectedHeightPx"),
            )
        }
    }

    // ---- 에셋 팩 배치 예시 54개와 비교 ----

    @Test fun exampleLayoutPositionsMatchAssetPack() {
        assertEquals(54, examples.size)
        examples.forEach { ex ->
            val b = RoomLayout.spriteBounds(room, ex.placement)
            // 앱 JSON은 소수 4자리로 줄였다. 그 차이가 0.01px(1080px 방 기준) 안이어야 한다.
            assertEquals("${ex.id} 왼쪽", ex.left, b.left.toDouble(), 0.01)
            assertEquals("${ex.id} 위", ex.top, b.top.toDouble(), 0.01)
            assertEquals("${ex.id} 너비", ex.width.toFloat(), b.width, 0f)
            assertEquals("${ex.id} 높이", ex.height.toFloat(), b.height, 0f)
        }
    }

    @Test fun exampleLayoutDrawOrderMatchesAssetPack() {
        val expected = fixture["expectedDrawOrder"]!!.jsonArray.map { it.jsonPrimitive.content }
        val actual = RoomLayout.drawOrder(examples.map { it.placement }).map { examples[it].id }
        assertEquals(expected, actual)
    }

    @Test fun exampleLayoutHasNoProblems() {
        assertEquals(emptySet<Int>(), RoomLayout.problems(room, examples.map { it.placement }))
    }

    // ---- 칸 ↔ 방 이미지 좌표 ----

    @Test fun gridAtIsInverseOfGroundPx() {
        listOf(0f to 0f, 16f to 16f, 3.5f to 7.25f, 15.9f to 0.1f, -1f to 20f).forEach { (gx, gy) ->
            val (bx, by) = RoomLayout.gridAt(room, RoomLayout.groundPx(room, gx, gy))
            assertEquals(gx, bx, 0.0001f)
            assertEquals(gy, by, 0.0001f)
        }
    }

    @Test fun cellAtFindsCellUnderFingerAndNullOutside() {
        assertEquals(Cell(3, 5), RoomLayout.cellAt(room, RoomLayout.groundPx(room, 3.5f, 5.5f)))
        assertEquals(Cell(0, 0), RoomLayout.cellAt(room, RoomLayout.groundPx(room, 0.01f, 0.01f)))
        assertEquals(Cell(15, 15), RoomLayout.cellAt(room, RoomLayout.groundPx(room, 15.99f, 15.99f)))
        assertNull(RoomLayout.cellAt(room, RoomLayout.groundPx(room, -0.5f, 3f)))
        assertNull(RoomLayout.cellAt(room, RoomLayout.groundPx(room, 3f, 16.5f)))
        assertNull("벽 위쪽 빈 곳", RoomLayout.cellAt(room, PxPoint(540f, 10f)))
    }

    @Test fun snapStartPutsFurnitureCenterUnderFinger() {
        val desk = place("desk_oak", 4, 6)
        val finger = RoomLayout.groundPx(room, desk.centerX, desk.centerY)
        assertEquals(Cell(4, 6), RoomLayout.snapStart(room, finger, desk.width, desk.depth))

        val sofa = place("sofa_oat", 2, 3, Rotation.R90) // 90°: 1×2칸
        val f2 = RoomLayout.groundPx(room, sofa.centerX + 0.3f, sofa.centerY - 0.4f) // 가운데에서 조금 비껴도 같은 칸
        assertEquals(Cell(2, 3), RoomLayout.snapStart(room, f2, sofa.width, sofa.depth))
    }

    // ---- 방 범위·겹침 ----

    @Test fun fitsInRoomChecksRotatedSize() {
        assertTrue(RoomLayout.fitsInRoom(room, place("desk_oak", 14, 15)))   // 2×1, 오른쪽 끝
        assertFalse(RoomLayout.fitsInRoom(room, place("desk_oak", 15, 15)))
        assertTrue(RoomLayout.fitsInRoom(room, place("desk_oak", 15, 14, Rotation.R90)))  // 돌리면 1×2
        assertFalse(RoomLayout.fitsInRoom(room, place("desk_oak", 15, 15, Rotation.R90)))
        assertFalse(RoomLayout.fitsInRoom(room, place("chair_sage", -1, 0)))
        assertFalse(RoomLayout.fitsInRoom(room, place("chair_sage", 0, -1)))
    }

    @Test fun overlappingFurnitureAreBothProblems() {
        val list = listOf(place("chair_sage", 5, 5), place("desk_oak", 4, 5), place("plant_green", 9, 9))
        assertEquals(setOf(0, 1), RoomLayout.problems(room, list))
    }

    @Test fun rugMayOverlapButNotLeaveRoom() {
        val ok = listOf(place("rug_sage", 5, 5), place("coffee_table_oak", 5, 5), place("armchair_sage", 6, 6))
        assertEquals(emptySet<Int>(), RoomLayout.problems(room, ok))

        val outside = listOf(place("rug_sage", 15, 15), place("chair_sage", 15, 15))
        assertEquals("러그만 방 밖(2×2가 16칸을 넘음)", setOf(0), RoomLayout.problems(room, outside))
    }

    @Test fun outOfRoomIsProblemEvenWithoutOverlap() {
        assertEquals(setOf(1), RoomLayout.problems(room, listOf(place("chair_sage", 0, 0), place("bed_lilac", 15, 3))))
    }

    @Test fun rugIsDrawnFirstThenBackToFront() {
        val list = listOf(place("chair_sage", 10, 10), place("rug_sage", 12, 12), place("plant_green", 1, 1), place("desk_oak", 5, 2))
        assertEquals(listOf(1, 2, 3, 0), RoomLayout.drawOrder(list))
    }

    @Test fun hitTestPicksFrontMostFurniture() {
        val back = place("wardrobe_cream", 5, 5)
        val front = place("chair_sage", 6, 7) // 옷장보다 앞(아래쪽)에 있어 나중에 그려진다
        val list = listOf(front, back)
        val b = RoomLayout.spriteBounds(room, back)
        val f = RoomLayout.spriteBounds(room, front)
        // 두 그림이 겹치는 곳
        val overlap = PxPoint((maxOf(b.left, f.left) + minOf(b.right, f.right)) / 2, (maxOf(b.top, f.top) + minOf(b.bottom, f.bottom)) / 2)
        assertTrue(b.contains(overlap) && f.contains(overlap))
        assertEquals(0, RoomLayout.hitTest(room, list, overlap))
        assertNull(RoomLayout.hitTest(room, list, PxPoint(5f, 5f)))
    }

    // ---- 서버 비율 ----

    @Test fun serverRatioRoundTripsEveryCell() {
        for (i in 0 until 16) {
            assertEquals(i, RoomLayout.fromServerRatio(RoomLayout.toServerRatio(i, 16), 16))
        }
        assertEquals(0.875, RoomLayout.toServerRatio(14, 16), 0.0)
        assertEquals(7, RoomLayout.fromServerRatio(0.4375, 16)) // 연동 규칙 11-5 예시
        assertEquals(3, RoomLayout.fromServerRatio(0.1875, 16))
    }

    @Test fun serverRatioRejectsValuesOffTheGrid() {
        assertNull("32칸 방 시절의 반 칸", RoomLayout.fromServerRatio(0.03125, 16))
        assertNull("방 끝(16번째 칸은 없음)", RoomLayout.fromServerRatio(1.0, 16))
        assertNull(RoomLayout.fromServerRatio(-0.0625, 16))
        assertEquals("소수 4자리 반올림 오차는 허용", 5, RoomLayout.fromServerRatio(0.3125 + 0.00004, 16))
    }
}

package com.dailyquest.app.ui.decor

import com.dailyquest.app.data.decor.Cell
import com.dailyquest.app.data.decor.PxPoint
import com.dailyquest.app.data.decor.RoomCatalog
import com.dailyquest.app.data.decor.RoomLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** STEP 10-2 두 손가락 확대·이동 계산. 방 이미지는 1080×810. */
class RoomViewportTest {
    private val w = 1080f
    private val h = 810f

    @Test fun fitUsesSmallerSideAndCenters() {
        val square = RoomViewport.fit(1080f, 1080f, w, h)
        assertEquals(1f, square.scale, 0.0001f)
        assertEquals(0f, square.offsetX, 0.0001f)
        assertEquals("위아래 남는 270px을 반씩", 135f, square.offsetY, 0.0001f)

        // 방 화면의 칸(358×252 비율): 세로가 먼저 닿아 세로에 맞추고 가로 가운데
        val card = RoomViewport.fit(358f, 252f, w, h)
        assertEquals(252f / 810f, card.scale, 0.0001f)
        assertEquals((358f - w * card.scale) / 2f, card.offsetX, 0.0001f)
        assertEquals(0f, card.offsetY, 0.0001f)
    }

    @Test fun zoomKeepsPointUnderFingersInPlace() {
        val v = RoomViewport.fit(1080f, 810f, w, h)
        val focusX = 300f
        val focusY = 500f
        val before = v.toContent(focusX, focusY)
        val z = v.zoomedBy(2f, focusX, focusY)
        assertEquals(2f, z.zoom, 0.0001f)
        val after = z.toContent(focusX, focusY)
        assertEquals(before.x, after.x, 0.01f)
        assertEquals(before.y, after.y, 0.01f)
    }

    @Test fun zoomStaysBetweenOneAndThree() {
        val v = RoomViewport.fit(1080f, 810f, w, h)
        assertEquals(3f, v.zoomedBy(10f, 540f, 405f).zoom, 0.0001f)
        assertEquals(1f, v.zoomedBy(2f, 540f, 405f).zoomedBy(0.1f, 540f, 405f).zoom, 0.0001f)
        assertFalse(v.isZoomed)
        assertTrue(v.zoomedBy(1.5f, 0f, 0f).isZoomed)
    }

    @Test fun panCannotShowEmptySpaceOutsideRoom() {
        val z = RoomViewport.fit(1080f, 810f, w, h).zoomedBy(2f, 540f, 405f)
        listOf(-5000f to -5000f, 5000f to 5000f, -5000f to 5000f).forEach { (dx, dy) ->
            val p = z.pannedBy(dx, dy)
            val topLeft = p.toContent(0f, 0f)
            val bottomRight = p.toContent(1080f, 810f)
            assertTrue("왼쪽·위 빈 곳 없음 $topLeft", topLeft.x >= -0.01f && topLeft.y >= -0.01f)
            assertTrue("오른쪽·아래 빈 곳 없음 $bottomRight", bottomRight.x <= w + 0.01f && bottomRight.y <= h + 0.01f)
        }
    }

    @Test fun cannotPanWhenRoomFitsTheView() {
        val v = RoomViewport.fit(1080f, 1080f, w, h)
        val p = v.pannedBy(200f, 200f)
        assertEquals(v.offsetX, p.offsetX, 0.0001f)
        assertEquals(v.offsetY, p.offsetY, 0.0001f)
    }

    @Test fun resetReturnsToFit() {
        val v = RoomViewport.fit(1080f, 900f, w, h)
        val back = v.zoomedBy(2.5f, 100f, 100f).pannedBy(-300f, -50f).reset()
        assertEquals(v, back)
    }

    @Test fun resizeKeepsZoomAndStaysInBounds() {
        val z = RoomViewport.fit(1080f, 810f, w, h).zoomedBy(2f, 1000f, 800f)
        val r = z.resized(540f, 405f)
        assertEquals(2f, r.zoom, 0.0001f)
        assertEquals(r, r.clamped())
    }

    @Test fun screenAndContentRoundTrip() {
        val v = RoomViewport.fit(720f, 1280f, w, h).zoomedBy(2.2f, 360f, 640f).pannedBy(-40f, 30f)
        val p = PxPoint(123.4f, 567.8f)
        val s = v.toScreen(p)
        val back = v.toContent(s.x, s.y)
        assertEquals(p.x, back.x, 0.01f)
        assertEquals(p.y, back.y, 0.01f)
    }

    /** 확대·이동한 상태에서도 손가락 아래 칸을 정확히 찾는다(6단계 편집에서 쓰는 조합). */
    @Test fun fingerPointsAtSameCellWhenZoomed() {
        val room = RoomCatalog.parse(File("src/main/assets/" + RoomCatalog.ASSET_PATH).readText()).room
        val v = RoomViewport.fit(1080f, 2000f, w, h).zoomedBy(3f, 700f, 900f).pannedBy(-150f, 40f)
        for (cell in listOf(Cell(7, 7), Cell(0, 0), Cell(15, 15), Cell(3, 12))) {
            val ground = RoomLayout.groundPx(room, cell.x + 0.5f, cell.y + 0.5f)
            val finger = v.toScreen(ground)
            assertEquals(cell, RoomLayout.cellAt(room, v.toContent(finger.x, finger.y)))
        }
    }
}

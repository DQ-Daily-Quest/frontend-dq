package com.dailyquest.app.ui.avatar

import io.github.sceneview.math.Position
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/** 상점 미리보기 좌우 돌리기: 카메라를 바라보는 점 둘레로 세로축 기준 회전. */
class AvatarOrbitTest {
    private val cam = AvatarCamera(Position(0f, 1f, 3f), Position(0f, 1f, 0f))
    private fun assertPos(x: Float, y: Float, z: Float, p: Position) {
        assertEquals(x, p.x, 1e-4f); assertEquals(y, p.y, 1e-4f); assertEquals(z, p.z, 1e-4f)
    }

    @Test fun `0도면 그대로`() = assertSame(cam, cam.orbitAroundY(0f))

    @Test fun `90도 돌면 옆에서 보고 높이와 거리는 그대로`() {
        val c = cam.orbitAroundY(90f)
        assertPos(3f, 1f, 0f, c.position)
        assertEquals(cam.target, c.target)
    }

    @Test fun `180도 돌면 뒤에서 보고 360도면 제자리`() {
        assertPos(0f, 1f, -3f, cam.orbitAroundY(180f).position)
        assertPos(0f, 1f, 3f, cam.orbitAroundY(360f).position)
    }
}

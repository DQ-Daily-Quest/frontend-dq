package com.dailyquest.app.ui.decor

import com.dailyquest.app.data.decor.PxPoint

/**
 * 방을 화면에 보여주는 확대·이동 상태 (STEP 10-2). 화면 없이 숫자만 다룬다.
 *
 * - 확대 1배: 방 이미지 전체가 화면 칸 안에 들어오는 크기(가로·세로 중 먼저 닿는 쪽에 맞춤).
 * - 확대 [minZoom]~[maxZoom]배. 두 손가락 사이 점([zoomedBy]의 focus)이 확대 전후에 같은 자리에 머문다.
 * - 방이 화면 칸보다 크면 빈 곳이 보이지 않을 만큼만 이동할 수 있고, 작으면 가운데에 둔다.
 *
 * 화면 좌표 = [offsetX], [offsetY] + 방 이미지 좌표 × [scale]. 모든 값은 화면 픽셀이다.
 * 바꿀 때마다 새 객체를 돌려준다(Compose 상태로 그대로 쓰기 위해).
 */
data class RoomViewport(
    val viewWidth: Float,
    val viewHeight: Float,
    val contentWidth: Float,
    val contentHeight: Float,
    val zoom: Float = 1f,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val minZoom: Float = 1f,
    val maxZoom: Float = 3f,
) {
    /** 확대 1배일 때 방 이미지 1px이 화면에서 몇 px인지. */
    val baseScale: Float get() = minOf(viewWidth / contentWidth, viewHeight / contentHeight)

    val scale: Float get() = baseScale * zoom

    val isZoomed: Boolean get() = zoom > minZoom + ZOOM_EPSILON

    fun toScreen(p: PxPoint): PxPoint = PxPoint(offsetX + p.x * scale, offsetY + p.y * scale)

    fun toContent(screenX: Float, screenY: Float): PxPoint = PxPoint((screenX - offsetX) / scale, (screenY - offsetY) / scale)

    /** 두 손가락으로 [factor]배 확대·축소. ([focusX], [focusY]) 화면 위치에 있던 방 지점이 그 자리에 머문다. */
    fun zoomedBy(factor: Float, focusX: Float, focusY: Float): RoomViewport {
        val newZoom = (zoom * factor).coerceIn(minZoom, maxZoom)
        val anchor = toContent(focusX, focusY)
        val newScale = baseScale * newZoom
        return copy(zoom = newZoom, offsetX = focusX - anchor.x * newScale, offsetY = focusY - anchor.y * newScale).clamped()
    }

    /** 한 손가락으로 화면을 옮긴다. */
    fun pannedBy(dx: Float, dy: Float): RoomViewport = copy(offsetX = offsetX + dx, offsetY = offsetY + dy).clamped()

    /** 두 번 탭: 원래 크기. */
    fun reset(): RoomViewport = copy(zoom = minZoom).clamped()

    /** 화면 칸 크기가 바뀌었을 때(회전 등). 확대 배율은 유지한다. */
    fun resized(width: Float, height: Float): RoomViewport = copy(viewWidth = width, viewHeight = height).clamped()

    /** 이동 범위를 지킨다. 방이 화면보다 작은 방향은 가운데, 큰 방향은 빈 곳이 안 보이게. */
    fun clamped(): RoomViewport {
        val w = contentWidth * scale
        val h = contentHeight * scale
        return copy(offsetX = clampAxis(offsetX, w, viewWidth), offsetY = clampAxis(offsetY, h, viewHeight))
    }

    private fun clampAxis(offset: Float, content: Float, view: Float): Float =
        if (content <= view) (view - content) / 2f else offset.coerceIn(view - content, 0f)

    companion object {
        private const val ZOOM_EPSILON = 0.001f

        /** 처음 보여줄 때: 1배, 가운데. */
        fun fit(viewWidth: Float, viewHeight: Float, contentWidth: Float, contentHeight: Float): RoomViewport =
            RoomViewport(viewWidth, viewHeight, contentWidth, contentHeight).clamped()
    }
}

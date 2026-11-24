package com.dailyquest.app.ui.avatar

import android.graphics.Bitmap
import android.graphics.Rect
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import kotlinx.coroutines.delay

/**
 * 3D 아바타 화면을 사진(Bitmap)으로 뽑는다 (프로필 얼굴 1단계).
 *
 * [AvatarView]는 SceneView를 TextureView 방식(`SurfaceType.TextureSurface`)으로 그린다. SceneView는 그 TextureView를
 * 밖으로 내주지 않으므로, 화면 뷰 트리에서 [AvatarView]가 놓인 자리와 겹치는 TextureView를 찾아 `getBitmap()`으로 읽는다.
 * AvatarView가 `isOpaque = false`라 배경은 투명하게 나온다.
 */
object AvatarSnapshot {

    /**
     * [root] 아래에서 창 기준 [boundsInWindow]와 가장 많이 겹치는 TextureView의 지금 모습. 못 찾거나 아직 그려지지 않았으면 null.
     * 메인 스레드에서 부른다.
     */
    fun capture(root: View, boundsInWindow: Rect): Bitmap? {
        val target = textureViews(root).maxByOrNull { overlap(it, boundsInWindow) }?.takeIf { overlap(it, boundsInWindow) > 0 }
            ?: return null
        if (!target.isAvailable) return null
        return target.bitmap
    }

    /**
     * 화면이 다 올라온 뒤의 사진. [intervalMs]마다 찍어서 **비어 있지 않고, 바로 전 사진과 거의 같을 때** 돌려준다.
     * 3D 엔진이 프레임마다 아주 작은 노이즈(디더링)를 넣어 두 사진이 픽셀까지 똑같지는 않으므로 [nearlySame]으로 비교한다.
     * 3D 화면이 처음 뜨거나 모습이 바뀌는 중에는 빈 사진이나 이전 모습이 찍힐 수 있어서다(1단계 실험에서 실제로 발생).
     * [maxTries]번 안에 안정되지 않으면 null.
     */
    suspend fun captureSettled(root: View, boundsInWindow: Rect, intervalMs: Long = 150, maxTries: Int = 40): Bitmap? {
        var previous: Bitmap? = null
        repeat(maxTries) {
            val now = capture(root, boundsInWindow)
            val before = previous
            if (now != null && before != null && hasContent(now) && nearlySame(before, now)) return now
            previous = now
            delay(intervalMs)
        }
        return null
    }

    /** 불투명한 점이 조금이라도(격자로 훑어 2% 이상) 있는지. 3D가 아직 안 그려진 빈 사진을 거른다. */
    fun hasContent(b: Bitmap, grid: Int = 24, minRatio: Double = 0.02): Boolean {
        var hit = 0
        for (gy in 0 until grid) for (gx in 0 until grid) {
            val x = (gx + 0.5f) * b.width / grid
            val y = (gy + 0.5f) * b.height / grid
            if (b.getPixel(x.toInt(), y.toInt()) ushr 24 > 200) hit++
        }
        return hit >= grid * grid * minRatio
    }

    /**
     * 격자로 훑어 두 사진의 같은 자리를 비교해, 차이가 [tolerance] 이하인 점이 [minRatio] 이상이면 같다고 본다.
     * 투명한 배경은 투명도가 0이어도 색 값이 프레임마다 달라질 수 있어, **색에 투명도를 곱해서** 비교한다(둘 다 투명하면 같음).
     */
    fun nearlySame(a: Bitmap, b: Bitmap, grid: Int = 32, tolerance: Int = 12, minRatio: Double = 0.98): Boolean {
        if (a.width != b.width || a.height != b.height) return false
        var same = 0
        for (gy in 0 until grid) for (gx in 0 until grid) {
            val x = ((gx + 0.5f) * a.width / grid).toInt()
            val y = ((gy + 0.5f) * a.height / grid).toInt()
            if (pixelDiff(a.getPixel(x, y), b.getPixel(x, y)) <= tolerance) same++
        }
        return same >= grid * grid * minRatio
    }

    /** 투명도를 곱한 색끼리의 가장 큰 차이(0~255). */
    private fun pixelDiff(p: Int, q: Int): Int {
        val pa = p ushr 24
        val qa = q ushr 24
        fun ch(c: Int, shift: Int, alpha: Int) = (c shr shift and 0xFF) * alpha / 255
        return maxOf(
            maxOf(kotlin.math.abs(pa - qa), kotlin.math.abs(ch(p, 16, pa) - ch(q, 16, qa))),
            maxOf(kotlin.math.abs(ch(p, 8, pa) - ch(q, 8, qa)), kotlin.math.abs(ch(p, 0, pa) - ch(q, 0, qa))),
        )
    }

    private fun textureViews(v: View): List<TextureView> = when (v) {
        is TextureView -> listOf(v)
        is ViewGroup -> (0 until v.childCount).flatMap { textureViews(v.getChildAt(it)) }
        else -> emptyList()
    }

    private fun overlap(v: View, bounds: Rect): Long {
        val loc = IntArray(2)
        v.getLocationInWindow(loc)
        val r = Rect(loc[0], loc[1], loc[0] + v.width, loc[1] + v.height)
        if (!r.intersect(bounds)) return 0
        return r.width().toLong() * r.height()
    }
}

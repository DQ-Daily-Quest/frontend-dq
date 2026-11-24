package com.dailyquest.app.debug

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.platform.app.InstrumentationRegistry
import com.dailyquest.app.data.decor.RoomCatalogs
import com.dailyquest.app.ui.decor.RoomView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlin.math.abs
import kotlin.math.max

/**
 * STEP 10-4: 에셋 팩 배치 예시 54개를 앱의 RoomView로 **실제 S3 그림**을 받아 1080×810px(1배)로 그린 뒤,
 * 에셋 팩 미리보기(androidTest assets `decor/room_example_1080.png`)와 픽셀을 비교한다.
 * 위치·그리는 순서·방향 그림이 하나라도 틀리면 그 가구 자리의 픽셀이 달라진다.
 * 인터넷이 필요하다(S3 공개 주소).
 */
class RoomExampleRenderTest {
    @get:Rule val compose = createComposeRule()

    private val target = InstrumentationRegistry.getInstrumentation().targetContext
    private val testAssets = InstrumentationRegistry.getInstrumentation().context.assets

    /** 미리보기에서 완전히 불투명한 픽셀 중, 세 색 모두 [tolerance] 이하로 차이 나는 비율과 평균 차이. */
    private class Match(val ratio: Double, val meanDiff: Double)

    private fun compare(actual: Bitmap, expected: Bitmap, tolerance: Int = 8): Match {
        var counted = 0
        var close = 0
        var diffSum = 0L
        for (y in 0 until expected.height) for (x in 0 until expected.width) {
            val e = expected.getPixel(x, y)
            if (e ushr 24 != 0xFF) continue // 방 바깥 투명한 곳은 비교하지 않는다
            val a = actual.getPixel(x, y)
            val d = max(abs((e shr 16 and 0xFF) - (a shr 16 and 0xFF)), max(abs((e shr 8 and 0xFF) - (a shr 8 and 0xFF)), abs((e and 0xFF) - (a and 0xFF))))
            counted++
            diffSum += d
            if (d <= tolerance) close++
        }
        return Match(close.toDouble() / counted, diffSum.toDouble() / counted)
    }

    @Test fun exampleLayoutLooksLikeAssetPackPreview() {
        val catalog = RoomCatalogs.load(target)
        val layout = ExampleRoomLayout.load(target, catalog)
        var withFurniture by mutableStateOf(true)
        var settled by mutableStateOf(-1)
        compose.setContent {
            val px = with(LocalDensity.current) { Modifier.requiredSize(catalog.room.widthPx.toDp(), catalog.room.heightPx.toDp()) }
            RoomView(
                catalog.room, ExampleRoomLayout.ROOM_URL, if (withFurniture) layout else emptyList(), px,
                zoomable = false, onImagesSettled = { settled = it },
            )
        }
        compose.waitUntil(90_000) { settled >= 0 }
        assertEquals("S3 그림을 모두 받아야 한다", 0, settled)
        compose.waitForIdle()

        val expected = testAssets.open("decor/room_example_1080.png").use { BitmapFactory.decodeStream(it) }
        val shot = compose.onNodeWithTag("roomView").captureToImage().asAndroidBitmap()
        assertEquals(1080, shot.width)
        assertEquals(810, shot.height)
        val furnished = compare(shot, expected)

        // 가구 없이 빈 방만 그린 화면과도 비교해, 이 비교가 가구 자리를 실제로 구분하는지 확인한다.
        settled = -1
        withFurniture = false
        compose.waitUntil(30_000) { settled >= 0 }
        compose.waitForIdle()
        val empty = compare(compose.onNodeWithTag("roomView").captureToImage().asAndroidBitmap(), expected)

        Log.i("RoomExampleRender", "가구 있음: 일치 %.4f 평균차 %.3f / 빈 방: 일치 %.4f 평균차 %.3f".format(furnished.ratio, furnished.meanDiff, empty.ratio, empty.meanDiff))
        assertTrue("미리보기와 거의 같아야 한다: ${furnished.ratio}, 평균 ${furnished.meanDiff}", furnished.ratio >= 0.995 && furnished.meanDiff <= 1.0)
        assertTrue("빈 방은 미리보기와 확실히 달라야 한다(비교가 가구를 구분함): ${empty.ratio}", empty.ratio < furnished.ratio - 0.05)
    }
}

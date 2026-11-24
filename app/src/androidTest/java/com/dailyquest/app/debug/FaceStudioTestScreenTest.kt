package com.dailyquest.app.debug

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.SystemClock
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.platform.app.InstrumentationRegistry
import com.dailyquest.app.data.avatar.FaceKey
import com.dailyquest.app.ui.avatar.FaceStudios
import com.dailyquest.app.ui.avatar.clearAvatarFaceMemory
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * 프로필 얼굴 2단계: 얼굴 공장(가려진 3D 화면 하나 + 대기열 + 기기 저장). 디버그 "얼굴 공장 테스트" 화면으로 확인한다.
 * 실제 S3 GLB를 쓰므로 인터넷이 필요하다.
 */
class FaceStudioTestScreenTest {
    private val target = InstrumentationRegistry.getInstrumentation().targetContext
    private val studio = FaceStudios.get(target)
    private fun keyOf(o: TestOutfit) = FaceKey(o.codes.mapValues { (_, code) -> modelUrl(o.body, code) })

    // 화면을 띄우기 전에 저장된 얼굴과 메모리를 비운다.
    @Before fun clean() = runBlocking { studio.store.clear(); clearAvatarFaceMemory() }

    @get:Rule val compose = createAndroidComposeRule<FaceStudioTestActivity>()

    private fun waitStatus(text: String, timeoutMs: Long) {
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        val m = hasTestTag("faceStudioStatus") and hasText(text, substring = true)
        while (SystemClock.uptimeMillis() < deadline) {
            compose.mainClock.advanceTimeBy(100)
            if (compose.onAllNodes(m).fetchSemanticsNodes().isNotEmpty()) return
            Thread.sleep(100)
        }
        fail("${timeoutMs}ms 안에 '$text'이(가) 나오지 않았습니다")
    }

    private fun yellowRatio(file: File): Double {
        val b = BitmapFactory.decodeFile(file.path)
        var y = 0
        for (py in 0 until b.height) for (px in 0 until b.width) {
            val c = b.getPixel(px, py)
            if (c ushr 24 > 200 && (c shr 16 and 0xFF) > 190 && (c shr 8 and 0xFF) > 160 && (c and 0xFF) < 120) y++
        }
        return y / (b.width * b.height).toDouble()
    }

    @Test fun missingFacesAreShotOneByOneAndSaved() {
        // 공장(3D)이 매 프레임 그려서 화면이 멈추지 않는다. 프레임 시계를 직접 넘긴다(STEP 9 3D 테스트와 같음).
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("faceStudioShow").performSemanticsAction(SemanticsActions.OnClick)
        waitStatus("보이는 얼굴 4/4", 180_000)

        val files = TEST_OUTFITS.associateWith { studio.store.fileFor(keyOf(it)) }
        files.forEach { (o, f) ->
            assertTrue("${o.label} 얼굴 사진이 저장되어야 한다", f.isFile)
            val b = BitmapFactory.decodeFile(f.path)
            assertEquals("${o.label}: 256×256", 256, b.width)
            assertEquals(256, b.height)
        }
        // 맞는 의상이 찍혔는지: 노란 레인 모자는 "여 섞어 입기"에만 있다(1단계 실측 기준).
        val byLabel = files.mapKeys { it.key.label }
        assertTrue(yellowRatio(byLabel.getValue("여 섞어 입기")) > 0.015)
        assertTrue(yellowRatio(byLabel.getValue("남 기본")) < 0.005)
        assertTrue(yellowRatio(byLabel.getValue("여 기본")) < 0.005)
    }

    @Test fun savedFacesShowWithoutStartingThe3dStudio() = runBlocking {
        // 이미 저장된 얼굴: 아무 그림이나 넣어 둔다.
        val png = ByteArrayOutputStream().use { out ->
            Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.MAGENTA) }.compress(Bitmap.CompressFormat.PNG, 100, out)
            out.toByteArray()
        }
        TEST_OUTFITS.forEach { studio.store.put(keyOf(it), png) }

        compose.onNodeWithTag("faceStudioShow").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasTestTag("faceStudioStatus") and hasText("보이는 얼굴 4/4", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("faceStudioHost").assertDoesNotExist()
        compose.onNode(hasTestTag("faceStudioStatus") and hasText("대기 0", substring = true)).assertExists()
        Unit
    }
}

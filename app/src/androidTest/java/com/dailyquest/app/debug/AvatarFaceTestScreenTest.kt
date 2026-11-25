package com.dailyquest.app.debug

import android.graphics.BitmapFactory
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import android.os.SystemClock
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.performSemanticsAction
import org.junit.Assert.fail
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 * 프로필 얼굴 1단계: 디버그 "얼굴 테스트" 화면에서 테스트 의상 4가지를 차례로 찍는다(실제 S3 GLB, 인터넷 필요).
 * - 4장 모두 파일로 저장되고, 배경은 투명하며 가운데에 아바타가 있다.
 * - **바뀐 의상이 찍혔는지**: 노란 모자를 쓴 "여 섞어 입기"에만 노란 픽셀이 많다. 처음 구현은 이전 의상이 찍히는 경우가 있었다.
 */
class AvatarFaceTestScreenTest {
    private val target = InstrumentationRegistry.getInstrumentation().targetContext
    private val dir = File(target.filesDir, "face_test")

    // 화면을 띄우기 전에 지난 실행의 사진을 지운다.
    @Before fun clean() { dir.deleteRecursively() }

    @get:Rule val compose = createAndroidComposeRule<AvatarFaceTestActivity>()

    // 3D 화면이 매 프레임 그려서 Compose가 멈추지 않는다. STEP 9 3D 테스트와 같이 프레임 시계를 직접 넘긴다.
    @Before fun pauseFrameClock() { compose.mainClock.autoAdvance = false }

    private fun waitShots(n: Int) {
        val deadline = SystemClock.uptimeMillis() + 180_000
        val done = hasTestTag("faceTestStatus") and hasText("찍음 ${n}장", substring = true)
        while (SystemClock.uptimeMillis() < deadline) {
            compose.mainClock.advanceTimeBy(100)
            if (compose.onAllNodes(done).fetchSemanticsNodes().isNotEmpty()) return
            Thread.sleep(100)
        }
        fail("180초 안에 ${n}장을 찍지 못했습니다")
    }

    private class Stats(val opaqueRatio: Double, val centerOpaque: Boolean, val yellowRatio: Double)

    private fun stats(file: File): Stats {
        val b = BitmapFactory.decodeFile(file.path)
        var opaque = 0
        var yellow = 0
        for (y in 0 until b.height) for (x in 0 until b.width) {
            val c = b.getPixel(x, y)
            if (c ushr 24 < 200) continue
            opaque++
            val r = c shr 16 and 0xFF
            val g = c shr 8 and 0xFF
            val bl = c and 0xFF
            if (r > 190 && g > 160 && bl < 120) yellow++
        }
        val center = b.getPixel(b.width / 2, b.height / 2) ushr 24 > 200
        val total = (b.width * b.height).toDouble()
        return Stats(opaque / total, center, yellow / total)
    }

    @Test fun shootsEveryOutfitAndEachPhotoIsThatOutfit() {
        // performClick은 화면이 멈추기를 기다려서 쓰지 않는다.
        compose.onNodeWithTag("faceTestShootAll").performSemanticsAction(SemanticsActions.OnClick)
        waitShots(4)
        val files = dir.listFiles()!!.sortedBy { it.name }
        assertEquals(4, files.size)
        val s = files.map(::stats)
        s.forEachIndexed { i, st ->
            assertTrue("${files[i].name}: 배경이 투명해야 한다(불투명 ${st.opaqueRatio})", st.opaqueRatio < 0.8)
            assertTrue("${files[i].name}: 아바타가 보여야 한다(불투명 ${st.opaqueRatio})", st.opaqueRatio > 0.15)
            assertTrue("${files[i].name}: 가운데(얼굴)가 비어 있으면 안 된다", st.centerOpaque)
        }
        // TEST_OUTFITS 순서: 남 기본, 남 카페+모자, 여 기본, 여 섞어 입기(노란 레인 모자)
        // 실측(에뮬레이터): 노란 모자 2.69%, 다른 셋 0~0.27%. 그 사이에 기준을 둔다.
        assertTrue("노란 모자 의상에 노란색이 보여야 한다: ${s[3].yellowRatio}", s[3].yellowRatio > 0.015)
        listOf(0, 2).forEach { i ->
            assertTrue("${files[i].name}: 노란 모자가 없어야 한다(이전·다음 의상이 찍히지 않음): ${s[i].yellowRatio}", s[i].yellowRatio < 0.005)
        }
    }
}

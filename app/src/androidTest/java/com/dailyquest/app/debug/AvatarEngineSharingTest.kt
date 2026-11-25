package com.dailyquest.app.debug

import android.os.SystemClock
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.dailyquest.app.ui.avatar.AvatarEngine
import com.dailyquest.app.ui.avatar.AvatarLoadStatus
import com.dailyquest.app.ui.avatar.AvatarView
import androidx.compose.ui.test.junit4.createComposeRule
import io.github.sceneview.loaders.ModelLoader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * 아바타 속도 2단계: 3D 엔진·모델 로더를 앱 전체가 함께 쓸 때, 각 화면이 자기 모델을 빠짐없이 지우는지(메모리 누수·강제 종료 없음).
 * 실제 S3 GLB(테스트 의상)를 쓰므로 인터넷이 필요하다. 모델 수는 공유 로더 안의 목록을 읽어 센다(테스트에서만).
 */
class AvatarEngineSharingTest {
    @get:Rule val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var loader: ModelLoader
    private var base = 0

    private val boyBasic = TEST_OUTFITS.first { it.label == "남 기본" }.parts
    private val boyCafe = TEST_OUTFITS.first { it.label == "남 카페+모자" }.parts
    private val girlMix = TEST_OUTFITS.first { it.label == "여 섞어 입기" }.parts

    /** 공유 로더가 지금 가진 모델 수. */
    private fun liveModels(): Int {
        val f = ModelLoader::class.java.getDeclaredField("models").apply { isAccessible = true }
        return (f.get(loader) as List<*>).size
    }

    @Before fun setUp() {
        // SceneView가 매 프레임 그려서 Compose가 멈추지 않으므로 프레임 시계를 직접 넘긴다(STEP 9 3D 테스트와 같음).
        compose.mainClock.autoAdvance = false
        InstrumentationRegistry.getInstrumentation().runOnMainSync { loader = AvatarEngine.get(context).modelLoader }
        base = onMain { liveModels() }
    }

    private fun <T> onMain(block: () -> T): T {
        var r: Result<T>? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync { r = runCatching(block) }
        return r!!.getOrThrow()
    }

    private fun waitUntil(what: String, timeoutMs: Long = 90_000, condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < deadline) {
            compose.mainClock.advanceTimeBy(100)
            if (condition()) return
            Thread.sleep(50)
        }
        fail("${timeoutMs}ms 안에 '$what'이(가) 되지 않았습니다 (모델 ${onMain { liveModels() }}개, 기준 $base)")
    }

    @Test fun modelsAreReleasedOnOutfitChangeAndWhenTheViewLeaves() {
        var parts by mutableStateOf(boyBasic)
        var shown by mutableStateOf(true)
        var status: AvatarLoadStatus = AvatarLoadStatus.Loading
        compose.setContent {
            if (shown) AvatarView(parts, Modifier.size(200.dp), interactive = false, onStatusChange = { status = it })
        }

        waitUntil("남 기본 준비") { status is AvatarLoadStatus.Ready && onMain { liveModels() } == base + boyBasic.size }

        // 바꿔 입기: 빠진 파츠의 모델은 지우고, 새 파츠만 만든다.
        status = AvatarLoadStatus.Loading
        parts = boyCafe
        waitUntil("카페 옷으로 바꿈") { status is AvatarLoadStatus.Ready && onMain { liveModels() } == base + boyCafe.size }

        // 화면을 떠나면 이 화면의 모델을 모두 지운다(엔진·로더는 그대로).
        shown = false
        waitUntil("화면을 떠나면 모델이 모두 지워짐", timeoutMs = 10_000) { onMain { liveModels() } == base }
    }

    @Test fun closingWhileModelsAreBeingCreatedStillReleasesThem() {
        var shown by mutableStateOf(true)
        var status: AvatarLoadStatus = AvatarLoadStatus.Ready(emptySet())
        compose.setContent {
            if (shown) AvatarView(girlMix, Modifier.size(200.dp), interactive = false, onStatusChange = { status = it })
        }
        // 첫 모델이 만들어지기 시작하면 바로 닫는다(파일은 캐시에 있으면 금방 만든다).
        waitUntil("모델 만들기 시작") { onMain { liveModels() } > base }
        shown = false
        // 만들던 모델은 텍스처가 끝난 뒤 지워진다(최대 5초 기다림).
        waitUntil("만들던 모델까지 모두 지워짐", timeoutMs = 15_000) { onMain { liveModels() } == base }
    }

    @Test fun twoViewsShareOneEngineAndBothLoad() {
        var a: AvatarLoadStatus = AvatarLoadStatus.Loading
        var b: AvatarLoadStatus = AvatarLoadStatus.Loading
        var shown by mutableStateOf(true)
        compose.setContent {
            if (shown) Row {
                AvatarView(boyBasic, Modifier.size(150.dp), interactive = false, onStatusChange = { a = it })
                AvatarView(girlMix, Modifier.size(150.dp), interactive = false, onStatusChange = { b = it })
            }
        }
        waitUntil("두 화면 모두 준비") {
            a is AvatarLoadStatus.Ready && b is AvatarLoadStatus.Ready && onMain { liveModels() } == base + boyBasic.size + girlMix.size
        }
        assertSame("같은 엔진", loader, onMain { AvatarEngine.get(context).modelLoader })
        shown = false
        waitUntil("두 화면 모델 모두 지워짐", timeoutMs = 10_000) { onMain { liveModels() } == base }
        assertEquals(base, onMain { liveModels() })
    }
}

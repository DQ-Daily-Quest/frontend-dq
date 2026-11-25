package com.dailyquest.app.debug

import android.os.SystemClock
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.dailyquest.app.ui.avatar.AvatarEngine
import com.dailyquest.app.ui.avatar.AvatarLoadStatus
import com.dailyquest.app.ui.avatar.AvatarModelCache
import com.dailyquest.app.ui.avatar.AvatarView
import io.github.sceneview.loaders.ModelLoader
import io.github.sceneview.model.ModelInstance
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * 아바타 속도 2·3단계: 3D 엔진과 모델 로더를 앱 전체가 함께 쓰고, 파츠 모델을 보관해 다시 쓴다.
 * - 2단계: 엔진 하나로 여러 화면이 동시에 그린다.
 * - 3단계: 화면을 닫아도 모델은 보관함에 남고, 다시 열면 새로 만들지 않는다. 안 쓰는 모델은 상한을 넘지 않는다.
 * 실제 S3 GLB(테스트 의상)를 쓰므로 인터넷이 필요하다. 모델 수는 공유 로더 안의 목록을 읽어 센다(테스트에서만).
 */
class AvatarEngineSharingTest {
    @get:Rule val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var loader: ModelLoader
    private lateinit var cache: AvatarModelCache<ModelInstance>

    private val boyBasic = TEST_OUTFITS.first { it.label == "남 기본" }.parts
    private val boyCafe = TEST_OUTFITS.first { it.label == "남 카페+모자" }.parts
    private val girlBasic = TEST_OUTFITS.first { it.label == "여 기본" }.parts
    private val girlMix = TEST_OUTFITS.first { it.label == "여 섞어 입기" }.parts

    private fun <T> onMain(block: () -> T): T {
        var r: Result<T>? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync { r = runCatching(block) }
        return r!!.getOrThrow()
    }

    /** 공유 로더가 지금 가진 모델 수(보관 중인 것 포함). */
    private fun liveModels(): Int = onMain {
        val f = ModelLoader::class.java.getDeclaredField("models").apply { isAccessible = true }
        (f.get(loader) as List<*>).size
    }
    private fun inUse() = onMain { cache.inUse }
    private fun kept() = onMain { cache.size }

    /** 보관함을 비운다: 안 쓰는 모델을 모두 지운다(유예 시간이 지난 뒤). */
    private fun emptyCache() {
        onMain { cache.maxUnused = 0 }
        Thread.sleep(AvatarModelCache.DEFAULT_GRACE_MS + 200)
        onMain { cache.evictIfNeeded() }
        onMain { cache.maxUnused = AvatarModelCache.DEFAULT_MAX_UNUSED }
    }

    @Before fun setUp() {
        // SceneView가 매 프레임 그려서 Compose가 멈추지 않으므로 프레임 시계를 직접 넘긴다(STEP 9 3D 테스트와 같음).
        compose.mainClock.autoAdvance = false
        onMain { AvatarEngine.get(context).let { loader = it.modelLoader; cache = it.models } }
        emptyCache()
    }

    @After fun restore() = onMain { cache.maxUnused = AvatarModelCache.DEFAULT_MAX_UNUSED }

    private fun waitUntil(what: String, timeoutMs: Long = 90_000, condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < deadline) {
            compose.mainClock.advanceTimeBy(100)
            if (condition()) return
            Thread.sleep(50)
        }
        fail("${timeoutMs}ms 안에 '$what'이(가) 되지 않았습니다 (모델 ${liveModels()}개, 보관 ${kept()}, 쓰는 중 ${inUse()})")
    }

    @Test fun closedViewKeepsModelsAndReopeningCreatesNothingNew() {
        val base = liveModels()
        var shown by mutableStateOf(true)
        var status: AvatarLoadStatus = AvatarLoadStatus.Loading
        compose.setContent { if (shown) AvatarView(boyBasic, Modifier.size(200.dp), interactive = false, onStatusChange = { status = it }) }

        waitUntil("남 기본 준비") { status is AvatarLoadStatus.Ready && liveModels() == base + boyBasic.size }
        assertEquals(boyBasic.size, inUse())

        shown = false
        waitUntil("닫으면 모두 돌려줌", timeoutMs = 10_000) { inUse() == 0 }
        assertEquals("닫아도 지우지 않고 보관", base + boyBasic.size, liveModels())

        status = AvatarLoadStatus.Loading
        shown = true
        waitUntil("다시 열면 바로 준비", timeoutMs = 10_000) { status is AvatarLoadStatus.Ready }
        assertEquals("다시 열 때 새로 만들지 않음", base + boyBasic.size, liveModels())
        assertEquals(boyBasic.size, inUse())
    }

    @Test fun unusedModelsStayUnderTheLimitWhenChangingOutfits() {
        onMain { cache.maxUnused = 3 }
        var parts by mutableStateOf(boyBasic)
        var status: AvatarLoadStatus = AvatarLoadStatus.Loading
        compose.setContent { AvatarView(parts, Modifier.size(200.dp), interactive = false, onStatusChange = { status = it }) }
        for (outfit in listOf(boyBasic, boyCafe, girlBasic, girlMix, boyBasic)) {
            status = AvatarLoadStatus.Loading
            parts = outfit
            waitUntil("${outfit.size}개 파츠로 바꿈") { status is AvatarLoadStatus.Ready && inUse() == outfit.size }
            // 바꿔 입어 빠진 모델의 유예 시간이 지나도록 잠깐 둔다.
            Thread.sleep(AvatarModelCache.DEFAULT_GRACE_MS + 200)
            onMain { cache.evictIfNeeded() }
            assertTrue("안 쓰는 모델은 3개 이하 (보관 ${kept()}, 쓰는 중 ${inUse()})", kept() - inUse() <= 3)
        }
        assertEquals("로더의 모델 수 = 보관함 수(지운 것은 로더에서도 빠짐)", kept(), liveModels())
    }

    @Test fun closingWhileModelsAreBeingCreatedReturnsEverything() {
        var shown by mutableStateOf(true)
        compose.setContent { if (shown) AvatarView(girlMix, Modifier.size(200.dp), interactive = false) }
        // 첫 모델이 만들어지기 시작하면 바로 닫는다.
        waitUntil("모델 만들기 시작") { kept() > 0 }
        shown = false
        // 만들던 모델도 끝까지 만들어 보관함에 넣고, 이 화면이 빌린 것은 모두 돌려준다.
        waitUntil("빌린 모델을 모두 돌려줌", timeoutMs = 20_000) { inUse() == 0 }
        Thread.sleep(1_500)
        assertEquals(0, inUse())
        assertEquals(kept(), liveModels())
    }

    @Test fun twoViewsWithTheSameOutfitShareOneSetOfModels() {
        val base = liveModels()
        var a: AvatarLoadStatus = AvatarLoadStatus.Loading
        var b: AvatarLoadStatus = AvatarLoadStatus.Loading
        var shown by mutableStateOf(true)
        compose.setContent {
            if (shown) Row {
                AvatarView(boyBasic, Modifier.size(150.dp), interactive = false, onStatusChange = { a = it })
                AvatarView(boyBasic, Modifier.size(150.dp), interactive = false, onStatusChange = { b = it })
            }
        }
        waitUntil("두 화면 모두 준비") { a is AvatarLoadStatus.Ready && b is AvatarLoadStatus.Ready }
        assertEquals("같은 옷은 모델 한 벌만", base + boyBasic.size, liveModels())
        assertEquals(boyBasic.size, inUse())
        shown = false
        waitUntil("두 화면 모두 돌려줌", timeoutMs = 10_000) { inUse() == 0 }
    }

    @Test fun twoViewsWithDifferentOutfitsBothLoad() {
        var a: AvatarLoadStatus = AvatarLoadStatus.Loading
        var b: AvatarLoadStatus = AvatarLoadStatus.Loading
        var shown by mutableStateOf(true)
        compose.setContent {
            if (shown) Row {
                AvatarView(boyBasic, Modifier.size(150.dp), interactive = false, onStatusChange = { a = it })
                AvatarView(girlMix, Modifier.size(150.dp), interactive = false, onStatusChange = { b = it })
            }
        }
        waitUntil("두 화면 모두 준비") { a is AvatarLoadStatus.Ready && b is AvatarLoadStatus.Ready }
        assertEquals(boyBasic.size + girlMix.size, inUse())
        shown = false
        waitUntil("두 화면 모두 돌려줌", timeoutMs = 10_000) { inUse() == 0 }
    }
}

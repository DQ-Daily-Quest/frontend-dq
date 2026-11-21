package com.dailyquest.app.ui.avatar

import com.dailyquest.app.data.avatar.AvatarModelFetcher
import com.dailyquest.app.data.avatar.AvatarPart
import com.dailyquest.app.data.avatar.AvatarPart.BOTTOM
import com.dailyquest.app.data.avatar.AvatarPart.EYES
import com.dailyquest.app.data.avatar.AvatarPart.HAIR
import com.dailyquest.app.data.avatar.AvatarPart.HAT
import com.dailyquest.app.data.avatar.AvatarPart.SHOES
import com.dailyquest.app.data.avatar.AvatarPart.SKIN
import com.dailyquest.app.data.avatar.AvatarPart.TOP
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * STEP 9-4 AvatarView의 핵심 로직. 3D 엔진 대신 모델을 문자열("model:<주소>")로 바꿔서 검사한다.
 * - 바뀐 슬롯만 받는지, 이전 모델을 지우는지, 실패 시 이전 모습을 유지하는지, 다시 시도하면 실패한 슬롯만 받는지.
 */
class AvatarPartsLoaderTest {

    /** 받은 주소를 기록하고, [failing]에 든 주소는 실패시키는 가짜 다운로더 */
    private class FakeFetcher : AvatarModelFetcher {
        val requested = mutableListOf<String>()
        val failing = mutableSetOf<String>()
        override suspend fun fetch(url: String): Result<ByteArray> {
            requested += url
            return if (url in failing) Result.failure(IOException("HTTP 404")) else Result.success(url.toByteArray())
        }
    }

    private val fetcher = FakeFetcher()
    private val created = mutableListOf<String>()
    private val released = mutableListOf<String>()
    private val loader = AvatarPartsLoader(
        fetcher = fetcher,
        createModel = { bytes -> "model:${String(bytes)}".also { created += it } },
        releaseModel = { model -> released += model },
    )

    private val boyDefault = mapOf(
        SKIN to "u/body", EYES to "u/eyes", HAIR to "u/hair", TOP to "u/top", BOTTOM to "u/bottom", SHOES to "u/shoes",
    )

    private fun shownUrls() = loader.shown.mapValues { it.value.url }

    @Test fun `처음에는 모든 슬롯을 받아 보여준다`() = runBlocking {
        loader.sync(boyDefault)

        assertEquals(boyDefault, shownUrls())
        assertEquals(boyDefault.values.toSet(), fetcher.requested.toSet())
        assertEquals(6, fetcher.requested.size)
        assertEquals(AvatarLoadStatus.Ready(boyDefault.keys), loader.status)
        assertTrue(released.isEmpty())
    }

    @Test fun `슬롯 하나를 바꾸면 그 파일만 받고 이전 모델은 지운다`() = runBlocking {
        loader.sync(boyDefault)
        fetcher.requested.clear()

        loader.sync(boyDefault + (TOP to "u/top-cafe"))

        assertEquals(listOf("u/top-cafe"), fetcher.requested)
        assertEquals("u/top-cafe", loader.shown[TOP]?.url)
        assertEquals(listOf("model:u/top"), released)
        assertEquals(AvatarLoadStatus.Ready(boyDefault.keys), loader.status)
    }

    @Test fun `모자를 쓰면 모자만 받고 지우는 모델은 없다`() = runBlocking {
        loader.sync(boyDefault)
        fetcher.requested.clear()

        loader.sync(boyDefault + (HAT to "u/hat"))

        assertEquals(listOf("u/hat"), fetcher.requested)
        assertTrue(released.isEmpty())
        assertEquals(AvatarLoadStatus.Ready(boyDefault.keys + HAT), loader.status)
    }

    @Test fun `모자를 벗으면 아무것도 받지 않고 모자 모델만 지운다`() = runBlocking {
        loader.sync(boyDefault + (HAT to "u/hat"))
        fetcher.requested.clear()

        loader.sync(boyDefault)

        assertTrue(fetcher.requested.isEmpty())
        assertEquals(boyDefault, shownUrls())
        assertEquals(listOf("model:u/hat"), released)
        assertEquals(AvatarLoadStatus.Ready(boyDefault.keys), loader.status)
    }

    @Test fun `같은 구성으로 다시 맞추면 아무것도 받거나 지우지 않는다`() = runBlocking {
        loader.sync(boyDefault)
        fetcher.requested.clear()

        loader.sync(boyDefault)

        assertTrue(fetcher.requested.isEmpty())
        assertTrue(released.isEmpty())
        assertEquals(6, created.size)
    }

    @Test fun `받지 못한 슬롯은 이전 모델을 유지하고 실패로 알린다`() = runBlocking {
        loader.sync(boyDefault)
        fetcher.failing += "u/top-broken"

        loader.sync(boyDefault + (TOP to "u/top-broken"))

        assertEquals("u/top", loader.shown[TOP]?.url)
        assertTrue(released.isEmpty())
        assertEquals(AvatarLoadStatus.Failed(setOf(TOP), boyDefault.keys), loader.status)
    }

    @Test fun `처음부터 일부가 실패하면 나머지는 보이고 실패한 슬롯만 알린다`() = runBlocking {
        fetcher.failing += "u/hair"

        loader.sync(boyDefault)

        assertEquals(boyDefault - HAIR, shownUrls())
        assertEquals(AvatarLoadStatus.Failed(setOf(HAIR), boyDefault.keys - HAIR), loader.status)
    }

    @Test fun `다시 시도하면 실패한 슬롯만 다시 받는다`() = runBlocking {
        fetcher.failing += "u/hair"
        loader.sync(boyDefault)
        fetcher.failing.clear()
        fetcher.requested.clear()

        loader.sync(boyDefault)

        assertEquals(listOf("u/hair"), fetcher.requested)
        assertEquals(boyDefault, shownUrls())
        assertEquals(AvatarLoadStatus.Ready(boyDefault.keys), loader.status)
    }

    @Test fun `모델 생성이 실패한 슬롯도 실패로 알린다`() = runBlocking {
        val broken = AvatarPartsLoader(
            fetcher = fetcher,
            createModel = { bytes -> if (String(bytes) == "u/eyes") throw IllegalArgumentException("Failed to parse glTF") else "m" },
            releaseModel = {},
        )

        broken.sync(boyDefault)

        assertEquals(AvatarLoadStatus.Failed(setOf(EYES), boyDefault.keys - EYES), broken.status)
    }

    @Test fun `여러 슬롯을 바꿀 때는 모두 받은 뒤 한꺼번에 바꾼다`() = runBlocking {
        loader.sync(boyDefault)
        val gate = CompletableDeferred<Unit>()
        val slow = AvatarPartsLoader(
            fetcher = { url -> if (url == "u/hair-cafe") gate.await(); Result.success(url.toByteArray()) },
            createModel = { bytes -> String(bytes) },
            releaseModel = {},
        )
        slow.sync(boyDefault)

        val job = async { slow.sync(boyDefault + (TOP to "u/top-cafe") + (HAIR to "u/hair-cafe")) }
        repeat(5) { yield() }
        // 머리를 기다리는 동안에는 상의도 아직 이전 것이다
        assertEquals("u/top", slow.shown[TOP]?.url)
        assertEquals(AvatarLoadStatus.Loading, slow.status)

        gate.complete(Unit)
        job.await()
        assertEquals("u/top-cafe", slow.shown[TOP]?.url)
        assertEquals("u/hair-cafe", slow.shown[HAIR]?.url)
    }

    @Test fun `sync가 끝날 때마다 completedSyncs가 는다`() = runBlocking {
        loader.sync(boyDefault)
        loader.sync(boyDefault)
        assertEquals(2, loader.completedSyncs)
    }

    @Test fun `diffParts - 바뀐 것과 빠진 것만 고른다`() {
        val shown = mapOf(SKIN to "a", TOP to "b", HAT to "c")
        val target = mapOf(SKIN to "a", TOP to "b2", SHOES to "d")

        val diff = diffParts(shown, target)

        assertEquals(mapOf(TOP to "b2", SHOES to "d"), diff.toLoad)
        assertEquals(setOf<AvatarPart>(HAT), diff.toRemove)
    }
}

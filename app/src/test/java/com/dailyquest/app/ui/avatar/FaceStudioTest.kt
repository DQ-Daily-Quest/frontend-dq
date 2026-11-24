package com.dailyquest.app.ui.avatar

import com.dailyquest.app.data.avatar.AvatarPart
import com.dailyquest.app.data.avatar.FaceKey
import com.dailyquest.app.data.avatar.FaceStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** 프로필 얼굴 2단계: 찍을 차례 관리(대기열). 실제로 찍는 3D 화면은 UI 테스트에서 확인한다. */
class FaceStudioTest {
    @get:Rule val temp = TemporaryFolder()

    private fun key(hat: String) = FaceKey(mapOf(AvatarPart.SKIN to "body.glb", AvatarPart.HAT to hat))
    private fun studio() = FaceStudio(FaceStore(temp.newFolder()))

    @Test fun `같은 얼굴은 한 번만 줄 선다`() {
        val s = studio()
        s.request(key("a"))
        s.request(key("a"))
        s.request(key("b"))
        assertEquals(listOf(key("a").id, key("b").id), s.pending.value.map { it.id })
    }

    @Test fun `이미 저장된 얼굴과 빈 조합은 줄 서지 않는다`() = runBlocking {
        val s = studio()
        s.store.put(key("a"), byteArrayOf(1))
        s.request(key("a"))
        s.request(FaceKey(emptyMap()))
        assertTrue(s.pending.value.isEmpty())
    }

    @Test fun `찍으면 저장하고 줄에서 빠지며 version이 오른다`() = runBlocking {
        val s = studio()
        s.request(key("a"))
        s.request(key("b"))
        s.complete(key("a"), byteArrayOf(7))
        assertEquals(listOf(key("b").id), s.pending.value.map { it.id })
        assertTrue(s.store.has(key("a")))
        assertEquals(1, s.version.value)
    }

    @Test fun `실패하면 맨 뒤로 가고 두 번 실패하면 포기한다`() = runBlocking {
        val s = studio()
        s.request(key("a"))
        s.request(key("b"))
        s.complete(key("a"), null)
        assertEquals("다른 얼굴을 먼저 찍는다", listOf(key("b").id, key("a").id), s.pending.value.map { it.id })
        s.complete(key("a"), null)
        assertEquals(listOf(key("b").id), s.pending.value.map { it.id })
        s.request(key("a"))
        assertEquals("포기한 얼굴은 이번 실행에서 다시 줄 서지 않는다", listOf(key("b").id), s.pending.value.map { it.id })
        assertEquals(0, s.version.value)
    }
}

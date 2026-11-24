package com.dailyquest.app.data.avatar

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.concurrent.atomic.AtomicLong

/** 프로필 얼굴 2단계: 얼굴 이름표(FaceKey)와 기기 저장(FaceStore). */
class FaceStoreTest {
    @get:Rule val temp = TemporaryFolder()

    private val s3 = "https://dq-uploads-prod.s3.ap-northeast-2.amazonaws.com/items/avatar/boy"
    private val basic = mapOf(
        AvatarPart.SKIN to "$s3/DQ_Boy_01_Body.glb",
        AvatarPart.HAIR to "$s3/DQ_Boy_03_Hair.glb",
        AvatarPart.EYES to "$s3/DQ_Boy_02_Eyes.glb",
    )

    @Test fun `같은 착용 조합이면 순서와 관계없이 같은 이름`() {
        val reordered = linkedMapOf(AvatarPart.EYES to basic.getValue(AvatarPart.EYES), AvatarPart.SKIN to basic.getValue(AvatarPart.SKIN), AvatarPart.HAIR to basic.getValue(AvatarPart.HAIR))
        assertEquals(FaceKey(basic).id, FaceKey(reordered).id)
        assertEquals(40, FaceKey(basic).id.length)
    }

    @Test fun `옷을 바꾸거나 모자를 쓰면 다른 이름`() {
        val withHat = basic + (AvatarPart.HAT to "$s3/DQ_Boy_Hat_Cafe.glb")
        val otherHair = basic + (AvatarPart.HAIR to "$s3/DQ_Boy_Hair_Curly.glb")
        assertNotEquals(FaceKey(basic).id, FaceKey(withHat).id)
        assertNotEquals(FaceKey(basic).id, FaceKey(otherHair).id)
        assertTrue(FaceKey(emptyMap()).isEmpty)
    }

    private val time = AtomicLong(1_000_000)
    private fun store(max: Long = FaceStore.DEFAULT_MAX_BYTES) = FaceStore(temp.newFolder("faces"), max, clock = { time.addAndGet(1_000) })

    @Test fun `저장한 사진을 다시 읽고, 없으면 null`() = runBlocking {
        val st = store()
        val key = FaceKey(basic)
        assertFalse(st.has(key))
        assertNull(st.get(key))
        st.put(key, byteArrayOf(1, 2, 3))
        assertTrue(st.has(key))
        assertArrayEquals(byteArrayOf(1, 2, 3), st.get(key))
        assertEquals(1 to 3L, st.stats())
    }

    @Test fun `용량을 넘으면 가장 오래 안 쓴 사진부터 지운다`() = runBlocking {
        val dir = temp.newFolder("small")
        val st = FaceStore(dir, maxBytes = 25, clock = { time.addAndGet(1_000) })
        val a = FaceKey(basic)
        val b = FaceKey(basic + (AvatarPart.HAT to "h1"))
        val c = FaceKey(basic + (AvatarPart.HAT to "h2"))
        st.put(a, ByteArray(10))
        st.put(b, ByteArray(10))
        st.get(a) // a를 최근에 씀 → b가 가장 오래 안 씀
        st.put(c, ByteArray(10))
        assertTrue(st.has(a))
        assertFalse("가장 오래 안 쓴 b가 지워진다", st.has(b))
        assertTrue(st.has(c))
        assertTrue(dir.listFiles()!!.none { it.name.endsWith(".tmp") })
    }

    @Test fun `clear하면 모두 지워진다`() = runBlocking {
        val st = store()
        st.put(FaceKey(basic), byteArrayOf(9))
        st.clear()
        assertFalse(st.has(FaceKey(basic)))
        assertEquals(0 to 0L, st.stats())
    }
}

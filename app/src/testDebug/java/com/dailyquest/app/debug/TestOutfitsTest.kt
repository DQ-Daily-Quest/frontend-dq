package com.dailyquest.app.debug

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** STEP 9-3 테스트용 한 벌 목록이 서버 규칙(체형별 폴더·코드, 필수 슬롯)과 맞는지 검사한다. */
class TestOutfitsTest {

    @Test fun `모든 한 벌은 모자를 뺀 6개 슬롯을 다 갖는다`() {
        val required = TestSlot.entries - TestSlot.HAT
        TEST_OUTFITS.forEach { outfit ->
            assertTrue("${outfit.label}: 빠진 슬롯 ${required - outfit.parts.keys}", outfit.parts.keys.containsAll(required))
        }
    }

    @Test fun `남자 한 벌은 남자 코드, 여자 한 벌은 여자 코드만 쓴다`() {
        TEST_OUTFITS.forEach { outfit ->
            outfit.parts.values.forEach { code ->
                when (outfit.body) {
                    TestBody.BOY -> assertTrue("${outfit.label}: $code", code.startsWith("DQ_Boy_"))
                    TestBody.GIRL -> assertFalse("${outfit.label}: $code", code.startsWith("DQ_Boy_"))
                }
            }
        }
    }

    @Test fun `주소는 체형 폴더와 코드로 만든다`() {
        assertEquals(
            "https://dq-uploads-prod.s3.ap-northeast-2.amazonaws.com/items/avatar/girl/DQ_Hat_LemonBucket.glb",
            modelUrl(TestBody.GIRL, "DQ_Hat_LemonBucket"),
        )
    }

    @Test fun `urls는 슬롯 순서대로 파츠 수만큼 나온다`() {
        val withHat = TEST_OUTFITS.first { it.parts.containsKey(TestSlot.HAT) }
        assertEquals(7, withHat.urls.size)
        assertTrue(withHat.urls.first().endsWith("Body.glb"))
        assertTrue(withHat.urls.last().contains("Hat"))
        val noHat = TEST_OUTFITS.first { !it.parts.containsKey(TestSlot.HAT) }
        assertEquals(6, noHat.urls.size)
    }
}

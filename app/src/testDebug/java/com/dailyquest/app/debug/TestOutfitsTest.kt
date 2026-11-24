package com.dailyquest.app.debug

import com.dailyquest.app.data.avatar.AvatarPart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** STEP 9 테스트 화면의 파츠·한 벌·교체 후보 목록이 서버 규칙(체형별 폴더·코드, 필수 슬롯)과 맞는지 검사한다. */
class TestOutfitsTest {

    private fun assertBodyCodes(label: String, body: TestBody, codes: Collection<String>) = codes.forEach { code ->
        when (body) {
            TestBody.BOY -> assertTrue("$label: $code", code.startsWith("DQ_Boy_"))
            TestBody.GIRL -> assertFalse("$label: $code", code.startsWith("DQ_Boy_"))
        }
    }

    @Test fun `모든 한 벌은 모자를 뺀 6개 슬롯을 다 갖는다`() {
        val required = AvatarPart.entries - AvatarPart.HAT
        TEST_OUTFITS.forEach { outfit ->
            assertTrue("${outfit.label}: 빠진 슬롯 ${required - outfit.codes.keys}", outfit.codes.keys.containsAll(required))
        }
    }

    @Test fun `남자 구성은 남자 코드, 여자 구성은 여자 코드만 쓴다`() {
        (TEST_SINGLE_PARTS + TEST_OUTFITS).forEach { assertBodyCodes(it.label, it.body, it.codes.values) }
        TEST_HATS.forEach { (body, codes) -> assertBodyCodes("모자 후보", body, codes) }
        TEST_TOPS.forEach { (body, codes) -> assertBodyCodes("상의 후보", body, codes) }
    }

    @Test fun `주소는 체형 폴더와 코드로 만든다`() {
        assertEquals(
            "https://dq-uploads-prod.s3.ap-northeast-2.amazonaws.com/items/avatar/girl/DQ_Hat_LemonBucket.glb",
            modelUrl(TestBody.GIRL, "DQ_Hat_LemonBucket"),
        )
        val outfit = TEST_OUTFITS.first { it.label == "남 기본" }
        assertEquals(modelUrl(TestBody.BOY, "DQ_Boy_04_Top"), outfit.parts[AvatarPart.TOP])
    }

    @Test fun `nextOf는 다음 후보로 돌고, 목록에 없으면 처음 것을 고른다`() {
        val hats = listOf("a", "b", "c")
        assertEquals("b", nextOf(hats, "a"))
        assertEquals("a", nextOf(hats, "c"))
        assertEquals("a", nextOf(hats, null))
        assertEquals("a", nextOf(hats, "zzz"))
    }
}

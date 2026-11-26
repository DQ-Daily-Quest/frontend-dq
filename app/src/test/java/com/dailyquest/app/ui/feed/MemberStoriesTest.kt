package com.dailyquest.app.ui.feed

import com.dailyquest.app.data.mission.MissionUser
import com.dailyquest.app.data.proof.PendingProof
import com.dailyquest.app.data.proof.ProofMediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** QA 디자인 2-1단계: 방 피드로 멤버 얼굴 테두리(스토리 링)를 정한다. */
class MemberStoriesTest {
    private fun proof(id: Long, user: Long, viewed: Boolean) =
        PendingProof(id, MissionUser(user, "u$user"), 1, "미션$id", ProofMediaType.IMAGE, null, viewed, null)

    @Test fun `안 본 인증이 하나라도 있으면 분홍, 모두 봤으면 회색, 없으면 테두리 없음`() {
        val rings = storyRings(listOf(proof(5, 1, viewed = true), proof(4, 1, viewed = false), proof(3, 2, viewed = true), proof(2, 2, viewed = true)))
        assertEquals(StoryRing.NEW, rings[1])
        assertEquals(StoryRing.SEEN, rings[2])
        assertNull("인증이 없는 멤버", rings[3])
    }

    @Test fun `피드가 비면 아무도 테두리가 없다`() {
        assertTrue(storyRings(emptyList()).isEmpty())
    }

    @Test fun `그 사람 인증만 피드 순서(최신순)대로 모은다`() {
        val items = listOf(proof(9, 1, false), proof(8, 2, false), proof(7, 1, true))
        assertEquals(listOf(9L, 7L), proofsOf(items, 1).map { it.proofId })
        assertTrue(proofsOf(items, 3).isEmpty())
    }
}

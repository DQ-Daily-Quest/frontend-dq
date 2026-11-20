package com.dailyquest.app.data.proof

import org.junit.Assert.assertEquals
import org.junit.Test

class ProofStatusDisplayTest {
    @Test fun koreanLabelsForServerStatuses() {
        assertEquals("승인 대기", proofStatusLabel("PENDING"))
        assertEquals("승인 완료", proofStatusLabel("APPROVED"))
        assertEquals("인증 거절", proofStatusLabel("REJECTED"))
        assertEquals("인증 만료", proofStatusLabel("EXPIRED"))
        assertEquals("승인 대기", proofStatusLabel("pending"))
    }

    @Test fun unknownOrMissingStatusUsesSafeFallbackWithoutRawEnum() {
        assertEquals("상태 확인 중", proofStatusLabel("SOMETHING_NEW"))
        assertEquals("상태 확인 중", proofStatusLabel(null))
        assertEquals("승인 완료", ProofStatus.APPROVED.label())
    }

    private fun ProofStatus.label() = proofStatusLabel(this)
}

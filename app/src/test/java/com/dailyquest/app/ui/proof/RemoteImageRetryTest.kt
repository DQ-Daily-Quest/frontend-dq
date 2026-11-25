package com.dailyquest.app.ui.proof

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 상점 썸네일이 일시적인 실패 한 번으로 "이미지 없음"에 고정되지 않도록 재시도한다. */
class RemoteImageRetryTest {
    @Test fun retriesTransientFailureThenSucceeds() = runTest {
        var calls = 0
        val r = loadWithRetry(attempts = 3, delayMs = 1) { if (++calls < 3) null else "ok" }
        assertEquals("ok", r); assertEquals(3, calls)
    }

    @Test fun exceptionsCountAsFailureAndStopAfterAttempts() = runTest {
        var calls = 0
        val r = loadWithRetry<String>(attempts = 3, delayMs = 1) { calls++; throw java.io.IOException("x") }
        assertNull(r); assertEquals(3, calls)
    }

    @Test fun successDoesNotRetry() = runTest {
        var calls = 0
        loadWithRetry(attempts = 3, delayMs = 1) { calls++; "ok" }
        assertEquals(1, calls)
    }
}

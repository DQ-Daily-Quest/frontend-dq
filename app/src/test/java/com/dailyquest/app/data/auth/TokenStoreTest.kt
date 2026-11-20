package com.dailyquest.app.data.auth

import org.junit.Assert.*
import org.junit.Test

class TokenStoreTest {
    @Test fun saveRestoreAndClear() {
        val store = InMemoryTokenStore()
        SessionTokens(store).set(TokenPair("a1", "r1"))
        // 앱 재실행: 새 SessionTokens가 저장소에서 복원한다.
        val restored = SessionTokens(store).get()
        assertEquals("a1", restored?.accessToken); assertEquals("r1", restored?.refreshToken)
        val t = SessionTokens(store); t.clear()
        assertNull(t.get()); assertNull(store.read())
    }

    @Test fun pairReplacedTogether() {
        val t = SessionTokens(InMemoryTokenStore())
        t.set(TokenPair("a1", "r1")); t.set(TokenPair("a2", "r2"))
        assertEquals(TokenPair("a2", "r2"), t.get())
    }

    @Test fun toStringMasksValues() {
        assertFalse(TokenPair("secretA", "secretR").toString().contains("secret"))
    }

    @Test fun sessionManagerExpireClearsTokensAndRunsCleanersOnce() {
        var cleaned = 0
        val tokens = SessionTokens(InMemoryTokenStore(TokenPair("a", "r")))
        val m = SessionManager(tokens, listOf({ cleaned++ }))
        m.markAuthenticated(UserProfile(1, "e", "n", 0, 0, 0, 0, 0))
        m.expire(LogoutReason.USER); m.expire(LogoutReason.EXPIRED)
        assertNull(tokens.get()); assertEquals(1, cleaned)
        assertEquals(SessionState.Unauthenticated(LogoutReason.EXPIRED), m.state.value)
    }

    @Test fun switchingUserRunsCleaners() {
        var cleaned = 0
        val m = SessionManager(SessionTokens(InMemoryTokenStore()), listOf({ cleaned++ }))
        m.markAuthenticated(UserProfile(1, "e", "n", 0, 0, 0, 0, 0))
        m.markAuthenticated(UserProfile(1, "e", "n", 9, 0, 0, 0, 0)); assertEquals(0, cleaned)
        m.markAuthenticated(UserProfile(2, "e2", "n2", 0, 0, 0, 0, 0)); assertEquals(1, cleaned)
    }
}

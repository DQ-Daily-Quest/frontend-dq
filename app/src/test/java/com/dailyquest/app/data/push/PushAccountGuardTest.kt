package com.dailyquest.app.data.push

import com.dailyquest.app.data.auth.LogoutReason
import com.dailyquest.app.data.auth.SessionState
import com.dailyquest.app.data.auth.UserProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PushAccountGuardTest {
    private fun auth(id: Long) = SessionState.Authenticated(UserProfile(id, null, "n", 0, 0, 0, 0, 0))

    @Test fun loggedOutDeviceDoesNotShowPreviousAccountPush() {
        assertFalse(PushAccountGuard.shouldDisplay(SessionState.Unauthenticated(LogoutReason.USER)))
        assertFalse(PushAccountGuard.shouldDisplay(SessionState.Unauthenticated()))
    }

    @Test fun pushIsShownWhenSessionIsActiveOrStillRestoring() {
        assertTrue(PushAccountGuard.shouldDisplay(auth(1)))
        assertTrue(PushAccountGuard.shouldDisplay(SessionState.Loading))
        assertTrue(PushAccountGuard.shouldDisplay(SessionState.BootstrapFailed(true)))
    }

    @Test fun trayIsClearedOnLogoutAndAccountSwitchButNotOnFirstRestore() {
        assertTrue(PushAccountGuard.shouldClearTray(auth(1), SessionState.Unauthenticated(LogoutReason.USER)))
        assertTrue(PushAccountGuard.shouldClearTray(auth(1), auth(2)))
        assertFalse(PushAccountGuard.shouldClearTray(SessionState.Loading, auth(1)))
        assertFalse(PushAccountGuard.shouldClearTray(auth(1), auth(1)))
        assertEquals(false, PushAccountGuard.shouldClearTray(SessionState.Unauthenticated(), auth(1)))
    }
}

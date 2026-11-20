package com.dailyquest.app.data.profile

import com.dailyquest.app.ui.screens.formatJoinDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ProfileValidatorTest {
    @Test fun nicknameLengthAndSameAsCurrent() {
        assertNotNull(ProfileValidator.nickname("a", "old"))
        assertNotNull(ProfileValidator.nickname("가".repeat(13), "old"))
        assertNotNull(ProfileValidator.nickname("   ", "old"))
        assertNotNull(ProfileValidator.nickname("old", "old"))
        assertNull(ProfileValidator.nickname("  새닉네임 ", "old"))
        assertNull(ProfileValidator.nickname("가".repeat(12), "old"))
    }

    @Test fun passwordRules() {
        assertNotNull(ProfileValidator.passwordChange("", "newpass12", "newpass12"))
        assertNotNull(ProfileValidator.passwordChange("oldpass12", "short", "short"))
        assertNotNull(ProfileValidator.passwordChange("oldpass12", "a".repeat(65), "a".repeat(65)))
        assertNotNull(ProfileValidator.passwordChange("oldpass12", "newpass12", "different12"))
        assertNotNull(ProfileValidator.passwordChange("oldpass12", "oldpass12", "oldpass12"))
        assertNull(ProfileValidator.passwordChange("oldpass12", "newpass12", "newpass12"))
    }

    @Test fun joinDateFormatting() {
        assertEquals("2025.03.04", formatJoinDate("2025-03-04T01:02:03Z"))
        assertNull(formatJoinDate(null))
        assertNull(formatJoinDate("garbage"))
    }
}

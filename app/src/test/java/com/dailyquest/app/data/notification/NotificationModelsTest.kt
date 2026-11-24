package com.dailyquest.app.data.notification

import com.dailyquest.app.data.remote.dto.NotificationDto
import com.dailyquest.app.navigation.Routes
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationModelsTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test fun parsesIsReadAndReadAliasAndMissingOptionalIds() {
        val a = json.decodeFromString<NotificationDto>("""{"notificationId":1,"type":"PROOF_APPROVED","title":"t","body":"b","proofId":5,"isRead":true,"createdAt":"2025-04-17T00:00:00Z"}""")
        val b = json.decodeFromString<NotificationDto>("""{"notificationId":2,"type":"NEW_APP_MISSION","title":"t","body":"b","read":true}""")
        assertTrue(a.isRead); assertTrue(b.isRead)
        assertNull(b.proofId); assertNull(b.roomId)
        assertEquals(NotificationType.PROOF_APPROVED, a.toNotification().type)
    }

    @Test fun unknownTypeAndInvalidIdsAreSafe() {
        val n = NotificationDto(3, "SOMETHING_NEW", "t", "b", roomId = -1, missionId = 0, proofId = null).toNotification()
        assertEquals(NotificationType.UNKNOWN, n.type)
        assertNull(n.roomId); assertNull(n.missionId)
        assertNull(NotificationTargets.routeFor(n))
        assertFalse(n.isRead)
    }

    @Test fun parseIdRejectsMalformedValues() {
        assertEquals(12L, NotificationTargets.parseId(" 12 "))
        listOf(null, "", "0", "-3", "1.5", "abc", "12abc", "9".repeat(19), "١٢").forEach { assertNull(it, NotificationTargets.parseId(it)) }
    }

    @Test fun routesPerType() {
        val t = NotificationTargets
        assertEquals(Routes.missionDetail(4, 2), t.routeFor(NotificationType.NEW_APP_MISSION, 2, 4, null))
        assertEquals(Routes.missionDetail(4, 2), t.routeFor(NotificationType.NEW_MEMBER_MISSION, 2, 4, null))
        assertEquals(Routes.proofDetail(9, 2), t.routeFor(NotificationType.PROOF_SUBMITTED, 2, 4, 9))
        assertEquals(Routes.proofDetail(9, 2), t.routeFor(NotificationType.PROOF_APPROVED, 2, 4, 9))
        assertEquals(Routes.proofDetail(9, 2), t.routeFor(NotificationType.PROOF_REJECTED, 2, 4, 9))
    }

    @Test fun routeFallsBackWhenIdsAreMissing() {
        val t = NotificationTargets
        assertEquals(Routes.missionDetail(4, 2), t.routeFor(NotificationType.PROOF_APPROVED, 2, 4, null))
        assertEquals(Routes.room(2), t.routeFor(NotificationType.PROOF_APPROVED, 2, null, null))
        assertEquals(Routes.room(2), t.routeFor(NotificationType.NEW_APP_MISSION, 2, null, null))
        assertNull(t.routeFor(NotificationType.PROOF_APPROVED, null, null, null))
    }

    @Test fun payloadRoutingTreatsValuesAsUntrusted() {
        val t = NotificationTargets
        assertEquals(Routes.proofDetail(9, 2), t.routeFromPayload(mapOf("type" to "PROOF_APPROVED", "roomId" to "2", "proofId" to "9")))
        assertNull(t.routeFromPayload(mapOf("type" to "HACK", "roomId" to "2")))
        assertNull(t.routeFromPayload(mapOf("type" to "PROOF_APPROVED", "proofId" to "../x")))
        assertNull(t.routeFromPayload(emptyMap()))
    }
}

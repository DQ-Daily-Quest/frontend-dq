package com.dailyquest.app.data.push

import android.content.Intent
import com.dailyquest.app.data.notification.NotificationTargets

/** 알림 탭 Intent extras ↔ FCM data payload 변환. 외부 입력이므로 허용한 키의 문자열 값만 읽는다. */
object PushIntents {
    val KEYS = listOf("type", "roomId", "missionId", "proofId")

    fun routeFrom(intent: Intent?): String? {
        val extras = intent?.extras ?: return null
        val data = KEYS.associateWith { key -> runCatching { extras.getString(key) }.getOrNull() }
        return NotificationTargets.routeFromPayload(data)
    }

    fun clear(intent: Intent?) { KEYS.forEach { intent?.removeExtra(it) } }
}

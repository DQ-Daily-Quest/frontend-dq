package com.dailyquest.app

import com.dailyquest.app.data.push.PushAccountGuard
import com.dailyquest.app.data.push.PushIntents
import com.dailyquest.app.data.push.PushNotifier
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/** FCM 수신. 토큰과 메시지 내용은 로그에 남기지 않는다. */
class DqMessagingService : FirebaseMessagingService() {
    private val container get() = (application as DQApplication).container

    override fun onNewToken(token: String) {
        container.pushRegistrar.onNewToken(token)
    }

    // 앱이 포그라운드일 때(또는 data-only 메시지)만 호출된다. 백그라운드의 notification 메시지는 시스템이 직접 표시한다.
    override fun onMessageReceived(message: RemoteMessage) {
        if (!PushAccountGuard.shouldDisplay(container.session.state.value)) return
        container.unreadTracker.refresh()
        val data = message.data.filterKeys { it in PushIntents.KEYS }
        PushNotifier.show(
            this,
            title = message.notification?.title ?: message.data["title"],
            body = message.notification?.body ?: message.data["body"],
            data = data,
            dedupeKey = message.messageId ?: "${data["type"]}:${data["roomId"]}:${data["missionId"]}:${data["proofId"]}",
        )
    }
}

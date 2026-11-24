package com.dailyquest.app.data.push

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** google-services.json 없이 빌드돼 Firebase가 초기화되지 않았다면 null(푸시 비활성)을 돌려준다. */
class FirebaseTokenSource(private val context: Context) : PushTokenSource {
    override suspend fun currentToken(): String? {
        if (FirebaseApp.getApps(context).isEmpty()) return null
        return suspendCancellableCoroutine { cont ->
            runCatching {
                FirebaseMessaging.getInstance().token.addOnCompleteListener { t ->
                    if (cont.isActive) cont.resume(if (t.isSuccessful) t.result else null)
                }
            }.onFailure { if (cont.isActive) cont.resume(null) }
        }
    }
}

package com.dailyquest.app.data.push

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.dailyquest.app.MainActivity
import com.dailyquest.app.R

/** 푸시 알림 채널과 포그라운드 수신 알림 표시. 알림 권한이 없으면 아무것도 표시하지 않는다. */
object PushNotifier {
    const val CHANNEL_ID = "dq_default"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(CHANNEL_ID, "Daily Quest 알림", NotificationManager.IMPORTANCE_DEFAULT)
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    fun canPost(context: Context): Boolean =
        (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()

    /** 같은 [dedupeKey]의 알림은 하나로 합쳐 중복 표시를 막는다. */
    fun show(context: Context, title: String?, body: String?, data: Map<String, String>, dedupeKey: String) {
        if (title.isNullOrBlank() && body.isNullOrBlank()) return
        if (!canPost(context)) return
        ensureChannel(context)
        val id = dedupeKey.hashCode()
        val tap = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            PushIntents.KEYS.forEach { key -> data[key]?.let { putExtra(key, it) } }
        }
        val pending = PendingIntent.getActivity(context, id, tap, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(id, n) }
    }
}

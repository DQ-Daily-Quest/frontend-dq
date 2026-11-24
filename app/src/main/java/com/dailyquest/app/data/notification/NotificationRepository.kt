package com.dailyquest.app.data.notification

import com.dailyquest.app.data.remote.ApiResult
import com.dailyquest.app.data.remote.DqApi
import com.dailyquest.app.data.remote.apiCall

/** 알림 API 호출 계층. 전역 상태를 보관하지 않는다. */
class NotificationRepository(private val api: DqApi) {
    suspend fun list(unreadOnly: Boolean, page: Int = 0, size: Int = DEFAULT_PAGE_SIZE): ApiResult<NotificationPage> = apiCall {
        val r = api.listNotifications(unreadOnly, page, size)
        NotificationPage(r.content.map { it.toNotification() }, r.page, r.totalPages, r.totalElements)
    }

    suspend fun unreadCount(): ApiResult<Int> = apiCall { api.getUnreadCount().unreadCount.coerceIn(0, Int.MAX_VALUE.toLong()).toInt() }

    suspend fun markRead(id: Long): ApiResult<Unit> = apiCall { api.markNotificationRead(id) }

    suspend fun markAllRead(): ApiResult<Unit> = apiCall { api.markAllNotificationsRead() }

    companion object { const val DEFAULT_PAGE_SIZE = 20 }
}

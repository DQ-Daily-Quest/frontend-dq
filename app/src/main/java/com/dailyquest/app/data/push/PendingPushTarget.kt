package com.dailyquest.app.data.push

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 알림 탭으로 열려는 화면 경로를 한 번만 전달하는 보관소(메모리 전용).
 * 로그아웃 상태에서 탭했다면 소유자 없이 보관해 로그인한 계정에 전달하고, 로그인 상태에서 탭했다면 그 계정에만 전달한다.
 * 다른 계정으로 바뀐 뒤에는 이전 계정용 대상을 버린다. 접근 권한은 도착한 화면의 서버 응답(403/404)이 판단한다.
 */
class PendingPushTarget {
    private data class Entry(val route: String, val ownerUserId: Long?)

    private val lock = Any()
    private var entry: Entry? = null
    private val _signal = MutableStateFlow(0)

    /** 새 대상이 들어올 때마다 증가한다. 화면이 이미 열려 있어도 전달 시점을 알 수 있게 한다. */
    val signal: StateFlow<Int> = _signal.asStateFlow()

    fun post(route: String, ownerUserId: Long?) {
        synchronized(lock) { entry = Entry(route, ownerUserId) }
        _signal.value = _signal.value + 1
    }

    /** [userId] 계정에게 전달할 경로를 꺼내고 비운다. 소유자가 다르면 버리고 null. */
    fun take(userId: Long): String? = synchronized(lock) {
        val e = entry ?: return null
        entry = null
        e.route.takeIf { e.ownerUserId == null || e.ownerUserId == userId }
    }

    fun clear() = synchronized(lock) { entry = null }
}

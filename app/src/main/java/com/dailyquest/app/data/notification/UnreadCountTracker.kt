package com.dailyquest.app.data.notification

import com.dailyquest.app.data.auth.SessionState
import com.dailyquest.app.data.remote.ApiResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * 알림 배지의 단일 출처. 값은 항상 GET /notifications/unread-count 응답만 쓰고 앱에서 누적·계산하지 않는다.
 * 로그인(계정 변경 포함) 때와 호출자가 요청할 때(앱 복귀, 읽음 처리 뒤)만 조회하며 주기적 폴링은 하지 않는다.
 * 계정이 바뀌면 값을 0으로 비우고 이전 계정의 늦은 응답은 버린다.
 */
class UnreadCountTracker(
    private val repo: NotificationRepository,
    private val session: StateFlow<SessionState>,
    private val scope: CoroutineScope,
) {
    private val _count = MutableStateFlow(0)
    val count: StateFlow<Int> = _count.asStateFlow()

    private var generation = 0
    private var job: Job? = null

    init {
        scope.launch {
            session
                .filter { it is SessionState.Authenticated || it is SessionState.Unauthenticated }
                .map { (it as? SessionState.Authenticated)?.profile?.userId }
                .distinctUntilChanged()
                .collect { userId ->
                    generation++
                    job?.cancel()
                    _count.value = 0
                    if (userId != null) fetch()
                }
        }
    }

    /** 로그인된 상태에서만 서버 값을 다시 읽는다. 실패하면 이전 값을 그대로 둔다. */
    fun refresh() {
        if (session.value !is SessionState.Authenticated) return
        job?.cancel()
        fetch()
    }

    private fun fetch() {
        val gen = ++generation
        job = scope.launch {
            val r = repo.unreadCount()
            if (gen != generation) return@launch
            if (r is ApiResult.Success) _count.value = r.value
        }
    }
}

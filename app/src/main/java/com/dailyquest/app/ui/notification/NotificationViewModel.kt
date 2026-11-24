package com.dailyquest.app.ui.notification

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dailyquest.app.data.auth.AppContainer
import com.dailyquest.app.data.auth.AuthErrorMessages
import com.dailyquest.app.data.auth.SessionState
import com.dailyquest.app.data.notification.AppNotification
import com.dailyquest.app.data.notification.NotificationRepository
import com.dailyquest.app.data.remote.ApiErrorCode
import com.dailyquest.app.data.remote.ApiException
import com.dailyquest.app.data.remote.ApiResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class NotificationUiState(
    val userId: Long? = null,
    val unreadOnly: Boolean = false,
    val items: List<AppNotification> = emptyList(),
    val page: Int = 0,
    val hasNext: Boolean = false,
    val loaded: Boolean = false,
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val error: String? = null,
    val actionError: String? = null,
    val markingAll: Boolean = false,
    /** 읽음 처리 요청이 진행 중인 알림 ID. 같은 알림을 중복 요청하지 않는다. */
    val markingIds: Set<Long> = emptySet(),
)

/**
 * 알림함. 읽음 상태는 서버가 성공을 응답한 뒤에만 화면에 반영하고, 배지 숫자는 서버 값으로 다시 맞춘다.
 * 계정이 바뀌면 이전 목록과 진행 중 요청을 버리고, 필터·페이지가 바뀐 뒤 도착한 늦은 응답은 무시한다.
 */
class NotificationViewModel(
    private val repo: NotificationRepository,
    session: StateFlow<SessionState>,
    private val onUnreadChanged: () -> Unit = {},
) : ViewModel() {

    private val _ui = MutableStateFlow(NotificationUiState())
    val ui: StateFlow<NotificationUiState> = _ui.asStateFlow()

    private var job: Job? = null
    private var generation = 0

    init {
        viewModelScope.launch {
            session
                .filter { it is SessionState.Authenticated || it is SessionState.Unauthenticated }
                .map { (it as? SessionState.Authenticated)?.profile?.userId }
                .distinctUntilChanged()
                .collect { userId ->
                    generation++
                    job?.cancel()
                    _ui.value = NotificationUiState(userId = userId)
                    if (userId != null) load(0, keep = false)
                }
        }
    }

    fun retry() = load(0, keep = false)
    fun refresh() = load(0, keep = true)

    fun setUnreadOnly(value: Boolean) {
        if (_ui.value.unreadOnly == value || _ui.value.userId == null) return
        _ui.update { it.copy(unreadOnly = value, items = emptyList(), loaded = false, hasNext = false, page = 0) }
        load(0, keep = false)
    }

    fun loadMore() {
        val s = _ui.value
        if (!s.hasNext || s.loading || s.loadingMore) return
        load(s.page + 1, keep = true)
    }

    private fun load(page: Int, keep: Boolean) {
        val s = _ui.value
        if (s.userId == null) return
        val gen = ++generation
        job?.cancel()
        _ui.update { it.copy(loading = page == 0, loadingMore = page > 0, error = null, items = if (keep) it.items else emptyList()) }
        job = viewModelScope.launch {
            val r = repo.list(s.unreadOnly, page)
            if (gen != generation) return@launch
            _ui.update {
                when (r) {
                    is ApiResult.Success -> it.copy(
                        items = if (page == 0) r.value.items else (it.items + r.value.items).distinctBy { n -> n.id },
                        page = r.value.page, hasNext = r.value.hasNext, loaded = true, loading = false, loadingMore = false,
                    )
                    is ApiResult.Failure -> it.copy(loading = false, loadingMore = false, error = AuthErrorMessages.forApi(r.error))
                }
            }
            if (r is ApiResult.Success && page == 0) onUnreadChanged()
        }
    }

    /** 알림을 눌렀을 때 호출한다. 이미 읽었거나 요청 중이면 아무것도 보내지 않는다. 이동은 읽음 결과와 무관하게 호출 쪽에서 한다. */
    fun markRead(id: Long) {
        val s = _ui.value
        val n = s.items.firstOrNull { it.id == id } ?: return
        if (n.isRead || id in s.markingIds || s.userId == null) return
        val gen = generation
        _ui.update { it.copy(markingIds = it.markingIds + id, actionError = null) }
        viewModelScope.launch {
            val r = repo.markRead(id)
            if (gen != generation) return@launch
            _ui.update { cur ->
                val done = cur.copy(markingIds = cur.markingIds - id)
                when {
                    r is ApiResult.Success -> done.copy(items = done.items.map { if (it.id == id) it.copy(isRead = true) else it })
                    r is ApiResult.Failure && (r.error as? ApiException.Http)?.code == ApiErrorCode.NOTIFICATION_NOT_FOUND ->
                        done.copy(items = done.items.filterNot { it.id == id }, actionError = "이미 삭제되었거나 확인할 수 없는 알림이에요.")
                    else -> done.copy(actionError = "읽음 처리하지 못했어요. 잠시 후 다시 시도해 주세요.")
                }
            }
            onUnreadChanged()
        }
    }

    fun markAllRead() {
        val s = _ui.value
        if (s.markingAll || s.userId == null) return
        val gen = generation
        _ui.update { it.copy(markingAll = true, actionError = null) }
        viewModelScope.launch {
            val r = repo.markAllRead()
            if (gen != generation) return@launch
            when (r) {
                is ApiResult.Success -> {
                    _ui.update { it.copy(markingAll = false, items = it.items.map { n -> n.copy(isRead = true) }) }
                    // 다른 기기에서 생긴 변화까지 반영하도록 서버 기준으로 다시 읽는다.
                    load(0, keep = true)
                }
                is ApiResult.Failure -> _ui.update { it.copy(markingAll = false, actionError = "모두 읽음 처리하지 못했어요. 잠시 후 다시 시도해 주세요.") }
            }
            onUnreadChanged()
        }
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            NotificationViewModel(container.notificationRepository, container.session.state) { container.unreadTracker.refresh() } as T
    }
}

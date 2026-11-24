package com.dailyquest.app.data.push

import com.dailyquest.app.data.auth.SessionState
import com.dailyquest.app.data.notification.NotificationRepository
import com.dailyquest.app.data.remote.ApiResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** 현재 기기의 FCM 토큰을 가져오는 원천. Firebase 설정이 없으면 null을 돌려준다. */
interface PushTokenSource {
    suspend fun currentToken(): String?
}

object NoPushTokenSource : PushTokenSource {
    override suspend fun currentToken(): String? = null
}

/**
 * FCM 토큰을 로그인한 계정에 등록한다. 서버는 PUT /users/me/fcm-token 으로 사용자당 토큰 하나를 덮어쓰는 멱등 요청이며 삭제 API는 없다.
 * - 등록 성공 여부는 (userId, token) 쌍으로만 메모리에 기억해 다른 계정과 섞지 않는다. 토큰은 저장·로그 출력하지 않는다.
 * - 로그인·계정 변경·새 토큰 수신·앱 복귀([sync]) 때 등록하고, 실패하면 다음 시점에 다시 시도한다(자동 반복 없음).
 * - 로그아웃하면 등록 기록을 비워 다음 로그인(같은 계정 포함)에서 다시 등록한다.
 */
class PushTokenRegistrar(
    private val repo: NotificationRepository,
    session: StateFlow<SessionState>,
    private val source: PushTokenSource,
    private val scope: CoroutineScope,
) {
    private val sessionState = session
    private var latestToken: String? = null
    private var registered: Pair<Long, String>? = null
    private var generation = 0
    private var job: Job? = null

    init {
        scope.launch {
            session
                .filter { it is SessionState.Authenticated || it is SessionState.Unauthenticated }
                .map { (it as? SessionState.Authenticated)?.profile?.userId }
                .distinctUntilChanged()
                .collect {
                    generation++
                    job?.cancel()
                    registered = null
                    sync()
                }
        }
    }

    fun onNewToken(token: String) {
        if (token.isBlank()) return
        latestToken = token
        sync()
    }

    /** 로그인된 상태에서만 동작한다. 같은 계정·같은 토큰이 이미 등록됐으면 요청하지 않는다. */
    fun sync() {
        val userId = (sessionState.value as? SessionState.Authenticated)?.profile?.userId ?: return
        val gen = ++generation
        job?.cancel()
        job = scope.launch {
            val token = (latestToken ?: source.currentToken())?.takeIf { it.isNotBlank() } ?: return@launch
            if (gen != generation) return@launch
            latestToken = token
            if (registered == userId to token) return@launch
            val r = repo.registerFcmToken(token)
            if (gen != generation) return@launch
            if (r is ApiResult.Success) registered = userId to token
        }
    }
}

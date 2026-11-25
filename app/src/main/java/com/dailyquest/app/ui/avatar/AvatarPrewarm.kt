package com.dailyquest.app.ui.avatar

import android.os.Looper
import android.os.MessageQueue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dailyquest.app.data.auth.AppContainer
import com.dailyquest.app.data.auth.SessionState
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * 3D 아바타 미리 준비 (아바타 속도 2-1단계). 로그인한 뒤 첫 화면이 다 그려지고 화면 스레드가 쉴 때 두 가지를 미리 한다.
 *
 * 1. **내 착용 정보 받기** — 인터넷 요청이라 화면을 멈추지 않는다. 받아 두면 상점을 처음 열 때도 서버를 기다리지 않는다(1단계 기억).
 * 2. **3D 엔진 만들기**([AvatarEngine]) — 화면 스레드에서만 만들 수 있어 잠깐 멈춘다(에뮬레이터 약 0.7~2초). 상점을 누를 때가 아니라
 *    홈을 보고 있을 때 멈추게 하려는 것이다. 이미 만들어졌으면(얼굴 공장이 먼저 만든 경우) 아무것도 하지 않는다.
 *
 * 로그아웃 상태(로그인 화면)에서는 하지 않는다. 계정이 바뀌면 착용 정보만 다시 받는다(엔진은 하나).
 * UI 테스트 환경(3D 끔)에서는 이 부품을 두지 않는다.
 */
@Composable
fun AvatarPrewarm(container: AppContainer) {
    val context = LocalContext.current
    AvatarPrewarm(
        session = container.session.state,
        prefetchMyAvatar = { userId -> if (container.myAvatarRepository.cached(userId) == null) container.myAvatarRepository.load() },
        createEngine = { AvatarEngine.get(context) },
    )
}

/**
 * [AvatarPrewarm]의 실제 동작. 테스트에서 가짜를 넣을 수 있게 나눴다.
 * @param prefetchMyAvatar 그 계정의 착용 정보를 받아 기억한다(이미 기억한 것이 있으면 받지 않는다).
 * @param createEngine 3D 엔진을 만든다(화면 스레드). 이미 있으면 아무것도 하지 않아야 한다.
 */
@Composable
internal fun AvatarPrewarm(
    session: StateFlow<SessionState>,
    prefetchMyAvatar: suspend (userId: Long) -> Unit,
    createEngine: () -> Unit,
    delayMs: Long = PREWARM_DELAY_MS,
) {
    val state by session.collectAsStateWithLifecycle()
    val userId = (state as? SessionState.Authenticated)?.profile?.userId
    LaunchedEffect(userId) {
        if (userId == null) return@LaunchedEffect
        // 첫 화면이 다 그려진 뒤 잠시 기다렸다가, 화면 스레드가 할 일이 없을 때 시작한다.
        delay(delayMs)
        awaitMainThreadIdle()
        launch { prefetchMyAvatar(userId) }
        awaitMainThreadIdle()
        createEngine()
    }
}

/** 화면 스레드가 처리할 일을 다 끝내고 쉬는 순간까지 기다린다. 상점 이미지 미리 받기([com.dailyquest.app.ui.shop.ShopImagePrefetch])도 쓴다. */
internal suspend fun awaitMainThreadIdle() = suspendCancellableCoroutine { cont ->
    val queue = Looper.getMainLooper().queue
    // false를 돌려주면 한 번 부른 뒤 스스로 빠진다.
    val handler = MessageQueue.IdleHandler { if (cont.isActive) cont.resume(Unit); false }
    queue.addIdleHandler(handler)
    cont.invokeOnCancellation { queue.removeIdleHandler(handler) }
}

/** 첫 화면이 뜨고 나서 미리 준비를 시작하기까지 기다리는 시간. 로그인 직후 홈을 그리는 일과 겹치지 않게. */
internal const val PREWARM_DELAY_MS = 1_500L

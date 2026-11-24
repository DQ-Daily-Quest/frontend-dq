package com.dailyquest.app.data.push

import com.dailyquest.app.data.auth.SessionState

/**
 * 서버에 FCM 토큰 삭제 API가 없어 로그아웃 뒤에도 이전 계정의 푸시가 이 기기로 올 수 있다.
 * 로그아웃 상태에서는 푸시를 표시하지 않고, 로그아웃·계정 전환 때 이미 떠 있는 알림을 지운다.
 */
object PushAccountGuard {
    fun shouldDisplay(state: SessionState): Boolean = state !is SessionState.Unauthenticated

    fun shouldClearTray(previous: SessionState, current: SessionState): Boolean = when (previous) {
        is SessionState.Authenticated -> when (current) {
            is SessionState.Unauthenticated -> true
            is SessionState.Authenticated -> previous.profile.userId != current.profile.userId
            else -> false
        }
        else -> false
    }
}

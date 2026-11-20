package com.dailyquest.app.data.proof

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 인증 제출·투표·앱 복귀처럼 서버 상태가 바뀌었을 수 있는 시점에 미션 목록·상세와 내 코인 잔액을 다시 조회하게 하는 신호.
 * 각 화면의 ViewModel은 [version]이 바뀌면 자기 데이터를 서버에서 다시 읽는다. 코인은 절대 앱에서 계산하지 않고 항상 /users/me 값만 쓴다.
 * 요청한 화면이 사라져도 갱신이 이어지도록 ViewModel이 아닌 앱 범위 [scope]에서 실행한다.
 */
class ProofSync(
    private val scope: CoroutineScope,
    private val refreshProfile: suspend () -> Unit,
) {
    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version.asStateFlow()

    /** 목록·상세를 다시 조회하게 하고, [refreshCoins]면 내 프로필(코인·스트릭)도 다시 조회한다. 실패는 호출 쪽 결과에 영향을 주지 않는다. */
    fun notifyChanged(refreshCoins: Boolean = true) {
        _version.value += 1
        if (refreshCoins) scope.launch { runCatching { refreshProfile() } }
    }
}

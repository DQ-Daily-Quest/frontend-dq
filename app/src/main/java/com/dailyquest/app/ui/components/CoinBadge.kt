package com.dailyquest.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dailyquest.app.data.auth.SessionState
import com.dailyquest.app.navigation.LocalNav
import com.dailyquest.app.navigation.Routes
import com.dailyquest.app.ui.auth.LocalAuth

/**
 * 홈·미션·쇼핑·마이 공통 코인 표시. 값은 세션(/users/me)의 실제 잔액 하나만 읽고, 누르면 코인 이용 내역으로 이동한다.
 * 화면별로 별도 잔액 상태를 두지 않으므로 보상·구매 뒤 모든 화면이 같은 값을 보여 준다.
 */
@Composable
fun SessionCoinPill(tag: String, modifier: Modifier = Modifier) {
    val nav = LocalNav.current
    val session by LocalAuth.current.session.collectAsStateWithLifecycle()
    val coins = (session as? SessionState.Authenticated)?.profile?.coins ?: 0
    val open = { nav.navigate(Routes.COIN_HISTORY) { launchSingleTop = true } }
    CoinPill(amount = coins, modifier = modifier.testTag(tag).clickable { open() }, onPlus = { open() })
}

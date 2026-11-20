package com.dailyquest.app.data.auth

import com.dailyquest.app.data.auth.social.SocialAuthClient
import com.dailyquest.app.data.auth.social.SocialProvider
import com.dailyquest.app.data.customization.CustomizationData
import com.dailyquest.app.data.mock.MockRoomData
import com.dailyquest.app.data.remote.DqApi
import com.dailyquest.app.data.remote.NetworkModule
import com.dailyquest.app.navigation.Routes

/** 수동 DI 컨테이너. 테스트에서는 [DQApplication.createContainer]를 오버라이드해 가짜 저장소·API를 주입한다. */
class AppContainer(
    tokenStore: TokenStore,
    apiFactory: ((SessionTokens, () -> Unit) -> DqApi)? = null,
    /** 로그인 직후 진입 화면. 기본은 홈이며 계측 테스트만 개발용 Catalog로 바꾼다. */
    val startAfterAuth: String = Routes.HOME,
    val socialClients: Map<SocialProvider, SocialAuthClient> = emptyMap(),
) {
    val tokens = SessionTokens(tokenStore)
    val session = SessionManager(
        tokens,
        cleaners = listOf({ MockRoomData.reset() }, { CustomizationData.reset() }),
    )
    val api: DqApi = (apiFactory ?: { t, onExpired -> NetworkModule.createApi(t, onExpired) })(tokens) {
        session.expire(LogoutReason.EXPIRED)
    }
    val authRepository = AuthRepository(api, tokens, session, socialClients)
}

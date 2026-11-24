package com.dailyquest.app.data.auth

import com.dailyquest.app.data.auth.social.SocialAuthClient
import com.dailyquest.app.data.auth.social.SocialProvider
import com.dailyquest.app.data.customization.CustomizationData
import com.dailyquest.app.data.shop.InventoryRepository
import com.dailyquest.app.data.shop.ShopRepository
import com.dailyquest.app.data.remote.DqApi
import com.dailyquest.app.data.mission.MissionRepository
import com.dailyquest.app.data.profile.ProfileRepository
import com.dailyquest.app.data.proof.ProofRepository
import com.dailyquest.app.data.proof.ProofSync
import com.dailyquest.app.data.proof.UploadRepository
import com.dailyquest.app.data.room.RoomRepository
import com.dailyquest.app.data.remote.NetworkModule
import com.dailyquest.app.navigation.Routes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

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
        cleaners = listOf({ CustomizationData.reset() }),
    )
    val api: DqApi = (apiFactory ?: { t, onExpired -> NetworkModule.createApi(t, onExpired) })(tokens) {
        session.expire(LogoutReason.EXPIRED)
    }
    val authRepository = AuthRepository(api, tokens, session, socialClients)
    val passwordResetRepository = PasswordResetRepository(api)
    val profileRepository = ProfileRepository(api, session, authRepository)
    val roomRepository = RoomRepository(api)
    val missionRepository = MissionRepository(api)
    val proofRepository = ProofRepository(api)
    val uploadRepository = UploadRepository(api)
    val shopRepository = ShopRepository(api)
    val inventoryRepository = InventoryRepository(api)

    /** 화면 수명과 무관하게 이어져야 하는 갱신(코인 잔액 등)을 위한 앱 범위. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val proofSync = ProofSync(appScope) {
        if (session.state.value is SessionState.Authenticated) authRepository.refreshProfile()
    }
}

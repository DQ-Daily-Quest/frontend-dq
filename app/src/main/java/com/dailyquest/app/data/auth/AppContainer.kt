package com.dailyquest.app.data.auth

import com.dailyquest.app.data.auth.social.SocialAuthClient
import com.dailyquest.app.data.auth.social.SocialProvider
import com.dailyquest.app.data.customization.CustomizationData
import com.dailyquest.app.data.shop.InventoryRepository
import com.dailyquest.app.data.avatar.AvatarFaceRepository
import com.dailyquest.app.data.avatar.MyAvatarRepository
import com.dailyquest.app.data.avatar.RoomMemberFaceRepository
import com.dailyquest.app.data.decor.DecorRepository
import com.dailyquest.app.ui.decor.DecorImageSource
import com.dailyquest.app.data.shop.ShopRepository
import com.dailyquest.app.data.remote.DqApi
import com.dailyquest.app.data.mission.MissionRepository
import com.dailyquest.app.data.notification.NotificationRepository
import com.dailyquest.app.data.notification.UnreadCountTracker
import com.dailyquest.app.data.profile.ProfileRepository
import com.dailyquest.app.data.proof.ProofRepository
import com.dailyquest.app.data.proof.ProofSync
import com.dailyquest.app.data.proof.UploadRepository
import com.dailyquest.app.data.room.RoomRepository
import com.dailyquest.app.data.remote.NetworkModule
import com.dailyquest.app.navigation.Routes
import kotlinx.coroutines.CoroutineScope
import com.dailyquest.app.data.push.NoPushTokenSource
import com.dailyquest.app.data.push.PendingPushTarget
import com.dailyquest.app.data.push.PushTokenRegistrar
import com.dailyquest.app.data.push.PushTokenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** 수동 DI 컨테이너. 테스트에서는 [DQApplication.createContainer]를 오버라이드해 가짜 저장소·API를 주입한다. */
class AppContainer(
    tokenStore: TokenStore,
    apiFactory: ((SessionTokens, () -> Unit) -> DqApi)? = null,
    /** 로그인 직후 진입 화면. 기본은 홈이며 계측 테스트만 개발용 Catalog로 바꾼다. */
    val startAfterAuth: String = Routes.HOME,
    val socialClients: Map<SocialProvider, SocialAuthClient> = emptyMap(),
    pushTokenSource: PushTokenSource = NoPushTokenSource,
    /**
     * 아바타를 3D로 그릴지 (STEP 9). UI 테스트만 false로 둔다. 3D 화면은 매 프레임 계속 그려서
     * "화면이 멈출 때까지 기다리는" Compose UI 테스트가 시간 초과로 실패하기 때문이다. false면 같은 자리에 빈 상자를 그린다.
     */
    val avatar3dEnabled: Boolean = true,
    /**
     * 방·가구 그림을 가져오는 곳 (STEP 10). null이면 S3에서 받아 기기에 저장하는 기본 캐시를 쓴다.
     * UI 테스트는 인터넷 없이 돌도록 빈 그림을 주는 가짜를 넣는다.
     */
    val decorImages: DecorImageSource? = null,
) {
    val tokens = SessionTokens(tokenStore)
    val session = SessionManager(
        tokens,
        // 계정이 바뀌면 이전 계정의 방 멤버 목록(홈 지도 얼굴)을 비운다.
        cleaners = listOf({ CustomizationData.reset() }, { roomMemberFaceRepository.clear() }),
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
    val notificationRepository = NotificationRepository(api)
    val myAvatarRepository = MyAvatarRepository(api, session)
    val decorRepository = DecorRepository(api)
    /** 다른 사람 아바타(아이템 ID) → 얼굴용 3D 주소. 아이템 주소를 앱이 켜진 동안 기억한다(프로필 얼굴). */
    val avatarFaceRepository = AvatarFaceRepository(api)
    /** 홈 지도에 보이는 방들의 멤버 목록(프로필 얼굴 6단계). */
    val roomMemberFaceRepository = RoomMemberFaceRepository(roomRepository)

    /** 화면 수명과 무관하게 이어져야 하는 갱신(코인 잔액 등)을 위한 앱 범위. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val unreadTracker = UnreadCountTracker(notificationRepository, session.state, appScope)
    val pushRegistrar = PushTokenRegistrar(notificationRepository, session.state, pushTokenSource, appScope)
    val pendingPushTarget = PendingPushTarget()
    val proofSync = ProofSync(appScope) {
        if (session.state.value is SessionState.Authenticated) authRepository.refreshProfile()
    }
}

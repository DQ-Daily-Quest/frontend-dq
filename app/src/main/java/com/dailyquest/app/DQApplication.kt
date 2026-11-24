package com.dailyquest.app

import android.app.Application
import com.dailyquest.app.data.auth.AppContainer
import com.dailyquest.app.data.auth.KeystoreTokenStore
import com.dailyquest.app.data.auth.social.GoogleAuthClient
import com.dailyquest.app.data.auth.social.KakaoAuthClient
import com.dailyquest.app.data.auth.social.NotConfiguredSocialClient
import com.dailyquest.app.data.auth.social.SocialProvider
import com.dailyquest.app.data.push.FirebaseTokenSource
import com.dailyquest.app.data.push.PushNotifier
import com.kakao.sdk.common.KakaoSdk

open class DQApplication : Application() {
    /** 로그인 후 알림 권한(Android 13+)을 요청할지. 계측 테스트는 시스템 대화상자를 막기 위해 끈다. */
    open val promptsNotificationPermission: Boolean = true

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        PushNotifier.ensureChannel(this)
        container = createContainer()
    }

    protected open fun createContainer(): AppContainer {
        // 키가 주입되지 않은 빌드에서도 이메일 로그인은 그대로 동작해야 한다.
        val kakaoKey = BuildConfig.KAKAO_NATIVE_APP_KEY
        if (kakaoKey.isNotBlank()) KakaoSdk.init(this, kakaoKey)
        val googleId = BuildConfig.GOOGLE_SERVER_CLIENT_ID
        return AppContainer(
            KeystoreTokenStore(this),
            socialClients = mapOf(
                SocialProvider.GOOGLE to
                    if (googleId.isNotBlank()) GoogleAuthClient(googleId, this) else NotConfiguredSocialClient,
                SocialProvider.KAKAO to
                    if (kakaoKey.isNotBlank()) KakaoAuthClient(kakaoKey) else NotConfiguredSocialClient,
            ),
            pushTokenSource = FirebaseTokenSource(this),
        )
    }
}

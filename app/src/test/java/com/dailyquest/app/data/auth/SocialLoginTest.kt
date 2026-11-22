package com.dailyquest.app.data.auth

import android.content.Context
import android.content.ContextWrapper
import com.dailyquest.app.data.auth.social.SocialAuthClient
import com.dailyquest.app.data.auth.social.SocialProvider
import com.dailyquest.app.data.auth.social.SocialSignInResult
import com.dailyquest.app.data.remote.ApiException
import com.dailyquest.app.data.remote.ApiResult
import com.dailyquest.app.data.remote.NetworkModule
import com.dailyquest.app.data.remote.dto.OAuthLoginRequest
import com.dailyquest.app.navigation.Routes
import com.dailyquest.app.ui.auth.AuthViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

private class FakeSocialClient(var result: SocialSignInResult = SocialSignInResult.Cancelled) : SocialAuthClient {
    var signIns = 0
    var signOuts = 0
    var gate: CompletableDeferred<Unit>? = null
    override suspend fun signIn(context: Context): SocialSignInResult {
        signIns++
        gate?.await()
        return result
    }
    override suspend fun signOut() { signOuts++ }
}

@OptIn(ExperimentalCoroutinesApi::class)
class SocialLoginTest {
    private lateinit var server: MockWebServer
    private lateinit var tokenStore: InMemoryTokenStore
    private lateinit var google: FakeSocialClient
    private lateinit var kakao: FakeSocialClient
    private lateinit var container: AppContainer
    private val ctx: Context = ContextWrapper(null)
    private val testDispatcher = StandardTestDispatcher()
    private val seen = mutableListOf<String>()

    private fun me(email: String?) = """{"userId":7,"email":${email?.let { "\"$it\"" }},"nickname":"n","coins":0,"gender":${meGender?.let { "\"$it\"" }},"avatar":{},"streak":{"current":0,"longest":0},"stats":{"roomCount":0,"completedMissionCount":0},"createdAt":"2025-01-01T00:00:00Z"}"""
    private fun tokenBody(isNew: Boolean) =
        """{"accessToken":"A","refreshToken":"R","expiresIn":1800,"user":{"userId":7,"nickname":"n","avatarUrl":null,"coins":0},"isNewUser":$isNew}"""
    private var oauthResponse: () -> MockResponse = { MockResponse().setBody(tokenBody(false)).setHeader("Content-Type", "application/json") }
    private var meEmail: String? = null
    /** /users/me의 gender (STEP 9-9). 기본은 null(캐릭터 없음) */
    private var meGender: String? = null

    @Before fun setUp() {
        Dispatchers.setMain(testDispatcher)
        server = MockWebServer().also { it.start() }
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                seen += request.path!!
                return when {
                    request.path!!.contains("auth/oauth/") -> oauthResponse()
                    request.path!!.endsWith("users/me") -> MockResponse().setBody(me(meEmail)).setHeader("Content-Type", "application/json")
                    else -> MockResponse().setResponseCode(204)
                }
            }
        }
        tokenStore = InMemoryTokenStore()
        google = FakeSocialClient(); kakao = FakeSocialClient()
        container = AppContainer(
            tokenStore,
            apiFactory = { t, onExpired -> NetworkModule.createApi(t, onExpired, server.url("/api/v1/").toString()) },
            socialClients = mapOf(SocialProvider.GOOGLE to google, SocialProvider.KAKAO to kakao),
        )
    }

    @After fun tearDown() { server.shutdown(); Dispatchers.resetMain() }

    @Test fun requestSerializesOnlyIdTokenAndMasksToString() {
        val body = Json.encodeToString(OAuthLoginRequest.serializer(), OAuthLoginRequest("tok"))
        assertEquals("""{"idToken":"tok"}""", body)
        assertFalse(OAuthLoginRequest("tok").toString().contains("tok"))
        assertFalse(SocialSignInResult.Token("secret").toString().contains("secret"))
    }

    @Test fun socialLoginStoresServiceJwtOnlyAndAuthenticatesWithNullEmail() = runBlocking {
        meEmail = null
        meGender = null
        val r = container.authRepository.socialLogin(SocialProvider.KAKAO, "kakao-id-token")
        assertTrue(r is ApiResult.Success)
        assertEquals("A", tokenStore.read()?.accessToken)
        val s = container.session.state.value as SessionState.Authenticated
        assertNull(s.profile.email)
        assertTrue(seen.any { it.endsWith("auth/oauth/kakao") })
        assertFalse(container.session.consumeAvatarSetup())
    }

    @Test fun newUserRequestsAvatarSetupOnce() = runBlocking {
        oauthResponse = { MockResponse().setBody(tokenBody(true)).setHeader("Content-Type", "application/json") }
        container.authRepository.socialLogin(SocialProvider.GOOGLE, "t")
        assertTrue(container.session.consumeAvatarSetup())
        assertFalse(container.session.consumeAvatarSetup())
    }

    @Test fun invalidOAuthTokenIsReportedAndLeavesNoSession() = runBlocking {
        oauthResponse = { MockResponse().setResponseCode(401).setHeader("Content-Type", "application/json").setBody("""{"code":"INVALID_OAUTH_TOKEN","message":"m"}""") }
        val r = container.authRepository.socialLogin(SocialProvider.GOOGLE, "bad") as ApiResult.Failure
        assertEquals("INVALID_OAUTH_TOKEN", (r.error as ApiException.Http).code)
        assertNull(tokenStore.read())
        assertFalse(container.session.state.value is SessionState.Authenticated)
    }

    @Test fun logoutClearsSdkStateWithoutUnlink() = runBlocking {
        container.authRepository.socialLogin(SocialProvider.GOOGLE, "t")
        container.authRepository.logout()
        assertEquals(1, google.signOuts); assertEquals(1, kakao.signOuts)
        assertNull(tokenStore.read())
        assertTrue(container.session.state.value is SessionState.Unauthenticated)
    }

    @Test fun sdkFailuresMapToMessagesAndCancelIsSilent() {
        val g = SocialProvider.GOOGLE; val k = SocialProvider.KAKAO
        assertNull(SocialErrorMessages.forResult(g, SocialSignInResult.Cancelled))
        for (r in listOf(SocialSignInResult.NoAccount, SocialSignInResult.Network, SocialSignInResult.MissingIdToken, SocialSignInResult.NotConfigured, SocialSignInResult.Failed))
            assertNotNull(SocialErrorMessages.forResult(k, r))
        assertTrue(SocialErrorMessages.forResult(k, SocialSignInResult.MissingIdToken)!!.contains("ID"))
    }

    @Test fun viewModelCancelDoesNotCallBackendOrShowError() = runBlocking {
        val vm = AuthViewModel(container)
        google.result = SocialSignInResult.Cancelled
        vm.socialLogin(SocialProvider.GOOGLE, ctx)
        advanceUntilIdleMain()
        assertFalse(vm.ui.value.loading); assertNull(vm.ui.value.error)
        assertTrue(seen.none { it.contains("oauth") })
    }

    @Test fun viewModelMissingIdTokenShowsErrorAndSkipsBackend() = runBlocking {
        val vm = AuthViewModel(container)
        kakao.result = SocialSignInResult.MissingIdToken
        vm.socialLogin(SocialProvider.KAKAO, ctx)
        advanceUntilIdleMain()
        assertNotNull(vm.ui.value.error); assertFalse(vm.ui.value.loading)
        assertTrue(seen.none { it.contains("oauth") })
    }

    @Test fun viewModelIgnoresDuplicateRequestsWhileInProgress() = runBlocking {
        val vm = AuthViewModel(container)
        google.gate = CompletableDeferred()
        google.result = SocialSignInResult.Cancelled
        vm.socialLogin(SocialProvider.GOOGLE, ctx)
        advanceUntilIdleMain()
        assertTrue(vm.ui.value.loading)
        vm.socialLogin(SocialProvider.GOOGLE, ctx)
        vm.socialLogin(SocialProvider.KAKAO, ctx)
        advanceUntilIdleMain()
        google.gate!!.complete(Unit)
        advanceUntilIdleMain()
        assertEquals(1, google.signIns); assertEquals(0, kakao.signIns)
        assertFalse(vm.ui.value.loading)
    }

    /** STEP 9-9: 캐릭터(성별)는 필수라서, 만들기 전까지는 로그인할 때마다 캐릭터 만들기 화면으로 간다. */
    @Test fun viewModelNewUserWithoutCharacterAlwaysRoutesToAvatar() = runBlocking {
        val vm = AuthViewModel(container)
        oauthResponse = { MockResponse().setBody(tokenBody(true)).setHeader("Content-Type", "application/json") }
        container.authRepository.socialLogin(SocialProvider.GOOGLE, "t")
        assertEquals(Routes.SIGNUP_AVATAR, vm.consumePostLoginRoute())
        assertEquals(Routes.SIGNUP_AVATAR, vm.consumePostLoginRoute())
    }

    /** 신규 가입 표시가 있으면 한 번은 캐릭터 화면으로, 캐릭터가 이미 있으면 그다음엔 홈으로 간다(기존 동작 유지). */
    @Test fun viewModelNewUserRoutesToAvatarThenHome() = runBlocking {
        meGender = "FEMALE"
        val vm = AuthViewModel(container)
        oauthResponse = { MockResponse().setBody(tokenBody(true)).setHeader("Content-Type", "application/json") }
        container.authRepository.socialLogin(SocialProvider.GOOGLE, "t")
        assertEquals(Routes.SIGNUP_AVATAR, vm.consumePostLoginRoute())
        assertEquals(Routes.HOME, vm.consumePostLoginRoute())
    }

    /** STEP 9-9: 캐릭터가 있는 기존 계정은 바로 홈으로 간다. */
    @Test fun viewModelExistingUserWithCharacterGoesHome() = runBlocking {
        meGender = "MALE"
        val vm = AuthViewModel(container)
        oauthResponse = { MockResponse().setBody(tokenBody(false)).setHeader("Content-Type", "application/json") }
        container.authRepository.socialLogin(SocialProvider.GOOGLE, "t")
        assertEquals(Routes.HOME, vm.consumePostLoginRoute())
    }

    @Test fun viewModelBackendNetworkErrorShowsMessage() = runBlocking {
        val vm = AuthViewModel(container)
        oauthResponse = { MockResponse().setSocketPolicy(okhttp3.mockwebserver.SocketPolicy.DISCONNECT_AT_START) }
        google.result = SocialSignInResult.Token("t")
        vm.socialLogin(SocialProvider.GOOGLE, ctx)
        advanceUntilIdleMain()
        // 실제 소켓 I/O는 다른 스레드에서 진행되므로 완료까지 대기
        val end = System.currentTimeMillis() + 5000
        while (vm.ui.value.loading && System.currentTimeMillis() < end) { Thread.sleep(50); advanceUntilIdleMain() }
        assertNotNull(vm.ui.value.error); assertFalse(vm.ui.value.loading)
        assertNull(tokenStore.read())
    }

    private fun advanceUntilIdleMain() = testDispatcher.scheduler.advanceUntilIdle()
}

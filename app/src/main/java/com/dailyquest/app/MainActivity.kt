package com.dailyquest.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.dailyquest.app.data.auth.AppContainer
import com.dailyquest.app.data.auth.SessionState
import com.dailyquest.app.data.push.PushIntents
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelProvider
import com.dailyquest.app.navigation.DQNavGraph
import com.dailyquest.app.ui.avatar.AvatarPrewarm
import com.dailyquest.app.ui.avatar.FaceStudioHost
import com.dailyquest.app.ui.avatar.FaceStudios
import com.dailyquest.app.ui.auth.AuthViewModel
import com.dailyquest.app.ui.auth.LocalAuth
import com.dailyquest.app.ui.mission.LocalMyMissions
import com.dailyquest.app.ui.mission.MyMissionsViewModel
import com.dailyquest.app.ui.room.LocalAppContainer
import com.dailyquest.app.ui.room.LocalMyRooms
import com.dailyquest.app.ui.room.LocalPublicRooms
import com.dailyquest.app.ui.room.MyRoomsViewModel
import com.dailyquest.app.ui.room.PublicRoomsViewModel
import com.dailyquest.app.ui.shop.InventoryViewModel
import com.dailyquest.app.ui.shop.LocalInventory
import com.dailyquest.app.ui.shop.LocalShop
import com.dailyquest.app.ui.shop.ShopViewModel
import com.dailyquest.app.ui.theme.DQColors
import com.dailyquest.app.ui.theme.DailyQuestTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as DQApplication).container
        // 화면 회전 등으로 다시 만들어질 때는 이미 처리한 실행 Intent를 다시 열지 않는다.
        if (savedInstanceState == null) postPushTarget(intent)
        if ((application as DQApplication).promptsNotificationPermission) requestNotificationPermissionOnce(container)
        val authViewModel = ViewModelProvider(this, AuthViewModel.Factory(container))[AuthViewModel::class.java]
        val publicRooms = ViewModelProvider(this, PublicRoomsViewModel.Factory(container))[PublicRoomsViewModel::class.java]
        val myRooms = ViewModelProvider(this, MyRoomsViewModel.Factory(container))[MyRoomsViewModel::class.java]
        val myMissions = ViewModelProvider(this, MyMissionsViewModel.Factory(container))[MyMissionsViewModel::class.java]
        val shop = ViewModelProvider(this, ShopViewModel.Factory(container))[ShopViewModel::class.java]
        val inventory = ViewModelProvider(this, InventoryViewModel.Factory(container))[InventoryViewModel::class.java]
        // 다른 계정에서 인증이 승인돼 코인이 바뀌었을 수 있으니, 앱으로 돌아오면 사용자 정보와 미션 목록을 다시 읽는다.
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            private var resumedOnce = false
            override fun onResume(owner: LifecycleOwner) {
                container.unreadTracker.refresh()
                container.pushRegistrar.sync()
                if (resumedOnce) container.proofSync.notifyChanged() else resumedOnce = true
            }
        })
        setContent {
            DailyQuestTheme {
                CompositionLocalProvider(LocalAuth provides authViewModel, LocalPublicRooms provides publicRooms, LocalMyRooms provides myRooms, LocalMyMissions provides myMissions, LocalAppContainer provides container, LocalShop provides shop, LocalInventory provides inventory) {
                    Box(Modifier.fillMaxSize()) {
                        // 프로필 얼굴 공장: 맨 아래 층에 두고 위를 불투명한 배경이 덮는다(보이지 않게 아바타 얼굴을 찍는다).
                        // UI 테스트 환경(3D 끔)에서는 두지 않는다. 얼굴 사진이 없으면 화면은 기본 그림을 보여준다.
                        if (container.avatar3dEnabled) {
                            FaceStudioHost(FaceStudios.get(this@MainActivity), Modifier.align(Alignment.Center))
                            // 로그인 뒤 홈이 다 그려지고 쉴 때 3D 엔진과 내 착용 정보를 미리 준비한다(아바타 속도 2-1단계).
                            AvatarPrewarm(container)
                        }
                        Box(Modifier.fillMaxSize().background(DQColors.Bg)) { DQNavGraph() }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        postPushTarget(intent)
    }

    private fun postPushTarget(intent: Intent?) {
        val route = PushIntents.routeFrom(intent) ?: return
        PushIntents.clear(intent)
        val container = (application as DQApplication).container
        val owner = (container.session.state.value as? SessionState.Authenticated)?.profile?.userId
        container.pendingPushTarget.post(route, owner)
    }

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    // 로그인 후 한 번만 묻는다. 거부해도 앱 기능은 그대로 쓰고, 알림만 표시되지 않는다.
    private fun requestNotificationPermissionOnce(container: AppContainer) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val prefs = getSharedPreferences("dq_push", MODE_PRIVATE)
        if (prefs.getBoolean("permission_asked", false)) return
        lifecycleScope.launch {
            container.session.state.first { it is SessionState.Authenticated }
            if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return@launch
            prefs.edit().putBoolean("permission_asked", true).apply()
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

package com.dailyquest.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelProvider
import com.dailyquest.app.navigation.DQNavGraph
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
import com.dailyquest.app.ui.theme.DailyQuestTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as DQApplication).container
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
                if (resumedOnce) container.proofSync.notifyChanged() else resumedOnce = true
            }
        })
        setContent {
            DailyQuestTheme {
                CompositionLocalProvider(LocalAuth provides authViewModel, LocalPublicRooms provides publicRooms, LocalMyRooms provides myRooms, LocalMyMissions provides myMissions, LocalAppContainer provides container, LocalShop provides shop, LocalInventory provides inventory) {
                    DQNavGraph()
                }
            }
        }
    }
}

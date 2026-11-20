package com.dailyquest.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.ViewModelProvider
import com.dailyquest.app.navigation.DQNavGraph
import com.dailyquest.app.ui.auth.AuthViewModel
import com.dailyquest.app.ui.auth.LocalAuth
import com.dailyquest.app.ui.room.LocalMyRooms
import com.dailyquest.app.ui.room.LocalPublicRooms
import com.dailyquest.app.ui.room.MyRoomsViewModel
import com.dailyquest.app.ui.room.PublicRoomsViewModel
import com.dailyquest.app.ui.theme.DailyQuestTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as DQApplication).container
        val authViewModel = ViewModelProvider(this, AuthViewModel.Factory(container))[AuthViewModel::class.java]
        val publicRooms = ViewModelProvider(this, PublicRoomsViewModel.Factory(container))[PublicRoomsViewModel::class.java]
        val myRooms = ViewModelProvider(this, MyRoomsViewModel.Factory(container))[MyRoomsViewModel::class.java]
        setContent {
            DailyQuestTheme {
                CompositionLocalProvider(LocalAuth provides authViewModel, LocalPublicRooms provides publicRooms, LocalMyRooms provides myRooms) {
                    DQNavGraph()
                }
            }
        }
    }
}

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
import com.dailyquest.app.ui.theme.DailyQuestTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as DQApplication).container
        val authViewModel = ViewModelProvider(this, AuthViewModel.Factory(container))[AuthViewModel::class.java]
        setContent {
            DailyQuestTheme {
                CompositionLocalProvider(LocalAuth provides authViewModel) {
                    DQNavGraph()
                }
            }
        }
    }
}

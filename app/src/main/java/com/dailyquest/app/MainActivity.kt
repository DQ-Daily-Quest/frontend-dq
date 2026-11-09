package com.dailyquest.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.dailyquest.app.navigation.DQNavGraph
import com.dailyquest.app.ui.theme.DailyQuestTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DailyQuestTheme {
                DQNavGraph()
            }
        }
    }
}

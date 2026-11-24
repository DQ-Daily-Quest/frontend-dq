package com.dailyquest.app.debug

import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.dailyquest.app.data.avatar.AvatarGender
import com.dailyquest.app.data.avatar.PersonFace
import com.dailyquest.app.ui.avatar.FaceStudioHost
import com.dailyquest.app.ui.avatar.FaceStudios
import com.dailyquest.app.ui.avatar.ProfileFace
import com.dailyquest.app.ui.avatar.clearAvatarFaceMemory
import com.dailyquest.app.ui.avatar.rememberAvatarFace
import com.dailyquest.app.ui.theme.DQColors
import com.dailyquest.app.ui.theme.DailyQuestTheme
import kotlinx.coroutines.launch

/**
 * 프로필 얼굴 2단계 확인 화면 (디버그 전용, 런처 아이콘 없음).
 * `adb shell am start -n com.dailyquest.app/.debug.FaceStudioTestActivity`
 *
 * 실제 앱과 같은 구조다: 얼굴 공장([FaceStudioHost])은 **맨 아래 층**에 두고, 그 위를 불투명한 화면이 덮는다.
 * "얼굴 보여주기"를 누르면 테스트 의상 4가지의 얼굴을 달라고 하고([rememberAvatarFace]), 공장이 차례로 찍어 채운다.
 */
class FaceStudioTestActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { DailyQuestTheme { FaceStudioTestScreen() } }
    }
}

@Composable
private fun FaceStudioTestScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val studio = remember { FaceStudios.get(context) }
    val pending by studio.pending.collectAsState()
    val version by studio.version.collectAsState()
    var showFaces by remember { mutableStateOf(false) }
    var round by remember { mutableIntStateOf(0) }
    var startedAt by remember { mutableStateOf(0L) }
    var doneMs by remember { mutableStateOf<Long?>(null) }
    var stored by remember { mutableStateOf(0) }

    LaunchedEffect(version, round) { stored = studio.store.stats().first }

    Box(Modifier.fillMaxSize()) {
        // 맨 아래 층: 얼굴 공장. 위의 화면이 덮어 보이지 않는다.
        FaceStudioHost(studio, Modifier.align(Alignment.Center))

        Column(
            Modifier.fillMaxSize().background(DQColors.Bg).systemBarsPadding().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("얼굴 공장 테스트 (디버그 전용)", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(
                    onClick = { startedAt = SystemClock.elapsedRealtime(); doneMs = null; showFaces = true; round++ },
                    label = { Text("얼굴 보여주기") },
                    modifier = Modifier.testTag("faceStudioShow"),
                )
                AssistChip(
                    onClick = {
                        scope.launch {
                            showFaces = false
                            studio.store.clear()
                            clearAvatarFaceMemory()
                            round++
                        }
                    },
                    label = { Text("사진 지우기") },
                    modifier = Modifier.testTag("faceStudioClear"),
                )
            }
            var shown = 0
            if (showFaces) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TEST_OUTFITS.forEach { o ->
                        val parts = remember(o) { o.codes.mapValues { (_, code) -> modelUrl(o.body, code) } }
                        val face = rememberAvatarFace(parts, studio)
                        if (face != null) shown++
                        // 실제 화면과 같은 얼굴 동그라미(사진이 없으면 성별별 기본 그림).
                        val person = remember(o) { PersonFace(if (o.body == TestBody.GIRL) AvatarGender.FEMALE else AvatarGender.MALE, parts) }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            ProfileFace(person, 64.dp, contentDescription = o.label)
                            ProfileFace(person, 32.dp, Modifier.padding(top = 4.dp))
                            Text(o.label, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
            LaunchedEffect(shown, showFaces) {
                if (showFaces && shown == TEST_OUTFITS.size && doneMs == null) doneMs = SystemClock.elapsedRealtime() - startedAt
            }
            Text(
                "보이는 얼굴 $shown/${TEST_OUTFITS.size} · 대기 ${pending.size} · 저장 ${stored}장" +
                    (doneMs?.let { " · 다 뜨기까지 ${it}ms" } ?: ""),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.testTag("faceStudioStatus"),
            )
        }
    }
}

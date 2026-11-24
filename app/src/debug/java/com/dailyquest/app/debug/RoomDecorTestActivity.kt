package com.dailyquest.app.debug

import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.dailyquest.app.data.avatar.CachingAvatarModelFetcher
import com.dailyquest.app.data.decor.RoomCatalogs
import com.dailyquest.app.ui.decor.DecorImages
import com.dailyquest.app.ui.decor.RoomView
import com.dailyquest.app.ui.decor.rememberRoomViewState
import com.dailyquest.app.ui.theme.DQColors
import com.dailyquest.app.ui.theme.DailyQuestTheme
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * STEP 10 디버그 전용 방 테스트 화면. debug 빌드에만 들어간다. 런처 아이콘은 없고(3D 테스트 화면과 같음)
 * `adb shell am start -n com.dailyquest.app/.debug.RoomDecorTestActivity` 또는 Run 설정의 Specified Activity로 연다.
 * - 10-4: 에셋 팩 배치 예시 54개를 실제 S3 그림으로 그린다. 두 손가락 확대·이동, 두 번 탭 되돌리기를 손으로 확인한다.
 * 앱 Navigation(Routes.kt, NavGraph.kt)은 건드리지 않는다.
 */
class RoomDecorTestActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { DailyQuestTheme { RoomDecorTestScreen() } }
    }
}

@Composable
private fun RoomDecorTestScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val catalog = remember { RoomCatalogs.load(context) }
    val example = remember { ExampleRoomLayout.load(context, catalog) }
    val viewState = rememberRoomViewState()
    var showExample by remember { mutableStateOf(true) }
    var cacheStats by remember { mutableStateOf(CachingAvatarModelFetcher.CacheStats(0, 0)) }
    var cacheVersion by remember { mutableStateOf(0) }
    // 바꾼 순간부터 그림을 다 받을 때까지 걸린 시간. 처음(인터넷)과 두 번째(기기 캐시)를 비교해 본다.
    var startedAt by remember { mutableStateOf(SystemClock.uptimeMillis()) }
    var shownMs by remember { mutableStateOf<Long?>(null) }
    var failedCount by remember { mutableStateOf(0) }

    LaunchedEffect(cacheVersion, showExample) {
        startedAt = SystemClock.uptimeMillis()
        shownMs = null
        cacheStats = DecorImages.files(context).stats()
    }

    Column(Modifier.fillMaxSize().systemBarsPadding()) {
        Text("방 테스트 (디버그 전용)", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp))
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(showExample, { showExample = true }, { Text("예시 54개") })
            FilterChip(!showExample, { showExample = false }, { Text("빈 방") })
            AssistChip(onClick = { viewState.reset() }, label = { Text("원래 크기") })
            AssistChip(
                onClick = {
                    scope.launch {
                        DecorImages.files(context).clear()
                        DecorImages.source(context).clearMemory()
                        cacheVersion++
                    }
                },
                label = { Text("캐시 비우기") },
            )
        }
        // 방 그림 비율(1080×810) 그대로 화면 너비에 맞춘다. 1배일 때 방 전체가 보인다.
        // 캐시를 비우면 RoomView를 새로 만들어 화면이 들고 있던 그림도 버리고 다시 받게 한다.
        key(cacheVersion) {
            RoomView(
                room = catalog.room,
                backgroundUrl = ExampleRoomLayout.ROOM_URL,
                furniture = if (showExample) example else emptyList(),
                state = viewState,
                modifier = Modifier.fillMaxWidth().aspectRatio(catalog.room.widthPx / catalog.room.heightPx.toFloat())
                    .background(DQColors.Bg).testTag("roomTestView"),
                onImagesSettled = { failed ->
                    failedCount = failed
                    shownMs = SystemClock.uptimeMillis() - startedAt
                    scope.launch { cacheStats = DecorImages.files(context).stats() }
                },
            )
        }
        val zoom = viewState.viewport?.zoom ?: 1f
        Text(
            "확대 %.1f배 · 가구 %d개 · 캐시 %d개 %.1fMB\n%s".format(
                Locale.US, zoom, if (showExample) example.size else 0, cacheStats.fileCount, cacheStats.totalBytes / 1_048_576.0,
                when (val ms = shownMs) {
                    null -> "불러오는 중…"
                    else -> if (failedCount > 0) "실패 ${failedCount}개 · ${ms}ms" else "준비됨 · 표시까지 ${ms}ms"
                },
            ),
            modifier = Modifier.padding(16.dp).testTag("roomTestStatus"),
        )
        Text(
            "두 손가락으로 확대·축소, 확대한 뒤 한 손가락으로 이동, 두 번 탭하면 원래 크기",
            style = MaterialTheme.typography.bodySmall,
            color = DQColors.Muted,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
}

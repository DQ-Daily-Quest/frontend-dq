package com.dailyquest.app.debug

import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.dailyquest.app.data.avatar.AvatarModelFetcher
import com.dailyquest.app.data.avatar.AvatarModels
import com.dailyquest.app.data.avatar.CachingAvatarModelFetcher
import com.dailyquest.app.data.avatar.AvatarPart
import com.dailyquest.app.data.avatar.HttpAvatarModelFetcher
import com.dailyquest.app.ui.avatar.AvatarLoadStatus
import com.dailyquest.app.ui.avatar.AvatarView
import com.dailyquest.app.ui.theme.DQColors
import com.dailyquest.app.ui.theme.DailyQuestTheme
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * STEP 9 디버그 전용 3D 테스트 화면. debug 빌드에만 들어가고(app/src/debug), 런처에 "DQ 3D 테스트" 아이콘으로 따로 보인다.
 * - 9-2 "파츠": 파츠 하나를 띄운다.
 * - 9-3 "한 벌": 파츠 6~7개를 같은 원점에 겹친다. FPS를 표시해 기기 성능을 본다.
 * - 9-4 "바꾸기": 앱 본체의 [AvatarView]로 그리며, 슬롯 하나만 바꿨을 때 그 파일만 다시 받는지 확인한다.
 * - 9-5 캐시: 앱과 같은 캐시 폴더를 쓴다. 상태 줄에 실제로 받은 파일과 캐시에서 꺼낸 파일을 나눠 보여주고, "캐시 비우기"로 지울 수 있다.
 * 앱 Navigation(Routes.kt, NavGraph.kt)은 건드리지 않는다.
 */
class Avatar3dTestActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { DailyQuestTheme { Avatar3dTestScreen() } }
    }
}

private val SELECTIONS: List<TestOutfit> = TEST_SINGLE_PARTS + TEST_OUTFITS

@Composable
private fun Avatar3dTestScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // 네트워크 → 캐시 → 화면 순서로 감싼다. network는 실제로 받은 파일만, requests는 캐시에서 꺼낸 것까지 모두 센다.
    val network = remember { CountingFetcher(HttpAvatarModelFetcher.shared) }
    val cache = remember { CachingAvatarModelFetcher(network, AvatarModels.cacheDirectory(context)) }
    val requests = remember { CountingFetcher(cache) }
    var cacheStats by remember { mutableStateOf(CachingAvatarModelFetcher.CacheStats(0, 0)) }
    val fps = remember { FpsCounter() }

    var selected by remember { mutableIntStateOf(0) }
    val selection = SELECTIONS[selected]
    // 지금 입힌 코드. 버튼을 누르면 선택한 구성으로 바뀌고, "바꾸기" 줄은 여기서 슬롯 하나만 바꾼다.
    var codes by remember { mutableStateOf(selection.codes) }
    var status by remember { mutableStateOf<AvatarLoadStatus>(AvatarLoadStatus.Loading) }
    var interactive by remember { mutableStateOf(true) }

    // 바꾸는 순간 상태를 "불러오는 중"으로 둔다. 이전 구성의 "준비됨"이 남아 있으면 UI 테스트가 너무 일찍 끝난다.
    // 구성이 그대로면 AvatarView가 아무 일도 하지 않아 상태 알림도 오지 않으므로 바꾸지 않는다.
    fun change(newCodes: Map<AvatarPart, String>) {
        if (newCodes == codes) return
        network.reset()
        requests.reset()
        status = AvatarLoadStatus.Loading
        codes = newCodes
    }
    fun select(index: Int) {
        selected = index
        change(SELECTIONS[index].codes)
    }

    Column(Modifier.fillMaxSize().systemBarsPadding()) {
        Text("3D 테스트 (디버그 전용)", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp))
        ChipRow("파츠") {
            TEST_SINGLE_PARTS.forEachIndexed { i, o -> FilterChip(i == selected, { select(i) }, { Text(o.label) }) }
        }
        ChipRow("한 벌") {
            TEST_OUTFITS.forEachIndexed { i, o ->
                val index = TEST_SINGLE_PARTS.size + i
                FilterChip(index == selected, { select(index) }, { Text(o.label) })
            }
        }
        ChipRow("바꾸기") {
            val body = selection.body
            AssistChip(onClick = { change(codes + (AvatarPart.HAT to nextOf(TEST_HATS.getValue(body), codes[AvatarPart.HAT]))) }, label = { Text("모자 바꾸기") })
            AssistChip(onClick = { change(codes + (AvatarPart.TOP to nextOf(TEST_TOPS.getValue(body), codes[AvatarPart.TOP]))) }, label = { Text("상의 바꾸기") })
            AssistChip(onClick = { change(codes - AvatarPart.HAT) }, label = { Text("모자 벗기") })
            FilterChip(!interactive, { interactive = !interactive }, { Text("고정 보기") })
            AssistChip(onClick = { scope.launch { cache.clear(); cacheStats = cache.stats() } }, label = { Text("캐시 비우기") })
        }
        // 배경은 앱 색. AvatarView는 투명하게 그 위에 그린다.
        Box(Modifier.weight(1f).fillMaxWidth().background(DQColors.Bg)) {
            AvatarView(
                parts = codes.mapValues { (_, code) -> modelUrl(selection.body, code) },
                modifier = Modifier.fillMaxSize(),
                interactive = interactive,
                fetcher = requests,
                onStatusChange = { status = it },
                onFrame = { fps.onFrame(it) },
            )
            FpsLabel(fps, Modifier.align(Alignment.TopEnd))
            CornerLabel("캐시 ${cacheStats.fileCount}개 · %.1fMB".format(Locale.US, cacheStats.totalBytes / 1_048_576.0), "avatar3dCache", Modifier.align(Alignment.TopStart))
        }
        LaunchedEffect(status) { cacheStats = cache.stats() }
        StatusText(selection.label, status, network, requests)
    }
}

@Composable
private fun ChipRow(title: String, content: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.labelMedium)
        content()
    }
}

/** 마지막 [reset] 이후 [inner]가 성공한 요청 수·크기·걸린 시간을 센다. 바뀐 슬롯만 받는지, 캐시를 쓰는지 확인하는 용도. */
private class CountingFetcher(private val inner: AvatarModelFetcher) : AvatarModelFetcher {
    var count by mutableIntStateOf(0)
        private set
    var bytes by mutableLongStateOf(0L)
        private set
    var elapsedMs by mutableLongStateOf(0L)
        private set
    private var startedAt = SystemClock.elapsedRealtime()

    fun reset() {
        count = 0
        bytes = 0
        elapsedMs = 0
        startedAt = SystemClock.elapsedRealtime()
    }

    override suspend fun fetch(url: String): Result<ByteArray> = inner.fetch(url).also { result ->
        result.onSuccess {
            count++
            bytes += it.size
        }
        elapsedMs = SystemClock.elapsedRealtime() - startedAt
    }
}

/** 1초마다 그 사이에 그린 프레임 수를 [value]에 넣는다. 화면 갱신은 1초에 한 번뿐이다. */
private class FpsCounter {
    var value by mutableIntStateOf(0)
        private set
    private var windowStartNanos = 0L
    private var frames = 0

    fun onFrame(frameTimeNanos: Long) {
        if (windowStartNanos == 0L) windowStartNanos = frameTimeNanos
        frames++
        val elapsed = frameTimeNanos - windowStartNanos
        if (elapsed >= 1_000_000_000L) {
            value = (frames * 1_000_000_000L / elapsed).toInt()
            frames = 0
            windowStartNanos = frameTimeNanos
        }
    }
}

/** FPS 값을 여기서만 읽어서, 1초마다 값이 바뀌어도 3D 화면 쪽은 다시 구성되지 않게 한다. */
@Composable
private fun FpsLabel(fps: FpsCounter, modifier: Modifier) = CornerLabel("FPS ${fps.value}", "avatar3dFps", modifier)

@Composable
private fun CornerLabel(text: String, tag: String, modifier: Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        modifier = modifier.padding(8.dp).background(Color(0x33000000))
            .padding(horizontal = 6.dp, vertical = 2.dp).testTag(tag),
    )
}

@Composable
private fun StatusText(title: String, status: AvatarLoadStatus, network: CountingFetcher, requests: CountingFetcher) {
    // 둘째 줄: 실제로 받은 파일 / 캐시에서 꺼낸 파일 / 바꾼 뒤 끝날 때까지 걸린 시간
    val detail = String.format(
        Locale.US, "받은 파일 %d개 %.2fMB · 캐시 %d개 · %dms",
        network.count, network.bytes / 1_048_576.0, requests.count - network.count, requests.elapsedMs,
    )
    val (text, color) = when (status) {
        AvatarLoadStatus.Loading -> "$title · 불러오는 중…" to Color.Unspecified
        is AvatarLoadStatus.Ready -> "$title · 준비됨 · 파츠 ${status.shown.size}개\n$detail" to Color(0xFF2E7D32)
        is AvatarLoadStatus.Failed ->
            "$title · 실패: ${status.failed.joinToString { it.serverKey }} · 파츠 ${status.shown.size}개\n$detail" to Color(0xFFC62828)
    }
    // 글자 줄 수가 바뀌면 3D 화면 높이가 바뀌어 화면 버퍼를 다시 만들게 된다(에뮬레이터에서 EGL_BAD_ALLOC). 두 줄로 고정한다.
    Text(
        text,
        color = color,
        minLines = 2,
        maxLines = 2,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.fillMaxWidth().background(Color(0x0F000000)).padding(12.dp).testTag("avatar3dStatus"),
    )
}

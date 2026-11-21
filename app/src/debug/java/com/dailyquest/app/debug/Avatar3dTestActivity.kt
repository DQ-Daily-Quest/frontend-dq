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
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.dailyquest.app.ui.theme.DQColors
import com.dailyquest.app.ui.theme.DailyQuestTheme
import io.github.sceneview.SceneView
import io.github.sceneview.SurfaceType
import io.github.sceneview.math.Position
import io.github.sceneview.model.ModelInstance
import io.github.sceneview.rememberCameraManipulator
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberModelLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.nio.ByteBuffer
import java.util.Locale

/**
 * STEP 9 디버그 전용 3D 테스트 화면.
 * - 9-2: S3에 올라간 아바타 파츠 GLB 하나를 띄운다 ("파츠" 줄).
 * - 9-3: 파츠 6~7개를 같은 원점에 겹쳐 한 벌을 입힌다 ("한 벌" 줄). FPS를 표시해 기기 성능을 본다.
 * debug 빌드에만 들어가고(app/src/debug), 런처에 "DQ 3D 테스트" 아이콘으로 따로 보인다.
 * 앱 Navigation(Routes.kt, NavGraph.kt)은 건드리지 않는다.
 */
class Avatar3dTestActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { DailyQuestTheme { Avatar3dTestScreen() } }
    }
}

/** 크기·종류가 다른 파츠 몇 개. 머리(3.7MB, 12만 삼각형)가 가장 무겁다. */
internal val SAMPLE_PARTS = listOf(
    "남 몸체" to "$ASSET_BASE/boy/DQ_Boy_01_Body.glb",
    "남 머리" to "$ASSET_BASE/boy/DQ_Boy_03_Hair.glb",
    "남 모자" to "$ASSET_BASE/boy/DQ_Boy_Hat_Cafe.glb",
    "여 몸체" to "$ASSET_BASE/girl/DQ_Body.glb",
    "여 상의" to "$ASSET_BASE/girl/DQ_Top_BerryCardigan.glb",
)

/** 화면에 띄울 대상: 파츠 하나 또는 한 벌. [title]은 상태 줄 맨 앞에 나온다. */
private data class TestSelection(val title: String, val urls: List<String>)

private val SELECTIONS: List<TestSelection> =
    SAMPLE_PARTS.map { (_, url) -> TestSelection(url.substringAfterLast('/'), listOf(url)) } +
        TEST_OUTFITS.map { TestSelection(it.label, it.urls) }

/**
 * 아바타(원점 = 발 사이 바닥, 키 약 1.3~1.45m)를 정면에서 전신이 보이게 비추는 카메라 위치와 바라보는 점 (단위: m).
 * 모자 꼭대기(약 1.44m)까지 화면 안에 들어오도록 정했다.
 */
private val AVATAR_CAMERA_HOME = Position(0f, 0.75f, 2.6f)
private val AVATAR_CAMERA_TARGET = Position(0f, 0.7f, 0f)

private sealed interface LoadState {
    data object Loading : LoadState
    data class Loaded(val count: Int, val bytes: Long, val downloadMs: Long, val createMs: Long) : LoadState
    data class Failed(val message: String) : LoadState
}

private val sharedClient: OkHttpClient by lazy { OkHttpClient() }

@Composable
private fun Avatar3dTestScreen() {
    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    val downloader = remember { GlbDownloader(sharedClient) }

    var selected by remember { mutableIntStateOf(0) }
    var reloadCount by remember { mutableIntStateOf(0) }
    var state by remember { mutableStateOf<LoadState>(LoadState.Loading) }
    var instances by remember { mutableStateOf<List<ModelInstance>>(emptyList()) }
    val fps = remember { FpsCounter() }

    LaunchedEffect(selected, reloadCount) {
        state = LoadState.Loading
        instances = emptyList()
        val urls = SELECTIONS[selected].urls
        val started = SystemClock.elapsedRealtime()
        // 파츠를 동시에 받는다. 한 벌(6~7개)을 하나씩 받으면 시간이 개수만큼 늘어난다.
        val results = coroutineScope { urls.map { url -> async { url to downloader.download(url) } }.awaitAll() }
        val downloaded = SystemClock.elapsedRealtime()
        val failed = results.firstOrNull { it.second.isFailure }
        if (failed != null) {
            val reason = failed.second.exceptionOrNull()?.let { it.message ?: it::class.simpleName }
            state = LoadState.Failed("다운로드 실패(${failed.first.substringAfterLast('/')}): $reason")
            return@LaunchedEffect
        }
        val buffers = results.map { it.second.getOrThrow() }
        // Filament 모델 생성은 반드시 메인 스레드에서 해야 한다. 다른 스레드면 네이티브 코드가 앱을 강제 종료한다.
        // LaunchedEffect가 보통 메인에서 돌지만 UI 테스트 환경에서는 IO 스레드에서 이어져 실제로 종료됐다. 그래서 명시한다.
        state = try {
            instances = withContext(Dispatchers.Main) { buffers.map { modelLoader.createModelInstance(ByteBuffer.wrap(it)) } }
            LoadState.Loaded(buffers.size, buffers.sumOf { it.size.toLong() }, downloaded - started, SystemClock.elapsedRealtime() - downloaded)
        } catch (e: Exception) {
            LoadState.Failed("모델 생성 실패: ${e.message ?: e::class.simpleName}")
        }
    }

    Column(Modifier.fillMaxSize().systemBarsPadding()) {
        Text("3D 테스트 (디버그 전용)", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp))
        ChipRow("파츠", SAMPLE_PARTS.map { it.first }, offset = 0, selected = selected) { selected = it }
        ChipRow("한 벌", TEST_OUTFITS.map { it.label }, offset = SAMPLE_PARTS.size, selected = selected) { selected = it }
        // 배경은 앱 색을 쓰고 3D는 투명하게 그 위에 그린다(isOpaque = false).
        Box(Modifier.weight(1f).fillMaxWidth().background(DQColors.Bg)) {
            // 3D 화면(SceneView)은 하나만 만들어 두고 모델만 바꾼다.
            // 파츠마다 SceneView를 새로 만들면 에뮬레이터에서 화면 버퍼 할당이 실패해(EGL_BAD_ALLOC) 모델이 안 보이거나
            // 화면이 비었고, 카메라 위치도 매번 달라졌다.
            SceneView(
                modifier = Modifier.fillMaxSize().testTag("avatar3dScene"),
                // 기본값(SurfaceView)은 Compose 화면 뒤에 따로 그려져 에뮬레이터에서 모델이 보이지 않았다.
                // TextureView는 Compose 화면 안에 함께 그려져 겹침·캡처·UI 테스트·투명 배경에서 안정적이다.
                surfaceType = SurfaceType.TextureSurface,
                engine = engine,
                modelLoader = modelLoader,
                isOpaque = false,
                // 파츠는 모두 원점(발 사이 바닥)을 공유하므로 위치를 옮기지 않고 그대로 겹친다.
                autoCenterContent = false,
                // 기본 카메라 조작기는 원점(발밑)을 바라본다. 키 약 1.3m 아바타의 허리 높이를 중심으로 돌게 한다.
                // (autoFitContent는 카메라 조작기가 있으면 동작하지 않아서 쓰지 않는다.)
                cameraManipulator = rememberCameraManipulator(
                    orbitHomePosition = AVATAR_CAMERA_HOME,
                    targetPosition = AVATAR_CAMERA_TARGET,
                ),
                onFrame = { fps.onFrame(it) },
            ) {
                instances.forEach { ModelNode(modelInstance = it) }
            }
            if (state == LoadState.Loading) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            }
            FpsLabel(fps, Modifier.align(Alignment.TopEnd))
        }
        StatusText(state, SELECTIONS[selected].title)
        Button(onClick = { reloadCount++ }, modifier = Modifier.fillMaxWidth().padding(16.dp)) { Text("다시 불러오기") }
    }
}

@Composable
private fun ChipRow(title: String, labels: List<String>, offset: Int, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.labelMedium)
        labels.forEachIndexed { index, label ->
            FilterChip(selected = offset + index == selected, onClick = { onSelect(offset + index) }, label = { Text(label) })
        }
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
private fun FpsLabel(fps: FpsCounter, modifier: Modifier) {
    Text(
        "FPS ${fps.value}",
        style = MaterialTheme.typography.labelSmall,
        modifier = modifier.padding(8.dp).background(Color(0x33000000))
            .padding(horizontal = 6.dp, vertical = 2.dp).testTag("avatar3dFps"),
    )
}

@Composable
private fun StatusText(state: LoadState, title: String) {
    val (text, color) = when (state) {
        LoadState.Loading -> "$title 불러오는 중…" to Color.Unspecified
        is LoadState.Loaded -> String.format(
            Locale.US, "%s · %d개 · %.2fMB · 다운로드 %dms · 모델 생성 %dms",
            title, state.count, state.bytes / 1_048_576.0, state.downloadMs, state.createMs,
        ) to Color(0xFF2E7D32)
        is LoadState.Failed -> "$title · ${state.message}" to Color(0xFFC62828)
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

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
import com.dailyquest.app.ui.theme.DailyQuestTheme
import io.github.sceneview.SceneView
import io.github.sceneview.SurfaceType
import io.github.sceneview.math.Position
import io.github.sceneview.model.ModelInstance
import io.github.sceneview.rememberCameraManipulator
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberModelLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.nio.ByteBuffer
import java.util.Locale

/**
 * STEP 9-2: S3에 올라간 아바타 파츠 GLB 하나를 SceneView로 띄워 보는 디버그 전용 화면.
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

private const val ASSET_BASE = "https://dq-uploads-prod.s3.ap-northeast-2.amazonaws.com/items/avatar"

/** 크기·종류가 다른 파츠 몇 개. 머리(3.7MB, 12만 삼각형)가 가장 무겁다. */
internal val SAMPLE_PARTS = listOf(
    "남 몸체" to "$ASSET_BASE/boy/DQ_Boy_01_Body.glb",
    "남 머리" to "$ASSET_BASE/boy/DQ_Boy_03_Hair.glb",
    "남 모자" to "$ASSET_BASE/boy/DQ_Boy_Hat_Cafe.glb",
    "여 몸체" to "$ASSET_BASE/girl/DQ_Body.glb",
    "여 상의" to "$ASSET_BASE/girl/DQ_Top_BerryCardigan.glb",
)

/** 아바타(원점 = 발 사이 바닥, 키 약 1.3~1.45m)를 정면에서 전신이 보이게 비추는 카메라 위치와 바라보는 점 (단위: m). */
private val AVATAR_CAMERA_HOME = Position(0f, 0.75f, 2.6f)
private val AVATAR_CAMERA_TARGET = Position(0f, 0.7f, 0f)

private sealed interface LoadState {
    data object Loading : LoadState
    data class Loaded(val bytes: Int, val downloadMs: Long, val createMs: Long) : LoadState
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
    var instance by remember { mutableStateOf<ModelInstance?>(null) }

    LaunchedEffect(selected, reloadCount) {
        state = LoadState.Loading
        instance = null
        val started = SystemClock.elapsedRealtime()
        downloader.download(SAMPLE_PARTS[selected].second).fold(
            onSuccess = { bytes ->
                val downloaded = SystemClock.elapsedRealtime()
                // Filament 모델 생성은 반드시 메인 스레드에서 해야 한다. 다른 스레드면 네이티브 코드가 앱을 강제 종료한다.
                // LaunchedEffect가 보통 메인에서 돌지만 UI 테스트 환경에서는 IO 스레드에서 이어져 실제로 종료됐다. 그래서 명시한다.
                state = try {
                    instance = withContext(Dispatchers.Main) { modelLoader.createModelInstance(ByteBuffer.wrap(bytes)) }
                    LoadState.Loaded(bytes.size, downloaded - started, SystemClock.elapsedRealtime() - downloaded)
                } catch (e: Exception) {
                    LoadState.Failed("모델 생성 실패: ${e.message ?: e::class.simpleName}")
                }
            },
            onFailure = { e -> state = LoadState.Failed("다운로드 실패: ${e.message ?: e::class.simpleName}") },
        )
    }

    Column(Modifier.fillMaxSize().systemBarsPadding()) {
        Text("3D 테스트 (디버그 전용)", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp))
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SAMPLE_PARTS.forEachIndexed { index, (label, _) ->
                FilterChip(selected = index == selected, onClick = { selected = index }, label = { Text(label) })
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            // 3D 화면(SceneView)은 하나만 만들어 두고 모델만 바꾼다.
            // 파츠마다 SceneView를 새로 만들면 에뮬레이터에서 화면 버퍼 할당이 실패해(EGL_BAD_ALLOC) 모델이 안 보이거나
            // 화면이 비었고, 카메라 위치도 매번 달라졌다.
            SceneView(
                modifier = Modifier.fillMaxSize().testTag("avatar3dScene"),
                // 기본값(SurfaceView)은 Compose 화면 뒤에 따로 그려져 에뮬레이터에서 모델이 보이지 않았다.
                // TextureView는 Compose 화면 안에 함께 그려져 겹침·캡처·UI 테스트에서 안정적이다.
                surfaceType = SurfaceType.TextureSurface,
                engine = engine,
                modelLoader = modelLoader,
                // 파츠는 모두 원점(발 사이 바닥)을 공유하므로 위치를 옮기지 않는다. 3단계에서 겹칠 때도 같다.
                autoCenterContent = false,
                // 기본 카메라 조작기는 원점(발밑)을 바라본다. 키 약 1.3m 아바타의 허리 높이를 중심으로 돌게 한다.
                // (autoFitContent는 카메라 조작기가 있으면 동작하지 않아서 쓰지 않는다.)
                cameraManipulator = rememberCameraManipulator(
                    orbitHomePosition = AVATAR_CAMERA_HOME,
                    targetPosition = AVATAR_CAMERA_TARGET,
                ),
            ) {
                instance?.let { ModelNode(modelInstance = it) }
            }
            if (state == LoadState.Loading) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            }
        }
        StatusText(state, SAMPLE_PARTS[selected].second.substringAfterLast('/'))
        Button(onClick = { reloadCount++ }, modifier = Modifier.fillMaxWidth().padding(16.dp)) { Text("다시 불러오기") }
    }
}

@Composable
private fun StatusText(state: LoadState, fileName: String) {
    val (text, color) = when (state) {
        LoadState.Loading -> "$fileName 불러오는 중…" to Color.Unspecified
        is LoadState.Loaded -> String.format(
            Locale.US, "%s · %.2fMB · 다운로드 %dms · 모델 생성 %dms",
            fileName, state.bytes / 1_048_576.0, state.downloadMs, state.createMs,
        ) to Color(0xFF2E7D32)
        is LoadState.Failed -> "$fileName · ${state.message}" to Color(0xFFC62828)
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

package com.dailyquest.app.ui.avatar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.dailyquest.app.data.avatar.AvatarModelFetcher
import com.dailyquest.app.data.avatar.AvatarPart
import com.dailyquest.app.data.avatar.AvatarModels
import io.github.sceneview.SceneView
import io.github.sceneview.SurfaceType
import io.github.sceneview.math.Position
import io.github.sceneview.model.ModelInstance
import io.github.sceneview.model.model
import io.github.sceneview.rememberCameraManipulator
import io.github.sceneview.rememberCameraNode
import android.os.SystemClock
import io.github.sceneview.loaders.ModelLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer

/**
 * 아바타(원점 = 발 사이 바닥, 키 약 1.3~1.45m)를 정면에서 전신이 보이게 비추는 카메라 위치와 바라보는 점 (단위: m).
 * 모자 꼭대기(약 1.44m)까지 화면 안에 들어온다. STEP 9-2·3 테스트 화면에서 정했다.
 */
private val AVATAR_CAMERA_HOME = Position(0f, 0.75f, 2.6f)
private val AVATAR_CAMERA_TARGET = Position(0f, 0.7f, 0f)

/**
 * 아바타를 비추는 카메라 자리. [position]에서 [target]을 바라본다(단위: m, 원점 = 발 사이 바닥).
 * - [FULL_BODY]: 전신 (기본, STEP 9)
 * - [FACE]: 머리만 가깝게 (프로필 얼굴 사진용).
 */
data class AvatarCamera(val position: Position, val target: Position) {
    companion object {
        val FULL_BODY = AvatarCamera(AVATAR_CAMERA_HOME, AVATAR_CAMERA_TARGET)
        /** 머리 전체와 큰 모자까지 정사각형 안에 들어오는 값 (얼굴 테스트 화면에서 남·여·모자 4가지로 맞춤). */
        val FACE = AvatarCamera(Position(0f, 1.11f, 1.3f), Position(0f, 1.08f, 0f))
    }
}

/**
 * 슬롯별 GLB를 같은 원점에 겹쳐 그리는 3D 아바타.
 *
 * - [parts]가 바뀌면 바뀐 슬롯만 다시 받는다. 새 모델이 준비될 때까지 이전 모습이 그대로 보인다.
 * - 받는 중에는 가운데에 진행 표시, 실패하면 "다시 시도" 버튼을 보여준다.
 * - 배경은 투명하다. 바깥 [modifier]의 배경색이 그대로 보인다.
 * - 3D 화면(SceneView)은 이 컴포넌트 안에 하나만 만든다. 파츠마다 새로 만들면 화면 버퍼 할당이 실패할 수 있다(EGL_BAD_ALLOC).
 *   화면 크기가 자주 바뀌지 않는 자리에 두는 것이 좋다.
 *
 * @param parts 슬롯 → GLB 주소(`modelUrl`). 없는 슬롯은 그리지 않는다(예: 모자를 안 쓴 경우).
 * @param interactive true면 드래그로 돌리고 두 손가락으로 확대·축소할 수 있다. false면 고정된 정면 모습.
 * @param camera 카메라 자리. 기본은 전신. 프로필 얼굴 사진은 [AvatarCamera.FACE].
 * @param fetcher GLB 내용을 가져오는 방법. 기본은 앱 공용 디스크 캐시([AvatarModels.fetcher]) — 한 번 받은 파일은 다시 받지 않는다.
 * @param onStatusChange 불러오기 상태가 바뀔 때마다 호출된다.
 * @param onFrame 3D 화면이 한 프레임 그릴 때마다 호출된다 (FPS 측정 등).
 */
@Composable
fun AvatarView(
    parts: Map<AvatarPart, String>,
    modifier: Modifier = Modifier,
    interactive: Boolean = true,
    camera: AvatarCamera = AvatarCamera.FULL_BODY,
    fetcher: AvatarModelFetcher = AvatarModels.fetcher(LocalContext.current),
    onStatusChange: (AvatarLoadStatus) -> Unit = {},
    onFrame: ((frameTimeNanos: Long) -> Unit)? = null,
    /** 값이 바뀌면 같은 [parts]라도 다시 맞춰 보고 상태를 다시 알린다(실패한 파츠는 다시 받는다). 얼굴 공장이 다시 찍을 때 쓴다. */
    reloadKey: Int = 0,
) {
    // 3D 엔진(Filament)은 처음 만들 때 메인 스레드를 오래 붙잡는다(에뮬레이터 실측 약 5~6초).
    // 그동안은 화면이 멈춰 진행 표시도 그려지지 않으므로, 진행 표시를 먼저 한 번 그린 뒤 엔진을 만든다.
    var sceneStarted by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        withFrameNanos { }
        sceneStarted = true
    }
    if (sceneStarted) {
        AvatarScene(parts, modifier, interactive, camera, fetcher, onStatusChange, onFrame, reloadKey)
    } else {
        Box(modifier.testTag("avatarView")) {
            CircularProgressIndicator(Modifier.align(Alignment.Center).testTag("avatarViewLoading"))
        }
    }
}

@Composable
private fun AvatarScene(
    parts: Map<AvatarPart, String>,
    modifier: Modifier,
    interactive: Boolean,
    camera: AvatarCamera,
    fetcher: AvatarModelFetcher,
    onStatusChange: (AvatarLoadStatus) -> Unit,
    onFrame: ((frameTimeNanos: Long) -> Unit)?,
    reloadKey: Int,
) {
    // 앱 전체가 함께 쓰는 엔진·로더·환경 조명(아바타 속도 2단계). 화면을 떠나도 지우지 않는다.
    val context = LocalContext.current
    val shared = remember { AvatarEngine.get(context) }
    val engine = shared.engine
    val modelLoader = shared.modelLoader
    // 모델 로더를 함께 쓰므로 이 화면이 만든 모델은 이 화면이 지운다. 화면을 떠나면 남은 모델을 모두 지운다.
    val models = remember { SceneModels(modelLoader) }
    DisposableEffect(Unit) { onDispose { models.disposeAll() } }

    val loader = remember(modelLoader, fetcher) {
        AvatarPartsLoader<ModelInstance>(
            fetcher = fetcher,
            // Filament 모델 생성·삭제는 반드시 메인 스레드에서 한다. 다른 스레드면 네이티브 코드가 앱을 강제 종료한다(STEP 9-2).
            createModel = { bytes -> withContext(Dispatchers.Main) { createModelSafely(modelLoader, bytes, models) } },
            releaseModel = { instance ->
                withContext(Dispatchers.Main) {
                    // 화면에서 노드가 빠진 다음 프레임 이후에 지운다. 그리는 중인 모델을 지우면 안 된다.
                    withFrameNanos { }
                    withFrameNanos { }
                    models.release(instance)
                }
            },
        )
    }

    var retryCount by remember { mutableIntStateOf(0) }
    LaunchedEffect(loader, parts, retryCount, reloadKey) { loader.sync(parts) }

    val currentOnStatusChange by rememberUpdatedState(onStatusChange)
    // 불러오기가 끝날 때마다(결과가 이전과 같아도) 한 번씩 알린다.
    LaunchedEffect(loader) {
        snapshotFlow { loader.completedSyncs to loader.status }.collect { (_, status) -> currentOnStatusChange(status) }
    }

    val cameraNode = rememberCameraNode(engine) {
        position = camera.position
        lookAt(camera.target)
    }
    // 카메라 자리가 바뀌면(얼굴 테스트 화면에서 맞출 때) 같은 카메라를 옮긴다. 3D 화면은 새로 만들지 않는다.
    LaunchedEffect(cameraNode, camera) {
        cameraNode.position = camera.position
        cameraNode.lookAt(camera.target)
    }
    // 카메라 조작기가 있으면 매 프레임 카메라 위치를 정한다. 없으면 위 cameraNode 위치에 고정된다.
    val cameraManipulator = if (interactive) {
        rememberCameraManipulator(orbitHomePosition = camera.position, targetPosition = camera.target)
    } else {
        null
    }

    Box(modifier.testTag("avatarView")) {
        SceneView(
            modifier = Modifier.matchParentSize(),
            // SurfaceView(기본값)는 Compose 화면 뒤에 따로 그려져 에뮬레이터에서 안 보였다. TextureView는 투명 배경도 된다.
            surfaceType = SurfaceType.TextureSurface,
            engine = engine,
            modelLoader = modelLoader,
            materialLoader = shared.materialLoader,
            environmentLoader = shared.environmentLoader,
            environment = shared.environment,
            isOpaque = false,
            // 파츠는 모두 원점을 공유하므로 위치를 옮기지 않고 그대로 겹친다.
            autoCenterContent = false,
            cameraNode = cameraNode,
            cameraManipulator = cameraManipulator,
            onFrame = onFrame,
        ) {
            loader.shown.values.forEach { ModelNode(modelInstance = it.model) }
        }
        // 3D 화면이 막 생겨 아직 불러오기를 시작하지 않은 순간에도 빈 상자 대신 진행 표시를 보인다.
        val notStartedYet = parts.isNotEmpty() && loader.shown.isEmpty() && loader.status is AvatarLoadStatus.Ready
        AvatarStatusOverlay(
            if (notStartedYet) AvatarLoadStatus.Loading else loader.status,
            isEmpty = loader.shown.isEmpty(),
            onRetry = { retryCount++ },
        )
    }
}

/**
 * 모델을 만들고, 텍스처를 다 만든 뒤에 원본 데이터를 지운다.
 *
 * SceneView의 createModelInstance()는 모델을 만들자마자 원본 데이터를 지우는데, 텍스처는 그 뒤에도 백그라운드에서 만든다.
 * Filament는 텍스처를 원본 데이터의 **메모리 주소**로 구분해 재사용하므로, 다음 모델의 원본 데이터가 방금 비워진 같은 주소에
 * 들어오면 "아직 만드는 중인 텍스처"를 쓰려다 앱이 강제 종료된다
 * (`PreconditionPanic in setImage: Texture is not created yet. It may be in the process of asynchronous loading`, STEP 9-6에서 발생).
 * 원본 데이터를 텍스처가 끝날 때까지 들고 있으면 주소가 겹치지 않는다. 모델도 하나씩 차례로 만들게 된다.
 *
 * 화면이 사라지면 새로 만들지 않는다. 텍스처를 만드는 중에 화면이 사라지면 **텍스처가 다 만들어진 뒤에** 지운다.
 * 모델 로더를 여러 화면이 함께 쓰므로(아바타 속도 2단계) 로더의 진행 중인 작업을 통째로 취소할 수 없고,
 * 텍스처를 만드는 중인 모델을 지우면 로더가 지워진 모델의 텍스처를 계속 만들다 앱이 강제 종료될 수 있다.
 * 이 함수는 [AvatarPartsLoader]의 취소되지 않는 구간에서 불려 화면이 사라져도 끝까지 돈다. 모두 메인 스레드에서 돈다.
 */
private suspend fun createModelSafely(modelLoader: ModelLoader, bytes: ByteArray, models: SceneModels): ModelInstance {
    models.ensureAlive()
    val model = modelLoader.createModel(ByteBuffer.wrap(bytes), releaseSourceData = false)
    val deadline = SystemClock.uptimeMillis() + TEXTURE_LOAD_TIMEOUT_MS
    // 진행률은 렌더 루프가 매 프레임 갱신하지만, 화면이 아직 안 그려지는 중이거나 이미 사라졌을 수 있어 직접도 갱신한다.
    while (modelLoader.progress < 1f && SystemClock.uptimeMillis() < deadline) {
        modelLoader.updateLoad()
        delay(TEXTURE_POLL_MS)
    }
    model.releaseSourceData()
    // 기다리는 사이 화면이 사라졌으면 지금 지운다. 아니면 이 화면의 모델로 등록한다.
    models.track(model.instance)
    models.ensureAlive()
    return model.instance
}

private const val TEXTURE_LOAD_TIMEOUT_MS = 5_000L
private const val TEXTURE_POLL_MS = 16L

/**
 * 한 3D 화면이 만든 모델 목록 (아바타 속도 2단계). 모델 로더를 여러 화면이 함께 쓰므로, 로더 대신 화면이 자기 모델을 지운다.
 * - 다 만든 모델을 [track]으로 등록하고(화면이 이미 사라졌으면 바로 지움), 바꿔 입어 빠진 모델은 [release]로,
 *   화면을 떠나면 [disposeAll]로 남은 것을 모두 지운다.
 * - 한 모델을 두 번 지우면 네이티브 코드가 앱을 강제 종료하므로, 목록에 있는 것만 한 번 지운다.
 * - 모두 메인 스레드에서 부른다.
 */
private class SceneModels(private val modelLoader: ModelLoader) {
    private val owned = LinkedHashSet<ModelInstance>()
    var disposed = false
        private set

    /** 화면이 이미 사라졌으면 모델 만들기를 멈춘다. 이 실패는 [AvatarPartsLoader]가 "못 불러옴"으로 처리한다. */
    fun ensureAlive() {
        check(!disposed) { "AvatarView가 이미 화면에서 사라졌습니다" }
    }

    fun track(instance: ModelInstance) {
        if (disposed) destroy(instance) else owned += instance
    }

    /** 바꿔 입어 빠진 모델을 지운다. 화면을 떠난 뒤라면 이미 [disposeAll]이 지웠다. */
    fun release(instance: ModelInstance) {
        if (owned.remove(instance)) destroy(instance)
    }

    fun disposeAll() {
        disposed = true
        owned.toList().forEach(::destroy)
        owned.clear()
    }

    private fun destroy(instance: ModelInstance) {
        runCatching { modelLoader.destroyModel(instance.model) }
    }
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.AvatarStatusOverlay(
    status: AvatarLoadStatus,
    isEmpty: Boolean,
    onRetry: () -> Unit,
) {
    when (status) {
        // 처음 불러올 때만 크게 표시한다. 옷을 바꾸는 중에는 이전 모습이 보이므로 작게 표시한다.
        AvatarLoadStatus.Loading -> CircularProgressIndicator(
            modifier = if (isEmpty) Modifier.align(Alignment.Center).testTag("avatarViewLoading")
            else Modifier.align(Alignment.TopStart).padding(8.dp).testTag("avatarViewLoading"),
            strokeWidth = if (isEmpty) 4.dp else 2.dp,
        )
        is AvatarLoadStatus.Failed -> Column(
            modifier = Modifier.align(if (isEmpty) Alignment.Center else Alignment.BottomCenter).padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("아바타를 불러오지 못했어요", style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = onRetry, modifier = Modifier.testTag("avatarViewRetry")) { Text("다시 시도") }
        }
        is AvatarLoadStatus.Ready -> Unit
    }
}

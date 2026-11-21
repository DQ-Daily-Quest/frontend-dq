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
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberModelLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer

/**
 * 아바타(원점 = 발 사이 바닥, 키 약 1.3~1.45m)를 정면에서 전신이 보이게 비추는 카메라 위치와 바라보는 점 (단위: m).
 * 모자 꼭대기(약 1.44m)까지 화면 안에 들어온다. STEP 9-2·3 테스트 화면에서 정했다.
 */
private val AVATAR_CAMERA_HOME = Position(0f, 0.75f, 2.6f)
private val AVATAR_CAMERA_TARGET = Position(0f, 0.7f, 0f)

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
 * @param fetcher GLB 내용을 가져오는 방법. 기본은 앱 공용 디스크 캐시([AvatarModels.fetcher]) — 한 번 받은 파일은 다시 받지 않는다.
 * @param onStatusChange 불러오기 상태가 바뀔 때마다 호출된다.
 * @param onFrame 3D 화면이 한 프레임 그릴 때마다 호출된다 (FPS 측정 등).
 */
@Composable
fun AvatarView(
    parts: Map<AvatarPart, String>,
    modifier: Modifier = Modifier,
    interactive: Boolean = true,
    fetcher: AvatarModelFetcher = AvatarModels.fetcher(LocalContext.current),
    onStatusChange: (AvatarLoadStatus) -> Unit = {},
    onFrame: ((frameTimeNanos: Long) -> Unit)? = null,
) {
    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    // 화면을 떠나면 modelLoader가 모델을 모두 지운다. 그 뒤에 남은 지우기 작업이 다시 지우지 않게 막는다.
    val lifecycle = remember { ViewLifecycle() }
    DisposableEffect(Unit) { onDispose { lifecycle.disposed = true } }

    val loader = remember(modelLoader, fetcher) {
        AvatarPartsLoader<ModelInstance>(
            fetcher = fetcher,
            // Filament 모델 생성·삭제는 반드시 메인 스레드에서 한다. 다른 스레드면 네이티브 코드가 앱을 강제 종료한다(STEP 9-2).
            createModel = { bytes -> withContext(Dispatchers.Main) { modelLoader.createModelInstance(ByteBuffer.wrap(bytes)) } },
            releaseModel = { instance ->
                withContext(Dispatchers.Main) {
                    // 화면에서 노드가 빠진 다음 프레임 이후에 지운다. 그리는 중인 모델을 지우면 안 된다.
                    withFrameNanos { }
                    withFrameNanos { }
                    if (!lifecycle.disposed) modelLoader.destroyModel(instance.model)
                }
            },
        )
    }

    var retryCount by remember { mutableIntStateOf(0) }
    LaunchedEffect(loader, parts, retryCount) { loader.sync(parts) }

    val currentOnStatusChange by rememberUpdatedState(onStatusChange)
    // 불러오기가 끝날 때마다(결과가 이전과 같아도) 한 번씩 알린다.
    LaunchedEffect(loader) {
        snapshotFlow { loader.completedSyncs to loader.status }.collect { (_, status) -> currentOnStatusChange(status) }
    }

    val cameraNode = rememberCameraNode(engine) {
        position = AVATAR_CAMERA_HOME
        lookAt(AVATAR_CAMERA_TARGET)
    }
    // 카메라 조작기가 있으면 매 프레임 카메라 위치를 정한다. 없으면 위 cameraNode 위치에 고정된다.
    val cameraManipulator = if (interactive) {
        rememberCameraManipulator(orbitHomePosition = AVATAR_CAMERA_HOME, targetPosition = AVATAR_CAMERA_TARGET)
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
            isOpaque = false,
            // 파츠는 모두 원점을 공유하므로 위치를 옮기지 않고 그대로 겹친다.
            autoCenterContent = false,
            cameraNode = cameraNode,
            cameraManipulator = cameraManipulator,
            onFrame = onFrame,
        ) {
            loader.shown.values.forEach { ModelNode(modelInstance = it.model) }
        }
        AvatarStatusOverlay(loader.status, isEmpty = loader.shown.isEmpty(), onRetry = { retryCount++ })
    }
}

private class ViewLifecycle {
    @Volatile var disposed = false
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

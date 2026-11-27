package com.dailyquest.app.ui.avatar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import io.github.sceneview.math.Position
import androidx.compose.ui.unit.dp
import com.dailyquest.app.ui.components.Bold
import com.dailyquest.app.ui.components.Btn
import com.dailyquest.app.ui.components.BtnStyle
import com.dailyquest.app.ui.components.Txt
import com.dailyquest.app.ui.room.LocalAppContainer
import com.dailyquest.app.ui.theme.DQColors

/**
 * 상점·꾸미기 화면 위쪽에 놓는 "내 아바타" 미리보기 (STEP 9-7).
 * - 내가 입은 옷 위에 입어 보는 옷을 덮어 3D로 보여준다.
 * - 입어 보는 중이면 아이템 이름과 "원래대로" 버튼을 보여준다.
 * - 성별을 정하지 않은 계정, 불러오기 실패, 불러오는 중을 각각 안내한다.
 * - UI 테스트 환경([com.dailyquest.app.data.auth.AppContainer.avatar3dEnabled] = false)에서는 3D 대신 빈 상자를 그린다.
 *   3D 화면은 매 프레임 계속 그려서, "화면이 멈출 때까지 기다리는" 기존 UI 테스트가 시간 초과로 실패하기 때문이다.
 *
 * 높이는 바깥 [modifier]로 고정해서 쓴다. 3D 화면 크기가 자주 바뀌면 화면 버퍼를 다시 만들게 된다(STEP 9-2).
 * 바깥 높이가 바뀌는 자리(상점 시트)에서는 [sceneHeight]로 3D 화면만 그 높이로 **고정**하고 [sceneScale]만큼 줄여 그린다.
 * 그러면 3D 화면 크기는 한 번도 바뀌지 않는다(바꾸면 에뮬레이터에서 EGL_BAD_ALLOC으로 다시 그리지 못했다). 글자·버튼은 줄이지 않는다.
 */
@Composable
fun MyAvatarPreview(
    state: MyAvatarUiState,
    onRetry: () -> Unit,
    onClearTryOn: () -> Unit,
    modifier: Modifier = Modifier,
    sceneHeight: Dp? = null,
    sceneScale: () -> Float = { 1f },
) {
    // 상점 시트 방식([sceneHeight])에서는 3D 화면이 보이는 상자보다 크게(시트 아래까지) 놓인다. SceneView의 내장 돌리기는
    // 그 큰 화면 전체에서 손가락을 받아 시트 끌기를 가로채므로 끄고, 보이는 상자 안에서만 좌우로 끌어 직접 돌린다.
    var yaw by rememberSaveable { mutableFloatStateOf(0f) }
    val render3d = LocalAppContainer.current.avatar3dEnabled
    val shape = RoundedCornerShape(22.dp)
    Box(modifier.clip(shape).background(DQColors.Soft, shape).testTag("myAvatarPreview")) {
        val avatar = state.avatar
        when {
            avatar == null && state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
            avatar == null && state.error != null -> Column(
                Modifier.align(Alignment.Center).padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Txt(state.error, size = 13f, color = DQColors.Muted)
                Btn("다시 시도", Modifier.testTag("myAvatarRetry"), style = BtnStyle.Outline, onClick = onRetry)
            }
            avatar != null && avatar.needsGender -> Txt(
                "캐릭터를 먼저 만들어 주세요",
                size = 13f, color = DQColors.Muted,
                modifier = Modifier.align(Alignment.Center).testTag("myAvatarNeedsGender"),
            )
            render3d && sceneHeight == null -> AvatarView(parts = state.previewParts, modifier = Modifier.fillMaxSize())
            render3d -> AvatarView(
                parts = state.previewParts,
                interactive = false,
                camera = remember(yaw) { AvatarCamera.FULL_BODY.orbitAroundY(yaw) },
                modifier = if (sceneHeight == null) Modifier.fillMaxSize()
                else Modifier.align(Alignment.TopCenter).fillMaxWidth()
                    .wrapContentHeight(Alignment.Top, unbounded = true).requiredHeight(sceneHeight)
                    .graphicsLayer {
                        val s = sceneScale()
                        transformOrigin = TransformOrigin(0.5f, 0f)
                        scaleX = s
                        scaleY = s
                    },
            )
            else -> Txt(
                "3D 미리보기 (테스트 환경)",
                size = 12f, color = DQColors.Muted,
                modifier = Modifier.align(Alignment.Center).testTag("myAvatarPlaceholder"),
            )
        }
        if (render3d && sceneHeight != null && avatar != null && !avatar.needsGender) {
            // 보이는 상자 안에서 좌우로 끌면 돌린다(오른쪽으로 끌면 아바타가 오른쪽으로 돈다).
            Box(
                Modifier.matchParentSize().pointerInput(Unit) {
                    detectHorizontalDragGestures { change, dx ->
                        change.consume()
                        yaw -= dx * ROTATE_DEGREES_PER_PX
                    }
                }.testTag("myAvatarRotate"),
            )
        }
        if (state.isTryingOn) {
            Row(
                Modifier.align(Alignment.BottomCenter).padding(10.dp)
                    .background(DQColors.White, RoundedCornerShape(16.dp)).padding(start = 12.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Txt(
                    "입어보는 중: " + state.tryOn.values.joinToString { it.name },
                    size = 12f, weight = Bold, maxLines = 1,
                    modifier = Modifier.weight(1f, fill = false).testTag("myAvatarTryOnLabel"),
                )
                Btn("원래대로", Modifier.testTag("myAvatarTryOnClear"), style = BtnStyle.Soft, onClick = onClearTryOn)
            }
        }
    }
}

/** 좌우로 1픽셀 끌 때 도는 각도. */
private const val ROTATE_DEGREES_PER_PX = 0.35f

/** 카메라를 바라보는 점 둘레로 세로축을 기준으로 [degrees]만큼 돌린다(아바타를 옆·뒤에서 보기). */
internal fun AvatarCamera.orbitAroundY(degrees: Float): AvatarCamera {
    if (degrees == 0f) return this
    val r = Math.toRadians(degrees.toDouble())
    val c = kotlin.math.cos(r).toFloat()
    val sn = kotlin.math.sin(r).toFloat()
    val dx = position.x - target.x
    val dz = position.z - target.z
    return AvatarCamera(Position(target.x + dx * c + dz * sn, position.y, target.z - dx * sn + dz * c), target)
}

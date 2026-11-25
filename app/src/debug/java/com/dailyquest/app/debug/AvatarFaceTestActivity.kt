package com.dailyquest.app.debug

import android.graphics.Bitmap
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.dailyquest.app.ui.avatar.AvatarCamera
import com.dailyquest.app.ui.avatar.AvatarLoadStatus
import com.dailyquest.app.ui.avatar.AvatarSnapshot
import com.dailyquest.app.ui.avatar.AvatarView
import com.dailyquest.app.ui.theme.DQColors
import com.dailyquest.app.ui.theme.DailyQuestTheme
import io.github.sceneview.math.Position
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale
import kotlin.math.roundToInt

/**
 * 프로필 얼굴 1단계 실험 화면 (디버그 전용, 런처 아이콘 없음).
 * `adb shell am start -n com.dailyquest.app/.debug.AvatarFaceTestActivity`
 *
 * - 3D 아바타를 얼굴 카메라로 비추고 "찍기"로 사진을 뽑는다(투명 배경 PNG). 동그랗게 잘라 프로필처럼 보여준다.
 * - 카메라 높이·거리를 바꿔 가며 얼굴이 잘 들어오는 값을 찾는다.
 * - "모두 찍기": 테스트 의상을 차례로 입혀 하나씩 찍고, 바꾼 순간부터 찍힐 때까지의 시간을 잰다.
 * - 찍은 PNG는 기기 `files/face_test/`에 저장한다(adb pull로 확인).
 */
class AvatarFaceTestActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { DailyQuestTheme { AvatarFaceTestScreen() } }
    }
}

private class Shot(val label: String, val bitmap: Bitmap, val ms: Long)

@Composable
private fun AvatarFaceTestScreen() {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()

    var selected by remember { mutableIntStateOf(0) }
    val outfit = TEST_OUTFITS[selected]
    var camY by remember { mutableStateOf(AvatarCamera.FACE.position.y) }
    var camZ by remember { mutableStateOf(AvatarCamera.FACE.position.z) }
    val camera = AvatarCamera(Position(0f, camY, camZ), Position(0f, camY - 0.03f, 0f))

    var status by remember { mutableStateOf<AvatarLoadStatus>(AvatarLoadStatus.Loading) }
    var framesSinceReady by remember { mutableIntStateOf(0) }
    var bounds by remember { mutableStateOf<android.graphics.Rect?>(null) }
    var changedAt by remember { mutableStateOf(SystemClock.elapsedRealtime()) }
    val shots = remember { mutableStateListOf<Shot>() }
    var busy by remember { mutableStateOf(false) }
    // 찍기 전에 기다리는 정도. 화면에서 바꿔 보며 얼마나 기다려야 하는지 찾는다.
    var settleFrames by remember { mutableIntStateOf(10) }
    var settleMs by remember { mutableStateOf(500L) }

    fun select(i: Int) {
        if (i == selected) return
        status = AvatarLoadStatus.Loading
        framesSinceReady = 0
        changedAt = SystemClock.elapsedRealtime()
        selected = i
    }

    /**
     * 준비되고 3D 화면이 [settleFrames]번 더 그린 뒤 [settleMs] 더 기다렸다 찍는다(새 모습이 화면에 올라오기까지).
     * 처음에는 3번만 기다렸더니 에뮬레이터에서 이전 의상이 찍힌 경우가 있었다(1단계 기록 참고).
     */
    suspend fun shoot(label: String) {
        snapshotFlow { status is AvatarLoadStatus.Ready && framesSinceReady >= settleFrames }.first { it }
        delay(settleMs)
        val b = bounds ?: return
        // 비어 있지 않고 두 번 연달아 같은 사진일 때만 받는다(빈 사진·이전 모습 거르기)
        val bmp = AvatarSnapshot.captureSettled(view.rootView, b) ?: return
        val ms = SystemClock.elapsedRealtime() - changedAt
        shots += Shot(label, bmp, ms)
        val dir = File(context.filesDir, "face_test").apply { mkdirs() }
        File(dir, "${shots.size}_${label.replace(' ', '_')}.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    Column(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("얼굴 테스트 (디버그 전용)", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TEST_OUTFITS.forEachIndexed { i, o -> FilterChip(i == selected, { select(i) }, { Text(o.label) }) }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            AssistChip(onClick = { camY += 0.03f }, label = { Text("위로") })
            AssistChip(onClick = { camY -= 0.03f }, label = { Text("아래로") })
            AssistChip(onClick = { camZ -= 0.05f }, label = { Text("가깝게") })
            AssistChip(onClick = { camZ += 0.05f }, label = { Text("멀게") })
            Text("높이 %.2f · 거리 %.2f".format(Locale.US, camY, camZ), style = MaterialTheme.typography.labelSmall, modifier = Modifier.testTag("faceTestCamera"))
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // 정사각형 3D 화면 하나. 이 화면 그대로 찍는다.
            Box(
                Modifier.size(200.dp).background(DQColors.Soft).testTag("faceTestView")
                    .onGloballyPositioned { c ->
                        val r = c.boundsInWindow()
                        bounds = android.graphics.Rect(r.left.roundToInt(), r.top.roundToInt(), r.right.roundToInt(), r.bottom.roundToInt())
                    },
            ) {
                AvatarView(
                    parts = outfit.codes.mapValues { (_, code) -> modelUrl(outfit.body, code) },
                    modifier = Modifier.fillMaxSize(),
                    interactive = false,
                    camera = camera,
                    onStatusChange = { status = it },
                    onFrame = { if (status is AvatarLoadStatus.Ready) framesSinceReady++ },
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(
                    enabled = !busy,
                    onClick = { scope.launch { busy = true; shoot(outfit.label); busy = false } },
                    label = { Text("찍기") },
                    modifier = Modifier.testTag("faceTestShoot"),
                )
                AssistChip(
                    enabled = !busy,
                    onClick = {
                        scope.launch {
                            busy = true
                            TEST_OUTFITS.indices.forEach { i ->
                                select(i)
                                shoot(TEST_OUTFITS[i].label)
                            }
                            busy = false
                        }
                    },
                    label = { Text("모두 찍기") },
                    modifier = Modifier.testTag("faceTestShootAll"),
                )
            }
        }
        val avg = if (shots.isEmpty()) 0 else shots.map { it.ms }.average().roundToInt()
        Text(
            "${outfit.label} · ${if (status is AvatarLoadStatus.Ready) "준비됨" else "불러오는 중…"} · 찍음 ${shots.size}장 · 평균 ${avg}ms",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.testTag("faceTestStatus"),
        )
        // 찍은 사진을 프로필처럼 동그랗게(큰 것 64dp, 홈 지도 크기 32dp)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            shots.forEach { s ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(s.bitmap.asImageBitmap(), s.label, Modifier.size(64.dp).clip(CircleShape).background(DQColors.Soft))
                    Image(s.bitmap.asImageBitmap(), null, Modifier.padding(top = 4.dp).size(32.dp).clip(CircleShape).background(DQColors.Soft))
                    Text("${s.label}\n${s.ms}ms", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        Box(Modifier.fillMaxWidth())
    }
}

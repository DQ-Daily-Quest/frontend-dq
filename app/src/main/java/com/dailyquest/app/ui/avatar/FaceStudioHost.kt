package com.dailyquest.app.ui.avatar

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.dailyquest.app.data.avatar.FaceKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream
import kotlin.math.roundToInt

/**
 * 프로필 얼굴을 실제로 찍는 보이지 않는 3D 화면 (프로필 얼굴 2단계).
 *
 * 화면의 **가장 아래 층**(다른 화면이 덮는 자리)에 둔다. TextureView는 화면에 붙어 그려져야 내용이 생기므로 숨기지 않고 가려 둔다.
 * - 대기열이 처음 생길 때 3D 화면을 만들고, 그 뒤로는 계속 둔다(3D 엔진을 만들 때 화면이 잠깐 멈추므로 한 번만).
 * - 맨 앞 얼굴을 입혀 준비되면 [AvatarSnapshot.captureSettled]로 찍고, [SIZE_PX]로 줄여 PNG로 넘긴다.
 * - 한 얼굴에 [TIMEOUT_MS]를 넘기면 실패로 넘긴다(대기열이 막히지 않게).
 */
@Composable
fun FaceStudioHost(studio: FaceStudio, modifier: Modifier = Modifier) {
    val pending by studio.pending.collectAsState()
    // 처음 대기열이 생긴 뒤로는 계속 둔다(3D 엔진을 다시 만들지 않게).
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(pending.isNotEmpty()) { if (pending.isNotEmpty()) started = true }
    if (!started) return

    val view = LocalView.current
    val current = pending.firstOrNull()
    var status by remember { mutableStateOf<AvatarLoadStatus>(AvatarLoadStatus.Loading) }
    var framesSinceReady by remember { mutableIntStateOf(0) }
    var bounds by remember { mutableStateOf<android.graphics.Rect?>(null) }

    Box(
        modifier.size(HOST_DP.dp).testTag("faceStudioHost").onGloballyPositioned { c ->
            val r = c.boundsInWindow()
            bounds = android.graphics.Rect(r.left.roundToInt(), r.top.roundToInt(), r.right.roundToInt(), r.bottom.roundToInt())
        },
    ) {
        AvatarView(
            parts = current?.parts.orEmpty(),
            modifier = Modifier.fillMaxSize(),
            interactive = false,
            camera = AvatarCamera.FACE,
            onStatusChange = {
                status = it
                if (it !is AvatarLoadStatus.Ready) framesSinceReady = 0
            },
            onFrame = { if (status is AvatarLoadStatus.Ready && framesSinceReady < 1000) framesSinceReady++ },
        )
    }

    LaunchedEffect(current?.id) {
        val key = current ?: return@LaunchedEffect
        status = AvatarLoadStatus.Loading
        framesSinceReady = 0
        val png = withTimeoutOrNull(TIMEOUT_MS) {
            // 이 얼굴의 파츠로 준비되고 몇 프레임 더 그린 뒤(또는 실패하면 바로)
            val (s, _) = snapshotFlow { status to framesSinceReady }
                .first { (st, frames) -> (st is AvatarLoadStatus.Ready && frames >= SETTLE_FRAMES) || st is AvatarLoadStatus.Failed }
            if (s is AvatarLoadStatus.Failed) return@withTimeoutOrNull null
            delay(SETTLE_MS)
            val b = bounds ?: return@withTimeoutOrNull null
            val bmp = AvatarSnapshot.captureSettled(view.rootView, b) ?: return@withTimeoutOrNull null
            withContext(Dispatchers.Default) { toPng(bmp) }
        }
        studio.complete(key, png)
    }
}

/** 찍은 사진을 [FaceStudioHost.SIZE_PX] 정사각형으로 줄여 PNG로. */
private fun toPng(bmp: Bitmap): ByteArray {
    val scaled = if (bmp.width == SIZE_PX) bmp else Bitmap.createScaledBitmap(bmp, SIZE_PX, SIZE_PX, true)
    return ByteArrayOutputStream().use { out -> scaled.compress(Bitmap.CompressFormat.PNG, 100, out); out.toByteArray() }
}

/** 화면에 붙일 얼굴 사진 크기. 64dp 동그라미(약 170px)를 또렷하게 그릴 만큼. */
private const val SIZE_PX = 256
private const val HOST_DP = 128
private const val SETTLE_FRAMES = 10
private const val SETTLE_MS = 300L
private const val TIMEOUT_MS = 30_000L

/** 읽어 둔 얼굴 그림. 화면을 오갈 때 다시 읽지 않는다. 한 장 약 256KB(256×256). */
private object FaceMemory : LruCache<String, ImageBitmap>(8 * 1024 * 1024) {
    override fun sizeOf(key: String, value: ImageBitmap): Int = value.width * value.height * 4
}

/** 읽어 둔 얼굴 그림을 비운다 (디버그·테스트에서 다시 찍어 볼 때). */
fun clearAvatarFaceMemory() = FaceMemory.evictAll()

/**
 * [parts] 착용 조합의 얼굴 그림. 메모리 → 기기 저장 → 없으면 [studio]에 찍기를 부탁하고, 찍히면 다시 그린다.
 * 아직 없으면 null(화면은 기본 그림을 보여준다). [parts]가 null이거나 비어 있어도 null.
 */
@Composable
fun rememberAvatarFace(parts: Map<com.dailyquest.app.data.avatar.AvatarPart, String>?, studio: FaceStudio): ImageBitmap? {
    val key = remember(parts) { parts?.takeIf { it.isNotEmpty() }?.let(::FaceKey) }
    val version by studio.version.collectAsState()
    val face by produceState(key?.let { FaceMemory.get(it.id) }, key, version) {
        if (key == null) { value = null; return@produceState }
        FaceMemory.get(key.id)?.let { value = it; return@produceState }
        val bytes = studio.store.get(key)
        if (bytes == null) {
            studio.request(key)
            return@produceState
        }
        val img = withContext(Dispatchers.Default) { BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() }
        if (img != null) {
            FaceMemory.put(key.id, img)
            value = img
        }
    }
    return face
}

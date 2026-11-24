package com.dailyquest.app.ui.decor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.dailyquest.app.data.decor.PlacedFurniture
import com.dailyquest.app.data.decor.RoomGeometry
import com.dailyquest.app.data.decor.RoomLayout
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlin.math.roundToInt

/**
 * 방 화면의 확대·이동 상태. 화면 밖(편집 화면 등)에서 읽거나 되돌릴 수 있게 따로 둔다.
 * 화면 크기를 알기 전에는 [viewport]가 null이다.
 */
@Stable
class RoomViewState {
    var viewport: RoomViewport? by mutableStateOf(null)

    /** 두 번 탭과 같다: 원래 크기. */
    fun reset() {
        viewport = viewport?.reset()
    }

    internal fun onSize(width: Float, height: Float, room: RoomGeometry) {
        if (width <= 0f || height <= 0f) return
        val current = viewport
        viewport = if (current == null || current.contentWidth != room.widthPx.toFloat() || current.contentHeight != room.heightPx.toFloat()) {
            RoomViewport.fit(width, height, room.widthPx.toFloat(), room.heightPx.toFloat())
        } else {
            current.resized(width, height)
        }
    }
}

@Composable
fun rememberRoomViewState(): RoomViewState = remember { RoomViewState() }

/**
 * 방과 놓인 가구를 보여준다 (STEP 10-4). 보여주기만 하고 가구를 놓거나 옮기지는 않는다(6단계).
 *
 * - 위치·그리는 순서는 [RoomLayout] 계산을 그대로 쓴다(에셋 팩 미리보기와 같은 방식).
 *   가구 그림 왼쪽 위는 방 이미지 기준 정수 픽셀로 맞춘다(에셋 팩도 반올림해서 붙였다). 방과 가구에 같은 배율을 쓴다.
 * - [zoomable]이면 두 손가락으로 1~3배 확대·축소, 확대 상태에서 한 손가락으로 이동, 두 번 탭하면 원래 크기.
 *   **1배에서 한 손가락으로 끄는 동작은 가로채지 않는다.** 방 화면처럼 세로 스크롤 안에 넣어도 스크롤이 막히지 않게 하기 위해서다.
 * - 그림을 받는 동안 가운데에 진행 표시, 하나라도 실패하면 "다시 시도". 받은 그림부터 하나씩 바로 그린다.
 *
 * @param backgroundUrl 방 그림 주소. null이면 가구만 그린다.
 */
@Composable
fun RoomView(
    room: RoomGeometry,
    backgroundUrl: String?,
    furniture: List<PlacedFurniture>,
    modifier: Modifier = Modifier,
    state: RoomViewState = rememberRoomViewState(),
    zoomable: Boolean = true,
    images: DecorImageSource = DecorImages.source(LocalContext.current),
    /** 그림 받기가 한 차례 끝날 때마다(일부 실패 포함) 호출된다. 실패한 그림 수를 넘긴다. */
    onImagesSettled: (failedCount: Int) -> Unit = {},
) {
    val drawList = remember(room, furniture) {
        val grid = furniture.map { it.placement }
        RoomLayout.drawOrder(grid).map { i ->
            val b = RoomLayout.spriteBounds(room, grid[i])
            Sprite(furniture[i].item.spriteUrl(grid[i].rotation), IntOffset(b.left.roundToInt(), b.top.roundToInt()))
        }
    }
    val urls = remember(backgroundUrl, drawList) { (listOfNotNull(backgroundUrl) + drawList.mapNotNull { it.url }).distinct() }

    val loaded = remember { mutableStateMapOf<String, ImageBitmap>() }
    var failed by remember { mutableStateOf(emptySet<String>()) }
    var retry by remember { mutableIntStateOf(0) }
    val settled by rememberUpdatedState(onImagesSettled)
    LaunchedEffect(urls, retry) {
        failed = emptySet()
        val missing = urls.filter { it !in loaded }
        // 하나 받을 때마다 바로 화면에 넣는다(메인 스레드에서 상태를 바꾼다). 받는 일 자체는 images가 다른 스레드에서 한다.
        coroutineScope {
            missing.map { url ->
                async { images.load(url).onSuccess { loaded[url] = it }.onFailure { failed = failed + url } }
            }.awaitAll()
        }
        settled(failed.size)
    }
    val loading = urls.any { it !in loaded && it !in failed }

    val gestures = if (zoomable) {
        Modifier
            .pointerInput(state) { detectTapGestures(onDoubleTap = { state.reset() }) }
            .pointerInput(state) { detectZoomAndPan(state) }
    } else {
        Modifier
    }

    Box(
        modifier
            .clipToBounds()
            .onSizeChanged { state.onSize(it.width.toFloat(), it.height.toFloat(), room) }
            .then(gestures)
            .testTag("roomView"),
    ) {
        Canvas(Modifier.matchParentSize()) {
            val vp = state.viewport ?: return@Canvas
            withTransform({
                translate(vp.offsetX, vp.offsetY)
                scale(vp.scale, vp.scale, pivot = Offset.Zero)
            }) {
                backgroundUrl?.let(loaded::get)?.let { drawImage(it, dstOffset = IntOffset.Zero, filterQuality = FilterQuality.Medium) }
                drawList.forEach { s ->
                    s.url?.let(loaded::get)?.let { drawImage(it, dstOffset = s.topLeft, filterQuality = FilterQuality.Medium) }
                }
            }
        }
        when {
            loading -> CircularProgressIndicator(Modifier.align(Alignment.Center).testTag("roomViewLoading"))
            failed.isNotEmpty() -> Column(
                Modifier.align(Alignment.Center).padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text("방 그림을 불러오지 못했어요", style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { retry++ }, modifier = Modifier.testTag("roomViewRetry")) { Text("다시 시도") }
            }
        }
    }
}

/** 그릴 가구 하나: 그림 주소와 방 이미지 기준 왼쪽 위(정수 픽셀). */
private class Sprite(val url: String?, val topLeft: IntOffset)

/**
 * 두 손가락 = 확대·축소(+ 함께 이동), 한 손가락 = 확대 상태에서만 이동.
 * 1배에서는 한 손가락 움직임을 소비하지 않아 바깥 스크롤이 그대로 받는다.
 */
private suspend fun androidx.compose.ui.input.pointer.PointerInputScope.detectZoomAndPan(state: RoomViewState) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        do {
            val event = awaitPointerEvent()
            val vp = state.viewport
            val pressed = event.changes.count { it.pressed }
            if (vp != null && event.changes.none { it.isConsumed }) {
                if (pressed >= 2) {
                    val c = event.calculateCentroid(useCurrent = true)
                    val pan = event.calculatePan()
                    state.viewport = vp.zoomedBy(event.calculateZoom(), c.x, c.y).pannedBy(pan.x, pan.y)
                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                } else if (pressed == 1 && vp.isZoomed) {
                    val pan = event.calculatePan()
                    if (pan != Offset.Zero) {
                        state.viewport = vp.pannedBy(pan.x, pan.y)
                        event.changes.forEach { if (it.positionChanged()) it.consume() }
                    }
                }
            }
        } while (event.changes.any { it.pressed })
    }
}

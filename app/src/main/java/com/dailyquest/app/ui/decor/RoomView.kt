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
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.dailyquest.app.data.decor.Cell
import com.dailyquest.app.data.decor.GridPlacement
import com.dailyquest.app.data.decor.PlacedFurniture
import com.dailyquest.app.data.decor.PxPoint
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
 * 편집 화면에서 손가락 동작을 받는 곳 (STEP 10-6). 번호는 [RoomView]에 넘긴 furniture 목록의 순번이다.
 * - 가구를 누르면 [onSelect](번호), 빈 곳을 한 번 누르면 [onSelect](null).
 * - 가구를 잡고 끌면 손가락이 가리키는 칸으로 [onMove]가 계속 불린다(칸이 바뀔 때마다).
 */
interface RoomEditor {
    fun onSelect(index: Int?)
    fun onMove(index: Int, start: Cell)
}

/**
 * 방과 놓인 가구를 보여준다 (STEP 10-4). 편집 모드(10-6)에서는 가구를 잡아 옮길 수 있다.
 *
 * - 위치·그리는 순서는 [RoomLayout] 계산을 그대로 쓴다(에셋 팩 미리보기와 같은 방식).
 *   가구 그림 왼쪽 위는 방 이미지 기준 정수 픽셀로 맞춘다(에셋 팩도 반올림해서 붙였다). 방과 가구에 같은 배율을 쓴다.
 * - [zoomable]이면 두 손가락으로 1~3배 확대·축소, 확대 상태에서 한 손가락으로 이동, 두 번 탭하면 원래 크기.
 *   **1배에서 한 손가락으로 끄는 동작은 가로채지 않는다.** 방 화면처럼 세로 스크롤 안에 넣어도 스크롤이 막히지 않게 하기 위해서다.
 * - 그림을 받는 동안 가운데에 진행 표시, 하나라도 실패하면 "다시 시도". 받은 그림부터 하나씩 바로 그린다.
 *
 * 편집 모드([editor]가 있으면, STEP 10-6):
 * - 가구를 잡고 끌면 가구가 칸에 맞춰 옮겨지고, 빈 곳을 끌면 (확대 상태에서) 화면이 움직인다. 두 손가락은 확대·축소.
 * - [selectedIndex] 가구는 바닥 칸을 초록 테두리로, [problemIndices] 가구(겹침·방 밖)는 바닥 칸과 그림을 빨갛게 표시한다.
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
    /** 한 번 탭 (예: 방 화면에서 크게 보기). [zoomable]과 관계없이 동작한다. 편집 모드에서는 쓰지 않는다. */
    onTap: (() -> Unit)? = null,
    editor: RoomEditor? = null,
    selectedIndex: Int? = null,
    problemIndices: Set<Int> = emptySet(),
) {
    val drawList = remember(room, furniture) {
        val grid = furniture.map { it.placement }
        RoomLayout.drawOrder(grid).map { i ->
            val b = RoomLayout.spriteBounds(room, grid[i])
            Sprite(i, furniture[i].item.spriteUrl(grid[i].rotation), IntOffset(b.left.roundToInt(), b.top.roundToInt()))
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

    val tap by rememberUpdatedState(onTap)
    val currentFurniture by rememberUpdatedState(furniture)
    val currentSelected by rememberUpdatedState(selectedIndex)
    val currentEditor by rememberUpdatedState(editor)
    val gestures = if (editor != null) {
        Modifier.pointerInput(state, room) {
            detectEditGestures(state, room, { currentFurniture.map { it.placement } }, { currentSelected }, { currentEditor })
        }
    } else {
        val taps = when {
            zoomable -> Modifier.pointerInput(state) { detectTapGestures(onTap = { tap?.invoke() }, onDoubleTap = { state.reset() }) }
            onTap != null -> Modifier.pointerInput(state) { detectTapGestures(onTap = { tap?.invoke() }) }
            else -> Modifier
        }
        if (zoomable) taps.pointerInput(state) { detectZoomAndPan(state) } else taps
    }

    Box(
        modifier
            .clipToBounds()
            .onSizeChanged { state.onSize(it.width.toFloat(), it.height.toFloat(), room) }
            .then(gestures)
            // 화면 읽기 프로그램과 UI 테스트가 무엇이 그려졌는지 알 수 있게 한다.
            .semantics { contentDescription = "방, 가구 ${furniture.size}개" }
            .testTag("roomView"),
    ) {
        Canvas(Modifier.matchParentSize()) {
            val vp = state.viewport ?: return@Canvas
            withTransform({
                translate(vp.offsetX, vp.offsetY)
                scale(vp.scale, vp.scale, pivot = Offset.Zero)
            }) {
                backgroundUrl?.let(loaded::get)?.let { drawImage(it, dstOffset = IntOffset.Zero, filterQuality = FilterQuality.Medium) }
                // 편집 표시: 바닥 칸 채우기는 가구 그림 아래에 그려 가구가 가리지 않는 부분만 보인다.
                problemIndices.forEach { i -> furniture.getOrNull(i)?.let { drawPath(footprint(room, it.placement), PROBLEM_FILL) } }
                selectedIndex?.let { i -> furniture.getOrNull(i)?.let { drawPath(footprint(room, it.placement), SELECTED_FILL) } }
                drawList.forEach { s ->
                    s.url?.let(loaded::get)?.let {
                        drawImage(
                            it, dstOffset = s.topLeft, filterQuality = FilterQuality.Medium,
                            colorFilter = if (s.index in problemIndices) PROBLEM_TINT else null,
                        )
                    }
                }
                // 선택 테두리는 가구 위에 그려 어디를 차지하는지 항상 보이게 한다.
                selectedIndex?.let { i ->
                    furniture.getOrNull(i)?.let {
                        val color = if (i in problemIndices) PROBLEM_LINE else SELECTED_LINE
                        drawPath(footprint(room, it.placement), color, style = Stroke(width = 3f / vp.scale))
                    }
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

/** 그릴 가구 하나: furniture 목록 순번, 그림 주소, 방 이미지 기준 왼쪽 위(정수 픽셀). */
private class Sprite(val index: Int, val url: String?, val topLeft: IntOffset)

private val SELECTED_FILL = Color(0x405DA37B)
private val SELECTED_LINE = Color(0xFF3F8A5C)
private val PROBLEM_FILL = Color(0x55E5484D)
private val PROBLEM_LINE = Color(0xFFE5484D)
private val PROBLEM_TINT = ColorFilter.tint(Color(0x66E5484D), BlendMode.SrcAtop)

/** 가구가 차지한 바닥 칸들의 테두리(마름모). 방 이미지 좌표. */
private fun footprint(room: RoomGeometry, p: GridPlacement): Path {
    val a = RoomLayout.groundPx(room, p.gridX.toFloat(), p.gridY.toFloat())
    val b = RoomLayout.groundPx(room, (p.gridX + p.width).toFloat(), p.gridY.toFloat())
    val c = RoomLayout.groundPx(room, (p.gridX + p.width).toFloat(), (p.gridY + p.depth).toFloat())
    val d = RoomLayout.groundPx(room, p.gridX.toFloat(), (p.gridY + p.depth).toFloat())
    return Path().apply { moveTo(a.x, a.y); lineTo(b.x, b.y); lineTo(c.x, c.y); lineTo(d.x, d.y); close() }
}

/**
 * 편집 모드 손가락 동작.
 * - 가구 위에서 누르기: 그 가구 선택. 이미 선택한 가구가 겹쳐 있으면 그것을 먼저 잡는다(뒤에 가려진 선택 가구도 옮길 수 있게).
 * - 가구를 잡고 끌기: 잡은 지점과 가구 바닥 가운데의 거리를 유지한 채 칸에 맞춰 옮긴다. 반 칸 넘게 움직여야 칸이 바뀐다.
 * - 빈 곳 끌기: 확대 상태면 화면 이동. 빈 곳을 움직이지 않고 떼면 선택 해제.
 * - 두 손가락: 확대·축소(+ 이동). 이때는 가구를 옮기지 않는다.
 */
private suspend fun androidx.compose.ui.input.pointer.PointerInputScope.detectEditGestures(
    state: RoomViewState,
    room: RoomGeometry,
    placements: () -> List<GridPlacement>,
    selected: () -> Int?,
    editor: () -> RoomEditor?,
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val vp0 = state.viewport ?: return@awaitEachGesture
        val p0 = vp0.toContent(down.position.x, down.position.y)
        val list = placements()
        val sel = selected()
        val hit = sel?.takeIf { it in list.indices && RoomLayout.spriteBounds(room, list[it]).contains(p0) }
            ?: RoomLayout.hitTest(room, list, p0)
        var grab: PxPoint? = null
        if (hit != null) {
            editor()?.onSelect(hit)
            val g = RoomLayout.groundPx(room, list[hit].centerX, list[hit].centerY)
            grab = PxPoint(p0.x - g.x, p0.y - g.y)
            down.consume()
        }
        var moved = false
        var multi = false
        do {
            val event = awaitPointerEvent()
            val vp = state.viewport ?: break
            val pressed = event.changes.filter { it.pressed }
            if (pressed.size >= 2) {
                multi = true
                val c = event.calculateCentroid(useCurrent = true)
                val pan = event.calculatePan()
                state.viewport = vp.zoomedBy(event.calculateZoom(), c.x, c.y).pannedBy(pan.x, pan.y)
                event.changes.forEach { if (it.positionChanged()) it.consume() }
            } else if (pressed.size == 1 && !multi) {
                val ch = pressed.first()
                if (ch.positionChanged()) {
                    if (hit != null && grab != null) {
                        val now = placements().getOrNull(hit)
                        if (now != null) {
                            val c = vp.toContent(ch.position.x, ch.position.y)
                            val start = RoomLayout.snapStart(room, PxPoint(c.x - grab.x, c.y - grab.y), now.width, now.depth)
                            editor()?.onMove(hit, start)
                        }
                        moved = true
                        ch.consume()
                    } else {
                        val d = ch.position - ch.previousPosition
                        if (vp.isZoomed) {
                            state.viewport = vp.pannedBy(d.x, d.y)
                            ch.consume()
                        }
                        if ((ch.position - down.position).getDistance() > viewConfiguration.touchSlop) moved = true
                    }
                }
            }
        } while (event.changes.any { it.pressed })
        if (hit == null && !moved && !multi) editor()?.onSelect(null)
    }
}

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

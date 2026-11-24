package com.dailyquest.app.ui.decor

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.activity.compose.BackHandler
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dailyquest.app.data.decor.AvailableFurniture
import com.dailyquest.app.data.decor.Cell
import com.dailyquest.app.data.decor.RoomCatalog
import com.dailyquest.app.ui.components.Bold
import com.dailyquest.app.ui.components.Btn
import com.dailyquest.app.ui.components.BtnStyle
import com.dailyquest.app.ui.components.DqScreen
import com.dailyquest.app.ui.components.NavHeader
import com.dailyquest.app.ui.components.Txt
import com.dailyquest.app.navigation.LocalNav
import com.dailyquest.app.ui.room.LocalAppContainer
import com.dailyquest.app.ui.theme.DQColors

/**
 * 방 꾸미기 편집 화면 (STEP 10-6). 방 화면의 "방 꾸미기" 버튼으로 연다.
 *
 * - 위: 방. 가구를 잡고 끌면 칸에 맞춰 옮겨진다. 두 손가락으로 확대, 확대한 뒤 빈 곳을 끌면 화면 이동.
 * - 가운데: 선택한 가구의 돌리기·빼기, 겹침·방 밖 개수.
 * - 아래: 놓을 수 있는 가구 목록(바닥·벽 제외). 누르면 방 가운데 가까운 빈자리에 놓인다. 남은 개수를 보여준다.
 * - 오른쪽 위 "저장"(STEP 10-7): 바뀐 것이 있을 때만 켜진다. 저장하면 방 화면으로 돌아간다.
 * - 저장하지 않고 나가려 하면(위 뒤로 버튼·기기 뒤로 버튼) 확인을 묻는다.
 */
@Composable
fun RoomDecorEditScreen(vm: RoomDecorEditViewModel, catalog: RoomCatalog) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val container = LocalAppContainer.current
    val images = container.decorImages ?: remember { DecorImages.source(context) }
    val viewState = rememberRoomViewState()

    val nav = LocalNav.current
    var askLeave by remember { mutableStateOf(false) }

    LaunchedEffect(ui.message) {
        ui.message?.let { Toast.makeText(context, it, Toast.LENGTH_SHORT).show(); vm.consumeMessage() }
    }
    LaunchedEffect(ui.saved) {
        if (ui.saved) {
            vm.consumeSaved()
            nav.popBackStack()
        }
    }
    // 바뀐 것이 있으면 나가기 전에 묻는다. 저장 중에는 응답을 기다린다.
    val leave: () -> Unit = {
        when {
            ui.saving -> Unit
            ui.dirty -> askLeave = true
            else -> { nav.popBackStack() }
        }
    }
    BackHandler(enabled = ui.dirty || ui.saving) { leave() }

    if (askLeave) {
        AlertDialog(
            onDismissRequest = { askLeave = false },
            title = { Text("저장하지 않고 나갈까요?") },
            text = { Text("바꾼 배치가 사라져요.") },
            confirmButton = {
                TextButton(onClick = { askLeave = false; nav.popBackStack() }, modifier = Modifier.testTag("decorLeaveConfirm")) { Text("나가기") }
            },
            dismissButton = {
                TextButton(onClick = { askLeave = false }, modifier = Modifier.testTag("decorLeaveCancel")) { Text("계속 꾸미기") }
            },
        )
    }

    DqScreen {
        Column(Modifier.fillMaxSize().navigationBarsPadding().testTag("decorEdit")) {
            NavHeader(
                "방 꾸미기",
                onBack = leave,
                right = {
                    Btn(
                        if (ui.saving) "저장 중…" else "저장",
                        Modifier.width(72.dp).height(34.dp).testTag("decorSave"),
                        size = 13f, enabled = ui.canSave, onClick = vm::save,
                    )
                },
            )
            when {
                ui.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.testTag("decorEditLoading"))
                }
                ui.loadError != null -> Column(
                    Modifier.fillMaxSize().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Txt(ui.loadError.orEmpty(), size = 14f, align = TextAlign.Center, modifier = Modifier.testTag("decorEditError"))
                    Btn("다시 시도", Modifier.padding(top = 16.dp).width(160.dp).height(44.dp).testTag("decorEditRetry"), onClick = vm::load)
                }
                else -> EditBody(ui, vm, catalog, images, viewState)
            }
        }
    }
}

@Composable
private fun EditBody(
    ui: DecorEditUiState,
    vm: RoomDecorEditViewModel,
    catalog: RoomCatalog,
    images: DecorImageSource,
    viewState: RoomViewState,
) {
    val keys = ui.items.map { it.key }
    val editor = remember(vm) {
        object : RoomEditor {
            // 화면이 최신 목록으로 다시 그려진 뒤의 순번을 쓰도록 매번 현재 상태에서 찾는다.
            override fun onSelect(index: Int?) = vm.select(index?.let { vm.ui.value.items.getOrNull(it)?.key })
            override fun onMove(index: Int, start: Cell) {
                vm.ui.value.items.getOrNull(index)?.let { vm.moveTo(it.key, start) }
            }
        }
    }
    val selectedIndex = ui.selectedKey?.let { keys.indexOf(it) }?.takeIf { it >= 0 }
    val problemIndices = keys.indices.filterTo(HashSet()) { keys[it] in ui.problemKeys }

    Column(Modifier.fillMaxSize()) {
        RoomView(
            room = catalog.room,
            backgroundUrl = ui.background?.imageUrl ?: com.dailyquest.app.data.decor.DecorRepository.DEFAULT_ROOM_IMAGE_URL,
            furniture = ui.furniture,
            modifier = Modifier.fillMaxWidth().aspectRatio(catalog.room.widthPx / catalog.room.heightPx.toFloat())
                .background(DQColors.Soft).testTag("decorEditRoom"),
            state = viewState,
            images = images,
            editor = editor,
            selectedIndex = selectedIndex,
            problemIndices = problemIndices,
        )

        // 상태 줄: 가구 수, 문제 수, 원래 크기
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Txt(
                buildString {
                    append("가구 ${ui.items.size}개")
                    if (ui.problemKeys.isNotEmpty()) append(" · 겹치거나 방 밖 ${ui.problemKeys.size}개")
                },
                size = 12.5f,
                color = if (ui.problemKeys.isEmpty()) DQColors.Muted else DQColors.Orange,
                modifier = Modifier.weight(1f).testTag("decorEditStatus"),
            )
            if (viewState.viewport?.isZoomed == true) {
                Btn("원래 크기", Modifier.width(84.dp).height(30.dp).testTag("decorEditResetZoom"), style = BtnStyle.Outline, size = 12f) {
                    viewState.reset()
                }
            }
        }

        // 선택한 가구 도구
        val selected = ui.selected
        Row(
            Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 16.dp).testTag("decorEditTools"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (selected == null) {
                Txt("가구를 누르면 돌리거나 뺄 수 있어요. 잡고 끌면 옮겨져요.", size = 12f, color = DQColors.Muted)
            } else {
                Txt(selected.item.name, size = 14f, weight = Bold, maxLines = 1, modifier = Modifier.weight(1f).testTag("decorEditSelected"))
                Btn("돌리기", Modifier.width(76.dp).height(36.dp).testTag("decorRotate"), style = BtnStyle.Outline, size = 13f, onClick = vm::rotateSelected)
                Btn("빼기", Modifier.width(64.dp).height(36.dp).testTag("decorRemove"), style = BtnStyle.Outline, size = 13f, onClick = vm::removeSelected)
            }
        }

        Txt("가구 넣기", size = 14f, weight = Bold, modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 6.dp))
        val furniture = ui.choices?.furniture.orEmpty()
        if (furniture.isEmpty()) {
            Txt(
                "방 멤버가 가진 가구가 없어요. 상점의 \"우리 집\"에서 가구를 사 보세요.",
                size = 12.5f, color = DQColors.Muted,
                modifier = Modifier.padding(horizontal = 16.dp).testTag("decorTrayEmpty"),
            )
        } else {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.testTag("decorTray"),
            ) {
                items(furniture, key = { it.item.itemId }) { f ->
                    TrayCard(f, ui.remaining(f), images) { vm.add(f) }
                }
            }
        }
    }
}

/** 가구 목록 카드 하나: 그림, 이름, 남은 개수. 더 놓을 수 없으면 흐리게(눌러도 이유를 알려준다). */
@Composable
private fun TrayCard(f: AvailableFurniture, remaining: Int, images: DecorImageSource, onAdd: () -> Unit) {
    val usable = f.spec != null && remaining > 0
    val shape = RoundedCornerShape(14.dp)
    Column(
        Modifier.width(92.dp)
            .clip(shape)
            .border(1.dp, DQColors.Soft, shape)
            .background(DQColors.White, shape)
            .clickable(onClick = onAdd)
            .padding(8.dp)
            .alpha(if (usable) 1f else 0.45f)
            .testTag("decorTray_${f.item.itemId}"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        DecorThumb(f.item.imageUrl, images, Modifier.size(64.dp))
        Txt(f.item.name, size = 11.5f, maxLines = 1, align = TextAlign.Center)
        Txt(
            when {
                f.spec == null -> "업데이트 필요"
                else -> "남은 ${remaining}개"
            },
            size = 11f, color = if (usable) DQColors.Green else DQColors.Muted, weight = Bold,
            modifier = Modifier.testTag("decorTrayRemaining_${f.item.itemId}"),
        )
    }
}

/** 가구 그림을 칸에 잘리지 않게 맞춰 보여준다. 방 그림과 같은 캐시를 쓴다. */
@Composable
fun DecorThumb(url: String?, images: DecorImageSource, modifier: Modifier = Modifier) {
    val bitmap by produceState<ImageBitmap?>(null, url, images) {
        value = url?.let { images.load(it).getOrNull() }
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        bitmap?.let { Image(it, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize()) }
    }
}

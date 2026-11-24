package com.dailyquest.app.ui.decor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dailyquest.app.data.decor.RoomCatalogs
import com.dailyquest.app.ui.components.Bold
import com.dailyquest.app.ui.components.Btn
import com.dailyquest.app.ui.components.BtnStyle
import com.dailyquest.app.ui.components.Txt
import com.dailyquest.app.ui.room.LocalAppContainer
import com.dailyquest.app.ui.theme.DQColors

/**
 * 방 화면에 넣는 "꾸민 방" (STEP 10-5). 예전 고정 일러스트(RoomInterior) 자리를 대신한다.
 *
 * - 멤버: 서버에 저장된 배치를 보여준다. 한 번도 저장하지 않은 방은 기본 방. 카드 안쪽 아래에 마지막으로 꾸민 사람과 "방 꾸미기" 버튼.
 * - 비멤버: 꾸미기 API가 멤버 전용이라 부르지 않고 기본 방만 보여준다.
 * - 방을 누르면 크게 보기 창이 열린다(두 손가락 확대·이동, 두 번 탭 되돌리기).
 *   카드 안에서는 확대하지 않는다. 방 화면이 세로 스크롤이라 손가락 동작이 겹치지 않게 하기 위해서다.
 *
 * ViewModel은 방 ID별로 이 안에서 만든다(앱 Navigation 파일은 건드리지 않는다).
 */
@Composable
fun RoomDecorCard(
    roomId: Long,
    isMember: Boolean,
    onDecorate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val container = LocalAppContainer.current
    val catalog = remember { RoomCatalogs.load(context) }
    val images = container.decorImages ?: remember { DecorImages.source(context) }
    val vm: RoomDecorViewModel = viewModel(key = "roomDecor-$roomId", factory = RoomDecorViewModel.Factory(container, roomId, catalog))
    val ui by vm.ui.collectAsStateWithLifecycle()
    var fullScreen by rememberSaveable(roomId) { mutableStateOf(false) }

    LaunchedEffect(isMember) { vm.setMember(isMember) }

    val furniture = ui.decor?.furniture.orEmpty()
    val shape = RoundedCornerShape(20.dp)
    // 크기는 예전 고정 일러스트(RoomInterior, 358×252 디자인)와 같은 비율이다. 방 화면의 다른 부분 위치가 바뀌지 않게 하기 위해서다.
    // 방 그림(4:3)은 그 안에 맞춰 넣고 남는 양옆은 배경색으로 둔다. 안내 문구도 카드 안쪽 아래에 넣는다.
    Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(CARD_WIDTH / CARD_HEIGHT)
            .clip(shape)
            .border(1.dp, Color(0xFFE8D9BF), shape)
            .background(DQColors.Soft, shape)
            .testTag("roomDecorCard"),
    ) {
        RoomView(
            room = catalog.room,
            backgroundUrl = ui.backgroundUrl,
            furniture = furniture,
            modifier = Modifier.fillMaxSize(),
            zoomable = false,
            images = images,
            onTap = { fullScreen = true },
        )
        if (ui.loading && ui.decor == null) {
            CircularProgressIndicator(Modifier.align(Alignment.Center).size(28.dp).testTag("roomDecorLoading"), strokeWidth = 3.dp)
        }
        Row(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            RoomDecorCaption(ui, onRetry = vm::refresh, modifier = Modifier.weight(1f))
            if (isMember) {
                Btn(
                    "방 꾸미기",
                    Modifier.width(86.dp).height(32.dp).testTag("roomDecorEdit"),
                    style = BtnStyle.Outline, size = 12f, onClick = onDecorate,
                )
            }
        }
    }

    if (fullScreen) {
        RoomFullScreen(
            onClose = { fullScreen = false },
        ) {
            RoomView(
                room = catalog.room,
                backgroundUrl = ui.backgroundUrl,
                furniture = furniture,
                modifier = Modifier.fillMaxWidth().aspectRatio(catalog.room.widthPx / catalog.room.heightPx.toFloat()).testTag("roomDecorFullView"),
                zoomable = true,
                images = images,
            )
        }
    }
}

/** 예전 고정 일러스트의 디자인 크기 (RoomScreen의 RoomInterior height = 252f, 358 디자인 캔버스). */
private const val CARD_WIDTH = 358f
private const val CARD_HEIGHT = 252f

/** 카드 안쪽 아래의 작은 안내 띠. 오류가 있으면 다시 시도. 내용이 없으면 그리지 않는다. */
@Composable
private fun RoomDecorCaption(ui: RoomDecorUiState, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    val text: String? = when {
        !ui.isMember -> "방에 참여하면 멤버들이 꾸민 모습을 볼 수 있어요."
        ui.error != null -> ui.error
        ui.decor == null -> null
        ui.decor.updatedBy != null -> "마지막으로 꾸민 사람: ${ui.decor.updatedBy.nickname}"
        else -> "아직 꾸미지 않은 방이에요. 가구를 놓아 보세요."
    }
    val kept = ui.decor?.kept?.size ?: 0
    if (text == null && kept == 0) {
        Box(modifier)
        return
    }
    Row(
        // 방 그림 위에 겹치므로 반투명 흰 바탕으로 글자를 읽기 쉽게 한다.
        modifier.background(DQColors.White.copy(alpha = 0.85f), RoundedCornerShape(10.dp)).padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Column(Modifier.weight(1f)) {
            text?.let {
                Txt(
                    it, size = 11f, maxLines = 2,
                    color = if (ui.error != null) DQColors.Orange else DQColors.Muted,
                    modifier = Modifier.testTag("roomDecorCaption"),
                )
            }
            if (kept > 0) {
                Txt(
                    "앱을 업데이트하면 보이는 가구가 ${kept}개 있어요.", size = 10.5f, color = DQColors.Muted,
                    modifier = Modifier.testTag("roomDecorKept"),
                )
            }
        }
        if (ui.error != null) {
            Txt(
                "다시 시도", size = 12f, weight = Bold, color = DQColors.Green,
                modifier = Modifier.testTag("roomDecorRetry").clickable(onClick = onRetry).padding(4.dp),
            )
        }
    }
}

/** 크게 보기: 화면 전체 창. 두 손가락 확대·이동은 안의 RoomView가 한다. */
@Composable
private fun RoomFullScreen(onClose: () -> Unit, content: @Composable () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.fillMaxSize().background(DQColors.Bg).systemBarsPadding().testTag("roomDecorFull"),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Txt("방 크게 보기", size = 17f, weight = Bold, modifier = Modifier.weight(1f))
                Btn("닫기", Modifier.width(72.dp).height(36.dp).testTag("roomDecorFullClose"), style = BtnStyle.Outline, size = 13f, onClick = onClose)
            }
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { content() }
            Txt(
                "두 손가락으로 확대·축소, 확대한 뒤 한 손가락으로 이동, 두 번 탭하면 원래 크기",
                size = 12f, color = DQColors.Muted,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            )
        }
    }
}

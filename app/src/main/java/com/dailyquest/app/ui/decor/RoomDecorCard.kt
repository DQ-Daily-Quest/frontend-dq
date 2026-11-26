package com.dailyquest.app.ui.decor

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dailyquest.app.R
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
 * - 멤버: 서버에 저장된 배치를 보여준다. 한 번도 저장하지 않은 방은 기본 방. 카드 아래 테두리 가운데에 걸친 "방 꾸미기" 버튼.
 * - 방 그림 안에는 안내 글자를 넣지 않는다(QA 디자인 3단계, 사용자 요청). 꾸미기 정보를 못 받으면 기본 방이 보이고,
 *   방 화면에 다시 들어오면(ON_RESUME) 다시 받는다.
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
    // 편집 화면에서 저장하고 돌아오면(또는 다른 멤버가 바꿨으면) 다시 받는다. 받는 동안 이전 모습은 그대로 보인다.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.onResume() }

    val furniture = ui.decor?.furniture.orEmpty()
    val shape = RoundedCornerShape(20.dp)
    // 크기는 예전 고정 일러스트(RoomInterior, 358×252 디자인)와 같은 비율이다. 방 화면의 다른 부분 위치가 바뀌지 않게 하기 위해서다.
    // 방 그림(4:3)은 그 안에 맞춰 넣고 남는 양옆은 배경색으로 둔다. 안내 문구도 카드 안쪽 아래에 넣는다.
    // 멤버면 "방 꾸미기" 버튼을 방 그림 아래 테두리 가운데에 반쯤 걸치게 둔다(QA 디자인 3단계).
    // 버튼이 눌리도록 바깥 상자 안에 두고, 방 그림은 버튼 절반 높이만큼 위로 띄운다.
    Box(modifier.fillMaxWidth()) {
    Box(
        Modifier
            .padding(bottom = if (isMember) DECOR_BUTTON_HEIGHT / 2 else 0.dp)
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
    }
    if (isMember) {
        Row(
            Modifier.align(Alignment.BottomCenter).height(DECOR_BUTTON_HEIGHT)
                .shadow(6.dp, CircleShape, spotColor = DQColors.Shadow.copy(alpha = 0.35f))
                .clip(CircleShape).background(DQColors.InkGreen)
                .clickable(role = Role.Button, onClick = onDecorate)
                .padding(horizontal = 22.dp)
                .testTag("roomDecorEdit"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Image(painterResource(R.drawable.ic_sofa), null, Modifier.size(20.dp))
            Txt("방 꾸미기", size = 15f, weight = Bold, color = DQColors.White)
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

/** 방 꾸미기 버튼 높이. 이 절반만큼 방 그림 아래 테두리에 걸친다. */
private val DECOR_BUTTON_HEIGHT = 44.dp

/** 예전 고정 일러스트의 디자인 크기 (RoomScreen의 RoomInterior height = 252f, 358 디자인 캔버스). */
private const val CARD_WIDTH = 358f
private const val CARD_HEIGHT = 252f

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

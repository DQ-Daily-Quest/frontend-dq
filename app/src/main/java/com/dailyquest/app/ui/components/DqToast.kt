package com.dailyquest.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.dailyquest.app.R
import com.dailyquest.app.ui.theme.DQColors
import kotlinx.coroutines.delay

/** 알림 한 건. [id]는 같은 글자를 다시 띄워도 새 알림으로 보이게 한다. */
data class DqToastMessage(val text: String, val isError: Boolean, val id: Long)

/**
 * 앱 안에 그리는 알림 (QA 디자인 5단계). 안드로이드 기본 토스트는 Android 11부터 모양을 바꿀 수 없어 직접 그린다.
 * 성공은 진한 초록 + 체크, 실패는 빨강 + 느낌표. 앱 전체에 하나([LocalDqToast])를 두고 [DqToastHost]가 화면 아래쪽에 그린다.
 */
@Stable
class DqToastState {
    var current by mutableStateOf<DqToastMessage?>(null)
        private set
    private var nextId = 0L

    fun show(text: String, isError: Boolean = false) { current = DqToastMessage(text, isError, nextId++) }
    fun dismiss(id: Long) { if (current?.id == id) current = null }
}

/** 앱 전체 알림. MainActivity가 넣는다. 넣지 않은 곳(부품 단위 테스트)에서는 그리는 곳이 없어 아무것도 보이지 않는다. */
val LocalDqToast = staticCompositionLocalOf { DqToastState() }

/** 알림이 떠 있는 시간. */
const val DQ_TOAST_MILLIS = 2_200L

/** 화면 아래쪽 가운데에 알림을 그린다. 같은 상자 안에서 [modifier]로 위치를 정한다. */
@Composable
fun DqToastHost(state: DqToastState, modifier: Modifier = Modifier) {
    val message = state.current
    LaunchedEffect(message?.id) {
        val id = message?.id ?: return@LaunchedEffect
        delay(DQ_TOAST_MILLIS)
        state.dismiss(id)
    }
    AnimatedVisibility(
        visible = message != null,
        modifier = modifier,
        enter = fadeIn() + slideInVertically { it / 2 },
        exit = fadeOut() + slideOutVertically { it / 2 },
    ) {
        // 사라지는 동안에도 마지막 글자를 보여 준다.
        val m = message ?: return@AnimatedVisibility
        DqToast(m.text, m.isError)
    }
}

/** 알림 한 줄의 모양. */
@Composable
fun DqToast(text: String, isError: Boolean, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(24.dp)
    Row(
        modifier
            .padding(horizontal = 24.dp)
            .widthIn(max = 420.dp)
            .heightIn(min = 46.dp)
            .shadow(8.dp, shape, spotColor = DQColors.Shadow.copy(alpha = 0.35f))
            .background(if (isError) DQColors.Danger else DQColors.InkGreen, shape)
            .padding(horizontal = 18.dp, vertical = 11.dp)
            .testTag("dqToast")
            .semantics {
                stateDescription = if (isError) "실패" else "성공"
                liveRegion = LiveRegionMode.Polite
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(20.dp).border(1.5.dp, DQColors.White, CircleShape), contentAlignment = Alignment.Center) {
            if (isError) Txt("!", size = 12f, weight = Bold, color = DQColors.White)
            else Image(painterResource(R.drawable.ic_check_white), null, Modifier.size(11.dp))
        }
        Txt(text, size = 14f, weight = Bold, color = DQColors.White, maxLines = 2)
    }
}

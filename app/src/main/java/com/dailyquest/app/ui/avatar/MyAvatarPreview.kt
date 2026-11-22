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
 */
@Composable
fun MyAvatarPreview(
    state: MyAvatarUiState,
    onRetry: () -> Unit,
    onClearTryOn: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
            render3d -> AvatarView(parts = state.previewParts, modifier = Modifier.fillMaxSize())
            else -> Txt(
                "3D 미리보기 (테스트 환경)",
                size = 12f, color = DQColors.Muted,
                modifier = Modifier.align(Alignment.Center).testTag("myAvatarPlaceholder"),
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

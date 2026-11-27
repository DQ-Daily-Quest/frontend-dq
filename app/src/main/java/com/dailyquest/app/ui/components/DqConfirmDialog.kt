package com.dailyquest.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.dailyquest.app.R
import com.dailyquest.app.ui.theme.DQColors

/**
 * 확인 창 (QA 디자인): 흰 둥근 카드, 위 가운데 동그란 아이콘, 가운데 정렬 제목·설명, 아래 취소(테두리)·확인(채움).
 * 구매 확인 창과 같은 모양이다. [danger]면 아이콘·확인 버튼이 빨강(거절·삭제 등), 아니면 진한 초록.
 *
 * @param confirmTag 확인 버튼 테스트 표시
 */
@Composable
fun DqConfirmDialog(
    title: String,
    body: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    danger: Boolean = false,
    confirmTag: String = "dqConfirm",
    dismissLabel: String = "취소",
) {
    val accent = if (danger) DQColors.Danger else DQColors.InkGreen
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.padding(horizontal = 32.dp).fillMaxWidth().widthIn(max = 400.dp)
                .background(DQColors.White, RoundedCornerShape(24.dp)).padding(horizontal = 20.dp, vertical = 22.dp)
                .testTag("dqConfirmDialog"),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.size(52.dp).clip(CircleShape).background(accent.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                Image(
                    painterResource(if (danger) R.drawable.ic_close else R.drawable.ic_check_white), null,
                    Modifier.size(if (danger) 24.dp else 20.dp), colorFilter = ColorFilter.tint(accent),
                )
            }
            Txt(title, size = 18f, weight = Bold, color = DQColors.InkGreen, align = TextAlign.Center, modifier = Modifier.padding(top = 14.dp))
            Txt(body, size = 13.5f, color = DQColors.Muted, align = TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
            val shape = RoundedCornerShape(14.dp)
            Row(Modifier.padding(top = 20.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    Modifier.weight(1f).height(48.dp).clip(shape).border(1.dp, DQColors.Line, shape)
                        .clickable(role = Role.Button, onClick = onDismiss).testTag("dqConfirmCancel"),
                    contentAlignment = Alignment.Center,
                ) { Txt(dismissLabel, size = 15f, weight = Bold, color = DQColors.Muted) }
                Box(
                    Modifier.weight(1f).height(48.dp).clip(shape).background(accent)
                        .clickable(role = Role.Button, onClick = onConfirm).testTag(confirmTag),
                    contentAlignment = Alignment.Center,
                ) { Txt(confirmLabel, size = 15f, weight = Bold, color = DQColors.White) }
            }
        }
    }
}

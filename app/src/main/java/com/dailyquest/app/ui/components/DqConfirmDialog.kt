package com.dailyquest.app.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.dailyquest.app.R
import com.dailyquest.app.ui.theme.DQColors

/**
 * 확인 팝업 (QA 디자인): 흰 둥근 카드, 위 가운데 동그란 아이콘, 가운데 정렬 제목, [content](방 이름 칩·인증 카드 등), 설명,
 * [footer](안내 칩 등), 아래 취소(테두리)·확인(채움). [danger]면 빨강(거절·삭제·나가기), 아니면 진한 초록.
 *
 * @param icon 위 아이콘. null이면 [danger]에 따라 ✕ 또는 ✓
 * @param dismissLabel null이면 확인 버튼 하나만
 * @param confirmTag 확인 버튼 테스트 표시
 */
@Composable
fun DqConfirmDialog(
    title: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    body: String? = null,
    danger: Boolean = false,
    @DrawableRes icon: Int? = null,
    confirmTag: String = "dqConfirm",
    dismissLabel: String? = "취소",
    onDismissButton: () -> Unit = onDismiss,
    confirmEnabled: Boolean = true,
    bodyTag: String? = null,
    content: @Composable ColumnScope.() -> Unit = {},
    footer: @Composable ColumnScope.() -> Unit = {},
) {
    val accent = if (danger) DQColors.Danger else DQColors.InkGreen
    DqPopup(onDismiss, tag = "dqConfirmDialog") {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            DqIconBadge(icon ?: if (danger) R.drawable.ic_close else R.drawable.ic_check_white, accent)
            Txt(title, size = 19f, weight = Bold, color = DQColors.InkGreen, align = TextAlign.Center, modifier = Modifier.padding(top = 14.dp))
            Column(Modifier.padding(top = 12.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, content = content)
            if (body != null) Txt(
                body, size = 14f, color = DQColors.Ink, align = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp).then(if (bodyTag != null) Modifier.testTag(bodyTag) else Modifier),
            )
            Column(Modifier.padding(top = 12.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, content = footer)
            Row(Modifier.padding(top = 8.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (dismissLabel != null) DqDialogButton(dismissLabel, Modifier.weight(1f).testTag("dqConfirmCancel"), filled = false, onClick = onDismissButton)
                DqDialogButton(confirmLabel, Modifier.weight(1f).testTag(confirmTag), color = accent, enabled = confirmEnabled, onClick = onConfirm)
            }
        }
    }
}

/** 가운데 뜨는 흰 둥근 팝업 틀. 내용이 길면 스크롤한다. */
@Composable
fun DqPopup(onDismiss: () -> Unit, tag: String = "dqPopup", content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.padding(horizontal = 28.dp, vertical = 24.dp).fillMaxWidth().widthIn(max = 420.dp)
                .background(DQColors.White, RoundedCornerShape(24.dp))
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 22.dp).testTag(tag),
            content = content,
        )
    }
}

/** 팝업 위의 동그란 아이콘(연한 바탕). */
@Composable
fun DqIconBadge(@DrawableRes icon: Int, color: Color, size: Dp = 56.dp) {
    Box(Modifier.size(size).clip(CircleShape).background(color.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
        Image(painterResource(icon), null, Modifier.size(size * 0.46f), colorFilter = ColorFilter.tint(color))
    }
}

/** 팝업 아래 버튼. [filled]면 [color]로 채우고, 아니면 연한 바탕 + 테두리(취소·닫기). */
@Composable
fun DqDialogButton(
    label: String, modifier: Modifier = Modifier, filled: Boolean = true, color: Color = DQColors.InkGreen,
    enabled: Boolean = true, onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier.height(50.dp).clip(shape)
            .then(
                if (filled) Modifier.background(if (enabled) color else DQColors.Line)
                else Modifier.background(DQColors.Cream).border(1.dp, DQColors.Line, shape),
            )
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Txt(label, size = 15f, weight = Bold, color = if (!filled) DQColors.Ink else if (enabled) DQColors.White else DQColors.Muted)
    }
}

/** 방 이름 같은 짧은 표시(연한 초록 칩). */
@Composable
fun DqNameChip(text: String, modifier: Modifier = Modifier) {
    Box(modifier.clip(RoundedCornerShape(10.dp)).background(DQColors.Soft).padding(horizontal = 12.dp, vertical = 5.dp)) {
        Txt(text, size = 13f, weight = Bold, color = DQColors.InkGreen, maxLines = 1)
    }
}

/** 아이콘 + 글자 안내 상자(보상이 지급됩니다, 재인증이 가능해요 등). */
@Composable
fun DqNoticeChip(@DrawableRes icon: Int, text: String, color: Color, modifier: Modifier = Modifier, tint: Boolean = true) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(color.copy(alpha = 0.10f)).padding(vertical = 11.dp, horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        Image(painterResource(icon), null, Modifier.size(18.dp), colorFilter = if (tint) ColorFilter.tint(color) else null)
        Txt(text, size = 13.5f, weight = Bold, color = color)
    }
}

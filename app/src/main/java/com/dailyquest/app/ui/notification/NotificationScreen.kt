package com.dailyquest.app.ui.notification

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dailyquest.app.data.mission.MissionTime
import com.dailyquest.app.data.notification.AppNotification
import com.dailyquest.app.data.notification.NotificationTargets
import com.dailyquest.app.data.notification.NotificationType
import com.dailyquest.app.navigation.LocalNav
import com.dailyquest.app.ui.components.Bold
import com.dailyquest.app.ui.components.Btn
import com.dailyquest.app.ui.components.BtnStyle
import com.dailyquest.app.ui.components.Chip
import com.dailyquest.app.ui.components.DqScreen
import com.dailyquest.app.ui.components.NavHeader
import com.dailyquest.app.ui.components.Txt
import com.dailyquest.app.ui.theme.DQColors

fun NotificationType.glyph(): String = when (this) {
    NotificationType.NEW_APP_MISSION -> "🎯"
    NotificationType.NEW_MEMBER_MISSION -> "📌"
    NotificationType.PROOF_SUBMITTED -> "📤"
    NotificationType.PROOF_APPROVED -> "✅"
    NotificationType.PROOF_REJECTED -> "❌"
    NotificationType.UNKNOWN -> "🔔"
}

@Composable
fun NotificationScreen(viewModel: NotificationViewModel) {
    val nav = LocalNav.current
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    DqScreen {
        Column(Modifier.fillMaxSize().testTag("notifications")) {
            NavHeader("알림", divider = true, right = {
                Txt(
                    "모두 읽음", size = 13f, weight = Bold, color = DQColors.PrimaryStrong,
                    modifier = Modifier.clickable(enabled = !ui.markingAll && ui.items.any { !it.isRead }) { viewModel.markAllRead() }.padding(8.dp).testTag("notificationsReadAll"),
                )
            })
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip("전체", !ui.unreadOnly, Modifier.testTag("notificationsAll")) { viewModel.setUnreadOnly(false) }
                    Chip("읽지 않음", ui.unreadOnly, Modifier.testTag("notificationsUnread")) { viewModel.setUnreadOnly(true) }
                }
                ui.actionError?.let { Txt(it, size = 12f, color = DQColors.Orange, modifier = Modifier.testTag("notificationsActionError")) }
                when {
                    ui.error != null -> {
                        Txt(ui.error.orEmpty(), size = 13f, color = DQColors.Orange, modifier = Modifier.testTag("notificationsError"))
                        Btn("다시 시도", Modifier.width(140.dp).height(44.dp).testTag("notificationsRetry")) { if (ui.loaded) viewModel.refresh() else viewModel.retry() }
                    }
                    !ui.loaded -> Txt("알림을 불러오는 중이에요.", size = 13f, color = DQColors.Muted, modifier = Modifier.testTag("notificationsLoading"))
                    ui.items.isEmpty() -> Txt(
                        if (ui.unreadOnly) "읽지 않은 알림이 없어요." else "아직 받은 알림이 없어요.",
                        size = 13f, color = DQColors.Muted, modifier = Modifier.testTag("notificationsEmpty"),
                    )
                }
                ui.items.forEach { n ->
                    NotificationRow(n) {
                        viewModel.markRead(n.id)
                        NotificationTargets.routeFor(n)?.let { route -> nav.navigate(route) { launchSingleTop = true } }
                    }
                }
                if (ui.hasNext) Btn("더 보기", Modifier.fillMaxWidth().height(40.dp).testTag("notificationsMore"), size = 13f, enabled = !ui.loadingMore) { viewModel.loadMore() }
                if (ui.loaded) Btn("새로고침", Modifier.fillMaxWidth().height(40.dp).testTag("notificationsRefresh"), BtnStyle.Outline, size = 13f, enabled = !ui.loading) { viewModel.refresh() }
            }
        }
    }
}

@Composable
private fun NotificationRow(n: AppNotification, onClick: () -> Unit) {
    val bg = if (n.isRead) DQColors.Soft else DQColors.PrimaryStrong.copy(alpha = 0.12f)
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(bg).clickable(onClick = onClick)
            .semantics { contentDescription = (if (n.isRead) "읽음 " else "읽지 않음 ") + n.title }
            .padding(12.dp).testTag("notification-${n.id}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(DQColors.White), contentAlignment = Alignment.Center) { Txt(n.type.glyph(), size = 18f) }
        Column(Modifier.weight(1f)) {
            Txt(n.title, size = 14f, weight = if (n.isRead) com.dailyquest.app.ui.components.Medium else Bold, maxLines = 2)
            Txt(n.body, size = 12.5f, color = DQColors.Muted, maxLines = 3)
            n.createdAtMs?.let { Txt(MissionTime.kstDateTimeLabel(it), size = 11f, color = DQColors.Muted) }
        }
        if (!n.isRead) Box(Modifier.size(8.dp).clip(CircleShape).background(DQColors.Orange).testTag("notificationDot-${n.id}"))
    }
}

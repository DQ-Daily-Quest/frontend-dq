package com.dailyquest.app.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dailyquest.app.R
import com.dailyquest.app.navigation.LocalNav
import com.dailyquest.app.navigation.Routes
import com.dailyquest.app.navigation.backOrCatalog
import com.dailyquest.app.navigation.switchTo
import com.dailyquest.app.ui.theme.DQColors

/** 화면 루트 — 배경색 + (옵션) 상단 상태바 여백 */
@Composable
fun DqScreen(
    modifier: Modifier = Modifier,
    background: Color = DQColors.Bg,
    statusBarPadding: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier
            .fillMaxSize()
            .background(background)
            .then(if (statusBarPadding) Modifier.statusBarsPadding() else Modifier),
        content = content,
    )
}

/** 상단 내비 — 뒤로가기 + 가운데 타이틀 (+ 오른쪽 요소) */
@Composable
fun NavHeader(
    title: String,
    divider: Boolean = false,
    onBack: (() -> Unit)? = null,
    right: (@Composable RowScope.() -> Unit)? = null,
) {
    val nav = LocalNav.current
    Box(
        Modifier
            .fillMaxWidth()
            .height(54.dp)
            .background(DQColors.Bg)
            .then(
                if (divider) Modifier.drawBehind {
                    drawLine(DQColors.Soft, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
                } else Modifier
            ),
    ) {
        DqIcon(
            R.drawable.ic_back, 26.dp,
            Modifier
                .offset(14.dp, 12.dp)
                .clip(CircleShape)
                .clickable(role = Role.Button) { onBack?.invoke() ?: nav.backOrCatalog() },
        )
        Txt(
            title, size = 17f, weight = Bold, align = TextAlign.Center, maxLines = 1,
            modifier = Modifier.align(Alignment.Center).padding(horizontal = 60.dp).offset(y = (-2).dp),
        )
        if (right != null) {
            Row(
                Modifier.align(Alignment.CenterEnd).padding(end = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                content = right,
            )
        }
    }
}

/** 하단 고정 CTA 영역 */
@Composable
fun BoxScope.BottomCta(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 16.dp, end = 16.dp, bottom = 26.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

/* ---------- 탭바 (홈 · 미션 · 쇼핑 · 마이) ---------- */
enum class Tab { Home, Mission, Shop, My }

private data class TabSpec(val tab: Tab, val label: String, @DrawableRes val icon: Int, @DrawableRes val iconActive: Int)

private val TABS = listOf(
    TabSpec(Tab.Home, "홈", R.drawable.ic_tab_home, R.drawable.ic_tab_home_active),
    TabSpec(Tab.Mission, "미션", R.drawable.ic_tab_mission, R.drawable.ic_tab_mission),
    TabSpec(Tab.Shop, "쇼핑", R.drawable.ic_tab_shop, R.drawable.ic_tab_shop_active),
    TabSpec(Tab.My, "마이", R.drawable.ic_tab_my, R.drawable.ic_tab_my),
)

/** set = "a" (01~08 화면) / "b" (12~16 화면) — 탭이 이동할 화면 묶음 */
@Composable
fun TabBar(active: Tab, modifier: Modifier = Modifier, set: String = "a") {
    val nav = LocalNav.current
    val routes = if (set == "b") {
        mapOf(Tab.Home to Routes.B1_HOME, Tab.Mission to Routes.B3_MISSION_VERIFY, Tab.Shop to Routes.B5_SHOP)
    } else {
        mapOf(Tab.Home to Routes.HOME, Tab.Mission to Routes.MISSION_VERIFY, Tab.Shop to Routes.SHOP)
    }
    val shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)
    Row(
        modifier
            .fillMaxWidth()
            .shadow(8.dp, shape, ambientColor = Color.Black.copy(alpha = 0.04f), spotColor = Color.Black.copy(alpha = 0.10f))
            .background(DQColors.White, shape)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(start = 10.dp, end = 10.dp, top = 12.dp, bottom = 14.dp),
    ) {
        TABS.forEach { t ->
            val on = t.tab == active
            Column(
                Modifier
                    .weight(1f)
                    .clickable(role = Role.Tab) {
                        if (!on) routes[t.tab]?.let { nav.switchTo(it) }
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Box {
                    DqIcon(if (on) t.iconActive else t.icon, 26.dp)
                    if (t.tab == Tab.Mission) {
                        DqIcon(R.drawable.ic_badge_dot, 7.dp, Modifier.align(Alignment.TopEnd).offset(6.dp, (-2).dp))
                    }
                }
                Txt(t.label, size = 11f, weight = if (on) Bold else Medium, color = if (on) DQColors.PrimaryStrong else DQColors.Muted2)
            }
        }
    }
}

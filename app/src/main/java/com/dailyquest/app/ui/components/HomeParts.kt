package com.dailyquest.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.dailyquest.app.R
import com.dailyquest.app.navigation.LocalNav
import com.dailyquest.app.navigation.Routes
import com.dailyquest.app.navigation.switchTo
import com.dailyquest.app.ui.theme.DQColors

enum class Scope { Public, Private }

/** 홈 헤더 (01 · 02 · 12 공통) — 로고, 알림, 코인, 공개/우리만 토글 */
@Composable
fun HomeHeader(scope: Scope, variantB: Boolean = false) {
    val nav = LocalNav.current
    val onScope: (Scope) -> Unit = { s ->
        val target = when {
            s == Scope.Public -> Routes.HOME_PUBLIC
            variantB -> Routes.B1_HOME
            else -> Routes.HOME
        }
        nav.switchTo(target)
    }
    Column(Modifier.fillMaxWidth().statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 18.dp, end = 16.dp, top = 10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (variantB) DqIcon(R.drawable.ic_logo_mark_sm, 30.dp, width = 34.dp, height = 30.dp)
                else DqIcon(R.drawable.ic_logo_mark, 34.dp, width = 38.dp, height = 34.dp)
                Txt("Daily Quest", size = 24f, weight = Black, modifier = Modifier.offset(y = (-3).dp))
            }
            Box(Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 2.dp)) {
                if (scope == Scope.Public && !variantB) {
                    Box(
                        Modifier.size(38.dp).shadow(3.dp, CircleShape, spotColor = Color.Black.copy(alpha = 0.12f))
                            .background(DQColors.White, CircleShape).clip(CircleShape).clickable { },
                        contentAlignment = Alignment.Center,
                    ) { DqIcon(R.drawable.ic_bell_plain, 20.dp) }
                } else {
                    // 빨간 점이 있는 알림 아이콘 (그림자 포함 에셋 54x54)
                    Box(Modifier.size(38.dp).clickable { }) {
                        DqIcon(R.drawable.ic_bell, 54.dp, Modifier.offset((-8).dp, (-6).dp))
                    }
                }
                CoinPill()
            }
        }
        if (!variantB) {
            Txt("작은 하루들이 모여,\n더 특별한 우리", size = 11.5f, weight = Medium, lineHeight = 1.35f, modifier = Modifier.padding(start = 20.dp, top = 2.dp))
        }
        ScopeToggle(
            scope = scope,
            wide = variantB,
            onScope = onScope,
            modifier = if (variantB) Modifier.padding(top = 14.dp) else Modifier.padding(top = if (scope == Scope.Public) 2.dp else 0.dp),
        )
    }
}

@Composable
fun ScopeToggle(scope: Scope, wide: Boolean, onScope: (Scope) -> Unit, modifier: Modifier = Modifier) {
    val outer = RoundedCornerShape(if (wide) 21.dp else 20.dp)
    Box(Modifier.fillMaxWidth().then(modifier), contentAlignment = Alignment.Center) {
        Row(
            (if (wide) Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(42.dp) else Modifier.width(168.dp).height(40.dp))
                .shadow(4.dp, outer, spotColor = Color.Black.copy(alpha = 0.14f))
                .background(DQColors.White, outer)
                .padding(4.dp),
        ) {
            listOf(Scope.Public to "공개", Scope.Private to "우리만").forEach { (s, label) ->
                val on = s == scope
                val icon = when {
                    s == Scope.Public && on -> R.drawable.ic_globe_white
                    s == Scope.Public -> if (wide) R.drawable.ic_globe_gray else R.drawable.ic_globe
                    on -> R.drawable.ic_lock_white
                    else -> R.drawable.ic_lock
                }
                val shape = RoundedCornerShape(if (wide) 17.dp else 16.dp)
                Row(
                    Modifier.weight(1f).fillMaxHeight().clip(shape)
                        .background(if (on) DQColors.Primary else Color.Transparent)
                        .clickable(role = Role.Tab) { if (!on) onScope(s) },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally),
                ) {
                    DqIcon(icon, if (on) 14.dp else 16.dp)
                    Txt(
                        label, size = if (wide) 14f else 13f, weight = if (on) Bold else Medium,
                        color = if (on) DQColors.White else if (wide) DQColors.Muted else DQColors.Ink,
                    )
                }
            }
        }
    }
}

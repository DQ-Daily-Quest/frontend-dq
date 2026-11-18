package com.dailyquest.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dailyquest.app.data.auth.SessionState
import com.dailyquest.app.ui.auth.LocalAuth
import com.dailyquest.app.ui.components.Black
import com.dailyquest.app.ui.components.Bold
import com.dailyquest.app.ui.components.Btn
import com.dailyquest.app.ui.components.BtnStyle
import com.dailyquest.app.ui.components.DqScreen
import com.dailyquest.app.ui.components.Tab
import com.dailyquest.app.ui.components.TabBar
import com.dailyquest.app.ui.components.Txt
import com.dailyquest.app.ui.theme.DQColors
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.text.style.TextAlign

/** 앱 시작 화면: 저장된 세션을 확인하는 동안 표시하고, 서버 확인에 실패하면 재시도를 안내한다. */
@Composable
fun SplashScreen() {
    val auth = LocalAuth.current
    val session by auth.session.collectAsStateWithLifecycle()

    DqScreen {
        Column(
            Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Txt("Daily Quest", size = 28f, weight = Black, color = DQColors.InkGreen)
            val failed = session as? SessionState.BootstrapFailed
            if (failed == null) {
                CircularProgressIndicator(Modifier.padding(top = 24.dp), color = DQColors.Primary)
            } else {
                Txt(
                    if (failed.isNetwork) "네트워크에 연결할 수 없어요.\n연결을 확인하고 다시 시도해 주세요."
                    else "로그인 상태를 확인하지 못했어요.\n잠시 후 다시 시도해 주세요.",
                    size = 14f, color = DQColors.Muted, align = TextAlign.Center, modifier = Modifier.padding(top = 20.dp),
                )
                Btn("다시 시도", Modifier.padding(top = 20.dp).fillMaxWidth().height(50.dp)) { auth.retryBootstrap() }
                Btn("로그아웃", Modifier.padding(top = 10.dp).fillMaxWidth().height(50.dp), style = BtnStyle.Outline) { auth.logout() }
            }
        }
    }
}

/**
 * 최소 마이 화면(STEP 2): 서버 /users/me 값과 로그아웃 진입점만 제공한다.
 * 프로필 수정·비밀번호 변경·탈퇴 등은 STEP 8에서 구현한다.
 */
@Composable
fun AccountScreen() {
    val auth = LocalAuth.current
    val session by auth.session.collectAsStateWithLifecycle()
    val profile = (session as? SessionState.Authenticated)?.profile

    DqScreen {
        Column(Modifier.fillMaxSize()) {
            Txt("마이", size = 26f, weight = Black, modifier = Modifier.height(56.dp).padding(start = 20.dp, top = 12.dp))
            Column(Modifier.weight(1f).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (profile != null) {
                    Txt(profile.nickname, size = 22f, weight = Bold)
                    Txt(profile.email ?: "소셜 계정", size = 13f, color = DQColors.Muted)
                    Txt("코인 ${profile.coins}  ·  연속 ${profile.streakCurrent}일 (최장 ${profile.streakLongest}일)", size = 14f)
                    Txt("참여한 방 ${profile.roomCount}개  ·  완료한 미션 ${profile.completedMissionCount}개", size = 14f)
                }
                Btn("로그아웃", Modifier.padding(top = 20.dp).fillMaxWidth().height(50.dp), style = BtnStyle.Outline) { auth.logout() }
                Txt("프로필 수정 · 비밀번호 변경 · 탈퇴는 이후 단계에서 제공돼요.", size = 12f, color = DQColors.Muted)
            }
            TabBar(Tab.My)
        }
    }
}

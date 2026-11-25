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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dailyquest.app.data.auth.UserProfile
import com.dailyquest.app.navigation.LocalNav
import com.dailyquest.app.navigation.Routes
import com.dailyquest.app.ui.components.DqCard
import com.dailyquest.app.ui.components.Medium
import com.dailyquest.app.ui.components.SessionCoinPill
import com.dailyquest.app.ui.profile.ProfileViewModel
import com.dailyquest.app.ui.room.LocalAppContainer
import com.dailyquest.app.data.avatar.AvatarGender
import com.dailyquest.app.ui.avatar.ProfileFace

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
 * 마이페이지. 모든 값은 세션의 /users/me 응답이며, 서버가 주지 않는 값(누적 획득 코인·로그인 방식 등)은 표시하지 않는다.
 * 아바타 영역은 STEP 9에서 3D 모델로 바뀔 예정이라 닉네임 이니셜 플레이스홀더만 둔다.
 */
@Composable
fun AccountScreen() {
    val auth = LocalAuth.current
    val nav = LocalNav.current
    val vm: ProfileViewModel = viewModel(factory = ProfileViewModel.Factory(LocalAppContainer.current))
    val session by auth.session.collectAsStateWithLifecycle()
    val ui by vm.ui.collectAsStateWithLifecycle()
    val profile = (session as? SessionState.Authenticated)?.profile
    val userId = profile?.userId
    // 화면에 들어올 때와 계정이 바뀔 때마다 서버 값을 다시 읽는다(다른 계정에서 승인된 보상 등 반영).
    LaunchedEffect(userId) { if (userId != null) vm.refresh() }

    DqScreen {
        Column(Modifier.fillMaxSize().testTag("myScreen")) {
            Row(Modifier.fillMaxWidth().height(56.dp).padding(start = 20.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Txt("마이", size = 26f, weight = Black, modifier = Modifier.weight(1f))
                SessionCoinPill("myCoin")
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
                if (profile != null) {
                    ProfileCard(profile)
                    if (ui.error != null) {
                        Row(Modifier.padding(top = 10.dp).testTag("myError"), verticalAlignment = Alignment.CenterVertically) {
                            Txt("최신 정보를 불러오지 못했어요. ${ui.error}", size = 12f, color = DQColors.Orange, modifier = Modifier.weight(1f))
                            Btn("다시 시도", Modifier.padding(start = 8.dp).width(88.dp).height(34.dp), style = BtnStyle.Outline, size = 12f) { vm.refresh() }
                        }
                    } else if (ui.refreshing && !ui.loaded) {
                        Txt("불러오는 중…", size = 12f, color = DQColors.Muted, modifier = Modifier.padding(top = 10.dp, start = 4.dp))
                    }
                    StatsRow(profile)
                    MenuGroup {
                        MenuItem("프로필 수정", "myEditProfile") { nav.navigate(Routes.PROFILE_EDIT) { launchSingleTop = true } }
                        if (profile.hasPassword != false) {
                            MenuItem("비밀번호 변경", "myChangePassword") { nav.navigate(Routes.PASSWORD_CHANGE) { launchSingleTop = true } }
                        }
                        MenuItem("보유 아이템", "myInventory") { nav.navigate(Routes.INVENTORY) { launchSingleTop = true } }
                        MenuItem("코인 이용 내역", "myCoinHistory") { nav.navigate(Routes.COIN_HISTORY) { launchSingleTop = true } }
                    }
                    MenuGroup {
                        MenuItem("로그아웃", "myLogout") { auth.logout() }
                        MenuItem("회원 탈퇴", "myWithdraw", color = DQColors.Orange) { nav.navigate(Routes.WITHDRAW) { launchSingleTop = true } }
                    }
                }
            }
            TabBar(Tab.My)
        }
    }
}

@Composable
private fun ProfileCard(profile: UserProfile) {
    DqCard(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            // 내 아바타 얼굴. 착용을 바꾸면 세션의 내 정보가 갱신되어 바로 바뀐다(프로필 얼굴 5단계).
            ProfileFace(
                avatar = profile.avatar,
                size = 64.dp,
                modifier = Modifier.testTag("myProfileFace"),
                contentDescription = "내 아바타 얼굴",
                genderHint = AvatarGender.parse(profile.gender),
            )
            Column(Modifier.padding(start = 16.dp).weight(1f)) {
                Txt(profile.nickname, size = 20f, weight = Bold, maxLines = 1, modifier = Modifier.testTag("myNickname"))
                Txt(profile.email ?: "등록된 이메일이 없어요", size = 13f, color = DQColors.Muted, maxLines = 1, modifier = Modifier.padding(top = 3.dp).testTag("myEmail"))
                formatJoinDate(profile.createdAt)?.let {
                    Txt("가입일 $it", size = 12f, color = DQColors.Muted, modifier = Modifier.padding(top = 2.dp).testTag("myJoinedAt"))
                }
            }
        }
    }
}

@Composable
private fun StatsRow(profile: UserProfile) {
    Row(Modifier.padding(top = 12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StatBox("연속 달성", "${profile.streakCurrent}일", "myStreak", Modifier.weight(1f))
        StatBox("최장 연속", "${profile.streakLongest}일", "myLongestStreak", Modifier.weight(1f))
    }
    Row(Modifier.padding(top = 10.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StatBox("참여한 방", "${profile.roomCount}개", "myRoomCount", Modifier.weight(1f))
        StatBox("승인된 인증", "${profile.completedMissionCount}건", "myCompleted", Modifier.weight(1f))
    }
}

@Composable
private fun StatBox(label: String, value: String, tag: String, modifier: Modifier) {
    DqCard(modifier, soft = true) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp).semantics(mergeDescendants = true) {}.testTag(tag)) {
            Txt(label, size = 12f, color = DQColors.Muted)
            Txt(value, size = 20f, weight = Bold, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun MenuGroup(content: @Composable ColumnScope.() -> Unit) {
    DqCard(Modifier.padding(top = 12.dp).fillMaxWidth(), soft = true) { Column(content = content) }
}

@Composable
private fun MenuItem(label: String, tag: String, color: androidx.compose.ui.graphics.Color = DQColors.Ink, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(52.dp).clickable(onClick = onClick).padding(horizontal = 18.dp).testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Txt(label, size = 15f, weight = Medium, color = color, modifier = Modifier.weight(1f))
        Txt("›", size = 18f, color = DQColors.Muted)
    }
}

/** 서버 가입 시각(ISO)에서 날짜만 보여 준다. 형식을 모르면 표시하지 않는다. */
internal fun formatJoinDate(iso: String?): String? {
    val m = Regex("""^(\d{4})-(\d{2})-(\d{2})""").find(iso.orEmpty()) ?: return null
    val (y, mo, d) = m.destructured
    return "$y.$mo.$d"
}

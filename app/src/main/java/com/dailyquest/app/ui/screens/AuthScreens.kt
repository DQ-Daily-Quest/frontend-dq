package com.dailyquest.app.ui.screens

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.dailyquest.app.R
import com.dailyquest.app.navigation.LocalNav
import com.dailyquest.app.navigation.Routes
import com.dailyquest.app.ui.components.Black
import com.dailyquest.app.ui.components.Bold
import com.dailyquest.app.ui.components.BottomCta
import com.dailyquest.app.ui.components.Btn
import com.dailyquest.app.ui.components.BtnStyle
import com.dailyquest.app.ui.components.DqAvatar
import com.dailyquest.app.ui.components.DqIcon
import com.dailyquest.app.ui.components.DqImage
import com.dailyquest.app.ui.components.DqProgress
import com.dailyquest.app.ui.components.DqScreen
import com.dailyquest.app.ui.components.DqTextField
import com.dailyquest.app.ui.components.FormBody
import com.dailyquest.app.ui.components.Medium
import com.dailyquest.app.ui.components.NavHeader
import com.dailyquest.app.ui.components.Scaled
import com.dailyquest.app.ui.components.Txt
import com.dailyquest.app.ui.components.at
import com.dailyquest.app.ui.theme.DQColors

/** 09 · A1 로그인 */
@Composable
fun LoginScreen() {
    val nav = LocalNav.current
    var email by remember { mutableStateOf("") }
    var pw by remember { mutableStateOf("") }
    var showPw by remember { mutableStateOf(false) }

    DqScreen(statusBarPadding = false) {
        Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 32.dp)) {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val s = maxWidth.value / 390f
                Box(Modifier.fillMaxWidth().height((296 * s).dp)) {
                    DqImage(R.drawable.img_village_map, Modifier.fillMaxSize())
                    Column(
                        Modifier.padding(horizontal = 24.dp).offset(y = (105 * s).dp).fillMaxWidth().height(112.dp)
                            .shadow(12.dp, RoundedCornerShape(22.dp), spotColor = Color(0xFF14331F).copy(alpha = 0.3f))
                            .background(Color(0xF0FFFFF7), RoundedCornerShape(22.dp)),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            DqIcon(R.drawable.ic_logo_mark, 34.dp, width = 38.dp, height = 34.dp)
                            Txt("Daily Quest", size = 28f, weight = Black, color = Color(0xFF144029))
                        }
                        Txt("작은 하루들이 모여, 더 특별한 우리", size = 13f, weight = Medium, color = Color(0xFF335442), modifier = Modifier.padding(top = 12.dp))
                    }
                    DqImage(R.drawable.img_avatar_story_md_1, Modifier.offset((98 * s).dp, (222 * s).dp).size(40.dp))
                    DqAvatar(R.drawable.img_avatar_girl, 40.dp, Modifier.offset((254 * s).dp, (222 * s).dp))
                }
            }

            Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp)) {
                Txt("이메일", size = 13f, weight = Bold, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
                DqTextField(email, { email = it }, "example@email.com", radius = 16.dp, keyboardType = KeyboardType.Email, leading = R.drawable.ic_mail, shadow = true)
                Txt("비밀번호", size = 13f, weight = Bold, modifier = Modifier.padding(start = 4.dp, top = 6.dp, bottom = 6.dp))
                DqTextField(
                    pw, { pw = it }, "비밀번호 입력", radius = 16.dp, secure = !showPw, keyboardType = KeyboardType.Password,
                    leading = R.drawable.ic_lock_gray, shadow = true,
                    trailing = { DqIcon(R.drawable.ic_eye, 20.dp, Modifier.clip(CircleShape).clickable { showPw = !showPw }) },
                )
                Btn("로그인", Modifier.padding(top = 18.dp).fillMaxWidth().height(54.dp), size = 16f) { nav.navigate(Routes.HOME) }

                Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.width(110.dp).clickable { }, contentAlignment = Alignment.Center) { Txt("비밀번호 찾기", size = 13f, color = DQColors.Muted) }
                    Box(Modifier.padding(horizontal = 14.dp).size(1.dp, 14.dp).background(DQColors.Soft))
                    Box(Modifier.width(110.dp).clickable { nav.navigate(Routes.SIGNUP_INFO) }, contentAlignment = Alignment.Center) {
                        Txt("회원가입", size = 13f, weight = Bold, color = DQColors.Green)
                    }
                }

                Row(Modifier.padding(top = 25.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f).height(1.dp).background(DQColors.Soft))
                    Txt("간편 로그인", size = 12f, color = DQColors.Muted, modifier = Modifier.padding(horizontal = 18.dp))
                    Box(Modifier.weight(1f).height(1.dp).background(DQColors.Soft))
                }
                Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SocialButton("카카오로 시작하기", R.drawable.ic_brand_kakao, Color(0xFFFEE500))
                    SocialButton("Google로 시작하기", R.drawable.ic_brand_google, Color.White, border = true)
                    SocialButton("Apple로 시작하기", R.drawable.ic_brand_apple, Color(0xFF111111), text = Color.White)
                }
            }
        }
    }
}

@Composable
private fun SocialButton(label: String, @DrawableRes icon: Int, bg: Color, text: Color = DQColors.Ink, border: Boolean = false) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        Modifier.fillMaxWidth().height(50.dp).clip(shape).background(bg)
            .then(if (border) Modifier.border(1.dp, DQColors.Line, shape) else Modifier).clickable { },
        contentAlignment = Alignment.Center,
    ) {
        DqIcon(icon, 20.dp, Modifier.align(Alignment.CenterStart).padding(start = 18.dp).size(20.dp))
        Txt(label, size = 14.5f, weight = Bold, color = text)
    }
}

/* ---------- 회원가입 공통 헤더 ---------- */
@Composable
private fun SignupHeader(step: Int) {
    NavHeader("회원가입", right = { Txt("$step / 2", size = 13f, weight = Bold, color = DQColors.Green) })
    DqProgress(step / 2f, Modifier.padding(horizontal = 16.dp).offset(y = (-2).dp), height = 4.dp)
}

@Composable
private fun SignupTitle(text: String) {
    Txt(text, size = 22f, weight = Bold, lineHeight = 1.35f, modifier = Modifier.padding(start = 4.dp, top = 20.dp))
}

private data class Term(val label: String, val required: Boolean)

private val TERMS = listOf(
    Term("[필수] 이용약관 동의", true),
    Term("[필수] 개인정보 수집 · 이용 동의", true),
    Term("[선택] 마케팅 알림 수신", false),
)

/** 10 · A2 회원가입 · 정보 입력 */
@Composable
fun SignupInfoScreen() {
    val nav = LocalNav.current
    var email by remember { mutableStateOf("example@email.com") }
    var pw by remember { mutableStateOf("") }
    var pw2 by remember { mutableStateOf("") }
    var nick by remember { mutableStateOf("") }
    var show1 by remember { mutableStateOf(false) }
    var show2 by remember { mutableStateOf(false) }
    val agree = remember { mutableStateListOf(true, true, false) }
    val all = agree.all { it }

    DqScreen {
        Column(Modifier.fillMaxSize()) {
            SignupHeader(1)
            FormBody(top = 0.dp) {
                SignupTitle("반가워요!\n기본 정보를 알려주세요")

                Label("이메일", top = 16.dp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DqTextField(email, { email = it }, "", Modifier.weight(1f), radius = 16.dp, weight = FontWeight.Medium, focused = true, keyboardType = KeyboardType.Email, shadow = true)
                    Btn("인증하기", Modifier.width(104.dp).height(50.dp), style = BtnStyle.Soft, size = 14f)
                }
                Label("비밀번호")
                DqTextField(pw, { pw = it }, "영문, 숫자 포함 8자 이상", radius = 16.dp, secure = !show1, keyboardType = KeyboardType.Password, shadow = true,
                    trailing = { DqIcon(R.drawable.ic_eye, 20.dp, Modifier.clip(CircleShape).clickable { show1 = !show1 }) })
                Hint("영문 · 숫자 포함 8자 이상")
                Label("비밀번호 확인", top = 6.dp)
                DqTextField(pw2, { pw2 = it }, "비밀번호를 한 번 더 입력", radius = 16.dp, secure = !show2, keyboardType = KeyboardType.Password, shadow = true,
                    trailing = { DqIcon(R.drawable.ic_eye, 20.dp, Modifier.clip(CircleShape).clickable { show2 = !show2 }) })
                Label("닉네임")
                DqTextField(nick, { nick = it }, "친구들에게 보일 이름", radius = 16.dp, maxLength = 10, shadow = true)
                Hint("2~10자 · 방 멤버들에게 표시돼요")

                Column(Modifier.padding(top = 12.dp).fillMaxWidth().background(DQColors.Soft, RoundedCornerShape(16.dp)).padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 6.dp)) {
                    Row(
                        Modifier.fillMaxWidth().clickable { val v = !all; for (i in agree.indices) agree[i] = v }.padding(bottom = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Box(Modifier.size(22.dp).background(if (all) DQColors.Primary else Color(0xFFC9D3CB), CircleShape), contentAlignment = Alignment.Center) {
                            DqIcon(R.drawable.ic_check_white_12, 12.dp)
                        }
                        Txt("전체 동의", size = 15f, weight = Bold)
                    }
                    TERMS.forEachIndexed { i, t ->
                        Row(
                            Modifier.fillMaxWidth().height(32.dp).clickable { agree[i] = !agree[i] }.padding(start = 2.dp, end = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            DqIcon(if (agree[i]) R.drawable.ic_check_on else R.drawable.ic_check_off, 18.dp)
                            Txt(t.label, size = 13f, color = if (agree[i]) DQColors.Ink else DQColors.Muted, modifier = Modifier.weight(1f))
                            DqIcon(R.drawable.ic_chevron_right, 16.dp)
                        }
                    }
                }
            }
        }
        BottomCta {
            Btn("다음", Modifier.weight(1f).height(54.dp), size = 16f) { nav.navigate(Routes.SIGNUP_AVATAR) }
        }
    }
}

@Composable
private fun Label(text: String, top: androidx.compose.ui.unit.Dp = 12.dp) =
    Txt(text, size = 13f, weight = Bold, modifier = Modifier.padding(start = 4.dp, top = top, bottom = 6.dp))

@Composable
private fun Hint(text: String) = Txt(text, size = 11.5f, color = DQColors.Muted, modifier = Modifier.padding(start = 4.dp, top = 6.dp))

private val SWATCHES = listOf(
    "피부" to listOf(R.drawable.sw_skin_0, R.drawable.sw_skin_1, R.drawable.sw_skin_2, R.drawable.sw_skin_3, R.drawable.sw_skin_4),
    "헤어" to listOf(R.drawable.sw_hair_0, R.drawable.sw_hair_1, R.drawable.sw_hair_2, R.drawable.sw_hair_3, R.drawable.sw_hair_4),
    "옷" to listOf(R.drawable.sw_cloth_0, R.drawable.sw_cloth_1, R.drawable.sw_cloth_2, R.drawable.sw_cloth_3, R.drawable.sw_cloth_2),
)

/** 11 · A3 회원가입 · 아바타 만들기 */
@Composable
fun SignupAvatarScreen() {
    val nav = LocalNav.current
    val sel = remember { mutableStateListOf(0, 0, 0) }

    DqScreen {
        Column(Modifier.fillMaxSize()) {
            SignupHeader(2)
            FormBody(top = 0.dp, bottomSpace = 140.dp) {
                SignupTitle("나만의 아바타를\n만들어 볼까요?")
                Box(Modifier.padding(top = 15.dp).clip(RoundedCornerShape(22.dp))) {
                    Scaled(358f, 240f) {
                        DqImage(R.drawable.img_room_interior, Modifier.size(358.dp, 240.dp))
                        DqAvatar(R.drawable.img_avatar_boy, 140.dp, Modifier.at(109, 46))
                    }
                }
                SWATCHES.forEachIndexed { g, (label, list) ->
                    Txt(label, size = 14f, weight = Bold, modifier = Modifier.padding(start = 4.dp, top = if (g == 0) 24.dp else 4.dp, bottom = 4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        list.forEachIndexed { i, res ->
                            Box(Modifier.size(48.dp).clip(CircleShape).clickable { sel[g] = i }, contentAlignment = Alignment.Center) {
                                DqIcon(res, 40.dp)
                                if (sel[g] == i) DqIcon(R.drawable.ic_swatch_selected, 48.dp)
                            }
                        }
                    }
                }
            }
        }
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(DQColors.Bg).navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Btn("Daily Quest 시작하기", Modifier.fillMaxWidth().height(54.dp), size = 16f) { nav.navigate(Routes.B1_HOME) }
            Txt("나중에 꾸밀게요", size = 13f, color = DQColors.Muted, modifier = Modifier.padding(top = 14.dp).clickable { nav.navigate(Routes.B1_HOME) })
        }
    }
}

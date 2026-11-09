package com.dailyquest.app.ui.screens

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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dailyquest.app.R
import com.dailyquest.app.navigation.LocalNav
import com.dailyquest.app.navigation.backOrCatalog
import com.dailyquest.app.ui.components.Bold
import com.dailyquest.app.ui.components.Btn
import com.dailyquest.app.ui.components.BtnStyle
import com.dailyquest.app.ui.components.DqAvatar
import com.dailyquest.app.ui.components.DqIcon
import com.dailyquest.app.ui.components.DqImage
import com.dailyquest.app.ui.components.DqProgress
import com.dailyquest.app.ui.components.DqScreen
import com.dailyquest.app.ui.components.Medium
import com.dailyquest.app.ui.components.Txt
import com.dailyquest.app.ui.theme.DQColors
import com.dailyquest.app.ui.theme.NotoSansKr

/** 04 · 챌린지 스토리 뷰어 */
@Composable
fun StoryScreen() {
    val nav = LocalNav.current
    var votes by remember { mutableIntStateOf(2) }
    var liked by remember { mutableStateOf(false) }
    var comment by remember { mutableStateOf("") }

    DqScreen(background = Color(0xFF6FA0D8), statusBarPadding = false) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val w = maxWidth.value
            val h = maxHeight.value
            DqImage(R.drawable.img_photo_sky, Modifier.fillMaxSize(), description = "지수가 올린 하늘 사진")
            DqImage(R.drawable.img_cloud_1, Modifier.offset((w * 40 / 390).dp, (h * 240 / 844).dp).size(150.dp, 50.dp), ContentScale.FillBounds)
            DqImage(R.drawable.img_cloud_2, Modifier.offset((w * 200 / 390).dp, (h * 300 / 844).dp).size(170.dp, 56.dp), ContentScale.FillBounds)
            DqImage(R.drawable.img_skyline, Modifier.align(Alignment.BottomCenter).offset(y = 16.dp).fillMaxWidth().height(260.dp), ContentScale.FillBounds)
            Box(Modifier.fillMaxWidth().height(150.dp).background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.4f), Color.Transparent))))
        }

        // 상단: 진행바 · 작성자 · 닫기
        Column(Modifier.statusBarsPadding().padding(start = 14.dp, end = 14.dp, top = 6.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(3) { i ->
                    Box(Modifier.weight(1f).height(3.dp).background(if (i == 0) Color.White else Color.White.copy(alpha = 0.4f), RoundedCornerShape(2.dp)))
                }
            }
            Row(Modifier.padding(top = 11.dp).offset(x = (-2).dp), verticalAlignment = Alignment.CenterVertically) {
                DqAvatar(R.drawable.img_avatar_girl, 40.dp)
                Column(Modifier.padding(start = 6.dp).weight(1f)) {
                    Txt("지수", size = 14f, weight = Bold, color = Color.White)
                    Txt("2시간 전", size = 11f, color = Color.White, modifier = Modifier.padding(top = 2.dp))
                }
                DqIcon(R.drawable.ic_close_white, 24.dp, Modifier.clip(CircleShape).clickable { nav.backOrCatalog() })
            }
        }

        // 하단: 미션 태그 · 캡션 · 투표 · 댓글
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().imePadding().padding(start = 16.dp, end = 16.dp, bottom = 20.dp)) {
            Row(
                Modifier.width(226.dp).height(30.dp).background(Color.White.copy(alpha = 0.95f), RoundedCornerShape(15.dp)).padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                DqIcon(R.drawable.ic_cloud_sm, 16.dp)
                Txt("앱 미션 · 오늘의 하늘 찍기", size = 12f, weight = Bold)
            }
            Txt("퇴근길 하늘 너무 예쁘다", size = 15f, weight = Medium, color = Color.White, modifier = Modifier.padding(start = 2.dp, top = 10.dp))

            Column(
                Modifier.padding(top = 22.dp).fillMaxWidth().height(116.dp)
                    .background(Color.White.copy(alpha = 0.96f), RoundedCornerShape(20.dp)).padding(start = 16.dp, end = 16.dp, top = 14.dp),
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Txt("통과 투표", size = 14f, weight = Bold, modifier = Modifier.weight(1f))
                    Txt("$votes / 3명", size = 13f, weight = Bold, color = DQColors.Green)
                }
                DqProgress(votes / 3f, Modifier.padding(top = 10.dp))
                Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Btn("통과!", Modifier.weight(1f).height(42.dp)) { votes = 3 }
                    Btn("다시 해줘", Modifier.weight(1f).height(42.dp), style = BtnStyle.Outline)
                }
            }

            Row(Modifier.padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(
                    Modifier.weight(1f).height(44.dp).border(1.2.dp, Color.White, RoundedCornerShape(22.dp)).padding(horizontal = 16.8.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (comment.isEmpty()) Txt("댓글 달기...", size = 13.5f, color = Color.White)
                    BasicTextField(
                        value = comment, onValueChange = { comment = it }, singleLine = true,
                        cursorBrush = SolidColor(Color.White),
                        textStyle = TextStyle(fontFamily = NotoSansKr, fontSize = 13.5.sp, color = Color.White),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                DqIcon(if (liked) R.drawable.ic_heart else R.drawable.ic_heart_white, 28.dp, Modifier.clickable { liked = !liked })
            }
        }
    }
}

package com.dailyquest.app.ui.components

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dailyquest.app.R
import com.dailyquest.app.ui.theme.DQColors
import com.dailyquest.app.ui.theme.NotoSansKr

/** 스크롤 폼 본문 (하단 CTA 영역만큼 여백) */
@Composable
fun FormBody(modifier: Modifier = Modifier, bottomSpace: Dp = 110.dp, top: Dp = 16.dp, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = top, bottom = bottomSpace),
        content = content,
    )
}

/* ---------- 미션 요약 ---------- */
@Composable
fun MissionSummary(@DrawableRes icon: Int, title: String, desc: String, small: Boolean = false) {
    Row(
        Modifier.fillMaxWidth().height(if (small) 70.dp else 76.dp).background(DQColors.Soft, RoundedCornerShape(18.dp)).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(if (small) 46.dp else 52.dp).background(DQColors.White, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
            DqIcon(icon, if (small) 24.dp else 28.dp)
        }
        Column {
            Txt(title, size = 15f, weight = Bold)
            Txt(desc, size = if (small) 11.5f else 11f, color = DQColors.Muted, maxLines = 1, modifier = Modifier.padding(top = if (small) 6.dp else 5.dp))
        }
    }
}

/* ---------- 사진 · 동영상 선택 (시스템 포토 피커, 권한 불필요) ---------- */
@Composable
fun rememberMediaPicker(): Pair<ImageBitmap?, () -> Unit> {
    val context = LocalContext.current
    var bitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null) {
            bitmap = runCatching {
                context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it)?.asImageBitmap() }
            }.getOrNull()
        }
    }
    return bitmap to { launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) }
}

@Composable
fun UploadArea(large: Boolean = false) {
    val (picked, pick) = rememberMediaPicker()
    val radius = if (large) 24.dp else 22.dp
    Box(
        Modifier.padding(top = 16.dp).fillMaxWidth().height(if (large) 330.dp else 282.dp)
            .clip(RoundedCornerShape(radius)).background(DQColors.White)
            .dashedBorder(DQColors.Dashed, 1.5.dp, radius)
            .clickable { pick() },
        contentAlignment = Alignment.Center,
    ) {
        if (picked != null) {
            Image(picked, contentDescription = "선택한 사진", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val cam = if (large) 72.dp else 64.dp
                Box(Modifier.size(cam).background(DQColors.Primary, CircleShape).clip(CircleShape).clickable { pick() }, contentAlignment = Alignment.Center) {
                    DqIcon(if (large) R.drawable.ic_camera_white_32 else R.drawable.ic_camera_white, if (large) 32.dp else 28.dp)
                }
                Txt(if (large) "사진 · 동영상 올리기" else "사진 · 동영상 찍기", size = 15f, weight = Bold, modifier = Modifier.padding(top = if (large) 16.dp else 14.dp))
                Txt(if (large) "탭해서 촬영하거나 앨범에서 선택" else "최대 30초 · 지금 이 순간을 담아줘!", size = 12f, color = DQColors.Muted, modifier = Modifier.padding(top = 3.dp))
                if (!large) {
                    Row(
                        Modifier.padding(top = 18.dp).height(34.dp).clip(RoundedCornerShape(17.dp)).background(DQColors.White)
                            .border(1.dp, DQColors.Line, RoundedCornerShape(17.dp)).clickable { pick() }.padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        DqIcon(R.drawable.ic_image, 16.dp)
                        Txt("앨범에서 선택", size = 12f, weight = Medium)
                    }
                }
            }
        }
    }
}

/* ---------- 라벨 ---------- */
@Composable
fun FieldLabel(text: String, first: Boolean = false, size: Float = 14f, top: Dp = 18.dp, bottom: Dp = 8.dp) {
    Txt(text, size = size, weight = Bold, modifier = Modifier.padding(start = 4.dp, top = if (first) 2.dp else top, bottom = bottom))
}

/* ---------- 텍스트 입력 ---------- */
@Composable
fun DqTextField(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    height: Dp = 50.dp,
    radius: Dp = 14.dp,
    fontSize: Float = 14f,
    weight: FontWeight = FontWeight.Normal,
    focused: Boolean = false,
    secure: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    maxLength: Int = Int.MAX_VALUE,
    shadow: Boolean = false,
    @DrawableRes leading: Int? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    var hasFocus by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(radius)
    val active = focused || hasFocus
    Row(
        modifier.fillMaxWidth().height(height)
            .then(if (shadow) Modifier.softShadow(shape, 6.dp) else Modifier)
            .background(DQColors.White, shape)
            .border(if (active) 1.5.dp else 1.dp, if (active) DQColors.PrimaryStrong else DQColors.Line, shape)
            .padding(horizontal = if (leading != null) 13.dp else 15.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (leading != null) DqIcon(leading, 20.dp)
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) Txt(placeholder, size = fontSize, color = DQColors.Muted, maxLines = 1)
            BasicTextField(
                value = value,
                onValueChange = { if (it.length <= maxLength) onChange(it) },
                singleLine = true,
                visualTransformation = if (secure) PasswordVisualTransformation() else VisualTransformation.None,
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                textStyle = TextStyle(fontFamily = NotoSansKr, fontSize = fontSize.sp, fontWeight = weight, color = DQColors.Ink),
                modifier = Modifier.fillMaxWidth().onFocusChanged { hasFocus = it.isFocused },
            )
        }
        trailing?.invoke()
    }
}

@Composable
fun DqTextArea(placeholder: String, height: Dp = 80.dp, max: Int = 100, counter: Boolean = true) {
    var v by remember { mutableStateOf("") }
    val shape = RoundedCornerShape(16.dp)
    Box(Modifier.fillMaxWidth().height(height).background(DQColors.White, shape).border(1.dp, DQColors.Line, shape).padding(start = 15.dp, end = 15.dp, top = 13.dp, bottom = 24.dp)) {
        if (v.isEmpty()) Txt(placeholder, size = 13.5f, color = DQColors.Muted)
        BasicTextField(
            value = v, onValueChange = { if (it.length <= max) v = it },
            textStyle = TextStyle(fontFamily = NotoSansKr, fontSize = 13.5.sp, color = DQColors.Ink),
            modifier = Modifier.fillMaxSize(),
        )
        if (counter) Txt("${v.length} / $max", size = 11f, color = DQColors.Muted, modifier = Modifier.align(Alignment.BottomEnd).offset(y = 13.dp))
    }
}

/* ---------- 토글 카드 ---------- */
@Composable
fun ToggleCard(title: String, desc: String) {
    var on by remember { mutableStateOf(true) }
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier.padding(top = 14.dp).fillMaxWidth().height(62.dp).clip(shape).background(DQColors.White)
            .border(1.dp, DQColors.Line, shape).clickable { on = !on }.padding(horizontal = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Txt(title, size = 14f, weight = Bold)
            Txt(desc, size = 11.5f, color = DQColors.Muted, modifier = Modifier.padding(top = 4.dp))
        }
        DqSwitch(on) { on = it }
    }
}

@Composable
fun InfoNote(text: String) {
    Row(Modifier.padding(start = 4.dp, end = 4.dp, top = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        DqIcon(R.drawable.ic_info, 16.dp)
        Txt(text, size = 12f, color = DQColors.Muted)
    }
}

/* ---------- 받는 사람 선택 (다중 선택) ---------- */
data class Person(val name: String, @DrawableRes val avatar: Int)

@Composable
fun RecipientPicker(people: List<Person>, initial: Set<String>, withAll: Boolean = false) {
    var sel by remember { mutableStateOf(initial) }
    Row(Modifier.padding(start = 4.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        people.forEach { p ->
            val on = p.name in sel
            Column(
                Modifier.width(56.dp).clickable { sel = if (on) sel - p.name else sel + p.name },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Box(Modifier.size(56.dp)) {
                    DqAvatar(p.avatar, 56.dp)
                    if (on) {
                        DqIcon(R.drawable.ic_selected_ring, 52.dp, Modifier.offset(2.dp, 2.dp))
                        Box(Modifier.offset(38.dp, (-2).dp).size(20.dp).background(DQColors.Primary, CircleShape), contentAlignment = Alignment.Center) {
                            DqIcon(R.drawable.ic_check_white, 12.dp)
                        }
                    }
                }
                Txt(p.name, size = 12f, weight = if (on) Bold else FontWeight.Normal, color = if (on) DQColors.Ink else DQColors.Muted)
            }
        }
        if (withAll) {
            Column(
                Modifier.width(56.dp).clickable { sel = people.map { it.name }.toSet() },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(48.dp).background(DQColors.Soft, CircleShape).border(1.dp, DQColors.Line, CircleShape), contentAlignment = Alignment.Center) {
                        DqIcon(R.drawable.ic_users_lg, 24.dp)
                    }
                }
                Txt("전체", size = 12f, color = DQColors.Muted)
            }
        }
    }
}

/* ---------- 미션 입력 + 추천 칩 ---------- */
@Composable
fun MissionInput(suggestions: List<String>, height: Dp = 50.dp, fontSize: Float = 14.5f) {
    var v by remember { mutableStateOf("오늘 커피 한 잔 마시기") }
    DqTextField(v, { v = it }, "미션 내용", height = height, fontSize = fontSize, weight = FontWeight.Medium, focused = true)
    Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        suggestions.forEach { s -> Chip(s, false) { if (!s.startsWith("랜덤")) v = s } }
    }
}

/* ---------- 기획 결정 메모 (디자인에 포함된 박스) ---------- */
@Composable
fun DecisionNote(title: String, desc: String) {
    Row(
        Modifier.padding(top = 14.dp).fillMaxWidth().height(62.dp).background(DQColors.Soft, RoundedCornerShape(14.dp))
            .dashedBorder(DQColors.Orange, 1.2.dp, 14.dp).padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        DqIcon(R.drawable.ic_alert, 18.dp)
        Column {
            Txt(title, size = 12.5f, weight = Bold, color = DQColors.Orange)
            Txt(desc, size = 11.5f, color = DQColors.Orange, modifier = Modifier.padding(top = 5.dp))
        }
    }
}


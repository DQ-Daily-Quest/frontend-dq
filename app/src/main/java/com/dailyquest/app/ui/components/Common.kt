package com.dailyquest.app.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import com.dailyquest.app.R
import com.dailyquest.app.ui.theme.DQColors
import com.dailyquest.app.ui.theme.NotoSansKr

/* ---------- 텍스트 ---------- */
@Composable
fun Txt(
    text: String,
    modifier: Modifier = Modifier,
    size: Float = 14f,
    weight: FontWeight = FontWeight.Normal,
    color: Color = DQColors.Ink,
    align: TextAlign? = null,
    lineHeight: Float? = null,
    maxLines: Int = Int.MAX_VALUE,
) {
    Text(
        text = text,
        modifier = modifier,
        maxLines = maxLines,
        overflow = if (maxLines == Int.MAX_VALUE) TextOverflow.Clip else TextOverflow.Ellipsis,
        style = TextStyle(
            fontFamily = NotoSansKr,
            fontSize = size.sp,
            fontWeight = weight,
            color = color,
            textAlign = align ?: TextAlign.Unspecified,
            lineHeight = lineHeight?.let { (size * it).sp } ?: TextUnit.Unspecified,
            platformStyle = PlatformTextStyle(includeFontPadding = false),
        ),
    )
}

val Medium = FontWeight.Medium
val Bold = FontWeight.Bold
val Black = FontWeight.Black

/* ---------- 아이콘/이미지 (피그마 에셋) ---------- */
@Composable
fun DqIcon(@DrawableRes res: Int, size: Dp, modifier: Modifier = Modifier, width: Dp = size, height: Dp = size) {
    Image(
        painter = painterResource(res),
        contentDescription = null,
        modifier = modifier.size(width, height),
        contentScale = ContentScale.FillBounds,
    )
}

@Composable
fun DqImage(@DrawableRes res: Int, modifier: Modifier = Modifier, scale: ContentScale = ContentScale.Crop, description: String? = null) {
    Image(painter = painterResource(res), contentDescription = description, modifier = modifier, contentScale = scale)
}

/* ---------- 그림자 ---------- */
fun Modifier.softShadow(shape: Shape, elevation: Dp = 6.dp) =
    shadow(elevation, shape, clip = false, ambientColor = DQColors.Shadow.copy(alpha = 0.10f), spotColor = DQColors.Shadow.copy(alpha = 0.18f))

fun Modifier.cardShadow(shape: Shape) =
    shadow(3.dp, shape, clip = false, ambientColor = Color.Black.copy(alpha = 0.04f), spotColor = Color.Black.copy(alpha = 0.10f))

/* ---------- 점선 테두리 ---------- */
fun Modifier.dashedBorder(color: Color, width: Dp, radius: Dp, dash: Float = 10f, gap: Float = 8f) = drawBehind {
    val stroke = Stroke(width.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, gap), 0f))
    val inset = width.toPx() / 2
    drawRoundRect(
        color = color,
        topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
        size = androidx.compose.ui.geometry.Size(size.width - inset * 2, size.height - inset * 2),
        cornerRadius = CornerRadius(radius.toPx()),
        style = stroke,
    )
}

/* ---------- 버튼 ---------- */
enum class BtnStyle { Primary, Soft, Outline, Flow }

@Composable
fun Btn(
    label: String,
    modifier: Modifier = Modifier,
    style: BtnStyle = BtnStyle.Primary,
    size: Float = 15f,
    radius: Dp = 14.dp,
    onClick: () -> Unit = {},
) {
    val shape = RoundedCornerShape(radius)
    val (bg, fg) = when (style) {
        BtnStyle.Primary -> DQColors.Primary to DQColors.White
        BtnStyle.Flow -> DQColors.Flow to DQColors.White
        BtnStyle.Soft -> DQColors.Soft to DQColors.InkGreen
        BtnStyle.Outline -> DQColors.White to DQColors.InkGreen
    }
    Box(
        modifier = modifier
            .clip(shape)
            .background(bg)
            .then(if (style == BtnStyle.Outline) Modifier.border(1.5.dp, DQColors.PrimaryStrong, shape) else Modifier)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Txt(label, size = size, weight = Bold, color = fg, maxLines = 1)
    }
}

/* ---------- 칩 ---------- */
@Composable
fun Chip(label: String, active: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit = {}) {
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier
            .clip(shape)
            .background(if (active) DQColors.Primary else DQColors.White)
            .border(1.dp, if (active) DQColors.Primary else DQColors.Line, shape)
            .clickable(role = Role.Tab, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    ) {
        Txt(label, size = 13f, weight = if (active) Bold else Medium, color = if (active) DQColors.White else DQColors.InkDark, maxLines = 1)
    }
}

/* ---------- 아바타 (흰 테두리 원형) ---------- */
@Composable
fun DqAvatar(@DrawableRes res: Int, size: Dp, modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(res),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(DQColors.White)
            .border(2.dp, DQColors.White, CircleShape),
    )
}

/* ---------- 코인 Pill ---------- */
@Composable
fun CoinPill(amount: Int = 320, modifier: Modifier = Modifier, onPlus: () -> Unit = {}) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier
            .shadow(3.dp, shape, ambientColor = Color.Black.copy(alpha = 0.06f), spotColor = Color.Black.copy(alpha = 0.12f))
            .clip(shape)
            .background(DQColors.White)
            .padding(horizontal = 6.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        DqIcon(R.drawable.ic_coin, 22.dp)
        Txt("$amount", size = 15f, weight = Bold, color = DQColors.InkDark)
        DqIcon(R.drawable.ic_plus_circle, 22.dp, Modifier.clip(CircleShape).clickable(onClick = onPlus))
    }
}

/* ---------- 카드 ---------- */
@Composable
fun DqCard(modifier: Modifier = Modifier, radius: Dp = 18.dp, soft: Boolean = false, content: @Composable BoxScope.() -> Unit) {
    val shape = RoundedCornerShape(radius)
    Box(
        modifier
            .then(if (soft) Modifier.softShadow(shape) else Modifier.cardShadow(shape))
            .clip(shape)
            .background(DQColors.White)
            .border(1.dp, DQColors.Line, shape),
        content = content,
    )
}

/* ---------- 진행바 ---------- */
@Composable
fun DqProgress(value: Float, modifier: Modifier = Modifier, height: Dp = 6.dp, color: Color = DQColors.Primary, track: Color = DQColors.Soft, rounded: Boolean = true) {
    val shape = if (rounded) RoundedCornerShape(height / 2) else RoundedCornerShape(0.dp)
    Box(modifier.fillMaxWidth().height(height).clip(shape).background(track)) {
        Box(Modifier.fillMaxWidth(value.coerceIn(0f, 1f)).fillMaxHeight().clip(shape).background(color))
    }
}

/* ---------- 스위치 (on 상태는 디자인 에셋) ---------- */
@Composable
fun DqSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    Box(Modifier.clip(RoundedCornerShape(14.dp)).clickable(role = Role.Switch) { onChange(!checked) }) {
        if (checked) {
            DqIcon(R.drawable.ic_switch_on, 28.dp, width = 48.dp, height = 28.dp)
        } else {
            Box(Modifier.size(48.dp, 28.dp).background(DQColors.Handle, RoundedCornerShape(14.dp)).padding(3.dp)) {
                Box(Modifier.size(22.dp).background(DQColors.White, CircleShape))
            }
        }
    }
}

/* ---------- 투표 상태 칩 ---------- */
@Composable
fun VoteChip(pass: Boolean, width: Dp = 74.dp) {
    Row(
        Modifier.width(width).height(26.dp).background(DQColors.Soft, RoundedCornerShape(13.dp)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
    ) {
        if (pass) DqIcon(R.drawable.ic_check_green, 12.dp)
        Txt(if (pass) "통과" else "대기", size = 12f, weight = Bold, color = if (pass) DQColors.Green else DQColors.Muted)
    }
}

/* ---------- 디자인 캔버스(좌표 고정 영역)를 부모 폭에 맞게 확대/축소 ---------- */
@Composable
fun Scaled(designWidth: Float, designHeight: Float, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val s = maxWidth.value / designWidth
        Box(Modifier.fillMaxWidth().height((designHeight * s).dp).clipToBounds()) {
            Box(
                Modifier
                    .wrapContentSize(Alignment.TopStart, unbounded = true)
                    .requiredSize(designWidth.dp, designHeight.dp)
                    .graphicsLayer {
                        scaleX = s
                        scaleY = s
                        transformOrigin = TransformOrigin(0f, 0f)
                    },
                content = content,
            )
        }
    }
}

/** 디자인 좌표(dp) 그대로 배치 */
fun Modifier.at(x: Number, y: Number) = this.offset(x.toFloat().dp, y.toFloat().dp)


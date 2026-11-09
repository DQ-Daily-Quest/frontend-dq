package com.dailyquest.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.dailyquest.app.R

/** Daily Quest · 작은 마을 테마 — 디자인 토큰 (피그마 기준) */
object DQColors {
    val Bg = Color(0xFFF6FAF4)
    val Primary = Color(0xFF5DA37B)
    val PrimaryStrong = Color(0xFF3F8A5C)
    val Flow = Color(0xFF4A946E)
    val Ink = Color(0xFF254B3B)
    val InkDark = Color(0xFF25332A)
    val InkGreen = Color(0xFF2F5E3F)
    val Green = Color(0xFF448F6B)
    val Muted = Color(0xFF7B8E84)
    val Muted2 = Color(0xFF8A958D)
    val Line = Color(0xFFDCE5DD)
    val Soft = Color(0xFFEFF6F0)
    val Handle = Color(0xFFD3DAD4)
    val White = Color(0xFFFFFFFF)
    val Wood = Color(0xFFF4DEB2)
    val WoodLine = Color(0xFFD1B789)
    val WoodPill = Color(0xFFFFF8EA)
    val WoodPillLine = Color(0xFFB89366)
    val Cream = Color(0xFFFFFCF3)
    val Orange = Color(0xFFE07A2F)
    val Gold = Color(0xFFB07A10)
    val Dashed = Color(0xFF8DBF9C)
    val Shadow = Color(0xFF1F4733)
}

val NotoSansKr = FontFamily(
    Font(R.font.noto_sans_kr_regular, FontWeight.Normal),
    Font(R.font.noto_sans_kr_medium, FontWeight.Medium),
    Font(R.font.noto_sans_kr_bold, FontWeight.Bold),
    Font(R.font.noto_sans_kr_black, FontWeight.Black),
)

private val DQColorScheme = lightColorScheme(
    primary = DQColors.Primary,
    onPrimary = DQColors.White,
    background = DQColors.Bg,
    onBackground = DQColors.Ink,
    surface = DQColors.White,
    onSurface = DQColors.Ink,
)

private val DQTypography = Typography().run {
    val base = TextStyle(fontFamily = NotoSansKr)
    copy(
        bodyLarge = bodyLarge.merge(base),
        bodyMedium = bodyMedium.merge(base),
        bodySmall = bodySmall.merge(base),
        titleLarge = titleLarge.merge(base),
        titleMedium = titleMedium.merge(base),
        labelLarge = labelLarge.merge(base),
    )
}

@Composable
fun DailyQuestTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DQColorScheme, typography = DQTypography, content = content)
}

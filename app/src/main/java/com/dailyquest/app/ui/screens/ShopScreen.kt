package com.dailyquest.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dailyquest.app.R
import com.dailyquest.app.ui.components.Black
import com.dailyquest.app.ui.components.Bold
import com.dailyquest.app.ui.components.Btn
import com.dailyquest.app.ui.components.Chip
import com.dailyquest.app.ui.components.CoinPill
import com.dailyquest.app.ui.components.DqAvatar
import com.dailyquest.app.ui.components.DqIcon
import com.dailyquest.app.ui.components.DqImage
import com.dailyquest.app.ui.components.DqScreen
import com.dailyquest.app.ui.components.Medium
import com.dailyquest.app.ui.components.Scaled
import com.dailyquest.app.ui.components.Tab
import com.dailyquest.app.ui.components.TabBar
import com.dailyquest.app.ui.components.Txt
import com.dailyquest.app.ui.components.at
import com.dailyquest.app.ui.theme.DQColors

/** 쇼핑 아이템: price = null(보유 중) / -1(미션 보상) / 코인 가격 */
private data class ShopItem(val color: Color, val price: Int?)

private val ITEMS = listOf(
    ShopItem(Color(0xFF5DA37B), null),
    ShopItem(Color(0xFFF2A7B8), 120),
    ShopItem(Color(0xFFE3F0E6), 80),
    ShopItem(Color(0xFFF5D58A), null),
    ShopItem(Color(0xFFE3F0E6), 200),
    ShopItem(Color(0xFFF4B183), -1),
)

/** 08 · 쇼핑 (아바타 · 우리 집) */
@Composable
fun ShopScreen() = ShopContent(variantB = false)

/** 16 · B5 쇼핑 */
@Composable
fun B5ShopScreen() = ShopContent(variantB = true)

@Composable
private fun ShopContent(variantB: Boolean) {
    var segment by remember { mutableStateOf("아바타") }
    var category by remember { mutableStateOf("옷") }
    var selected by remember { mutableIntStateOf(1) }
    val categories = if (variantB) listOf("머리", "옷", "소품", "배경") else listOf("머리", "옷", "소품", "표정", "배경")
    val item = ITEMS[selected]
    val buyLabel = when (item.price) {
        null -> "착용하기"
        -1 -> "미션 완료 시 획득"
        else -> "${item.price} 코인으로 구매하기"
    }

    DqScreen {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().height(56.dp).padding(start = 20.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Txt("꾸미기", size = 26f, weight = Black, modifier = Modifier.weight(1f))
                CoinPill()
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                if (!variantB) {
                    Row(Modifier.padding(top = 4.dp).fillMaxWidth().height(42.dp).background(DQColors.Soft, RoundedCornerShape(21.dp)).padding(4.dp)) {
                        listOf("아바타", "우리 집").forEach { s ->
                            val on = s == segment
                            val shape = RoundedCornerShape(17.dp)
                            Box(
                                Modifier.weight(1f).fillMaxSize()
                                    .then(if (on) Modifier.shadow(3.dp, shape, spotColor = Color.Black.copy(alpha = 0.12f)).background(DQColors.White, shape) else Modifier)
                                    .clip(shape).clickable { segment = s },
                                contentAlignment = Alignment.Center,
                            ) { Txt(s, size = 14f, weight = if (on) Bold else Medium, color = if (on) DQColors.Ink else DQColors.Muted) }
                        }
                    }
                }

                // 미리보기
                val previewH = if (variantB) 250f else 228f
                Box(Modifier.padding(top = if (variantB) 4.dp else 12.dp).clip(RoundedCornerShape(22.dp))) {
                    Scaled(358f, previewH) {
                        DqImage(R.drawable.img_room_interior, Modifier.size(358.dp, previewH.dp))
                        DqAvatar(
                            R.drawable.img_avatar_girl, if (variantB) 140.dp else 130.dp,
                            Modifier.at(if (variantB) 109 else 114, if (variantB) 56 else 40),
                        )
                    }
                }

                // 카테고리
                if (variantB) {
                    Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        categories.forEach { c -> Chip(c, c == category) { category = c } }
                    }
                } else {
                    Row(
                        Modifier.padding(top = 16.dp).offset(x = (-16).dp).width(androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp.dp)
                            .drawBehind { drawLine(DQColors.Soft, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx()) },
                    ) {
                        categories.forEach { c ->
                            val on = c == category
                            Column(Modifier.weight(1f).clickable { category = c }, horizontalAlignment = Alignment.CenterHorizontally) {
                                Txt(c, size = 14f, weight = if (on) Bold else Medium, color = if (on) DQColors.Ink else DQColors.Muted)
                                Box(Modifier.padding(top = 9.dp).size(38.dp, 3.dp).background(if (on) DQColors.Primary else Color.Transparent, RoundedCornerShape(2.dp)))
                            }
                        }
                    }
                }

                // 아이템 그리드
                ITEMS.chunked(3).forEachIndexed { row, items ->
                    Row(Modifier.padding(top = if (row == 0) (if (variantB) 12.dp else 14.dp) else 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items.forEachIndexed { col, it ->
                            val idx = row * 3 + col
                            ItemCard(it, idx == selected, variantB, Modifier.weight(1f)) { selected = idx }
                        }
                    }
                }
                if (!variantB) Btn(buyLabel, Modifier.padding(top = 12.dp).fillMaxWidth().height(48.dp))
            }
            TabBar(Tab.Shop, set = if (variantB) "b" else "a")
        }
    }
}

@Composable
private fun ItemCard(item: ShopItem, selected: Boolean, variantB: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier.height(if (variantB) 126.dp else 120.dp).clip(shape).background(DQColors.White)
            .border(if (selected) 2.dp else 1.dp, if (selected) DQColors.PrimaryStrong else DQColors.Line, shape)
            .clickable(onClick = onClick).padding(8.dp),
    ) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.fillMaxWidth().height(if (variantB) 76.dp else 72.dp)
                    .background(if (variantB) DQColors.Soft else DQColors.White, RoundedCornerShape(if (variantB) 14.dp else 12.dp)),
                contentAlignment = Alignment.TopCenter,
            ) { Shirt(item.color, tall = variantB) }
            when (item.price) {
                null -> Txt("보유 중", size = 12f, weight = Medium, color = DQColors.Muted, align = TextAlign.Center, modifier = Modifier.padding(top = if (variantB) 10.dp else 8.dp))
                -1 -> if (variantB) {
                    Txt("미션 보상", size = 12f, weight = Bold, color = DQColors.Orange, modifier = Modifier.padding(top = 10.dp))
                } else {
                    Box(Modifier.padding(top = 6.dp).width(70.dp).height(22.dp).background(DQColors.Soft, RoundedCornerShape(11.dp)), contentAlignment = Alignment.Center) {
                        Txt("미션 보상", size = 11f, weight = Bold, color = DQColors.Orange)
                    }
                }
                else -> Row(Modifier.padding(top = if (variantB) 9.dp else 7.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    DqIcon(R.drawable.ic_coin_16, 16.dp)
                    Txt("${item.price}", size = 13f, weight = Bold)
                }
            }
        }
        if (selected && item.price != null && item.price > 0) {
            Box(Modifier.align(Alignment.TopEnd).offset(x = 2.dp, y = (-4).dp).size(20.dp).background(DQColors.Primary, CircleShape), contentAlignment = Alignment.Center) {
                DqIcon(R.drawable.ic_check_white, 12.dp)
            }
        }
    }
}

/** 옷 썸네일 (디자인의 단순 도형) */
@Composable
private fun Shirt(color: Color, tall: Boolean) {
    val h = if (tall) 42.dp else 40.dp
    val sleeve = if (tall) 22.dp else 20.dp
    Box(Modifier.padding(top = 18.dp).size(60.dp, h)) {
        Box(Modifier.offset(0.dp, 4.dp).size(16.dp, sleeve).background(color, RoundedCornerShape(6.dp)))
        Box(Modifier.offset(44.dp, 4.dp).size(16.dp, sleeve).background(color, RoundedCornerShape(6.dp)))
        Box(Modifier.offset(10.dp, 0.dp).size(40.dp, h).background(color, RoundedCornerShape(12.dp)))
    }
}

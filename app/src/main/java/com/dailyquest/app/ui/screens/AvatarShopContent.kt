package com.dailyquest.app.ui.screens

import android.widget.Toast

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.dailyquest.app.R
import com.dailyquest.app.data.customization.*
import com.dailyquest.app.ui.components.*
import com.dailyquest.app.ui.theme.DQColors

@Composable
fun AvatarShopContent() {
    val context = LocalContext.current
    val store = CustomizationData.store
    val state = store.state
    var segment by rememberSaveable { mutableStateOf("아바타") }
    var slot by rememberSaveable { mutableStateOf(AvatarSlot.TOP) }
    var ownedOnly by rememberSaveable { mutableStateOf(false) }
    var selectedId by rememberSaveable { mutableStateOf("top_premium") }
    var message by remember { mutableStateOf<String?>(null) }
    var draftIds by rememberSaveable { mutableStateOf(ArrayList(AvatarSlot.entries.map { state.equipped.getValue(it) })) }
    val draft = AvatarSlot.entries.associateWith { draftIds[it.ordinal] }
    val body = store.catalog.first { it.itemId == draft.getValue(AvatarSlot.BODY) }
    fun notify(text: String) {
        message = text
        Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
    }
    fun preview(itemId: String, announce: Boolean = false) {
        try {
            val next = store.preview(draft, itemId)
            draftIds = ArrayList(AvatarSlot.entries.map { next.getValue(it) })
            message = null
            if (announce) notify("미리보기에 적용했습니다. 장착 저장을 눌러 확정해 주세요.")
        } catch (e: IllegalArgumentException) { notify(e.message ?: "미리보기를 적용할 수 없습니다.") }
    }
    val products = store.catalog.filter { it.slot == slot && (!ownedOnly || it.itemId in state.owned) }
    val selected = products.find { it.itemId == selectedId }
    DqScreen {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Txt("꾸미기", size = 26f, weight = Black, modifier = Modifier.weight(1f))
                CoinPill(amount = state.coins)
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp)) {
                Row(Modifier.fillMaxWidth().height(42.dp).background(DQColors.Soft, RoundedCornerShape(21.dp)).padding(4.dp)) {
                    listOf("아바타", "우리 집").forEach { label ->
                        Box(Modifier.weight(1f).fillMaxHeight().background(if (segment == label) DQColors.White else DQColors.Soft, RoundedCornerShape(17.dp)).clickable { segment = label }, contentAlignment = Alignment.Center) {
                            Txt(label, weight = Bold, size = 14f)
                        }
                    }
                }
                Box(Modifier.padding(top = 12.dp)) {
                    AvatarPreview(AvatarPreviewSpec(body.visualKey, draft.mapKeys { it.key.name }.mapValues { (_, id) -> store.catalog.first { it.itemId == id }.visualKey }))
                }
                if (segment == "우리 집") {
                    Txt("방 가구 구매·배치는 다음 단계에서 제공됩니다.", modifier = Modifier.padding(vertical = 16.dp))
                } else {
                    InfoNote("2D 임시 미리보기 · 파츠별 실제 3D 외형은 반영되지 않습니다.")
                    Txt("호환성은 Mock 테스트용입니다.", size = 11f, color = DQColors.Muted)
                    Txt(if (draft == state.equipped) "미리보기와 저장된 장착이 동일합니다." else "저장 전 미리보기 · 장착 저장을 눌러 적용하세요.",
                        size = 12f, weight = Bold, color = DQColors.Green, modifier = Modifier.padding(top = 8.dp))
                    AvatarSlot.entries.forEach { entry ->
                        val equippedItem = store.catalog.first { it.itemId == draft.getValue(entry) }
                        Txt("${entry.label}: ${equippedItem.name}", size = 11f, modifier = Modifier.padding(top = 3.dp))
                    }
                    Row(Modifier.padding(top = 16.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        AvatarSlot.entries.forEach { entry ->
                            Column(Modifier.clickable { slot = entry; selectedId = ""; message = null }, horizontalAlignment = Alignment.CenterHorizontally) {
                                Txt(entry.label, weight = if (slot == entry) Bold else Medium, size = 14f)
                                Box(Modifier.padding(top = 9.dp).size(38.dp, 3.dp).background(if (slot == entry) DQColors.Primary else DQColors.White))
                            }
                        }
                    }
                    Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Chip("전체 상품", !ownedOnly) { ownedOnly = false; selectedId = "" }
                        Chip("보유 아이템", ownedOnly) { ownedOnly = true; selectedId = "" }
                    }
                    val savedItem = store.catalog.first { it.itemId == state.equipped.getValue(slot) }
                    Txt("저장된 장착: ${savedItem.name}", size = 12f, weight = Bold, modifier = Modifier.padding(top = 12.dp))
                    products.chunked(3).forEach { row ->
                        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { item ->
                                val owned = item.itemId in state.owned
                                Column(Modifier.weight(1f).height(164.dp).background(DQColors.White, RoundedCornerShape(16.dp)).border(if (selectedId == item.itemId) 2.dp else 1.dp, if (selectedId == item.itemId) DQColors.Primary else DQColors.Line, RoundedCornerShape(16.dp)).clickable { selectedId = item.itemId; preview(item.itemId) }.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    DqAvatar(if (item.visualKey == "female") R.drawable.img_avatar_girl else R.drawable.img_avatar_boy, 56.dp)
                                    Txt(item.name, size = 11f, maxLines = 2)
                                    if (draft[item.slot] == item.itemId) Txt("미리보기 중", size = 10f, color = DQColors.Orange)
                                    if (state.equipped[item.slot] == item.itemId) Txt("저장된 장착", size = 10f, color = DQColors.Green)
                                    Txt(if (item.acquisition == Acquisition.DEFAULT) "기본 제공" else if (owned) "보유 중" else if (item.acquisition == Acquisition.REWARD) "미션 보상" else "${item.price} 코인", size = 11f, color = DQColors.Muted)
                                }
                            }
                            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                    selected?.let { item ->
                        Btn(if (item.itemId in state.owned) "장착 미리보기" else if (item.acquisition == Acquisition.REWARD) "미션 보상 상품" else "${item.price} 코인으로 구매하기", Modifier.padding(top = 12.dp).fillMaxWidth().height(48.dp)) {
                            if (item.itemId in state.owned) preview(item.itemId, announce = true)
                            else notify(when (val result = store.purchase(item.itemId)) {
                                CustomizationResult.Success -> "구매 완료 · 보유 아이템에 추가했습니다."
                                is CustomizationResult.Failure -> result.message
                            })
                        }
                    }
                    Row(Modifier.padding(top = 12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Btn("취소", Modifier.weight(1f).height(44.dp), style = BtnStyle.Outline) {
                            draftIds = ArrayList(AvatarSlot.entries.map { store.state.equipped.getValue(it) })
                            selectedId = ""
                            notify("마지막으로 저장한 장착 상태로 복원했습니다. 구매 내역과 코인은 유지됩니다.")
                        }
                        Btn("장착 저장", Modifier.weight(1f).height(44.dp)) {
                            notify(when (val result = store.save(draft)) {
                                CustomizationResult.Success -> "장착이 완료되었습니다."
                                is CustomizationResult.Failure -> result.message
                            })
                        }
                    }
                    message?.let { Txt(it, modifier = Modifier.padding(top = 12.dp)) }
                }
            }
            TabBar(Tab.Shop)
        }
    }
}

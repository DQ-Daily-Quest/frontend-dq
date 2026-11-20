package com.dailyquest.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dailyquest.app.data.auth.SessionState
import com.dailyquest.app.data.shop.InventoryItem
import com.dailyquest.app.data.shop.ItemCategory
import com.dailyquest.app.data.shop.ItemType
import com.dailyquest.app.data.shop.ShopItem
import com.dailyquest.app.navigation.LocalNav
import com.dailyquest.app.navigation.Routes
import com.dailyquest.app.ui.auth.LocalAuth
import com.dailyquest.app.ui.components.*
import com.dailyquest.app.ui.proof.RemoteImage
import com.dailyquest.app.ui.shop.LocalInventory
import com.dailyquest.app.ui.shop.LocalShop
import com.dailyquest.app.ui.theme.DQColors

/** 카드에 그릴 공통 값. 상점 상품과 보유 아이템을 같은 카드로 보여 준다. */
private data class ShopCardData(
    val id: Long,
    val name: String,
    val imageUrl: String?,
    val owned: Boolean,
    val isDefault: Boolean,
    val price: Int,
    val item: ShopItem?,
)

private fun ShopItem.toCard(owned: Boolean) = ShopCardData(itemId, name, imageUrl, owned, isDefault, price, this)
private fun InventoryItem.toCard() = ShopCardData(itemId, name, imageUrl, true, isDefault, price, null)

/**
 * 상점 탭 — 서버 상품(GET /shop/items)·보유 아이템(GET /users/me/items)·구매(POST)와 연동된다.
 * 코인과 보유 여부는 서버 응답만 사용하며, 아바타 착용·가구 배치는 이후 단계에서 제공한다.
 */
@Composable
fun AvatarShopContent() {
    val context = LocalContext.current
    val nav = LocalNav.current
    val shopVm = LocalShop.current
    val invVm = LocalInventory.current
    val shop by shopVm.ui.collectAsStateWithLifecycle()
    val inv by invVm.ui.collectAsStateWithLifecycle()
    val purchaseVersion by shopVm.purchaseVersion.collectAsStateWithLifecycle()
    val session by LocalAuth.current.session.collectAsStateWithLifecycle()
    val coins = (session as? SessionState.Authenticated)?.profile?.coins ?: 0
    var ownedOnly by rememberSaveable { mutableStateOf(false) }

    // 구매가 확정되면 인벤토리를 다시 조회한다(첫 값은 건너뛴다).
    LaunchedEffect(purchaseVersion) { if (purchaseVersion > 0) invVm.refresh() }
    LaunchedEffect(shop.message) {
        shop.message?.let { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
    }

    val type = shop.type
    val category = if (ownedOnly) inv.category else shop.category

    DqScreen {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Txt("꾸미기", size = 26f, weight = Black, modifier = Modifier.weight(1f))
                CoinPill(
                    amount = coins,
                    modifier = Modifier.testTag("shopCoin").clickable { nav.navigate(Routes.COIN_HISTORY) { launchSingleTop = true } },
                    onPlus = { nav.navigate(Routes.COIN_HISTORY) { launchSingleTop = true } },
                )
            }
            Column(Modifier.padding(horizontal = 16.dp).padding(top = 8.dp)) {
                Row(Modifier.fillMaxWidth().height(42.dp).background(DQColors.Soft, RoundedCornerShape(21.dp)).padding(4.dp)) {
                    listOf(ItemType.AVATAR, ItemType.FURNITURE).forEach { t ->
                        Box(
                            Modifier.weight(1f).fillMaxHeight().testTag("shopType_${t.name}")
                                .background(if (type == t) DQColors.White else DQColors.Soft, RoundedCornerShape(17.dp))
                                .clickable { shopVm.selectType(t); invVm.selectType(t) },
                            contentAlignment = Alignment.Center,
                        ) { Txt(t.label, weight = Bold, size = 14f) }
                    }
                }
                Row(Modifier.padding(top = 12.dp).horizontalScroll(rememberScrollState()).testTag("shopCategories"), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    (listOf<ItemCategory?>(null) + ItemCategory.of(type)).forEach { c ->
                        Column(
                            Modifier.testTag("shopCategory_${c?.name ?: "ALL"}").clickable { shopVm.selectCategory(c); invVm.selectCategory(c) },
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Txt(c?.label ?: "전체", weight = if (category == c) Bold else Medium, size = 14f)
                            Box(Modifier.padding(top = 9.dp).size(38.dp, 3.dp).background(if (category == c) DQColors.Primary else DQColors.White))
                        }
                    }
                }
                Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip("전체 상품", !ownedOnly, Modifier.testTag("shopTabAll")) { ownedOnly = false }
                    Chip("보유 아이템", ownedOnly, Modifier.testTag("shopTabOwned")) { ownedOnly = true; invVm.refresh() }
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (ownedOnly) OwnedList(inv.loading, inv.error, inv.items.map { it.toCard() }, invVm::retry) { }
                else CatalogList(
                    loading = shop.loading, error = shop.error, cards = shop.items.map { it.toCard(shop.isOwned(it)) },
                    hasNext = shop.hasNext, loadingMore = shop.loadingMore, loadMoreError = shop.loadMoreError,
                    onRetry = shopVm::retry, onLoadMore = shopVm::loadMore,
                    onOpen = { c -> c.item?.let(shopVm::openDetail) },
                )
            }
            TabBar(Tab.Shop)
        }
    }

    shop.detail?.let { item ->
        ShopDetailDialog(
            item = item, owned = shop.isOwned(item), coins = coins, loading = shop.detailLoading, error = shop.detailError,
            purchasing = shop.purchasingId == item.itemId, unconfirmed = item.itemId in shop.unconfirmedIds,
            onBuy = { shopVm.purchase(item) }, onClose = shopVm::closeDetail,
        )
    }
}

@Composable
private fun CenterNote(text: String, tag: String, action: String? = null, onAction: () -> Unit = {}) {
    Column(Modifier.fillMaxSize().padding(24.dp).testTag(tag), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Txt(text, size = 14f, color = DQColors.Muted)
        if (action != null) Btn(action, Modifier.padding(top = 14.dp).height(44.dp), onClick = onAction)
    }
}

@Composable
private fun CatalogList(
    loading: Boolean, error: String?, cards: List<ShopCardData>, hasNext: Boolean, loadingMore: Boolean, loadMoreError: String?,
    onRetry: () -> Unit, onLoadMore: () -> Unit, onOpen: (ShopCardData) -> Unit,
) {
    when {
        loading -> Box(Modifier.fillMaxSize().testTag("shopLoading"), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        error != null && cards.isEmpty() -> CenterNote(error, "shopError", "다시 시도", onRetry)
        cards.isEmpty() -> CenterNote("표시할 상품이 없어요.", "shopEmpty")
        else -> CardGrid(cards, onOpen, Modifier.testTag("shopGrid")) {
            if (loadMoreError != null) {
                Txt(loadMoreError, size = 12f, color = DQColors.Muted, modifier = Modifier.padding(top = 10.dp))
                Btn("다시 시도", Modifier.padding(top = 8.dp).height(40.dp), style = BtnStyle.Outline, onClick = onLoadMore)
            } else if (hasNext) {
                Btn(if (loadingMore) "불러오는 중…" else "더 보기", Modifier.padding(top = 10.dp).fillMaxWidth().height(44.dp).testTag("shopLoadMore"), style = BtnStyle.Outline, enabled = !loadingMore, onClick = onLoadMore)
            }
        }
    }
}

@Composable
private fun OwnedList(loading: Boolean, error: String?, cards: List<ShopCardData>, onRetry: () -> Unit, onOpen: (ShopCardData) -> Unit) {
    when {
        loading -> Box(Modifier.fillMaxSize().testTag("inventoryLoading"), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        error != null && cards.isEmpty() -> CenterNote(error, "inventoryError", "다시 시도", onRetry)
        cards.isEmpty() -> CenterNote("보유한 아이템이 없어요.", "inventoryEmpty")
        else -> CardGrid(cards, onOpen, Modifier.testTag("inventoryGrid")) {}
    }
}

@Composable
private fun CardGrid(cards: List<ShopCardData>, onOpen: (ShopCardData) -> Unit, modifier: Modifier, footer: @Composable () -> Unit) {
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items(cards.chunked(3), key = { row -> row.first().id }) { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { c -> ProductCard(c, Modifier.weight(1f)) { onOpen(c) } }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        item { Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) { footer() } }
    }
}

@Composable
private fun ProductCard(c: ShopCardData, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier.height(164.dp).testTag("shopItem_${c.id}").background(DQColors.White, shape).border(1.dp, DQColors.Line, shape)
            .clickable(onClick = onClick).padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        RemoteImage(c.imageUrl, Modifier.size(72.dp).background(DQColors.Soft, RoundedCornerShape(12.dp)), placeholder = "이미지 없음")
        Txt(c.name, size = 11f, maxLines = 2, modifier = Modifier.padding(top = 6.dp))
        Txt(
            if (c.isDefault && c.owned) "기본 제공" else if (c.owned) "보유 중" else "${c.price} 코인",
            size = 11f, color = if (c.owned) DQColors.Green else DQColors.Muted, modifier = Modifier.testTag("shopItemState_${c.id}"),
        )
    }
}

@Composable
private fun ShopDetailDialog(
    item: ShopItem, owned: Boolean, coins: Int, loading: Boolean, error: String?,
    purchasing: Boolean, unconfirmed: Boolean, onBuy: () -> Unit, onClose: () -> Unit,
) {
    var confirming by rememberSaveable(item.itemId) { mutableStateOf(false) }
    val lacking = !owned && coins < item.price
    AlertDialog(
        onDismissRequest = { if (!purchasing) onClose() },
        modifier = Modifier.testTag("shopDetail"),
        title = { Text(if (confirming && !owned) "구매할까요?" else item.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (!confirming || owned) {
                    RemoteImage(item.imageUrl, Modifier.fillMaxWidth().height(140.dp).background(DQColors.Soft, RoundedCornerShape(12.dp)), placeholder = "이미지 없음")
                    Text("${item.type.label} · ${item.category.label}")
                    Text(if (item.isDefault && owned) "기본 제공 아이템" else if (owned) "보유 중" else "${item.price} 코인")
                    if (loading) Text("상세 정보를 확인하는 중이에요.")
                    error?.let { Text(it) }
                    if (unconfirmed && !owned) Text("이전 구매 결과를 확인하지 못했어요. 다시 시도하면 보유 여부를 먼저 확인해요.")
                    if (lacking) Text("코인이 부족해요. (보유 $coins 코인)")
                } else {
                    Text("'${item.name}' — ${item.price} 코인")
                    Text("보유 코인 $coins → 구매 후 ${coins - item.price}")
                    Text("구매 후에는 환불할 수 없어요.")
                }
            }
        },
        confirmButton = {
            when {
                owned -> TextButton(onClose) { Text("닫기") }
                !confirming -> TextButton(onClick = { confirming = true }, enabled = !lacking && !loading, modifier = Modifier.testTag("shopBuy")) { Text(if (lacking) "코인 부족" else "${item.price} 코인으로 구매") }
                else -> TextButton(onClick = onBuy, enabled = !purchasing && !lacking, modifier = Modifier.testTag("shopConfirm")) { Text(if (purchasing) "구매 중…" else "구매 확정") }
            }
        },
        dismissButton = {
            if (!owned) TextButton(onClick = { if (confirming) confirming = false else onClose() }, enabled = !purchasing) { Text(if (confirming) "뒤로" else "닫기") }
        },
    )
}

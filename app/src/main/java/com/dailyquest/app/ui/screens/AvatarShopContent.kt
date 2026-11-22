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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dailyquest.app.data.auth.SessionState
import com.dailyquest.app.data.avatar.AvatarPart
import com.dailyquest.app.data.remote.ApiConfig
import com.dailyquest.app.ui.avatar.MyAvatarPreview
import com.dailyquest.app.ui.avatar.MyAvatarViewModel
import com.dailyquest.app.ui.avatar.REMOVABLE_PARTS
import com.dailyquest.app.ui.avatar.TryOnItem
import com.dailyquest.app.ui.room.LocalAppContainer
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

/** 카드에 그릴 공통 값. 상점 상품과 보유 아이템을 같은 카드로 보여 준다. [category]·[modelUrl]은 아바타 입어보기(STEP 9-7)에 쓴다. */
private data class ShopCardData(
    val id: Long,
    val name: String,
    val imageUrl: String?,
    val owned: Boolean,
    val isDefault: Boolean,
    val price: Int,
    val item: ShopItem?,
    val category: ItemCategory = ItemCategory.UNKNOWN,
    val modelUrl: String? = null,
)

private fun ShopItem.toCard(owned: Boolean) = ShopCardData(itemId, name, imageUrl, owned, isDefault, price, this, category, modelUrl)
private fun InventoryItem.toCard() = ShopCardData(itemId, name, imageUrl, true, isDefault, price, null, category, modelUrl)

/** 아바타 카드면 입어볼 아이템으로 바꾼다. 아바타 슬롯이 아닌 카테고리면 null. */
private fun ShopCardData.toTryOn(): TryOnItem? =
    AvatarPart.fromKey(category.name)?.let { part -> TryOnItem(id, name, part, ApiConfig.resolveFileUrl(modelUrl)) }

/**
 * 상점 탭 — 서버 상품(GET /shop/items)·보유 아이템(GET /users/me/items)·구매(POST)와 연동된다.
 * 코인과 보유 여부는 서버 응답만 사용하며, 아바타 착용·가구 배치는 이후 단계에서 제공한다.
 *
 * 아바타 탭(STEP 9-7): 위쪽에 내 아바타 3D 미리보기가 있고, 아이템을 누르면 바로 입어 본다(같은 아이템을 다시 누르면 벗는다).
 * 고른 아이템은 아래 버튼으로 구매한다(기존 상세·구매 창). 가구 탭은 지금처럼 누르면 바로 상세 창이 뜬다.
 * 보유한 아이템은 아래 버튼으로 착용을 저장한다(STEP 9-8). 입고 있는 모자는 벗을 수 있고, 다른 슬롯은 벗을 수 없다.
 */
@Composable
fun AvatarShopContent(startOwned: Boolean = false) {
    val context = LocalContext.current
    val nav = LocalNav.current
    val shopVm = LocalShop.current
    val invVm = LocalInventory.current
    val shop by shopVm.ui.collectAsStateWithLifecycle()
    val inv by invVm.ui.collectAsStateWithLifecycle()
    val purchaseVersion by shopVm.purchaseVersion.collectAsStateWithLifecycle()
    val session by LocalAuth.current.session.collectAsStateWithLifecycle()
    val coins = (session as? SessionState.Authenticated)?.profile?.coins ?: 0
    var ownedOnly by rememberSaveable { mutableStateOf(startOwned) }
    val avatarVm: MyAvatarViewModel = viewModel(factory = MyAvatarViewModel.Factory(LocalAppContainer.current))
    val avatar by avatarVm.ui.collectAsStateWithLifecycle()
    // 아바타 탭에서 고른(입어 보는) 카드. 같은 카드를 다시 누르면 해제한다.
    var selectedId by rememberSaveable { mutableStateOf<Long?>(null) }
    LaunchedEffect(Unit) { if (startOwned) invVm.refresh() }

    // 구매가 확정되면 인벤토리를 다시 조회한다(첫 값은 건너뛴다).
    LaunchedEffect(purchaseVersion) { if (purchaseVersion > 0) invVm.refresh() }
    LaunchedEffect(shop.message) {
        shop.message?.let { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
    }
    LaunchedEffect(avatar.message) {
        avatar.message?.let { Toast.makeText(context, it, Toast.LENGTH_SHORT).show(); avatarVm.consumeMessage() }
    }

    val type = shop.type
    val category = if (ownedOnly) inv.category else shop.category
    val avatarTab = type == ItemType.AVATAR
    val cards = if (ownedOnly) inv.items.map { it.toCard() } else shop.items.map { it.toCard(shop.isOwned(it)) }
    val selectedCard = cards.firstOrNull { it.id == selectedId }

    fun selectForTryOn(c: ShopCardData) {
        val tryOn = c.toTryOn() ?: return
        selectedId = if (selectedId == c.id) null else c.id
        avatarVm.toggleTryOn(tryOn)
    }
    val onCard: (ShopCardData) -> Unit = { c -> if (avatarTab) selectForTryOn(c) else c.item?.let(shopVm::openDetail) }
    val noop: (ShopCardData) -> Unit = {}
    val tryingOn: (ShopCardData) -> Boolean = { c -> avatarTab && avatar.isTryingOn(c.id) }

    DqScreen {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Txt("꾸미기", size = 26f, weight = Black, modifier = Modifier.weight(1f))
                SessionCoinPill("shopCoin")
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
                if (avatarTab) {
                    // 높이를 고정한다. 3D 화면 크기가 자주 바뀌면 화면 버퍼를 다시 만들게 된다.
                    MyAvatarPreview(
                        state = avatar,
                        onRetry = avatarVm::refresh,
                        onClearTryOn = { avatarVm.clearTryOn(); selectedId = null },
                        modifier = Modifier.padding(top = 12.dp).fillMaxWidth().height(200.dp),
                    )
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (ownedOnly) OwnedList(inv.loading, inv.error, cards, invVm::retry, tryingOn, if (avatarTab) onCard else noop)
                else CatalogList(
                    loading = shop.loading, error = shop.error, cards = cards,
                    hasNext = shop.hasNext, loadingMore = shop.loadingMore, loadMoreError = shop.loadMoreError,
                    onRetry = shopVm::retry, onLoadMore = shopVm::loadMore,
                    isSelected = tryingOn, onOpen = onCard,
                )
            }
            val selectedTryOn = selectedCard?.toTryOn()
            if (avatarTab && selectedCard != null && selectedTryOn != null) TryOnActionBar(
                c = selectedCard,
                equipped = avatar.isEquipped(selectedTryOn),
                saving = avatar.saving,
                canEquip = avatar.avatar?.needsGender == false,
                onBuy = { selectedCard.item?.let(shopVm::openDetail) },
                onEquip = { avatarVm.equip(selectedTryOn) },
                onTakeOff = { avatarVm.takeOff(selectedTryOn.part) },
            )
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

/**
 * 아바타 탭에서 고른 아이템의 버튼 (STEP 9-7, 9-8).
 * - 보유하지 않음: "N 코인으로 구매" → 기존 상세·구매 창
 * - 보유, 입고 있지 않음: "착용하기" → 서버에 저장
 * - 입고 있는 모자: "모자 벗기" / 입고 있는 다른 슬롯: "착용 중"(비활성. 모자 외에는 벗을 수 없다)
 */
@Composable
private fun TryOnActionBar(
    c: ShopCardData, equipped: Boolean, saving: Boolean, canEquip: Boolean,
    onBuy: () -> Unit, onEquip: () -> Unit, onTakeOff: () -> Unit,
) {
    val part = c.toTryOn()?.part
    val modifier = Modifier.fillMaxWidth().height(48.dp).testTag("shopTryOnAction")
    Box(Modifier.fillMaxWidth().background(DQColors.White).padding(horizontal = 16.dp, vertical = 8.dp)) {
        when {
            !c.owned -> Btn("${c.price} 코인으로 구매", modifier, enabled = c.item != null, onClick = onBuy)
            saving -> Btn("저장 중…", modifier, style = BtnStyle.Soft, enabled = false)
            equipped && part in REMOVABLE_PARTS -> Btn("모자 벗기", modifier, style = BtnStyle.Outline, onClick = onTakeOff)
            equipped -> Btn("착용 중", modifier, style = BtnStyle.Soft, enabled = false)
            else -> Btn("착용하기", modifier, enabled = canEquip, onClick = onEquip)
        }
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
    onRetry: () -> Unit, onLoadMore: () -> Unit, isSelected: (ShopCardData) -> Boolean, onOpen: (ShopCardData) -> Unit,
) {
    when {
        loading -> Box(Modifier.fillMaxSize().testTag("shopLoading"), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        error != null && cards.isEmpty() -> CenterNote(error, "shopError", "다시 시도", onRetry)
        cards.isEmpty() -> CenterNote("표시할 상품이 없어요.", "shopEmpty")
        else -> CardGrid(cards, isSelected, onOpen, Modifier.testTag("shopGrid")) {
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
private fun OwnedList(
    loading: Boolean, error: String?, cards: List<ShopCardData>, onRetry: () -> Unit,
    isSelected: (ShopCardData) -> Boolean, onOpen: (ShopCardData) -> Unit,
) {
    when {
        loading -> Box(Modifier.fillMaxSize().testTag("inventoryLoading"), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        error != null && cards.isEmpty() -> CenterNote(error, "inventoryError", "다시 시도", onRetry)
        cards.isEmpty() -> CenterNote("보유한 아이템이 없어요.", "inventoryEmpty")
        else -> CardGrid(cards, isSelected, onOpen, Modifier.testTag("inventoryGrid")) {}
    }
}

@Composable
private fun CardGrid(
    cards: List<ShopCardData>, isSelected: (ShopCardData) -> Boolean, onOpen: (ShopCardData) -> Unit,
    modifier: Modifier, footer: @Composable () -> Unit,
) {
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items(cards.chunked(3), key = { row -> row.first().id }) { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { c -> ProductCard(c, isSelected(c), Modifier.weight(1f)) { onOpen(c) } }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        item { Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) { footer() } }
    }
}

@Composable
private fun ProductCard(c: ShopCardData, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier.height(164.dp).testTag("shopItem_${c.id}").background(DQColors.White, shape)
            .border(if (selected) 2.dp else 1.dp, if (selected) DQColors.PrimaryStrong else DQColors.Line, shape)
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

package com.dailyquest.app.ui.screens

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
import androidx.compose.ui.layout.ContentScale
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.layout.layout
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import com.dailyquest.app.ui.shop.purchaseSummary
import com.dailyquest.app.ui.shop.coinText
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.dailyquest.app.R
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

/**
 * 썸네일 맞춤 방식 (STEP 10-8). 아바타 썸네일은 512×512 정사각형이라 꽉 채워도 잘리지 않는다.
 * 가구는 512×512 상점용 썸네일(thumbnailUrl)이 있으면 그것을 쓰고, 없으면 방에 놓는 그림(28~120px 직사각형)을 쓴다.
 * 그 그림을 꽉 채우면 위아래·양옆이 잘리고 많이 늘어나므로, 가구는 칸 안에 다 보이게 맞춘다(썸네일이어도 결과는 같다).
 */
/** 시트 손잡이 위 여백·작은 아바타 높이·내렸을 때 보이는 시트 높이(손잡이 + 분류 + 칩). */
private val SHEET_GAP = 12.dp
private val SMALL_AVATAR_HEIGHT = 200.dp
private val SHEET_PEEK = 118.dp

/**
 * 상점 본문: 아바타(위) + 흰 시트(아래). 시트는 홈 아래 시트처럼 둥근 윗모서리·그림자·손잡이 막대가 있다.
 * - 아바타 탭: 시트 위쪽(손잡이·분류·칩)을 끌거나 손잡이를 누르면 시트가 내려가 아바타가 크게 보이고, 다시 올리면 작은 아바타 + 상품 목록.
 *   3D 화면은 크기가 바뀌면 그림 버퍼를 다시 만들다 실패할 수 있어(EGL_BAD_ALLOC), 가장 큰 크기로 한 번 만들고 줄여서만 그린다.
 *   아바타 상자(배경·입어보기 글자)의 높이만 시트 위치를 따라 바뀐다.
 * - 가구 탭: 아바타가 없어 시트가 위까지 차고 끌 수 없다.
 */
@Composable
private fun ShopSheetLayout(
    modifier: Modifier,
    avatarTab: Boolean,
    avatar: @Composable (modifier: Modifier, sceneHeight: Dp, sceneScale: () -> Float, interactive: Boolean) -> Unit,
    header: @Composable ColumnScope.() -> Unit,
    content: @Composable () -> Unit,
) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val density = LocalDensity.current
        val fullHeight = maxHeight
        val expandedTop = if (avatarTab) SHEET_GAP + SMALL_AVATAR_HEIGHT + SHEET_GAP else SHEET_GAP
        val collapsedTop = (fullHeight - SHEET_PEEK).coerceAtLeast(expandedTop)
        val expandedPx = with(density) { expandedTop.toPx() }
        val collapsedPx = with(density) { collapsedTop.toPx() }
        var collapsed by rememberSaveable { mutableStateOf(false) }
        if (!avatarTab) collapsed = false
        var top by remember { mutableFloatStateOf(expandedPx) }
        var settling by remember { mutableStateOf(false) }
        var dragging by remember { mutableStateOf(false) }
        // 화면 크기·탭이 바뀌면 현재 상태 위치로 맞춘다.
        LaunchedEffect(expandedPx, collapsedPx, avatarTab) { top = if (collapsed) collapsedPx else expandedPx }
        val scope = rememberCoroutineScope()

        fun settleTo(down: Boolean, velocity: Float = 0f) {
            scope.launch {
                settling = true
                val target = if (down) collapsedPx else expandedPx
                animate(top, target, velocity, spring(stiffness = Spring.StiffnessMediumLow)) { v, _ -> top = v }
                collapsed = down
                settling = false
            }
        }

        if (avatarTab) {
            // 3D 화면은 가장 큰 높이(시트를 내렸을 때)로 고정. 상자는 시트 위쪽까지만 보이고, 3D는 그 비율로 줄여 그린다.
            // 아래 "구매하기" 막대가 생기고 사라져도 3D 높이가 바뀌지 않게, 지금까지 잰 가장 큰 높이를 쓴다.
            val gapPx = with(density) { SHEET_GAP.toPx() }
            var scenePx by remember { mutableFloatStateOf(0f) }
            (collapsedPx - gapPx * 2).let { if (it > scenePx) scenePx = it }
            val sceneHeight = with(density) { scenePx.toDp() }
            avatar(
                Modifier.padding(horizontal = 16.dp).padding(top = SHEET_GAP).fillMaxWidth()
                    .layout { measurable, constraints ->
                        // 시트 위치를 읽어 높이만 다시 잰다(다시 그리기만, 화면 구성은 다시 하지 않음).
                        val h = (top - gapPx * 2).roundToInt().coerceIn(0, constraints.maxHeight)
                        val placeable = measurable.measure(constraints.copy(minHeight = h, maxHeight = h))
                        layout(placeable.width, h) { placeable.place(0, 0) }
                    }
                    .testTag(if (collapsed) "shopAvatarLarge" else "shopAvatarSmall"),
                sceneHeight,
                { ((top - gapPx * 2) / scenePx).coerceIn(0.05f, 1f) },
                // 3D 화면이 보이는 상자에 딱 맞을 때(시트를 내린 뒤, 아래 막대 없음)만 손가락으로 돌린다.
                // 3D 화면은 보이는 것보다 크게 놓여 시트 아래까지 이어지는데, 그때 돌리기를 켜 두면 시트 끌기를 가로챈다.
                collapsed && !dragging && !settling && scenePx <= collapsedPx - gapPx * 2 + 1f,
            )
        }

        val sheetShape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)
        Column(
            Modifier
                .offset { IntOffset(0, top.roundToInt()) }
                .fillMaxWidth()
                .height(fullHeight - expandedTop)
                .shadow(10.dp, sheetShape, spotColor = Color.Black.copy(alpha = 0.2f))
                .background(DQColors.White, sheetShape)
                .testTag("shopSheet"),
        ) {
            Column(
                Modifier.fillMaxWidth().then(
                    if (avatarTab) Modifier.draggable(
                        orientation = Orientation.Vertical,
                        enabled = !settling,
                        state = rememberDraggableState { delta -> top = (top + delta).coerceIn(expandedPx, collapsedPx) },
                        onDragStarted = { dragging = true },
                        onDragStopped = { velocity ->
                            dragging = false
                            val down = when {
                                velocity > 1_000f -> true
                                velocity < -1_000f -> false
                                else -> top > (expandedPx + collapsedPx) / 2
                            }
                            settleTo(down, velocity)
                        },
                    ) else Modifier,
                ),
            ) {
                if (avatarTab) {
                    // 손잡이: 누르면 내리기/올리기.
                    Box(
                        Modifier.fillMaxWidth().height(26.dp).clickable(role = Role.Button, enabled = !settling) { settleTo(!collapsed) }
                            .semantics { contentDescription = if (collapsed) "상품 목록 올리기" else "아바타 크게 보기" }
                            .testTag("shopSheetHandle"),
                        contentAlignment = Alignment.Center,
                    ) { Box(Modifier.size(40.dp, 4.dp).background(DQColors.Handle, RoundedCornerShape(2.dp))) }
                } else {
                    Spacer(Modifier.height(16.dp))
                }
                header()
                Spacer(Modifier.height(4.dp))
            }
            Box(Modifier.weight(1f).fillMaxWidth()) { content() }
        }
    }
}

private fun thumbScale(type: ItemType): ContentScale = if (type == ItemType.FURNITURE) ContentScale.Fit else ContentScale.Crop

// 카드에는 상점용 썸네일(있으면)을 쓴다. 3D 입어보기는 modelUrl만 쓰므로 영향이 없다.
private fun ShopItem.toCard(owned: Boolean) = ShopCardData(itemId, name, cardImageUrl, owned, isDefault, price, this, category, modelUrl)
private fun InventoryItem.toCard() = ShopCardData(itemId, name, cardImageUrl, true, isDefault, price, null, category, modelUrl)

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
    val toast = LocalDqToast.current
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
        shop.message?.let { toast.show(it, isError = shop.messageIsError); shopVm.consumeMessage() }
    }
    LaunchedEffect(avatar.message) {
        avatar.message?.let { toast.show(it, isError = avatar.messageIsError); avatarVm.consumeMessage() }
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
            }
            // 아바타 탭: 위에 내 아바타, 아래에 흰 시트(분류·상품 목록). 시트를 내리면 아바타가 크게, 올리면 지금처럼(QA 디자인).
            // 가구 탭: 아바타가 없어 시트가 위까지 차고 끌 수 없다.
            ShopSheetLayout(
                modifier = Modifier.weight(1f),
                avatarTab = avatarTab,
                avatar = { modifier, sceneHeight, sceneScale, interactive ->
                    MyAvatarPreview(
                        state = avatar,
                        onRetry = avatarVm::refresh,
                        onClearTryOn = { avatarVm.clearTryOn(); selectedId = null },
                        modifier = modifier,
                        sceneHeight = sceneHeight,
                        sceneScale = sceneScale,
                        sceneInteractive = interactive,
                    )
                },
                header = {
                    Row(Modifier.padding(horizontal = 16.dp).horizontalScroll(rememberScrollState()).testTag("shopCategories"), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
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
                    Row(Modifier.padding(horizontal = 16.dp).padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Chip("전체 상품", !ownedOnly, Modifier.testTag("shopTabAll")) { ownedOnly = false }
                        Chip("보유 아이템", ownedOnly, Modifier.testTag("shopTabOwned")) { ownedOnly = true; invVm.refresh() }
                    }
                },
            ) {
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
 * - 보유하지 않음: "구매하기" → 상세·구매 창(가격은 카드와 상세 창에 있다. QA 디자인)
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
            !c.owned -> Btn("구매하기", modifier, enabled = c.item != null, onClick = onBuy)
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
        RemoteImage(
            c.imageUrl, Modifier.size(72.dp).background(DQColors.Soft, RoundedCornerShape(12.dp)).testTag("shopThumb_${c.id}"),
            placeholder = "이미지 없음", contentScale = thumbScale(c.category.type),
        )
        Txt(c.name, size = 11f, maxLines = 2, modifier = Modifier.padding(top = 6.dp))
        Txt(
            if (c.isDefault && c.owned) "기본 제공" else if (c.owned) "보유 중" else "${c.price} 코인",
            size = 11f, color = if (c.owned) DQColors.Green else DQColors.Muted, modifier = Modifier.testTag("shopItemState_${c.id}"),
        )
    }
}

/** 구매 확인 창의 금액 한 줄: 왼쪽 이름, 오른쪽 숫자. */
@Composable
private fun ConfirmLine(label: String, value: String, color: androidx.compose.ui.graphics.Color, bold: Boolean, tag: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Txt(label, size = if (bold) 15f else 14f, weight = if (bold) Bold else Medium, color = DQColors.Ink, modifier = Modifier.weight(1f))
        Txt(value, size = if (bold) 17f else 14f, weight = Bold, color = color, modifier = Modifier.testTag(tag))
    }
}

/**
 * 상품 상세 창 (QA 디자인 5-1단계): "상품 상세" + X, 큰 그림, 분류·이름·가격, "○○ 코인으로 구매".
 * 누르면 같은 창 안에서 구매 확인(5-2단계)으로 넘어간다. 상품 설명은 서버에 없어 넣지 않는다.
 */
@Composable
private fun ShopDetailDialog(
    item: ShopItem, owned: Boolean, coins: Int, loading: Boolean, error: String?,
    purchasing: Boolean, unconfirmed: Boolean, onBuy: () -> Unit, onClose: () -> Unit,
) {
    var confirming by rememberSaveable(item.itemId) { mutableStateOf(false) }
    val lacking = !owned && coins < item.price
    Dialog(onDismissRequest = { if (!purchasing) onClose() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.padding(horizontal = 24.dp).fillMaxWidth().widthIn(max = 420.dp)
                .background(DQColors.White, RoundedCornerShape(24.dp)).padding(20.dp).testTag("shopDetail"),
        ) {
            if (!confirming || owned) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Txt("상품 상세", size = 17f, weight = Bold, color = DQColors.InkGreen, modifier = Modifier.weight(1f))
                    Box(
                        Modifier.size(32.dp).clip(CircleShape).clickable(enabled = !purchasing, role = Role.Button, onClick = onClose)
                            .semantics { contentDescription = "닫기" }.testTag("shopDetailClose"),
                        contentAlignment = Alignment.Center,
                    ) { Image(painterResource(R.drawable.ic_close), null, Modifier.size(22.dp)) }
                }
                RemoteImage(
                    item.cardImageUrl,
                    Modifier.padding(top = 12.dp).fillMaxWidth().height(190.dp).clip(RoundedCornerShape(16.dp)).background(DQColors.Soft),
                    placeholder = "이미지 없음", contentScale = ContentScale.Fit,
                )
                Txt("${item.type.label} · ${item.category.label}", size = 12.5f, color = DQColors.Muted, modifier = Modifier.padding(top = 14.dp))
                Txt(item.name, size = 20f, weight = Bold, color = DQColors.InkGreen, modifier = Modifier.padding(top = 2.dp))
                Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (item.isDefault && owned) Txt("기본 제공 아이템", size = 15f, weight = Bold, color = DQColors.Green)
                    else if (owned) Txt("보유 중", size = 15f, weight = Bold, color = DQColors.Green)
                    else {
                        Image(painterResource(R.drawable.ic_coin), null, Modifier.size(20.dp))
                        Txt("${item.price}", size = 17f, weight = Bold, color = DQColors.InkGreen)
                    }
                }
                val notes = listOfNotNull(
                    "상세 정보를 확인하는 중이에요.".takeIf { loading },
                    error,
                    "이전 구매 결과를 확인하지 못했어요. 다시 시도하면 보유 여부를 먼저 확인해요.".takeIf { unconfirmed && !owned },
                    "코인이 부족해요. (보유 $coins 코인)".takeIf { lacking },
                )
                notes.forEach { Txt(it, size = 12.5f, color = if (it == error || lacking && it.startsWith("코인")) DQColors.Danger else DQColors.Muted, modifier = Modifier.padding(top = 6.dp)) }
                val buttonShape = RoundedCornerShape(14.dp)
                if (owned) {
                    Box(
                        Modifier.padding(top = 18.dp).fillMaxWidth().height(52.dp).clip(buttonShape).border(1.dp, DQColors.Line, buttonShape)
                            .clickable(role = Role.Button, onClick = onClose),
                        contentAlignment = Alignment.Center,
                    ) { Txt("닫기", size = 15f, weight = Bold, color = DQColors.InkGreen) }
                } else {
                    val enabled = !lacking && !loading
                    Box(
                        Modifier.padding(top = 18.dp).fillMaxWidth().height(52.dp).clip(buttonShape)
                            .background(if (enabled) DQColors.InkGreen else DQColors.Line)
                            .clickable(enabled = enabled, role = Role.Button) { confirming = true }
                            .testTag("shopBuy"),
                        contentAlignment = Alignment.Center,
                    ) { Txt(if (lacking) "코인 부족" else "${item.price} 코인으로 구매", size = 15f, weight = Bold, color = if (enabled) DQColors.White else DQColors.Muted) }
                }
            } else {
                // 구매 확인(QA 디자인 5-2단계): 상품 줄, 보유 코인 / 사용 코인 / 구매 후 잔액, 취소·구매하기.
                val sum = purchaseSummary(coins, item.price)
                Txt("이 아이템을 구매할까요?", size = 18f, weight = Bold, color = DQColors.InkGreen, align = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                Row(
                    Modifier.padding(top = 16.dp).fillMaxWidth().border(1.dp, DQColors.Line, RoundedCornerShape(14.dp)).padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    RemoteImage(
                        item.cardImageUrl, Modifier.size(56.dp).clip(RoundedCornerShape(10.dp)).background(DQColors.Soft),
                        placeholder = "", contentScale = ContentScale.Fit,
                    )
                    Column(Modifier.weight(1f)) {
                        Txt(item.name, size = 15f, weight = Bold, maxLines = 1)
                        Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Image(painterResource(R.drawable.ic_coin), null, Modifier.size(16.dp))
                            Txt(coinText(item.price), size = 14f, weight = Bold)
                        }
                    }
                }
                Column(Modifier.padding(top = 16.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ConfirmLine("보유 코인", sum.balance, DQColors.Ink, bold = false, tag = "confirmBalance")
                    ConfirmLine("사용 코인", sum.cost, DQColors.Danger, bold = false, tag = "confirmCost")
                    Box(Modifier.fillMaxWidth().height(1.dp).background(DQColors.Line))
                    ConfirmLine("구매 후 잔액", sum.after, DQColors.Green, bold = true, tag = "confirmAfter")
                }
                Txt(
                    "구매 후에는 환불할 수 없어요.", size = 12.5f, color = DQColors.Muted, align = TextAlign.Center,
                    modifier = Modifier.padding(top = 16.dp).fillMaxWidth(),
                )
                val buttonShape = RoundedCornerShape(14.dp)
                Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    // 취소는 창을 닫지 않고 상품 상세로 돌아간다.
                    Box(
                        Modifier.weight(1f).height(50.dp).clip(buttonShape).border(1.dp, DQColors.PrimaryStrong, buttonShape)
                            .clickable(enabled = !purchasing, role = Role.Button) { confirming = false }.testTag("shopConfirmCancel"),
                        contentAlignment = Alignment.Center,
                    ) { Txt("취소", size = 15f, weight = Bold, color = DQColors.InkGreen) }
                    val canBuy = !purchasing && !lacking
                    Box(
                        Modifier.weight(1f).height(50.dp).clip(buttonShape).background(if (canBuy) DQColors.InkGreen else DQColors.Line)
                            .clickable(enabled = canBuy, role = Role.Button, onClick = onBuy).testTag("shopConfirm"),
                        contentAlignment = Alignment.Center,
                    ) { Txt(if (purchasing) "구매 중…" else "구매하기", size = 15f, weight = Bold, color = if (canBuy) DQColors.White else DQColors.Muted) }
                }
            }
        }
    }
}

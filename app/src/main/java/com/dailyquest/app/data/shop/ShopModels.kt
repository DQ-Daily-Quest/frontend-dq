package com.dailyquest.app.data.shop

import com.dailyquest.app.data.remote.ApiConfig
import com.dailyquest.app.data.remote.dto.CoinTransactionDto
import com.dailyquest.app.data.remote.dto.ItemDto
import com.dailyquest.app.data.remote.dto.MyItemDto

/** 서버 ItemType. 알 수 없는 값은 UNKNOWN으로 두고 목록의 '전체'에서만 보여 준다. */
enum class ItemType(val label: String) {
    AVATAR("아바타"), FURNITURE("우리 집"), UNKNOWN("기타");

    companion object { fun parse(v: String?) = entries.firstOrNull { it != UNKNOWN && it.name == v } ?: UNKNOWN }
}

/** 서버 ItemCategory(아바타 7·가구 7). */
enum class ItemCategory(val type: ItemType, val label: String) {
    HAIR(ItemType.AVATAR, "머리"), EYES(ItemType.AVATAR, "눈"), SKIN(ItemType.AVATAR, "피부"), TOP(ItemType.AVATAR, "상의"),
    BOTTOM(ItemType.AVATAR, "하의"), SHOES(ItemType.AVATAR, "신발"), HAT(ItemType.AVATAR, "모자"),
    DESK(ItemType.FURNITURE, "책상"), CHAIR(ItemType.FURNITURE, "의자"), BED(ItemType.FURNITURE, "침대"), STORAGE(ItemType.FURNITURE, "수납"),
    DECO(ItemType.FURNITURE, "소품"), WALL(ItemType.FURNITURE, "벽"), FLOOR(ItemType.FURNITURE, "바닥"),
    UNKNOWN(ItemType.UNKNOWN, "기타");

    companion object {
        fun parse(v: String?) = entries.firstOrNull { it != UNKNOWN && it.name == v } ?: UNKNOWN
        fun of(type: ItemType) = entries.filter { it.type == type && it != UNKNOWN }
    }
}

/**
 * 상점 상품. [imageUrl]은 요청 가능한 절대 주소(없으면 null), [modelUrl]은 STEP 9·10의 3D 렌더링용으로 서버 값 그대로 보관한다.
 * 가격 0 + 기본 아이템은 가입 시 지급되는 기본 제공 아이템이다.
 */
data class ShopItem(
    val itemId: Long,
    val name: String,
    val type: ItemType,
    val category: ItemCategory,
    val price: Int,
    val imageUrl: String?,
    val modelUrl: String?,
    val isDefault: Boolean,
    val owned: Boolean,
    /** 상점용 썸네일. 없으면 null (STEP 10-8). 카드에는 [cardImageUrl]을 쓴다. */
    val thumbnailUrl: String? = null,
) {
    /** 상점 카드·상세에 보여 줄 그림: 썸네일이 있으면 썸네일, 없으면 원래 그림. */
    val cardImageUrl: String? get() = thumbnailUrl ?: imageUrl
}

fun ItemDto.toShopItem() = ShopItem(
    itemId, name, ItemType.parse(type), ItemCategory.parse(category), price,
    ApiConfig.resolveFileUrl(imageUrl), modelUrl, isDefault, owned,
    thumbnailUrl = ApiConfig.resolveFileUrl(thumbnailUrl),
)

enum class AcquiredBy(val label: String) {
    SHOP("상점 구매"), DEFAULT("기본 제공"), UNKNOWN("보유");

    companion object { fun parse(v: String?) = entries.firstOrNull { it != UNKNOWN && it.name == v } ?: UNKNOWN }
}

/** 내 보유 아이템. [equipped]는 서버의 착용 여부이며 착용 변경 기능은 STEP 9에서 구현한다. */
data class InventoryItem(
    val itemId: Long,
    val name: String,
    val type: ItemType,
    val category: ItemCategory,
    val price: Int,
    val imageUrl: String?,
    val modelUrl: String?,
    val isDefault: Boolean,
    val acquiredAt: String?,
    val acquiredBy: AcquiredBy,
    val equipped: Boolean,
    /** 상점용 썸네일. 없으면 null (STEP 10-8). 카드에는 [cardImageUrl]을 쓴다. */
    val thumbnailUrl: String? = null,
) {
    val cardImageUrl: String? get() = thumbnailUrl ?: imageUrl
}

fun MyItemDto.toInventoryItem() = InventoryItem(
    itemId, name, ItemType.parse(type), ItemCategory.parse(category), price,
    ApiConfig.resolveFileUrl(imageUrl), modelUrl, isDefault, acquiredAt, AcquiredBy.parse(acquiredBy), equipped,
    thumbnailUrl = ApiConfig.resolveFileUrl(thumbnailUrl),
)

data class ShopPage(val items: List<ShopItem>, val page: Int, val totalPages: Int, val totalElements: Long) {
    val hasNext: Boolean get() = page + 1 < totalPages
}

/** 구매 성공 응답. [remainingCoins]는 서버가 계산한 차감 후 잔액이다. */
data class PurchaseResult(val itemId: Long, val remainingCoins: Int)

/** 서버 CoinReason. 알 수 없는 값은 UNKNOWN. */
enum class CoinReason(val label: String) {
    MISSION_REWARD("미션 보상"), STREAK_BONUS("연속 달성 보상"), PURCHASE("아이템 구매"), UNKNOWN("코인 변동");

    companion object { fun parse(v: String?) = entries.firstOrNull { it != UNKNOWN && it.name == v } ?: UNKNOWN }
}

data class CoinTransaction(
    val transactionId: Long,
    val amount: Int,
    val balanceAfter: Int,
    val reason: CoinReason,
    val refId: Long?,
    val createdAt: String?,
)

fun CoinTransactionDto.toTransaction() = CoinTransaction(transactionId, amount, balanceAfter, CoinReason.parse(reason), refId, createdAt)

data class CoinTransactionPage(val items: List<CoinTransaction>, val page: Int, val totalPages: Int) {
    val hasNext: Boolean get() = page + 1 < totalPages
}

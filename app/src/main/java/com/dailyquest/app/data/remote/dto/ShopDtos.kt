package com.dailyquest.app.data.remote.dto

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames

/**
 * GET /shop/items, GET /shop/items/{itemId}. type·category는 서버 enum 문자열 그대로 받고 알 수 없는 값은 도메인 변환에서 처리한다.
 * Boolean 필드의 실제 JSON 키는 isDefault 이고 OpenAPI 는 default 로 표기하므로 두 표기를 모두 읽는다.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class ItemDto(
    val itemId: Long,
    val name: String,
    val type: String? = null,
    val category: String? = null,
    val price: Int = 0,
    val imageUrl: String? = null,
    val modelUrl: String? = null,
    @SerialName("isDefault") @JsonNames("default") val isDefault: Boolean = false,
    val owned: Boolean = false,
)

/** POST /shop/items/{itemId}/purchase. remainingCoins는 차감 후 서버 잔액이다. */
@Serializable
data class PurchaseDto(val itemId: Long, val remainingCoins: Int)

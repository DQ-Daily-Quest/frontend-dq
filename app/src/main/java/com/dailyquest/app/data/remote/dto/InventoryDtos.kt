package com.dailyquest.app.data.remote.dto

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames

/** GET /users/me/items — 페이지네이션 없는 전체 배열. acquiredBy: SHOP | DEFAULT. */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class MyItemDto(
    val itemId: Long,
    val name: String,
    val type: String? = null,
    val category: String? = null,
    val price: Int = 0,
    val imageUrl: String? = null,
    val modelUrl: String? = null,
    @SerialName("isDefault") @JsonNames("default") val isDefault: Boolean = false,
    val owned: Boolean = true,
    val acquiredAt: String? = null,
    val acquiredBy: String? = null,
    val equipped: Boolean = false,
)

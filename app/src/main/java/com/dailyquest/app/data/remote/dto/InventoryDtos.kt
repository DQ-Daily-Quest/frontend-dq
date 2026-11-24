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
    /** 에셋 코드(= GLB 파일 이름). 가구는 null (backend v0.4, STEP 9-9 피부 고르기에 쓴다) */
    val code: String? = null,
    /** 착용 가능한 체형 MALE | FEMALE, null이면 공용 (backend v0.4) */
    val gender: String? = null,
)

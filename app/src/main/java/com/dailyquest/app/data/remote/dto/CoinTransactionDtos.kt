package com.dailyquest.app.data.remote.dto

import kotlinx.serialization.Serializable

/** GET /users/me/coins/transactions (최신순 페이지). reason: MISSION_REWARD | STREAK_BONUS | PURCHASE. refId는 사유별 참조 id(구매면 itemId). */
@Serializable
data class CoinTransactionDto(
    val transactionId: Long,
    val amount: Int,
    val balanceAfter: Int,
    val reason: String? = null,
    val refId: Long? = null,
    val createdAt: String? = null,
)

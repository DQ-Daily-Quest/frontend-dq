package com.dailyquest.app.data.remote.dto

import kotlinx.serialization.Serializable

/** 공통 에러 응답. 인증 필터(401)는 timestamp 없이 code·message만 내려주므로 모두 nullable이다. */
@Serializable
data class ErrorResponse(
    val code: String? = null,
    val message: String? = null,
    val timestamp: String? = null,
)

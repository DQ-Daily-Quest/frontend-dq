package com.dailyquest.app.data.remote

import com.dailyquest.app.data.remote.dto.ErrorResponse
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import okhttp3.Request
import okio.Buffer
import retrofit2.HttpException
import java.io.IOException
import javax.net.ssl.SSLException

/** 서버 에러 코드. 분기는 message가 아니라 항상 code로 한다. */
object ApiErrorCode {
    const val VALIDATION_ERROR = "VALIDATION_ERROR"
    const val UNAUTHORIZED = "UNAUTHORIZED"
    const val TOKEN_EXPIRED = "TOKEN_EXPIRED"
    const val INVALID_CREDENTIALS = "INVALID_CREDENTIALS"
    const val INVALID_REFRESH_TOKEN = "INVALID_REFRESH_TOKEN"
    const val INVALID_RESET_CODE = "INVALID_RESET_CODE"
    const val MAIL_UNAVAILABLE = "MAIL_UNAVAILABLE"
    const val DUPLICATE_EMAIL = "DUPLICATE_EMAIL"
    const val DUPLICATE_NICKNAME = "DUPLICATE_NICKNAME"
    const val INVALID_OAUTH_TOKEN = "INVALID_OAUTH_TOKEN"
    const val FORBIDDEN = "FORBIDDEN"
    const val ROOM_NOT_FOUND = "ROOM_NOT_FOUND"
    const val ROOM_NOT_PUBLIC = "ROOM_NOT_PUBLIC"
    const val ROOM_NOT_PRIVATE = "ROOM_NOT_PRIVATE"
    const val ALREADY_JOINED = "ALREADY_JOINED"
    const val ROOM_FULL = "ROOM_FULL"
    const val INVALID_INVITE_CODE = "INVALID_INVITE_CODE"
    const val OWNER_CANNOT_LEAVE = "OWNER_CANNOT_LEAVE"
    const val MAX_MEMBERS_BELOW_CURRENT = "MAX_MEMBERS_BELOW_CURRENT"
    const val NOT_A_MEMBER = "NOT_A_MEMBER"
    const val CANNOT_KICK_SELF = "CANNOT_KICK_SELF"
    const val ALREADY_OWNER = "ALREADY_OWNER"
    const val MISSION_NOT_FOUND = "MISSION_NOT_FOUND"
    const val NO_ACTIVE_TEMPLATE = "NO_ACTIVE_TEMPLATE"
    const val MISSION_EXPIRED = "MISSION_EXPIRED"
    const val PROOF_NOT_FOUND = "PROOF_NOT_FOUND"
    const val PROOF_ALREADY_SUBMITTED = "PROOF_ALREADY_SUBMITTED"
    const val PROOF_ALREADY_FINALIZED = "PROOF_ALREADY_FINALIZED"
    const val INVALID_EMOJI = "INVALID_EMOJI"
    const val NOTIFICATION_NOT_FOUND = "NOTIFICATION_NOT_FOUND"
    const val CREATOR_CANNOT_SUBMIT = "CREATOR_CANNOT_SUBMIT"
    const val NOT_MISSION_ASSIGNEE = "NOT_MISSION_ASSIGNEE"
    const val NOT_MISSION_APPROVER = "NOT_MISSION_APPROVER"
    const val CANNOT_VOTE_OWN_PROOF = "CANNOT_VOTE_OWN_PROOF"
    const val FILE_TOO_LARGE = "FILE_TOO_LARGE"
    const val FILE_EMPTY = "FILE_EMPTY"
    const val UNSUPPORTED_MEDIA_TYPE = "UNSUPPORTED_MEDIA_TYPE"
    const val ITEM_NOT_FOUND = "ITEM_NOT_FOUND"
    const val ITEM_ALREADY_OWNED = "ITEM_ALREADY_OWNED"
    const val INSUFFICIENT_COINS = "INSUFFICIENT_COINS"
    const val NO_PASSWORD_SET = "NO_PASSWORD_SET"
    const val OWNER_MUST_TRANSFER = "OWNER_MUST_TRANSFER"
    const val USER_NOT_FOUND = "USER_NOT_FOUND"
}

sealed class ApiException(message: String?, cause: Throwable? = null) : Exception(message, cause) {

    /** 서버가 응답한 오류. [report]는 인증 정보를 가린 문의용 요약이다. */
    class Http(
        val status: Int,
        val code: String?,
        val serverMessage: String?,
        val report: String,
    ) : ApiException("HTTP $status ${code.orEmpty()}")

    /** 연결 실패·타임아웃 등 일시적인 네트워크 오류. 인증 만료와 구분된다. */
    class Network(cause: Throwable) : ApiException("Network error: ${cause.javaClass.simpleName}", cause) {
        val isTlsFailure: Boolean get() = cause is SSLException
    }

    class Unexpected(cause: Throwable) : ApiException("Unexpected: ${cause.javaClass.simpleName}", cause)

    val httpCode: String? get() = (this as? Http)?.code
}

sealed interface ApiResult<out T> {
    data class Success<T>(val value: T) : ApiResult<T>
    data class Failure(val error: ApiException) : ApiResult<Nothing>
}

val errorJson = Json { ignoreUnknownKeys = true }

fun parseErrorBody(body: String?): ErrorResponse? {
    if (body.isNullOrBlank()) return null
    return runCatching { errorJson.decodeFromString(ErrorResponse.serializer(), body) }.getOrNull()
}

internal fun buildReport(request: Request?, status: Int, responseBody: String?): String {
    val requestBody = request?.body
        ?.takeIf { it.contentType()?.subtype == "json" && it.contentLength() in 0..4096 }
        ?.let { runCatching { Buffer().also { b -> it.writeTo(b) }.readUtf8() }.getOrNull() }
    return buildString {
        append(request?.method ?: "?").append(' ').append(request?.url ?: "?")
        append("\nrequest body: ").append(requestBody?.let(SensitiveMasker::maskRequestJson) ?: "-")
        append("\nstatus: ").append(status)
        append("\nresponse body: ").append(responseBody?.let(SensitiveMasker::maskJson) ?: "-")
    }
}

fun HttpException.toApiException(): ApiException.Http {
    val raw = response()?.raw()
    val body = runCatching { response()?.errorBody()?.string() }.getOrNull()
    val parsed = parseErrorBody(body)
    return ApiException.Http(code(), parsed?.code, parsed?.message, buildReport(raw?.request, code(), body))
}

/** Retrofit suspend 호출을 [ApiResult]로 변환한다. 취소 예외는 그대로 전파한다. */
suspend fun <T> apiCall(block: suspend () -> T): ApiResult<T> = try {
    ApiResult.Success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: HttpException) {
    ApiResult.Failure(e.toApiException())
} catch (e: IOException) {
    ApiResult.Failure(ApiException.Network(e))
} catch (e: Exception) {
    ApiResult.Failure(ApiException.Unexpected(e))
}

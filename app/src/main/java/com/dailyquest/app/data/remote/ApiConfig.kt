package com.dailyquest.app.data.remote

/** 서버 주소는 임시 도메인(sslip.io)이라 바뀔 수 있으므로 이 상수 한 곳에서만 관리한다. */
object ApiConfig {
    // Retrofit은 Base URL 끝의 '/'가 필수이며, API 경로는 '/' 없이 시작해야 한다.
    const val BASE_URL = "https://54-116-59-224.sslip.io/api/v1/"

    const val CONNECT_TIMEOUT_SEC = 15L
    const val READ_TIMEOUT_SEC = 30L
    const val WRITE_TIMEOUT_SEC = 30L

    /** 서버가 내려주는 파일 경로(/files/...)를 이미지 요청용 절대 주소로 바꾼다. 절대 http(s) 주소는 그대로 쓰고 그 외는 null. */
    fun resolveFileUrl(url: String?): String? {
        val u = url?.trim().orEmpty()
        return when {
            u.isEmpty() -> null
            u.startsWith("https://") || u.startsWith("http://") -> u
            u.startsWith("/") -> BASE_URL.substringBefore("/api/").trimEnd('/') + u
            else -> null
        }
    }
}

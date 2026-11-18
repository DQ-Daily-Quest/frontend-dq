package com.dailyquest.app.data.remote

/** 서버 주소는 임시 도메인(sslip.io)이라 바뀔 수 있으므로 이 상수 한 곳에서만 관리한다. */
object ApiConfig {
    // Retrofit은 Base URL 끝의 '/'가 필수이며, API 경로는 '/' 없이 시작해야 한다.
    const val BASE_URL = "https://54-116-59-224.sslip.io/api/v1/"

    const val CONNECT_TIMEOUT_SEC = 15L
    const val READ_TIMEOUT_SEC = 30L
    const val WRITE_TIMEOUT_SEC = 30L
}

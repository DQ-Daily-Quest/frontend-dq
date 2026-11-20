package com.dailyquest.app.data.auth

/** Access/Refresh 토큰 쌍. 로그에 노출되지 않도록 toString은 값을 가린다. */
data class TokenPair(val accessToken: String, val refreshToken: String) {
    override fun toString() = "TokenPair(***)"
}

/** 토큰의 영구 저장소. 구현체는 암호화 저장([KeystoreTokenStore]) 또는 테스트용 메모리 저장소다. */
interface TokenStore {
    fun read(): TokenPair?
    fun write(tokens: TokenPair)
    fun clear()
}

class InMemoryTokenStore(initial: TokenPair? = null) : TokenStore {
    @Volatile private var value: TokenPair? = initial
    override fun read() = value
    override fun write(tokens: TokenPair) { value = tokens }
    override fun clear() { value = null }
}

/**
 * 메모리 캐시를 가진 토큰 보관소. 두 토큰은 항상 함께 교체된다.
 * 동시 갱신 방지를 위해 [lock]을 TokenAuthenticator가 공유한다.
 */
class SessionTokens(private val store: TokenStore) {
    val lock = Any()
    private var cache: TokenPair? = null
    private var loaded = false

    fun get(): TokenPair? = synchronized(lock) {
        if (!loaded) {
            cache = runCatching { store.read() }.getOrNull()
            loaded = true
        }
        cache
    }

    fun set(tokens: TokenPair) = synchronized(lock) {
        store.write(tokens)
        cache = tokens
        loaded = true
    }

    fun clear() = synchronized(lock) {
        runCatching { store.clear() }
        cache = null
        loaded = true
    }
}

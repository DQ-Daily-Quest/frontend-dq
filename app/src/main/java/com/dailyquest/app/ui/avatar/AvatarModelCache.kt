package com.dailyquest.app.ui.avatar

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred

/**
 * 한 번 만든 아바타 파츠 모델을 보관해 다시 쓰는 곳 (아바타 속도 3단계). 3D 엔진과 분리해 단위 테스트할 수 있게 모델 타입을 [T]로 둔다.
 *
 * 상점을 열 때마다 파츠 모델(모양·재질·텍스처)을 새로 만들고 닫을 때 지우던 것을, 앱이 켜진 동안 **파츠 주소별로 하나만** 만들어 둔다.
 * - [acquire]: 보관된 모델이 있으면 그것을, 없으면 [create]로 만든다. 같은 주소를 동시에 요청하면 한 번만 만든다.
 *   돌려준 모델은 "쓰는 중"으로 센다. 상점과 얼굴 공장이 같은 옷이면 같은 모델을 함께 쓴다(Filament는 한 엔티티를 여러 장면에 넣을 수 있다).
 * - [release]: 다 쓴 화면이 돌려준다. 아무도 안 쓰는 모델은 바로 지우지 않고 보관한다.
 * - 아무도 안 쓰는 모델이 [maxUnused]개를 넘으면 오래 안 쓴 것부터 [destroy]로 지운다. 단, 돌려받은 지 [graceMs]가 지나지 않은 것은
 *   남긴다(방금 장면에서 뺀 모델을 그리는 중일 수 있다).
 * - 모두 메인 스레드에서 부른다(Filament).
 */
class AvatarModelCache<T : Any>(
    private val destroy: (T) -> Unit,
    var maxUnused: Int = DEFAULT_MAX_UNUSED,
    var graceMs: Long = DEFAULT_GRACE_MS,
    private val clock: () -> Long = { android.os.SystemClock.uptimeMillis() },
) {
    private class Entry<T>(val model: T, var users: Int, var releasedAt: Long)

    private val entries = LinkedHashMap<String, Entry<T>>()
    private val creating = HashMap<String, Deferred<T?>>()

    /** 지금 보관 중인 모델 수(쓰는 중 + 안 쓰는 것). */
    val size: Int get() = entries.size

    /** 지금 쓰는 화면이 하나라도 있는 모델 수. */
    val inUse: Int get() = entries.values.count { it.users > 0 }

    /** [url] 모델이 이미 있어 바로 쓸 수 있는지(만드는 중이면 false). 파일을 미리 읽을지 정하는 데만 쓴다. */
    fun has(url: String): Boolean = url in entries

    /**
     * [url] 모델을 쓰기 시작한다. 보관된 것이 있으면 바로, 없으면 [create]로 만든다(만들다 실패하면 null).
     * 다른 화면이 같은 주소를 만드는 중이면 그것을 기다려 함께 쓴다.
     */
    suspend fun acquire(url: String, create: suspend () -> T?): T? {
        entries[url]?.let { it.users++; return it.model }
        creating[url]?.let { pending ->
            pending.await() ?: return null
            return entries[url]?.let { it.users++; it.model }
        }
        val pending = CompletableDeferred<T?>()
        creating[url] = pending
        val model = try {
            create()
        } catch (e: Throwable) {
            null
        } finally {
            creating.remove(url)
        }
        if (model != null) entries[url] = Entry(model, users = 1, releasedAt = 0)
        pending.complete(model)
        evictIfNeeded()
        return model
    }

    /** 화면이 [model]을 다 썼다. 보관은 계속한다. 모르는 모델이면(이미 지움) 무시한다. */
    fun release(model: T) {
        val entry = entries.values.firstOrNull { it.model === model } ?: return
        if (entry.users > 0) entry.users--
        if (entry.users == 0) entry.releasedAt = clock()
        evictIfNeeded()
    }

    /**
     * 보관을 끈다/켠다. 끄면 아무도 안 쓰는 모델을 돌려받는 즉시 지운다(3단계 이전과 같음). 파일 받기·디스크 캐시를 확인하는
     * 디버그 테스트가 모델 보관에 가려지지 않게 쓴다.
     */
    fun setKeeping(keep: Boolean) {
        maxUnused = if (keep) DEFAULT_MAX_UNUSED else 0
        graceMs = if (keep) DEFAULT_GRACE_MS else 0
        evictIfNeeded()
    }

    /** 안 쓰는 모델이 상한을 넘으면 오래 안 쓴 것부터 지운다. 방금 돌려받은 것은 남긴다. */
    fun evictIfNeeded() {
        val now = clock()
        var unused = entries.entries.filter { it.value.users == 0 }.sortedBy { it.value.releasedAt }
        while (unused.size > maxUnused) {
            val oldest = unused.first()
            if (now - oldest.value.releasedAt < graceMs) break
            entries.remove(oldest.key)
            runCatching { destroy(oldest.value.model) }
            unused = unused.drop(1)
        }
    }

    companion object {
        /** 아무도 안 쓰는 채로 보관할 최대 파츠 수. 한 벌이 6~7개라 서너 벌 정도. */
        const val DEFAULT_MAX_UNUSED = 24
        const val DEFAULT_GRACE_MS = 1_000L
    }
}

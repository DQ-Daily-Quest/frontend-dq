package com.dailyquest.app.ui.avatar

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.dailyquest.app.data.avatar.AvatarModelFetcher
import com.dailyquest.app.data.avatar.AvatarPart
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** [AvatarView]가 바깥에 알려 주는 불러오기 상태. */
sealed interface AvatarLoadStatus {
    /** 새로 받아야 할 파츠를 받는 중. 이전 모습은 그대로 보인다. */
    data object Loading : AvatarLoadStatus

    /** 요청한 파츠가 모두 보인다. [shown]은 지금 보이는 슬롯. */
    data class Ready(val shown: Set<AvatarPart>) : AvatarLoadStatus

    /** [failed] 슬롯을 받지 못했다. 그 슬롯에 이전 모델이 있었다면 이전 모델이 그대로 보인다. */
    data class Failed(val failed: Set<AvatarPart>, val shown: Set<AvatarPart>) : AvatarLoadStatus
}

/** 지금 보이는 모습([shown])을 목표([target])로 바꾸려면 무엇을 받고 무엇을 뺄지. */
internal data class PartsDiff(val toLoad: Map<AvatarPart, String>, val toRemove: Set<AvatarPart>)

internal fun diffParts(shown: Map<AvatarPart, String>, target: Map<AvatarPart, String>) = PartsDiff(
    toLoad = target.filter { (part, url) -> shown[part] != url },
    toRemove = shown.keys - target.keys,
)

/**
 * 슬롯별 3D 모델을 목표 상태에 맞춘다. 3D 엔진과 분리해서 단위 테스트할 수 있게 모델 타입을 [T]로 둔다.
 * - 바뀐 슬롯만 받는다. 같은 주소인 슬롯은 다시 받지 않는다.
 * - 새 모델이 준비된 뒤에 한꺼번에 바꿔서, 옷이 하나씩 바뀌며 깜빡이지 않게 한다.
 * - 빠진 슬롯과 교체된 슬롯의 이전 모델은 화면에서 뺀 뒤 [releaseModel]로 메모리에서 지운다.
 * - 받지 못한 슬롯은 이전 모델을 그대로 두고 [AvatarLoadStatus.Failed]로 알린다. [sync]를 다시 부르면 그 슬롯만 다시 받는다.
 *
 * @param createModel 그 주소의 GLB 내용으로 모델을 만든다. Filament는 메인 스레드에서 만들어야 하므로 구현이 스레드를 정한다.
 * @param releaseModel 화면에서 뺀 모델을 돌려준다(지우거나 보관한다).
 * @param isReady 이미 만들어 둔 모델이 있어 파일을 읽을 필요가 없는 주소인지(아바타 속도 3단계). 기본은 없음.
 * @param reuseModel [isReady]인 주소의 모델을 파일 없이 가져온다. 그사이 없어졌으면 null(그러면 파일을 읽어 만든다).
 */
class AvatarPartsLoader<T : Any>(
    private val fetcher: AvatarModelFetcher,
    private val createModel: suspend (url: String, bytes: ByteArray) -> T,
    private val releaseModel: suspend (T) -> Unit,
    private val isReady: (url: String) -> Boolean = { false },
    private val reuseModel: suspend (url: String) -> T? = { null },
) {
    data class Shown<T>(val url: String, val model: T)

    /** 지금 화면에 그릴 슬롯별 모델. Compose 상태라 바뀌면 화면이 다시 그려진다. */
    var shown: Map<AvatarPart, Shown<T>> by mutableStateOf(emptyMap())
        private set

    var status: AvatarLoadStatus by mutableStateOf(AvatarLoadStatus.Ready(emptySet()))
        private set

    /** [sync]가 끝날 때마다 1씩 는다. 결과 상태가 이전과 같아도 "끝났다"는 것을 알 수 있게 한다. */
    var completedSyncs: Int by mutableIntStateOf(0)
        private set

    suspend fun sync(target: Map<AvatarPart, String>) {
        syncInternal(target)
        completedSyncs++
    }

    private suspend fun syncInternal(target: Map<AvatarPart, String>) = coroutineScope {
        val diff = diffParts(shown.mapValues { it.value.url }, target)

        if (diff.toRemove.isNotEmpty()) {
            val removed = diff.toRemove.mapNotNull { shown[it]?.model }
            shown = shown - diff.toRemove
            removed.forEach { launch { releaseModel(it) } }
        }
        if (diff.toLoad.isEmpty()) {
            status = AvatarLoadStatus.Ready(shown.keys)
            return@coroutineScope
        }

        status = AvatarLoadStatus.Loading
        // 이미 만들어 둔 모델이 있는 주소는 파일을 읽지 않는다(3단계).
        val ready = diff.toLoad.filterValues(isReady).keys
        val fetched = (diff.toLoad - ready)
            .map { (part, url) -> async { part to fetcher.fetch(url) } }.awaitAll().toMap()

        // 모델을 만든 뒤 화면 상태에 넣기 전에 취소되면 만든 모델을 잃어버린다. 이 구간은 끝까지 실행한다.
        val replaced = mutableListOf<T>()
        withContext(NonCancellable) {
            var next = shown
            diff.toLoad.forEach { (part, url) ->
                val model = (if (part in ready) runCatching { reuseModel(url) }.getOrNull() else null) ?: run {
                    // 보관된 모델이 그사이 없어졌으면 여기서 파일을 읽는다(드문 경우).
                    val bytes = (fetched[part] ?: fetcher.fetch(url)).getOrNull() ?: return@forEach
                    runCatching { createModel(url, bytes) }.getOrNull() ?: return@forEach
                }
                next[part]?.let { replaced += it.model }
                next = next + (part to Shown(url, model))
            }
            shown = next
        }
        replaced.forEach { launch { releaseModel(it) } }

        val failed = target.filter { (part, url) -> shown[part]?.url != url }.keys
        status = if (failed.isEmpty()) AvatarLoadStatus.Ready(shown.keys) else AvatarLoadStatus.Failed(failed, shown.keys)
    }
}

package com.dailyquest.app.ui.shop

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dailyquest.app.data.auth.AppContainer
import com.dailyquest.app.data.auth.SessionState
import com.dailyquest.app.data.remote.ApiResult
import com.dailyquest.app.data.shop.ItemType
import com.dailyquest.app.data.shop.ShopPage
import com.dailyquest.app.data.shop.ShopRepository
import com.dailyquest.app.ui.avatar.PREWARM_DELAY_MS
import com.dailyquest.app.ui.avatar.awaitMainThreadIdle
import com.dailyquest.app.ui.proof.RemoteImages
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit

/**
 * 상점 상품 이미지 미리 받기 (상점 이미지 속도 3단계).
 *
 * 상품 칸은 화면에 보일 때에야 이미지를 받기 시작해서, 처음 보는 탭·스크롤은 한 장에 1~4초 걸렸다. 홈에서 쉴 때 상품 목록을 받아
 * 모든 상품 이미지를 **기기 저장소에만** 미리 받아 둔다(메모리에 올리지 않음). 상점을 열면 저장소에서 바로 읽는다(1단계).
 * - 이미 저장된 이미지는 인터넷을 쓰지 않는다. 그래서 실제로 받는 것은 처음 한 번(상품 108개, 약 15MB)과 새 상품뿐이다.
 * - 동시에 [parallelism]장씩만 받아 화면에 보이는 칸을 받는 데 방해가 되지 않게 한다.
 * - 목록을 받으면 앱이 켜진 동안 다시 하지 않는다. 목록을 못 받았으면(인터넷 끊김 등) 다음 기회(계정 바뀜·앱 재시작)에 다시 한다.
 * - 이미지 하나가 실패해도 나머지는 계속한다. 실패한 것은 화면에 보일 때 평소처럼 받는다.
 *
 * @param listImageUrls 미리 받을 이미지 주소. 목록을 못 받으면 null
 * @param fetch 주소 하나를 저장소에 받는다. 앱에서는 [RemoteImages.prefetch]
 */
class ShopImagePrefetcher(
    private val listImageUrls: suspend () -> List<String>?,
    private val fetch: suspend (url: String) -> Boolean,
    private val parallelism: Int = PARALLELISM,
) {
    private val mutex = Mutex()

    /** 한 번 끝났는지(목록을 받아 모두 시도함). */
    @Volatile var finished: Boolean = false
        private set

    /** 미리 받는다. 받기에 성공한 수(이미 저장된 것 포함)를 돌려준다. 이미 끝났으면 0, 목록을 못 받으면 null. */
    suspend fun run(): Int? = mutex.withLock {
        if (finished) return 0
        val urls = listImageUrls()?.filter { it.isNotBlank() }?.distinct() ?: return null
        val permits = Semaphore(parallelism)
        val ok = coroutineScope {
            urls.map { url -> async { permits.withPermit { runCatching { fetch(url) }.getOrDefault(false) } } }.awaitAll()
        }.count { it }
        finished = true
        ok
    }

    companion object {
        const val PARALLELISM = 4
    }
}

/**
 * 상점에 보이는 순서(아바타 → 가구, 서버 순서)대로 모든 상품의 카드 이미지 주소를 모은다. 하나라도 못 받으면 null.
 * @param listPage 상품 목록 한 쪽. 앱에서는 [ShopRepository.listItems]
 */
internal suspend fun shopImageUrls(
    listPage: suspend (type: ItemType, page: Int, size: Int) -> ApiResult<ShopPage>,
    pageSize: Int = LIST_PAGE_SIZE,
    maxPages: Int = MAX_LIST_PAGES,
): List<String>? {
    val urls = mutableListOf<String>()
    for (type in listOf(ItemType.AVATAR, ItemType.FURNITURE)) {
        var page = 0
        while (page < maxPages) {
            val result = listPage(type, page, pageSize) as? ApiResult.Success ?: return null
            result.value.items.mapNotNullTo(urls) { it.cardImageUrl }
            if (!result.value.hasNext) break
            page++
        }
    }
    return urls
}

/** 서버는 한 쪽 크기를 1~100으로 보정한다. */
internal const val LIST_PAGE_SIZE = 100
/** 혹시 상품이 아주 많아져도 목록 받기를 끝없이 하지 않게. 종류마다 최대 500개. */
internal const val MAX_LIST_PAGES = 5

/** 앱 전체에서 하나만 둔다(앱이 켜진 동안 한 번만 미리 받기 위해). */
object ShopImages {
    @Volatile private var prefetcher: ShopImagePrefetcher? = null

    fun prefetcher(context: Context, repository: ShopRepository): ShopImagePrefetcher = prefetcher ?: synchronized(this) {
        prefetcher ?: run {
            val app = context.applicationContext
            ShopImagePrefetcher(
                listImageUrls = { shopImageUrls({ type, page, size -> repository.listItems(type, page = page, size = size) }) },
                fetch = { url -> RemoteImages.prefetch(app, url) },
            )
        }.also { prefetcher = it }
    }
}

/**
 * 로그인 뒤 홈이 다 그려지고 화면 스레드가 쉴 때 상점 상품 이미지를 미리 받는다(아바타 미리 준비와 같은 시점).
 * 로그아웃 상태에서는 하지 않는다. UI 테스트 환경(인터넷 없음)에서는 이 부품을 두지 않는다([AppContainer.prefetchShopImages]).
 */
@Composable
fun ShopImagePrefetch(container: AppContainer) {
    val context = LocalContext.current
    ShopImagePrefetch(container.session.state, prefetch = { ShopImages.prefetcher(context, container.shopRepository).run() })
}

/** [ShopImagePrefetch]의 실제 동작. 테스트에서 가짜를 넣을 수 있게 나눴다. */
@Composable
internal fun ShopImagePrefetch(
    session: StateFlow<SessionState>,
    prefetch: suspend () -> Unit,
    delayMs: Long = PREWARM_DELAY_MS,
) {
    val state by session.collectAsStateWithLifecycle()
    val loggedIn = state is SessionState.Authenticated
    LaunchedEffect(loggedIn) {
        if (!loggedIn) return@LaunchedEffect
        delay(delayMs)
        awaitMainThreadIdle()
        prefetch()
    }
}

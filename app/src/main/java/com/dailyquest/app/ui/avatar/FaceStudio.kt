package com.dailyquest.app.ui.avatar

import android.content.Context
import com.dailyquest.app.data.avatar.FaceKey
import com.dailyquest.app.data.avatar.FaceStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File

/**
 * 프로필 얼굴을 찍을 차례를 관리한다 (프로필 얼굴 2단계). 찍는 일은 [FaceStudioHost]가 한다.
 *
 * - 화면이 얼굴을 달라고 하면([request]) 기기에 이미 있으면 아무것도 하지 않고, 없으면 대기열에 넣는다. 같은 얼굴은 한 번만 넣는다.
 * - 3D 엔진은 무거워서 한 번에 하나씩 찍는다. [FaceStudioHost]가 [pending]의 맨 앞을 찍고 [complete]로 알린다.
 * - 찍지 못하면(3D 파일을 받지 못함 등) [MAX_ATTEMPTS]번까지 다시 넣고, 그래도 안 되면 이번 실행에서는 포기한다(기본 그림이 보임).
 * - 새 사진이 저장될 때마다 [version]이 올라 화면이 다시 찾아본다.
 */
class FaceStudio(val store: FaceStore) {
    private val _pending = MutableStateFlow<List<FaceKey>>(emptyList())
    val pending: StateFlow<List<FaceKey>> = _pending.asStateFlow()

    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version.asStateFlow()

    private val attempts = HashMap<String, Int>()
    private val gaveUp = HashSet<String>()

    @Synchronized
    fun request(key: FaceKey) {
        if (key.isEmpty || key.id in gaveUp || store.has(key)) return
        _pending.update { q -> if (q.any { it.id == key.id }) q else q + key }
    }

    /**
     * [key]를 찍은 결과. [png]가 null이면 실패.
     * 실패하면 대기열 맨 뒤로 보내 다른 얼굴을 먼저 찍게 한다.
     */
    suspend fun complete(key: FaceKey, png: ByteArray?) {
        if (png != null) {
            store.put(key, png)
            synchronized(this) {
                _pending.update { q -> q.filterNot { it.id == key.id } }
                attempts.remove(key.id)
            }
            _version.update { it + 1 }
            return
        }
        synchronized(this) {
            val n = (attempts[key.id] ?: 0) + 1
            attempts[key.id] = n
            _pending.update { q ->
                val rest = q.filterNot { it.id == key.id }
                if (n >= MAX_ATTEMPTS) rest else rest + key
            }
            if (n >= MAX_ATTEMPTS) gaveUp += key.id
        }
    }

    companion object {
        const val MAX_ATTEMPTS = 2
    }
}

/** 앱 전체에서 같은 공장을 쓴다. */
object FaceStudios {
    @Volatile private var instance: FaceStudio? = null

    /** 저장 공간이 부족하면 시스템이 지울 수 있는 캐시 폴더. 지워지면 다시 찍는다. */
    fun directory(context: Context): File = File(context.applicationContext.cacheDir, "avatar_faces")

    fun get(context: Context): FaceStudio = instance ?: synchronized(this) {
        instance ?: FaceStudio(FaceStore(directory(context))).also { instance = it }
    }
}

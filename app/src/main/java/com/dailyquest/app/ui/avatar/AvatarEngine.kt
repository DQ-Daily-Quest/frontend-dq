package com.dailyquest.app.ui.avatar

import android.content.Context
import androidx.annotation.MainThread
import com.google.android.filament.Engine
import io.github.sceneview.createEglContext
import io.github.sceneview.createEngine
import io.github.sceneview.createEnvironment
import io.github.sceneview.createEnvironmentLoader
import io.github.sceneview.createMaterialLoader
import io.github.sceneview.createModelLoader
import io.github.sceneview.environment.Environment
import io.github.sceneview.loaders.EnvironmentLoader
import io.github.sceneview.loaders.MaterialLoader
import io.github.sceneview.loaders.ModelLoader
import io.github.sceneview.model.ModelInstance
import io.github.sceneview.model.model

/**
 * 앱 전체가 함께 쓰는 3D 엔진과 엔진 단위 자원 (아바타 속도 2단계).
 *
 * SceneView 기본값은 3D 화면마다 엔진·모델 로더·재질 로더·환경 조명(IBL 파일 읽기)을 새로 만들고 화면을 떠나면 버린다.
 * 상점·캐릭터 만들기·얼굴 공장처럼 3D 화면을 열 때마다 이것들을 다시 만드는 시간이 들어서, **처음 한 번만 만들고 앱이 켜진 동안 계속 쓴다.**
 *
 * - 화면마다 필요한 것(View·Renderer·Scene·조명·카메라)은 지금처럼 SceneView가 화면마다 만들고 지운다. 엔진 하나로 여러 화면이 동시에 그려도 된다.
 * - 이 자원은 **지우지 않는다.** 파츠 모델은 [models]에 보관해 여러 화면이 다시 쓴다(3단계). 각 화면은 다 쓴 모델을 보관함에 돌려준다([AvatarView]).
 * - Filament는 메인 스레드에서만 다룬다.
 */
class AvatarEngine private constructor(context: Context) {
    val engine: Engine = createEngine(createEglContext())
    val modelLoader: ModelLoader = engine.createModelLoader(context)
    val materialLoader: MaterialLoader = engine.createMaterialLoader(context)
    val environmentLoader: EnvironmentLoader = engine.createEnvironmentLoader(context)

    /** 투명 배경(아바타 화면은 모두 `isOpaque = false`)과 기본 환경 조명. */
    val environment: Environment = createEnvironment(environmentLoader, isOpaque = false)

    /** 한 번 만든 파츠 모델 보관함(아바타 속도 3단계). 아무도 안 쓰는 모델이 많아지면 오래된 것부터 지운다. */
    val models: AvatarModelCache<ModelInstance> = AvatarModelCache(destroy = { modelLoader.destroyModel(it.model) })

    companion object {
        @Volatile private var instance: AvatarEngine? = null

        /** 처음 부를 때 만든다. 메인 스레드에서만 부른다. */
        @MainThread
        fun get(context: Context): AvatarEngine =
            instance ?: AvatarEngine(context.applicationContext).also { instance = it }
    }
}

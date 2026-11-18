package com.dailyquest.app

import android.app.Application
import com.dailyquest.app.data.auth.AppContainer
import com.dailyquest.app.data.auth.KeystoreTokenStore

open class DQApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = createContainer()
    }

    protected open fun createContainer() = AppContainer(KeystoreTokenStore(this))
}

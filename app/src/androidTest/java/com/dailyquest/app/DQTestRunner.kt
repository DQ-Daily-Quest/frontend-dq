package com.dailyquest.app

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner

/** 계측 테스트에서는 서버 호출 없이 인증된 상태로 시작하는 [TestDQApplication]을 사용한다. */
class DQTestRunner : AndroidJUnitRunner() {
    override fun newApplication(cl: ClassLoader?, className: String?, context: Context?): Application =
        super.newApplication(cl, TestDQApplication::class.java.name, context)
}

package com.dailyquest.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** QA 디자인 5단계: 앱 안 알림 상태. */
class DqToastStateTest {
    @Test fun `띄우면 글자와 성공 실패가 담긴다`() {
        val s = DqToastState()
        s.show("구매가 완료됐어요")
        assertEquals("구매가 완료됐어요", s.current!!.text)
        assertFalse(s.current!!.isError)
        s.show("코인이 부족해요.", isError = true)
        assertTrue(s.current!!.isError)
    }

    @Test fun `같은 글자를 다시 띄워도 새 알림이다`() {
        val s = DqToastState()
        s.show("착용했어요"); val first = s.current!!.id
        s.show("착용했어요")
        assertNotEquals(first, s.current!!.id)
    }

    @Test fun `이전 알림의 시간이 끝나도 새 알림은 닫지 않는다`() {
        val s = DqToastState()
        s.show("첫 번째"); val old = s.current!!.id
        s.show("두 번째")
        s.dismiss(old)
        assertEquals("두 번째", s.current!!.text)
        s.dismiss(s.current!!.id)
        assertNull(s.current)
    }
}

package com.dailyquest.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.espresso.Espresso
import org.junit.Rule
import org.junit.Test

class RoomFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private fun click(text: String) = compose.onNode(hasText(text) and hasClickAction()).performClick()
    private fun back() { Espresso.pressBack(); compose.waitForIdle() }

    @Test
    fun creationDefaultsFollowEntryTabAndRemainEditable() {
        click("홈 · 우리만 (마을 지도)")
        click("+ 방 만들기")
        compose.onNodeWithText("초대코드로만 참여할 수 있어요.").assertExists()
        click("공개")
        compose.onNodeWithText("누구나 공개방 목록에서 참여할 수 있어요.").assertExists()
        back()
        compose.onNodeWithText("공개").performClick()
        click("+ 방 만들기")
        compose.onNodeWithText("누구나 공개방 목록에서 참여할 수 있어요.").assertExists()
        click("우리만 · 비공개")
        compose.onNodeWithText("초대코드로만 참여할 수 있어요.").assertExists()
        // 재생성 시 사용자가 바꾼 옵션이 유지되어야 한다.
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("초대코드로만 참여할 수 있어요.").assertExists()
        back()
        click("+ 방 만들기")
        compose.onNodeWithText("누구나 공개방 목록에서 참여할 수 있어요.").assertExists()
        back()
        compose.onNodeWithText("우리만").performClick()
        click("+ 방 만들기")
        compose.onNodeWithText("초대코드로만 참여할 수 있어요.").assertExists()
    }

    @Test
    fun createJoinAndMapPagination() {
        click("홈 · 우리만 (마을 지도)")
        click("+ 방 만들기")
        click("방 만들기")
        compose.onNodeWithText("방 이름은 2~20자여야 합니다.").assertExists()
        compose.onAllNodes(hasSetTextAction())[0].performTextInput("자동화 생성방")
        compose.onAllNodes(hasSetTextAction())[2].performTextReplacement("1")
        Espresso.closeSoftKeyboard()
        click("방 만들기")
        compose.onNodeWithText("최대 인원은 2~30명이어야 합니다.").assertExists()
        compose.onAllNodes(hasSetTextAction())[2].performTextReplacement("2")
        Espresso.closeSoftKeyboard()
        click("방 만들기")
        compose.onNodeWithText("자동화 생성방").assertExists()
        compose.onNodeWithText("아직 등록된 피드가 없어요.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("지수").assertDoesNotExist()
        back()
        compose.onNodeWithText("자동화 생성방").assertExists()
        compose.onNodeWithText("2 / 2 페이지 · 5개 방").assertExists()
        click("‹ 이전")
        compose.onNodeWithText("우리들의 아지트").assertExists()
        click("다음 ›")
        compose.onNodeWithText("자동화 생성방").assertExists()
        click("초대코드 참여")
        click("참여하기")
        compose.onNodeWithText("초대코드를 입력해 주세요.").assertExists()
        compose.onNode(hasSetTextAction()).performTextInput("WRONG")
        Espresso.closeSoftKeyboard()
        click("참여하기")
        compose.onNodeWithText("유효하지 않은 초대코드입니다.").assertExists()
        compose.onNode(hasSetTextAction()).performTextReplacement("DQ0010")
        Espresso.closeSoftKeyboard()
        click("참여하기")
        compose.onNodeWithText("현재 정원이 가득 찼습니다.").assertExists()
        compose.onNode(hasSetTextAction()).performTextReplacement(" dq0009 ")
        Espresso.closeSoftKeyboard()
        click("참여하기")
        compose.onNodeWithText("초대코드 테스트방").assertExists()
        compose.onNodeWithText("멤버 2/2명").assertExists()
        back()
        compose.onNodeWithText("2 / 2 페이지 · 6개 방").assertExists()
        click("초대코드 참여")
        compose.onNode(hasSetTextAction()).performTextInput("DQ0009")
        Espresso.closeSoftKeyboard()
        click("참여하기")
        compose.onNodeWithText("멤버 2/2명").assertExists()
        back()
        compose.onNodeWithText("공개").performClick()
        click("새벽 러닝클럽")
        click("참여하기")
        compose.onNodeWithText("멤버 7/10명").assertExists()
        back()
        click("새벽 러닝클럽")
        compose.onNodeWithText("멤버 7/10명").assertExists()
        back()
        compose.onNodeWithText("우리만").performClick()
        compose.onNodeWithText("2 / 2 페이지 · 7개 방").assertExists()
        compose.onNodeWithText("새벽 러닝클럽").assertExists()
        compose.onNodeWithText("공개").performClick()
        click("+ 방 만들기")
        compose.onAllNodes(hasSetTextAction())[0].performTextInput("공개 탭 생성방")
        Espresso.closeSoftKeyboard()
        click("방 만들기")
        compose.onNodeWithText("공개 탭 생성방").assertExists()
        compose.runOnIdle {
            org.junit.Assert.assertTrue(
                com.dailyquest.app.data.mock.MockRoomData.publicRooms.any { it.name == "공개 탭 생성방" }
            )
        }
        back()
        compose.onNodeWithText("다른 마을 구경하기").assertExists()
    }
}

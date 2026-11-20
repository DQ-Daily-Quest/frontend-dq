package com.dailyquest.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.espresso.Espresso
import com.dailyquest.app.data.mock.MockRoomData
import com.dailyquest.app.ui.room.RoomEntryGate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class RoomFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private fun click(text: String) = compose.onNode(hasText(text) and hasClickAction()).performClick()
    // 방 생성·참여 화면은 아직 Mock 구현이라 기본적으로 진입이 막혀 있다. 화면 로직 검증을 위해서만 연다.
    @Before fun openMockRoomEntries() { RoomEntryGate.mockRoomActionsEnabled = true }
    @After fun closeMockRoomEntries() {
        RoomEntryGate.mockRoomActionsEnabled = false
        TestRoomCalls.forbiddenRoomId = null
    }

    private fun mockRoomCount() = compose.runOnIdle { MockRoomData.myRooms.size }
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
        // 홈 지도는 서버 목록만 표시하므로 Mock 방 생성 결과는 데이터 계층에서 검증한다.
        assertEquals(5, mockRoomCount())
        compose.runOnIdle { assertTrue(MockRoomData.myRooms.any { it.name == "자동화 생성방" }) }
        compose.onNodeWithText("자동화 생성방").assertDoesNotExist()
        compose.onNodeWithText("1 / 2 페이지 · 5개 방").assertExists()
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
        assertEquals(6, mockRoomCount())
        click("초대코드 참여")
        compose.onNode(hasSetTextAction()).performTextInput("DQ0009")
        Espresso.closeSoftKeyboard()
        click("참여하기")
        compose.onNodeWithText("멤버 2/2명").assertExists()
        back()
        compose.onNodeWithText("공개").performClick()
        // 공개방 목록은 서버(FakeApi) 데이터이며 참여·상세는 아직 막혀 있다.
        compose.waitUntil(5_000) { compose.onAllNodesWithText("서버 공개방 A").fetchSemanticsNodes().isNotEmpty() }
        // 기존 Mock 방은 공개방 목록 영역에 없어야 한다(정적 랭킹 영역은 별도 태그).
        compose.onNode(hasText("새벽 러닝클럽") and hasAnyAncestor(hasTestTag("publicRoomList"))).assertDoesNotExist()
        compose.onNode(hasText("새벽 러닝클럽") and hasAnyAncestor(hasTestTag("sampleRanking"))).assertExists()
        compose.onNode(hasText("서버 공개방 A") and hasAnyAncestor(hasTestTag("publicRoomList"))).assertExists()
        click("서버 공개방 A")
        compose.onNodeWithText("참여하기").assertIsNotEnabled()
        click("닫기")
        click("서버 공개방 B")
        // 입장 가능 여부는 서버 상세(myRole)로 정해지며, 입장은 Mock 방이 아닌 서버 방으로 이동한다.
        compose.waitUntil(5_000) { compose.onAllNodesWithText("입장하기").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("입장하기").assertIsEnabled()
        click("닫기")
        compose.onNodeWithText("우리만").performClick()
        assertEquals(6, mockRoomCount())
        compose.onNodeWithText("새벽 러닝클럽").assertDoesNotExist()
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

    @Test
    fun myRoomsMapShowsServerRoomsOnly() {
        click("홈 · 우리만 (마을 지도)")
        compose.waitUntil(5_000) { compose.onAllNodesWithText("서버 내 방 1").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("1 / 2 페이지 · 5개 방").assertExists()
        compose.onNodeWithText("우리들의 아지트").assertDoesNotExist()
        compose.onNodeWithText("서버 내 방 4").assertExists()
        compose.onNodeWithText("서버 내 방 5").assertDoesNotExist()
        compose.onNodeWithText("‹ 이전").performClick()
        compose.onNodeWithText("1 / 2 페이지 · 5개 방").assertExists()
        click("다음 ›")
        compose.onNodeWithText("2 / 2 페이지 · 5개 방").assertExists()
        compose.onNodeWithText("서버 내 방 5").assertExists()
        compose.onNodeWithText("서버 내 방 1").assertDoesNotExist()
        click("다음 ›")
        compose.onNodeWithText("2 / 2 페이지 · 5개 방").assertExists()
        // 서버 방은 서버 상세 화면으로 이동하며 Mock 방 화면(MockRoomScreen)으로 가지 않는다.
        compose.onNodeWithText("서버 내 방 5").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("서버 설명 9105").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("아직 등록된 피드가 없어요.").assertDoesNotExist()
        compose.onNodeWithText("지수").assertDoesNotExist()
        back()
        // 방 생성·초대코드 참여도 Mock으로 진입하지 않는다.
        RoomEntryGate.mockRoomActionsEnabled = false
        click("+ 방 만들기")
        compose.onNodeWithText("방 만들기는 아직 서버와 연결되지 않았어요.", substring = true).assertExists()
        click("확인")
        click("초대코드 참여")
        compose.onNodeWithText("초대코드 참여는 아직 서버와 연결되지 않았어요.", substring = true).assertExists()
    }

    private fun openMyRoom(n: Int, description: String) {
        click("홈 · 우리만 (마을 지도)")
        compose.waitUntil(5_000) { compose.onAllNodesWithText("서버 내 방 1").fetchSemanticsNodes().isNotEmpty() }
        if (n == 5) click("다음 ›")
        compose.onNodeWithText("서버 내 방 $n").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText(description).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun ownerRoomShowsServerDetailMembersInviteCodeAndMission() {
        openMyRoom(1, "서버 설명 9101")
        compose.onNodeWithText("멤버 2/10명 · 방장 서버방장").assertExists()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("서버멤버").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("서버방장").assertExists()
        compose.onNodeWithText("방장").assertExists()
        compose.onNodeWithTag("roomInviteCode").assertTextEquals("초대코드: SRV9101")
        click("미션")
        compose.onNodeWithText("서버 오늘의 미션").assertExists()
        // 서버 방에서는 Mock 피드·멤버·초대코드가 나타나지 않는다.
        click("피드")
        compose.onNodeWithText("지수").assertDoesNotExist()
        compose.onNodeWithText("민수").assertDoesNotExist()
        compose.onNodeWithText("피드는 아직 서버와 연결되지 않았어요.", substring = true).assertExists()
        click("+ 미션 보내기")
        compose.onNodeWithText("미션 보내기는 아직 서버와 연결되지 않았어요.", substring = true).assertExists()
    }

    @Test
    fun memberFetchFailureKeepsDetailWithRetry() {
        click("홈 · 우리만 (마을 지도)")
        compose.waitUntil(5_000) { compose.onAllNodesWithText("서버 내 방 3").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("서버 내 방 3").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("서버 설명 9103").fetchSemanticsNodes().isNotEmpty() }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("멤버 다시 불러오기").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("서버 설명 9103").assertExists()
    }

    @Test
    fun publicPreviewBranchesByServerRoleAndNeverFetchesMembersForNonMembers() {
        TestRoomCalls.membersRequested.clear()
        click("홈 · 우리만 (마을 지도)")
        compose.onNodeWithText("공개").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("서버 공개방 A").fetchSemanticsNodes().isNotEmpty() }
        click("서버 공개방 A")
        compose.waitUntil(5_000) { compose.onAllNodesWithText("방 참여는 아직 서버와 연결되지 않았어요.", substring = true).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("참여하기").assertIsNotEnabled()
        compose.onNodeWithText("입장하기").assertDoesNotExist()
        click("닫기")
        click("서버 공개방 B")
        compose.waitUntil(5_000) { compose.onAllNodesWithText("입장하기").fetchSemanticsNodes().isNotEmpty() }
        click("입장하기")
        compose.waitUntil(5_000) { compose.onAllNodesWithText("서버 설명 9002").fetchSemanticsNodes().isNotEmpty() }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("서버멤버").fetchSemanticsNodes().isNotEmpty() }
        // 공개방에서는 서버가 코드를 주지 않으므로 초대코드가 없다.
        compose.onNodeWithTag("roomInviteCode").assertDoesNotExist()
        compose.onNodeWithText("지수").assertDoesNotExist()
        compose.runOnIdle {
            assertTrue(9002L in TestRoomCalls.membersRequested)
            assertTrue(9001L !in TestRoomCalls.membersRequested)
        }
    }

    @Test
    fun forbiddenRoomShowsNoAccessScreenWithoutLeakingData() {
        TestRoomCalls.membersRequested.clear()
        TestRoomCalls.forbiddenRoomId = 9101L
        click("홈 · 우리만 (마을 지도)")
        compose.waitUntil(5_000) { compose.onAllNodesWithText("서버 내 방 1").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("서버 내 방 1").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("접근 권한이 없어요.").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("이 작업을 수행할 권한이 없어요.").assertExists()
        // 네트워크 오류와 달리 재시도 버튼이 없고, 이전 상세·초대코드·멤버가 노출되지 않는다.
        compose.onNodeWithText("다시 시도").assertDoesNotExist()
        compose.onNodeWithTag("roomInviteCode").assertDoesNotExist()
        compose.onNodeWithTag("roomMembers").assertDoesNotExist()
        compose.onNodeWithText("서버 설명 9101").assertDoesNotExist()
        compose.onNodeWithText("서버멤버").assertDoesNotExist()
        compose.runOnIdle { assertTrue(9101L !in TestRoomCalls.membersRequested) }
        click("홈으로")
        compose.onNodeWithText("서버 내 방 1").assertExists()
        compose.onNodeWithText("접근 권한이 없어요.").assertDoesNotExist()
    }
}

package com.dailyquest.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.espresso.Espresso
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import retrofit2.HttpException
import retrofit2.Response
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class RoomFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private fun click(text: String) = compose.onNode(hasText(text) and hasClickAction()).performClick()
    @After fun resetFakeServer() { TestRoomCalls.reset() }

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
    fun inviteCodeJoinValidatesAndJoinsRealRoom() {
        click("홈 · 우리만 (마을 지도)")
        click("초대코드 참여")
        compose.onNodeWithTag("realJoinRoom").assertExists()
        click("참여하기")
        compose.onNodeWithText("초대코드를 입력해 주세요.").assertExists()
        compose.onNode(hasSetTextAction()).performTextInput("WRONG")
        Espresso.closeSoftKeyboard()
        click("참여하기")
        compose.onNodeWithText("초대코드는 영문·숫자 6자리예요.").assertExists()
        assertTrue(TestRoomCalls.calls.none { it.startsWith("joinCode") })
        compose.onNode(hasSetTextAction()).performTextReplacement("ZZZ999")
        Espresso.closeSoftKeyboard()
        click("참여하기")
        compose.waitUntil(5_000) { compose.onAllNodesWithText("유효하지 않은 초대코드예요.", substring = true).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasSetTextAction()).performTextReplacement("FULL01")
        Espresso.closeSoftKeyboard()
        click("참여하기")
        compose.waitUntil(5_000) { compose.onAllNodesWithText("방 정원이 가득 찼어요.").fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasSetTextAction()).performTextReplacement("DUP001")
        Espresso.closeSoftKeyboard()
        click("참여하기")
        compose.waitUntil(5_000) { compose.onAllNodesWithText("이미 참여 중인 방이에요.", substring = true).fetchSemanticsNodes().isNotEmpty() }
        // 앞뒤 공백·소문자는 정규화되며 성공하면 서버 방 상세로 이동한다.
        compose.onNode(hasSetTextAction()).performTextReplacement(" abc123 ")
        Espresso.closeSoftKeyboard()
        click("참여하기")
        compose.waitUntil(5_000) { compose.onAllNodesWithText("서버 설명 9001").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("realJoinRoom").assertDoesNotExist()
        assertEquals(4, TestRoomCalls.calls.count { it == "joinCode" })
        back()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("1 / 2 페이지 · 6개 방").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun publicPreviewJoinEntersRoomAndRefreshesMap() {
        click("홈 · 공개 (다른 마을 구경)")
        compose.waitUntil(5_000) { compose.onAllNodesWithText("서버 공개방 A").fetchSemanticsNodes().isNotEmpty() }
        click("서버 공개방 A")
        compose.waitUntil(5_000) { compose.onAllNodesWithText("참여하기").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("roomInviteCode").assertDoesNotExist()
        click("참여하기")
        compose.waitUntil(5_000) { compose.onAllNodesWithText("서버 설명 9001").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(listOf("join:9001"), TestRoomCalls.calls.toList())
        back()
        compose.onNodeWithText("우리만").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("1 / 2 페이지 · 6개 방").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun publicPreviewJoinFailureKeepsPreviewAndShowsServerMessage() {
        TestRoomCalls.mutationFailure = HttpException(
            Response.error<Any>(409, """{"code":"ROOM_FULL","message":"x"}""".toResponseBody("application/json".toMediaType()))
        )
        click("홈 · 공개 (다른 마을 구경)")
        compose.waitUntil(5_000) { compose.onAllNodesWithText("서버 공개방 A").fetchSemanticsNodes().isNotEmpty() }
        click("서버 공개방 A")
        compose.waitUntil(5_000) { compose.onAllNodesWithText("참여하기").fetchSemanticsNodes().isNotEmpty() }
        click("참여하기")
        compose.waitUntil(5_000) { compose.onAllNodesWithText("방 정원이 가득 찼어요.").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("참여하기").assertIsEnabled()
        assertTrue(TestRoomCalls.joined.isEmpty())
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
        // 방 생성과 초대코드 참여 모두 서버 연동 화면으로 진입한다(Mock 화면이 아님).
        click("+ 방 만들기")
        compose.onNodeWithTag("realCreateRoom").assertExists()
        back()
        click("초대코드 참여")
        compose.onNodeWithTag("realJoinRoom").assertExists()
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
        TestMissionCalls.seeded = true // 방 미션 목록은 방 진입 시 조회되므로 테스트 중 설정해도 된다.
        openMyRoom(1, "서버 설명 9101")
        compose.onNodeWithText("멤버 2/10명 · 방장 서버방장").assertExists()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("서버멤버").fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasText("서버방장") and hasAnyAncestor(hasTestTag("roomMembers"))).assertExists()
        compose.onNodeWithText("방장").assertExists()
        compose.onNodeWithTag("roomInviteCode").assertTextEquals("초대코드: SRV9101")
        click("미션")
        compose.waitUntil(5_000) { compose.onAllNodesWithText("서버 앱 미션 9101").fetchSemanticsNodes().isNotEmpty() }
        // 서버 방에서는 Mock 피드·멤버·초대코드가 나타나지 않는다.
        click("피드")
        compose.onNodeWithText("지수").assertDoesNotExist()
        compose.onNodeWithText("민수").assertDoesNotExist()
        compose.onNodeWithText("피드는 아직 서버와 연결되지 않았어요.", substring = true).assertDoesNotExist()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("feedContent").fetchSemanticsNodes().isNotEmpty() }
        click("+ 미션 보내기")
        compose.onNodeWithTag("sendRoomMission").assertExists()
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
        compose.waitUntil(5_000) { compose.onAllNodesWithText("참여하기").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("참여하기").assertIsEnabled()
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
        // 홈 지도는 핀 얼굴을 그리려고 보이는 방들의 멤버를 조회한다(프로필 얼굴 6단계). 그 조회가 끝난 뒤부터 방 화면의 조회만 센다.
        compose.waitUntil(5_000) { 9101L in TestRoomCalls.membersRequested }
        compose.waitForIdle()
        TestRoomCalls.membersRequested.clear()
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

    private fun createRoom(name: String, makePublic: Boolean) {
        compose.onAllNodes(hasSetTextAction())[0].performTextInput(name)
        compose.onNodeWithText(if (makePublic) "공개" else "우리만 · 비공개").performClick()
        click("방 만들기")
    }

    @Test
    fun realCreateEntryDefaultsFollowTabAndRemainEditable() {
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
        assertEquals(0, TestRoomCalls.createCalls)
    }

    @Test
    fun invalidInputShowsFieldErrorsWithoutServerCall() {
        click("홈 · 우리만 (마을 지도)")
        click("+ 방 만들기")
        click("방 만들기")
        compose.onNodeWithText("방 이름을 입력해 주세요.").assertExists()
        assertEquals(0, TestRoomCalls.createCalls)
    }

    @Test
    fun privateRoomCreationOpensRealRoomWithServerInviteCodeAndAppearsOnMap() {
        click("홈 · 우리만 (마을 지도)")
        click("+ 방 만들기")
        createRoom("  새 비공개방  ", makePublic = false)
        compose.waitUntil(5_000) { compose.onAllNodesWithText("멤버 1/10명 · 방장 uitest").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("새 비공개방").assertExists()
        compose.onNodeWithTag("roomInviteCode").assertTextEquals("초대코드: NEW9301")
        assertEquals(1, TestRoomCalls.createCalls)
        // 뒤로가기는 생성 화면이 아니라 홈으로 돌아가며 중복 생성되지 않는다.
        back()
        compose.onNodeWithTag("realCreateRoom").assertDoesNotExist()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("1 / 2 페이지 · 6개 방").fetchSemanticsNodes().isNotEmpty() }
        click("다음 ›")
        compose.onNodeWithText("새 비공개방").assertExists()
        assertEquals(1, TestRoomCalls.createCalls)
    }

    @Test
    fun publicRoomCreationShowsNoInviteCodeAndAppearsInPublicList() {
        click("홈 · 공개 (다른 마을 구경)")
        click("+ 방 만들기")
        createRoom("새 공개방", makePublic = true)
        compose.waitUntil(5_000) { compose.onAllNodesWithText("멤버 1/10명 · 방장 uitest").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("roomInviteCode").assertDoesNotExist()
        back()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("새 공개방").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun uncertainCreateOutcomeShowsGuidanceAndDoesNotRetry() {
        TestRoomCalls.createFailure = java.io.IOException("lost")
        click("홈 · 우리만 (마을 지도)")
        click("+ 방 만들기")
        createRoom("응답유실방", makePublic = false)
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("createOutcomeUnknown").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("생성되었").assertDoesNotExist()
        assertEquals(1, TestRoomCalls.createCalls)
    }
}

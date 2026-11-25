package com.dailyquest.app

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.dailyquest.app.data.remote.dto.AvatarDto
import com.dailyquest.app.data.remote.dto.RoomMemberDto
import com.dailyquest.app.ui.avatar.ProfileFaceKind
import com.dailyquest.app.ui.avatar.ProfileFaceKindKey
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain

/**
 * 프로필 얼굴 6단계: 홈 지도 핀에 실제 멤버 얼굴 (FakeApi).
 * 첫 페이지 방: 9101(1명)·9102(2명)·9103(3명, 멤버 조회가 항상 실패)·9104(4명). 멤버 목록은 서버방장(7)·서버멤버(8)·나(1).
 */
class HomeMapFacesTest {
    private val container = (InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as DQApplication).container
    private val girl = "https://test.invalid/items/avatar/girl"
    private val boy = "https://test.invalid/items/avatar/boy"

    private val compose = createAndroidComposeRule<MainActivity>()
    @get:Rule val rules: RuleChain = RuleChain.outerRule(object : ExternalResource() {
        override fun before() {
            TestRoomCalls.reset()
            TestAvatarCalls.reset()
            container.roomMemberFaceRepository.clear()
            TestShopCalls.stock += TestShopCalls.Stock(7601, "여 피부", "AVATAR", "SKIN", 0, modelUrl = "$girl/DQ_Girl_01_Body.glb", gender = "FEMALE")
            TestShopCalls.stock += TestShopCalls.Stock(7602, "남 피부", "AVATAR", "SKIN", 0, modelUrl = "$boy/DQ_Boy_01_Body.glb", gender = "MALE")
            TestRoomCalls.memberAvatars[7] = AvatarDto(skin = 7601)
            TestRoomCalls.memberAvatars[8] = AvatarDto(skin = 7602)
            TestRoomCalls.extraMembers += RoomMemberDto(userId = 1, nickname = "uitest", role = "MEMBER")
        }
    }).around(compose)

    @After fun resetFakeServer() {
        TestRoomCalls.reset()
        TestAvatarCalls.reset()
        container.roomMemberFaceRepository.clear()
        runBlocking { container.authRepository.refreshProfile() }
    }

    private fun kind(k: ProfileFaceKind) = SemanticsMatcher.expectValue(ProfileFaceKindKey, k)
    private fun inPin(roomId: Long) = hasAnyAncestor(hasTestTag("mapRoomFaces-$roomId"))
    private fun face(roomId: Long, name: String) = hasContentDescription("$name 아바타") and inPin(roomId)
    // 지도 핀은 누를 수 있는 줄이라 안의 얼굴 정보가 합쳐진다. 그래서 합치기 전 트리로 찾는다.
    private fun waitNode(m: SemanticsMatcher) = compose.waitUntil(5_000) { compose.onAllNodes(m, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
    private fun tab(label: String) = compose.onNode(hasText(label) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))

    private fun openHome() {
        compose.onNode(hasText("홈 · 우리만 (마을 지도)") and hasClickAction()).performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("서버 내 방 1").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun pinsShowRealMemberFacesWithMeLastAndMoreCount() {
        val batchBefore = TestShopCalls.batchCalls.get()
        openHome()
        // 다른 멤버 먼저(여자 방장, 남자 멤버), 나는 맨 뒤.
        waitNode(face(9101, "서버방장") and kind(ProfileFaceKind.GIRL))
        waitNode(face(9101, "서버멤버") and kind(ProfileFaceKind.BOY))
        waitNode(face(9101, "uitest"))
        val order = compose.onAllNodes(hasContentDescription("아바타", substring = true) and inPin(9102), useUnmergedTree = true).fetchSemanticsNodes()
            .sortedBy { it.boundsInRoot.left }.map { it.config[SemanticsProperties.ContentDescription].single() }
        assertEquals(listOf("서버방장 아바타", "서버멤버 아바타", "uitest 아바타"), order)

        // 4명 방: 3명 + "+1". 1~3명 방에는 "+N"이 없다.
        compose.onNode(hasText("+1") and hasAnyAncestor(hasTestTag("mapRoomMore-9104")), useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("mapRoomMore-9101", useUnmergedTree = true).assertDoesNotExist()

        // 멤버 조회가 실패한 방: 인원 수(3명)만큼 사람 아이콘.
        val unknown = compose.onAllNodes(kind(ProfileFaceKind.PERSON) and inPin(9103), useUnmergedTree = true).fetchSemanticsNodes()
        assertEquals(3, unknown.size)

        assertTrue("지도의 모든 얼굴 정보는 한 번에 받는다", TestShopCalls.batchCalls.get() - batchBefore <= 1)
    }

    @Test fun otherMembersNewOutfitShowsWhenComingBackHome() {
        openHome()
        waitNode(face(9101, "서버방장") and kind(ProfileFaceKind.GIRL))

        // 서버방장이 다른 기기에서 옷(체형이 다른 피부)을 바꿈 → 다른 탭에 갔다가 홈으로 돌아오면 새로 받는다.
        TestRoomCalls.memberAvatars[7] = AvatarDto(skin = 7602)
        tab("마이").performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(hasTestTag("myScreen")).fetchSemanticsNodes().isNotEmpty() }
        tab("홈").performClick()
        waitNode(face(9101, "서버방장") and kind(ProfileFaceKind.BOY))
    }
}

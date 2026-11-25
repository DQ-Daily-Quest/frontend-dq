package com.dailyquest.app

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import com.dailyquest.app.data.remote.dto.AvatarDto
import com.dailyquest.app.ui.avatar.ProfileFaceKind
import com.dailyquest.app.ui.avatar.ProfileFaceKindKey
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain

/**
 * 프로필 얼굴 7단계: 미션 보내기 받는 사람, 홈 "친구가 보낸 미션" 보낸 사람, 공개 탭 방장 얼굴 (FakeApi).
 * 서버방장(7)은 여자 피부, 서버멤버(8)는 남자 피부를 입고 있다.
 */
class ProfileFaceMoreScreensTest {
    private val container = (InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as DQApplication).container
    private val girl = "https://test.invalid/items/avatar/girl"
    private val boy = "https://test.invalid/items/avatar/boy"

    private val compose = createAndroidComposeRule<MainActivity>()
    // 앱 시작 시 전역 미션 목록이 로드되므로 시드는 Activity 시작 전에 설정한다.
    @get:Rule val rules: RuleChain = RuleChain.outerRule(object : ExternalResource() {
        override fun before() {
            TestRoomCalls.reset()
            TestMissionCalls.seeded = true
            container.roomMemberFaceRepository.clear()
            container.userAvatarRepository.clear()
            TestShopCalls.stock += TestShopCalls.Stock(7701, "여 피부", "AVATAR", "SKIN", 0, modelUrl = "$girl/DQ_Girl_01_Body.glb", gender = "FEMALE")
            TestShopCalls.stock += TestShopCalls.Stock(7702, "남 피부", "AVATAR", "SKIN", 0, modelUrl = "$boy/DQ_Boy_01_Body.glb", gender = "MALE")
            TestRoomCalls.memberAvatars[7] = AvatarDto(skin = 7701)
            TestRoomCalls.memberAvatars[8] = AvatarDto(skin = 7702)
        }
    }).around(compose)

    @After fun resetFakeServer() {
        TestRoomCalls.reset()
        container.roomMemberFaceRepository.clear()
        container.userAvatarRepository.clear()
        runBlocking { container.authRepository.refreshProfile() }
    }

    private fun kind(k: ProfileFaceKind) = SemanticsMatcher.expectValue(ProfileFaceKindKey, k)
    // 누를 수 있는 줄 안의 얼굴은 정보가 합쳐지므로 합치기 전 트리로 찾는다.
    private fun waitNode(m: SemanticsMatcher) = compose.waitUntil(5_000) { compose.onAllNodes(m, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
    private fun click(text: String) = compose.onNode(hasText(text) and hasClickAction()).performClick()
    private fun waitText(text: String) = compose.waitUntil(5_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    private fun openHome() { click("홈 · 우리만 (마을 지도)"); waitText("서버 내 방 1") }

    @Test fun sendMissionRecipientsShowTheirFaces() {
        openHome()
        compose.onNode(hasText("서버 내 방 1") and hasText("1명")).performClick()
        waitText("서버 설명 9101")
        click("미션")
        click("+ 미션 보내기")
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("recipient-8").fetchSemanticsNodes().isNotEmpty() }
        waitNode(hasContentDescription("서버방장 아바타") and hasAnyAncestor(hasTestTag("recipient-7")) and kind(ProfileFaceKind.GIRL))
        waitNode(hasContentDescription("서버멤버 아바타") and hasAnyAncestor(hasTestTag("recipient-8")) and kind(ProfileFaceKind.BOY))
        // "전체"는 얼굴이 아니라 사람들 아이콘 그대로.
        compose.onNode(hasAnyAncestor(hasTestTag("recipient-all")) and SemanticsMatcher.keyIsDefined(ProfileFaceKindKey), useUnmergedTree = true).assertDoesNotExist()
    }

    @Test fun homeFriendMissionShowsSendersFace() {
        openHome()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("homeFriendMissions").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("homeFriendMissions").performScrollTo()
        // 보낸 사람 정보(userId·닉네임)에는 아바타가 없어 GET /users/7 로 받는다.
        waitNode(hasContentDescription("서버방장 아바타") and hasAnyAncestor(hasTestTag("homeFriendMissions")) and kind(ProfileFaceKind.GIRL))
        assertTrue(7L in TestRoomCalls.usersRequested)
    }

    @Test fun publicTabShowsOwnerFaceOnlyForJoinedRooms() {
        openHome()
        compose.onNodeWithText("공개").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("서버 공개방 B").fetchSemanticsNodes().isNotEmpty() }
        // 9002는 참여한 방: 방장(서버방장, 여자) 얼굴. 9001은 참여하지 않은 방: 얼굴 없음, 멤버도 조회하지 않음.
        waitNode(hasTestTag("publicRoomOwner-9002") and kind(ProfileFaceKind.GIRL))
        compose.onNodeWithTag("publicRoomOwner-9001", useUnmergedTree = true).assertDoesNotExist()
        compose.runOnIdle { assertTrue(9001L !in TestRoomCalls.membersRequested) }
    }
}

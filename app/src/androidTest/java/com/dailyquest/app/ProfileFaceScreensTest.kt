package com.dailyquest.app

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
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
import androidx.test.platform.app.InstrumentationRegistry
import com.dailyquest.app.data.avatar.AvatarPart
import com.dailyquest.app.data.avatar.FaceKey
import com.dailyquest.app.data.remote.ApiResult
import com.dailyquest.app.data.remote.dto.AvatarDto
import com.dailyquest.app.data.remote.dto.RoomMemberDto
import com.dailyquest.app.ui.avatar.FaceStudios
import com.dailyquest.app.ui.avatar.ProfileFaceKind
import com.dailyquest.app.ui.avatar.ProfileFaceKindKey
import com.dailyquest.app.ui.avatar.clearAvatarFaceMemory
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import java.io.ByteArrayOutputStream

/**
 * 프로필 얼굴 5단계: 마이페이지 내 얼굴, 방 화면 멤버 얼굴 (FakeApi).
 * UI 테스트 환경은 3D를 끄므로 얼굴 공장이 돌지 않는다. "찍힌 사진"은 공장의 완료 처리([com.dailyquest.app.ui.avatar.FaceStudio.complete])로 흉내 낸다.
 */
class ProfileFaceScreensTest {
    private val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as DQApplication
    private val container = app.container
    private val studio = FaceStudios.get(app)

    private val boy = "https://test.invalid/items/avatar/boy"
    private val girl = "https://test.invalid/items/avatar/girl"

    private val compose = createAndroidComposeRule<MainActivity>()
    @get:Rule val rules: RuleChain = RuleChain.outerRule(object : ExternalResource() {
        override fun before() {
            TestRoomCalls.reset()
            TestAvatarCalls.reset()
            TestShopCalls.seedDefaults(); TestShopCalls.coins = 320
            // 다른 테스트와 겹치지 않는 아이템 ID(아이템 정보는 앱이 켜진 동안 기억되므로).
            TestShopCalls.stock += TestShopCalls.Stock(7401, "남 피부", "AVATAR", "SKIN", 0, modelUrl = "$boy/DQ_Boy_01_Body.glb", gender = "MALE")
            TestShopCalls.stock += TestShopCalls.Stock(7402, "남 머리", "AVATAR", "HAIR", 0, modelUrl = "$boy/DQ_Boy_03_Hair.glb", gender = "MALE")
            TestShopCalls.stock += TestShopCalls.Stock(7403, "카페 모자", "AVATAR", "HAT", 100, modelUrl = "$boy/DQ_Boy_Hat_Cafe.glb", gender = "MALE")
            TestShopCalls.stock += TestShopCalls.Stock(7501, "여 피부", "AVATAR", "SKIN", 0, modelUrl = "$girl/DQ_Girl_01_Body.glb", gender = "FEMALE")
            TestShopCalls.owned += listOf(7401L, 7402L, 7403L)
            TestAvatarCalls.equipped += mapOf("skin" to 7401L, "hair" to 7402L)
            runBlocking {
                studio.store.clear()
                clearAvatarFaceMemory()
                container.authRepository.refreshProfile() // 세션의 내 정보에 위 착용을 반영
            }
        }
    }).around(compose)

    @After fun resetFakeServer() {
        TestRoomCalls.reset()
        TestAvatarCalls.reset()
        runBlocking { container.authRepository.refreshProfile() }
    }

    private val myBasic = mapOf(AvatarPart.SKIN to "$boy/DQ_Boy_01_Body.glb", AvatarPart.HAIR to "$boy/DQ_Boy_03_Hair.glb")
    private val myWithHat = myBasic + (AvatarPart.HAT to "$boy/DQ_Boy_Hat_Cafe.glb")

    private fun photoShot(parts: Map<AvatarPart, String>) = runBlocking {
        val png = ByteArrayOutputStream().use { out ->
            Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.MAGENTA) }.compress(Bitmap.CompressFormat.PNG, 100, out)
            out.toByteArray()
        }
        studio.complete(FaceKey(parts), png)
    }

    private fun kind(k: ProfileFaceKind) = SemanticsMatcher.expectValue(ProfileFaceKindKey, k)
    private fun waitNode(m: SemanticsMatcher, timeoutMs: Long = 5_000) =
        compose.waitUntil(timeoutMs) { compose.onAllNodes(m).fetchSemanticsNodes().isNotEmpty() }
    private fun waitText(text: String) = compose.waitUntil(5_000) { compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }
    private fun tab(label: String) = compose.onNode(hasText(label) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))
    private fun openHome() {
        compose.onNode(hasText("홈 · 우리만 (마을 지도)") and hasClickAction()).performClick()
        waitText("서버 내 방 1")
    }
    private fun openMy() {
        openHome(); tab("마이").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("myScreen").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun myPageShowsMyFaceAndChangesRightAfterEquip() {
        openMy()
        // 사진이 아직 없으면 성별 그림, 얼굴 공장에 찍기를 부탁한다.
        waitNode(hasTestTag("myProfileFace") and kind(ProfileFaceKind.BOY))
        assertTrue(studio.pending.value.any { it.id == FaceKey(myBasic).id })
        // 찍히면 사진으로 바뀐다.
        photoShot(myBasic)
        waitNode(hasTestTag("myProfileFace") and kind(ProfileFaceKind.PHOTO))

        // 착용을 바꾸면(모자) 내 정보가 갱신되어 새 조합의 얼굴을 부탁하고, 찍히면 새 사진이 된다.
        val r = runBlocking { container.myAvatarRepository.equip(mapOf(AvatarPart.HAT to 7403L)) }
        assertTrue(r is ApiResult.Success)
        waitNode(hasTestTag("myProfileFace") and kind(ProfileFaceKind.BOY))
        compose.waitUntil(5_000) { studio.pending.value.any { it.id == FaceKey(myWithHat).id } }
        photoShot(myWithHat)
        waitNode(hasTestTag("myProfileFace") and kind(ProfileFaceKind.PHOTO))
    }

    @Test fun roomMembersShowTheirOwnFaces() {
        TestRoomCalls.memberAvatars[7] = AvatarDto(skin = 7501)
        // 서버의 멤버 목록에는 예전 착용(모자 없음)이 있어도 내 얼굴은 세션의 지금 착용(모자)을 쓴다.
        TestRoomCalls.extraMembers += RoomMemberDto(userId = 1, nickname = "uitest", role = "MEMBER", avatar = AvatarDto(skin = 7401, hair = 7402))
        TestAvatarCalls.equipped["hat"] = 7403L
        runBlocking { container.authRepository.refreshProfile() }
        photoShot(myWithHat)

        openHome()
        val batchBefore = TestShopCalls.batchCalls.get()
        compose.onNodeWithText("서버 내 방 1").performClick()
        waitText("서버멤버")

        fun face(name: String) = hasContentDescription("$name 아바타") and hasAnyAncestorTag("roomMembers")
        waitNode(face("서버방장") and kind(ProfileFaceKind.GIRL))
        waitNode(face("uitest") and kind(ProfileFaceKind.PHOTO))
        compose.onNode(face("서버멤버")).assert(kind(ProfileFaceKind.PERSON)) // 착용 정보 없음
        compose.onNodeWithText("멤버 아바타는 임시 이미지예요.").assertDoesNotExist()
        assertTrue("멤버 얼굴 정보는 한 번에 받는다", TestShopCalls.batchCalls.get() - batchBefore <= 1)
    }

    private fun hasAnyAncestorTag(tag: String) = androidx.compose.ui.test.hasAnyAncestor(hasTestTag(tag))
}

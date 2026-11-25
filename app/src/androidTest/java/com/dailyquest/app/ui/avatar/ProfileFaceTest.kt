package com.dailyquest.app.ui.avatar

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.dailyquest.app.DQApplication
import com.dailyquest.app.TestShopCalls
import com.dailyquest.app.data.avatar.AvatarGender
import com.dailyquest.app.data.avatar.AvatarPart
import com.dailyquest.app.data.avatar.FaceKey
import com.dailyquest.app.data.avatar.PersonFace
import com.dailyquest.app.data.remote.dto.AvatarDto
import com.dailyquest.app.ui.room.LocalAppContainer
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.IOException

/**
 * 프로필 얼굴 4단계: 얼굴 동그라미가 사진 → 성별별 기본 그림 → 사람 아이콘 순으로 고르는지.
 * UI 테스트 환경은 3D를 끄므로 얼굴 공장이 돌지 않는다. "찍힌 사진"은 기기 저장소에 직접 넣어 흉내 낸다.
 */
class ProfileFaceTest {
    @get:Rule val compose = createComposeRule()

    private val app = ApplicationProvider.getApplicationContext<DQApplication>()
    private val container = app.container
    private val studio = FaceStudios.get(app)

    private val girl = "https://test.invalid/items/avatar/girl"
    private val boy = "https://test.invalid/items/avatar/boy"

    @Before fun clean() = runBlocking {
        studio.store.clear()
        clearAvatarFaceMemory()
        TestShopCalls.reset()
        // 다른 테스트와 겹치지 않는 아이템 ID(아이템 정보는 앱이 켜진 동안 기억되므로).
        TestShopCalls.stock += TestShopCalls.Stock(7101, "여 피부", "AVATAR", "SKIN", 0, modelUrl = "$girl/DQ_Girl_01_Body.glb", gender = "FEMALE")
        TestShopCalls.stock += TestShopCalls.Stock(7102, "여 머리", "AVATAR", "HAIR", 0, modelUrl = "$girl/DQ_Girl_03_Hair.glb", gender = "FEMALE")
        TestShopCalls.stock += TestShopCalls.Stock(7103, "여 하의", "AVATAR", "BOTTOM", 0, modelUrl = "$girl/DQ_Girl_05_Bottom.glb", gender = "FEMALE")
        TestShopCalls.stock += TestShopCalls.Stock(7201, "남 피부", "AVATAR", "SKIN", 0, modelUrl = "$boy/DQ_Boy_01_Body.glb", gender = "MALE")
    }

    @After fun reset() = TestShopCalls.reset()

    private fun kind(k: ProfileFaceKind) = SemanticsMatcher.expectValue(ProfileFaceKindKey, k)

    private fun waitKind(tag: String, k: ProfileFaceKind) = compose.waitUntil(5_000) {
        compose.onAllNodes(hasTestTag(tag) and kind(k)).fetchSemanticsNodes().isNotEmpty()
    }

    private fun savePhoto(parts: Map<AvatarPart, String>) = runBlocking {
        val png = ByteArrayOutputStream().use { out ->
            Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.MAGENTA) }.compress(Bitmap.CompressFormat.PNG, 100, out)
            out.toByteArray()
        }
        studio.store.put(FaceKey(parts), png)
    }

    @Test fun withoutPhotoShowsGenderPictureOrPersonIcon() {
        val boyParts = mapOf(AvatarPart.SKIN to "$boy/none.glb")
        compose.setContent {
            Row {
                ProfileFace(PersonFace(AvatarGender.MALE, boyParts), 48.dp, Modifier.testTag("m"))
                ProfileFace(PersonFace(AvatarGender.FEMALE, emptyMap()), 48.dp, Modifier.testTag("f"))
                ProfileFace(PersonFace.UNKNOWN, 48.dp, Modifier.testTag("u"))
                ProfileFace(face = null, size = 48.dp, modifier = Modifier.testTag("n"))
            }
        }
        compose.onNodeWithTag("m").assert(kind(ProfileFaceKind.BOY))
        compose.onNodeWithTag("f").assert(kind(ProfileFaceKind.GIRL))
        compose.onNodeWithTag("u").assert(kind(ProfileFaceKind.PERSON))
        compose.onNodeWithTag("n").assert(kind(ProfileFaceKind.PERSON))
        compose.waitForIdle()
        assertTrue("사진이 없으면 얼굴 공장에 찍기를 부탁한다", studio.pending.value.any { it.id == FaceKey(boyParts).id })
    }

    @Test fun storedPhotoIsShownAndSizeIsKept() {
        val parts = mapOf(AvatarPart.SKIN to "$boy/DQ_Boy_01_Body.glb", AvatarPart.HAIR to "$boy/DQ_Boy_03_Hair.glb")
        savePhoto(parts)
        compose.setContent { ProfileFace(PersonFace(AvatarGender.MALE, parts), 40.dp, Modifier.testTag("p"), contentDescription = "진영") }
        waitKind("p", ProfileFaceKind.PHOTO)
        val node = compose.onNodeWithTag("p").fetchSemanticsNode()
        val density = node.layoutInfo.density.density
        assertEquals("40dp 동그라미", 40f * density, node.size.width.toFloat(), 1f)
        assertEquals(listOf("진영"), node.config[androidx.compose.ui.semantics.SemanticsProperties.ContentDescription])
    }

    @Test fun serverAvatarIsResolvedOnceAndUsesItemGender() {
        val her = AvatarDto(skin = 7101, hair = 7102, bottom = 7103)
        var second by mutableStateOf(false)
        compose.setContent {
            CompositionLocalProvider(LocalAppContainer provides container) {
                Row {
                    ProfileFace(her, 48.dp, Modifier.testTag("a"))
                    ProfileFace(her, 32.dp, Modifier.testTag("b"))
                    if (second) ProfileFace(her.copy(bottom = null), 48.dp, Modifier.testTag("c"))
                }
            }
        }
        waitKind("a", ProfileFaceKind.GIRL)
        waitKind("b", ProfileFaceKind.GIRL)
        assertEquals("같은 아이템은 한 번만 묻는다", 1, TestShopCalls.batchCalls.get())

        // 얼굴 슬롯(피부·머리)만으로 찍은 사진이 있으면 그 사진. 하의는 얼굴 이름에 들어가지 않는다.
        savePhoto(mapOf(AvatarPart.SKIN to "$girl/DQ_Girl_01_Body.glb", AvatarPart.HAIR to "$girl/DQ_Girl_03_Hair.glb"))
        second = true
        waitKind("c", ProfileFaceKind.PHOTO)
        assertEquals("이미 아는 아이템은 다시 묻지 않는다", 1, TestShopCalls.batchCalls.get())
    }

    @Test fun unknownItemsOrServerErrorShowPersonIcon() {
        TestShopCalls.batchFailure = IOException("offline")
        var second by mutableStateOf(false)
        compose.setContent {
            CompositionLocalProvider(LocalAppContainer provides container) {
                Row {
                    ProfileFace(AvatarDto(skin = 7201), 48.dp, Modifier.testTag("err"))
                    ProfileFace(avatar = null, size = 48.dp, modifier = Modifier.testTag("none"))
                    if (second) ProfileFace(AvatarDto(skin = 7999), 48.dp, Modifier.testTag("gone"))
                }
            }
        }
        compose.waitUntil(5_000) { TestShopCalls.batchCalls.get() >= 1 }
        compose.waitForIdle()
        compose.onNodeWithTag("err").assert(kind(ProfileFaceKind.PERSON))
        compose.onNodeWithTag("none").assert(kind(ProfileFaceKind.PERSON))

        // 서버에 없는 아이템만 입었으면 성별을 몰라 사람 아이콘.
        TestShopCalls.batchFailure = null
        second = true
        compose.waitUntil(5_000) { TestShopCalls.batchCalls.get() >= 2 }
        compose.waitForIdle()
        compose.onNodeWithTag("gone").assert(kind(ProfileFaceKind.PERSON))
    }
}

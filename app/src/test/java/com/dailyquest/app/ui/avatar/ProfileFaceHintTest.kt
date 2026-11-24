package com.dailyquest.app.ui.avatar

import com.dailyquest.app.data.auth.toProfile
import com.dailyquest.app.data.avatar.AvatarGender
import com.dailyquest.app.data.avatar.AvatarPart
import com.dailyquest.app.data.avatar.PersonFace
import com.dailyquest.app.data.remote.dto.AvatarDto
import com.dailyquest.app.data.remote.dto.StreakDto
import com.dailyquest.app.data.remote.dto.UserMeResponse
import com.dailyquest.app.data.remote.dto.UserStatsDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 프로필 얼굴 5단계: 내 얼굴에 쓰는 정보(세션의 착용, 성별 힌트). */
class ProfileFaceHintTest {
    @Test fun `서버 내 정보의 착용 아바타가 세션 내 정보에 들어간다`() {
        val me = UserMeResponse(
            userId = 3, email = null, nickname = "진영", coins = 0, hasPassword = true,
            streak = StreakDto(0, 0), stats = UserStatsDto(0, 0), gender = "FEMALE",
            avatar = AvatarDto(skin = 101, hair = 113, hat = 150),
        )
        val p = me.toProfile()
        assertEquals(AvatarDto(skin = 101, hair = 113, hat = 150), p.avatar)
        assertEquals("FEMALE", p.gender)
    }

    @Test fun `아이템으로 성별을 모를 때만 힌트로 채운다`() {
        val parts = mapOf(AvatarPart.SKIN to "body.glb")
        assertEquals("받아 오는 중(null)이면 힌트 성별 그림", PersonFace(AvatarGender.MALE, emptyMap()), null.withGenderHint(AvatarGender.MALE))
        assertEquals(PersonFace(AvatarGender.FEMALE, parts), PersonFace(null, parts).withGenderHint(AvatarGender.FEMALE))
        assertEquals("아이템 성별이 우선", PersonFace(AvatarGender.MALE, parts), PersonFace(AvatarGender.MALE, parts).withGenderHint(AvatarGender.FEMALE))
        assertNull("힌트가 없으면 그대로", null.withGenderHint(null))
    }
}

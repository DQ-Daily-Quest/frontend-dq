package com.dailyquest.app.data.proof

import com.dailyquest.app.data.mission.MissionType
import com.dailyquest.app.data.remote.NetworkModule
import com.dailyquest.app.data.remote.dto.ProofDetailDto
import com.dailyquest.app.data.remote.dto.SubmitProofRequest
import com.dailyquest.app.data.remote.dto.UploadDto
import com.dailyquest.app.data.remote.dto.VoteDto
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProofDataTest {
    private val json = NetworkModule.json

    private fun detail(type: String, status: String = "PENDING", userId: Long = 7, myVote: String? = null, extra: String = "") = json.decodeFromString<ProofDetailDto>(
        """{"proofId":5,"missionId":3,"user":{"userId":$userId,"nickname":"서버방장"},"mediaUrl":"https://cdn/p.jpg","thumbnailUrl":"https://cdn/t.jpg",
        "mediaType":"IMAGE","text":"인증","status":"$status","approval":{"approved":1,"rejected":0,"required":2,"myVote":${myVote?.let { "\"$it\"" }},
        "voters":[{"userId":2,"nickname":"지수","vote":"APPROVE"}]},"createdAt":"2025-04-17T00:00:00Z",
        "mission":{"missionId":3,"type":"$type","content":"하늘 찍기"},"room":{"roomId":10,"name":"방"}$extra}"""
    ).toDetail()

    @Test fun decodesUploadAndIgnoresUnknownFields() {
        val u = json.decodeFromString<UploadDto>("""{"url":"https://cdn/a.jpg","thumbnailUrl":"https://cdn/t.jpg","mediaType":"IMAGE","width":1,"height":2,"x":1}""").toUploaded()
        assertEquals(ProofMediaType.IMAGE, u.mediaType); assertEquals("https://cdn/a.jpg", u.url)
    }

    @Test fun submitRequestOmitsNullFields() {
        val s = json.encodeToString(SubmitProofRequest("IMAGE", "https://cdn/a.jpg"))
        assertTrue(s.contains("\"mediaType\":\"IMAGE\"")); assertFalse(s.contains("text")); assertFalse(s.contains("thumbnailUrl"))
    }

    @Test fun mapsDetailApprovalAndVoters() {
        val d = detail("APP")
        assertEquals(ProofStatus.PENDING, d.proof.status); assertEquals(MissionType.APP, d.missionType)
        assertEquals(10L, d.roomId); assertEquals(2, d.proof.approval.required)
        assertEquals(VoteChoice.APPROVE, d.proof.approval.voters.single().vote)
    }

    @Test fun unknownStatusIsKeptAsUnknownNotGuessed() {
        val d = detail("APP", status = "WEIRD")
        assertEquals(ProofStatus.UNKNOWN, d.proof.status); assertEquals("WEIRD", d.proof.rawStatus)
        assertFalse(d.canVote(1, null))
    }

    @Test fun appProofCanBeVotedByOthersButNotOwnerOrTwice() {
        assertTrue(detail("APP").canVote(1, null))
        assertFalse(detail("APP", userId = 1).canVote(1, null)) // 본인 인증
        assertFalse(detail("APP", myVote = "APPROVE").canVote(1, null)) // 이미 투표
        assertFalse(detail("APP", status = "APPROVED").canVote(1, null)) // 확정 후
    }

    @Test fun memberProofOnlyCreatorMayVoteWhenKnown() {
        val d = detail("MEMBER")
        assertTrue(d.canVote(1, missionCreatorId = 1))
        assertFalse(d.canVote(1, missionCreatorId = 2))
    }

    @Test fun voteResultCarriesRewardOnlyWhenServerGrantsIt() {
        val none = json.decodeFromString<VoteDto>("""{"proofId":5,"status":"PENDING","approval":{"approved":1,"rejected":0,"required":2,"myVote":"APPROVE"}}""").toResult()
        assertNull(none.rewardCoins); assertEquals(ProofStatus.PENDING, none.status)
        val granted = json.decodeFromString<VoteDto>("""{"proofId":5,"status":"APPROVED","approval":{"approved":2,"rejected":0,"required":2},"rewardGranted":{"type":"COIN","amount":50,"streakBonus":100}}""").toResult()
        assertEquals(50, granted.rewardCoins); assertEquals(100, granted.streakBonus)
    }

    // ----- 파일 검증 -----
    @Test fun sniffsRealFormatByMagicBytes() {
        assertEquals("image/jpeg", ProofValidation.sniffMime(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte())))
        assertEquals("image/png", ProofValidation.sniffMime(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)))
        assertEquals("image/webp", ProofValidation.sniffMime("RIFF....WEBP".toByteArray()))
        assertEquals("video/mp4", ProofValidation.sniffMime(byteArrayOf(0, 0, 0, 0x18, 0x66, 0x74, 0x79, 0x70, 0x6D, 0x70, 0x34, 0x32)))
        assertNull(ProofValidation.sniffMime("GIF89a".toByteArray()))
        assertNull(ProofValidation.sniffMime(ByteArray(0)))
    }

    @Test fun validatesSizeTypeAndVideoDuration() {
        assertNull(ProofValidation.validateMedia("image/jpeg", 1024, null))
        assertNotNull(ProofValidation.validateMedia("image/gif", 1024, null))
        assertNotNull(ProofValidation.validateMedia("image/png", 0, null))
        assertNull(ProofValidation.validateMedia("image/png", ProofValidation.IMAGE_MAX_BYTES, null))
        assertNotNull(ProofValidation.validateMedia("image/png", ProofValidation.IMAGE_MAX_BYTES + 1, null))
        assertNull(ProofValidation.validateMedia("video/mp4", 1024, 5_000))
        assertNull(ProofValidation.validateMedia("video/mp4", 1024, 5_500)) // 카메라 앱 오차 허용
        assertNotNull(ProofValidation.validateMedia("video/mp4", 1024, 5_501))
        assertNotNull(ProofValidation.validateMedia("video/mp4", 1024, null)) // 길이를 모르면 거부
        assertNotNull(ProofValidation.validateMedia("video/mp4", ProofValidation.VIDEO_MAX_BYTES + 1, 1_000))
    }

    @Test fun validatesAndNormalizesText() {
        assertNull(ProofValidation.validateText("a".repeat(300)))
        assertNotNull(ProofValidation.validateText("a".repeat(301)))
        assertNull(ProofValidation.validateText("  " + "a".repeat(300) + "  ")) // trim 후 길이
        assertNull(ProofValidation.normalizeText("   "))
        assertEquals("안녕", ProofValidation.normalizeText(" 안녕 "))
    }
}

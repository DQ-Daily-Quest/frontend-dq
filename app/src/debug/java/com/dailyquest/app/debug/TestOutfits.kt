package com.dailyquest.app.debug

/**
 * STEP 9-3 테스트 화면용 "한 벌" 목록. 실제 앱에서는 서버의 착용 정보(avatar)와 modelUrl로 정해지므로 여기서만 쓴다.
 * 코드 이름은 backend V10 시드(= S3 파일 이름)와 같다.
 */
internal const val ASSET_BASE = "https://dq-uploads-prod.s3.ap-northeast-2.amazonaws.com/items/avatar"

/** 서버 아바타 슬롯과 같은 7개. 그리는 순서와는 관계없다(모두 같은 원점에 겹친다). */
internal enum class TestSlot { SKIN, EYES, HAIR, TOP, BOTTOM, SHOES, HAT }

internal enum class TestBody(val folder: String) { BOY("boy"), GIRL("girl") }

internal data class TestOutfit(val label: String, val body: TestBody, val parts: Map<TestSlot, String>) {
    /** 슬롯 순서대로 정렬한 GLB 주소 목록 */
    val urls: List<String> get() = TestSlot.entries.mapNotNull { slot -> parts[slot]?.let { modelUrl(body, it) } }
}

internal fun modelUrl(body: TestBody, code: String) = "$ASSET_BASE/${body.folder}/$code.glb"

internal val TEST_OUTFITS = listOf(
    // 성별 확정 직후 기본 착용과 같다: 피부 첫 톤 + 기본 6종, 모자는 지급만 하고 착용하지 않는다.
    TestOutfit(
        "남 기본", TestBody.BOY,
        mapOf(
            TestSlot.SKIN to "DQ_Boy_01_Body", TestSlot.EYES to "DQ_Boy_02_Eyes", TestSlot.HAIR to "DQ_Boy_03_Hair",
            TestSlot.TOP to "DQ_Boy_04_Top", TestSlot.BOTTOM to "DQ_Boy_05_Bottom", TestSlot.SHOES to "DQ_Boy_06_Shoes",
        ),
    ),
    // 같은 테마 한 벌 + 모자 (모자와 머리가 겹치는지 확인)
    TestOutfit(
        "남 카페+모자", TestBody.BOY,
        mapOf(
            TestSlot.SKIN to "DQ_Boy_01_Body", TestSlot.EYES to "DQ_Boy_Eyes_Cafe", TestSlot.HAIR to "DQ_Boy_Hair_Cafe",
            TestSlot.TOP to "DQ_Boy_Top_Cafe", TestSlot.BOTTOM to "DQ_Boy_Bottom_Cafe", TestSlot.SHOES to "DQ_Boy_Shoes_Cafe",
            TestSlot.HAT to "DQ_Boy_Hat_Cafe",
        ),
    ),
    TestOutfit(
        "여 기본", TestBody.GIRL,
        mapOf(
            TestSlot.SKIN to "DQ_Body", TestSlot.EYES to "DQ_Eyes", TestSlot.HAIR to "DQ_Hair",
            TestSlot.TOP to "DQ_Top", TestSlot.BOTTOM to "DQ_Bottom", TestSlot.SHOES to "DQ_Shoes",
        ),
    ),
    // 서로 다른 테마를 섞어 입기 (메시가 뚫고 나오는지 확인)
    TestOutfit(
        "여 섞어 입기", TestBody.GIRL,
        mapOf(
            TestSlot.SKIN to "DQ_Body", TestSlot.EYES to "DQ_Eyes_Rose", TestSlot.HAIR to "DQ_Hair_CopperPony",
            TestSlot.TOP to "DQ_Top_BerryCardigan", TestSlot.BOTTOM to "DQ_Bottom_PetalSkirt",
            TestSlot.SHOES to "DQ_Shoes_BerryMaryJanes", TestSlot.HAT to "DQ_Hat_LemonBucket",
        ),
    ),
)

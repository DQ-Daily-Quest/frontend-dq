package com.dailyquest.app.debug

import com.dailyquest.app.data.avatar.AvatarPart

/**
 * STEP 9 테스트 화면용 파츠·한 벌 목록. 실제 앱에서는 서버의 착용 정보(avatar)와 modelUrl로 정해지므로 여기서만 쓴다.
 * 코드 이름은 backend V10 시드(= S3 파일 이름)와 같다.
 */
internal const val ASSET_BASE = "https://dq-uploads-prod.s3.ap-northeast-2.amazonaws.com/items/avatar"

internal enum class TestBody(val folder: String) { BOY("boy"), GIRL("girl") }

internal data class TestOutfit(val label: String, val body: TestBody, val codes: Map<AvatarPart, String>) {
    /** AvatarView에 넘길 슬롯 → GLB 주소 */
    val parts: Map<AvatarPart, String> get() = codes.mapValues { (_, code) -> modelUrl(body, code) }
}

internal fun modelUrl(body: TestBody, code: String) = "$ASSET_BASE/${body.folder}/$code.glb"

/** 9-2: 파츠 하나씩. 크기·종류가 다르다. 머리(3.7MB, 12만 삼각형)가 가장 무겁다. */
internal val TEST_SINGLE_PARTS = listOf(
    TestOutfit("남 몸체", TestBody.BOY, mapOf(AvatarPart.SKIN to "DQ_Boy_01_Body")),
    TestOutfit("남 머리", TestBody.BOY, mapOf(AvatarPart.HAIR to "DQ_Boy_03_Hair")),
    TestOutfit("남 모자", TestBody.BOY, mapOf(AvatarPart.HAT to "DQ_Boy_Hat_Cafe")),
    TestOutfit("여 몸체", TestBody.GIRL, mapOf(AvatarPart.SKIN to "DQ_Body")),
    TestOutfit("여 상의", TestBody.GIRL, mapOf(AvatarPart.TOP to "DQ_Top_BerryCardigan")),
)

/** 9-3: 한 벌. */
internal val TEST_OUTFITS = listOf(
    // 성별 확정 직후 기본 착용과 같다: 피부 첫 톤 + 기본 6종, 모자는 지급만 하고 착용하지 않는다.
    TestOutfit(
        "남 기본", TestBody.BOY,
        mapOf(
            AvatarPart.SKIN to "DQ_Boy_01_Body", AvatarPart.EYES to "DQ_Boy_02_Eyes", AvatarPart.HAIR to "DQ_Boy_03_Hair",
            AvatarPart.TOP to "DQ_Boy_04_Top", AvatarPart.BOTTOM to "DQ_Boy_05_Bottom", AvatarPart.SHOES to "DQ_Boy_06_Shoes",
        ),
    ),
    // 같은 테마 한 벌 + 모자 (모자와 머리가 겹치는지 확인)
    TestOutfit(
        "남 카페+모자", TestBody.BOY,
        mapOf(
            AvatarPart.SKIN to "DQ_Boy_01_Body", AvatarPart.EYES to "DQ_Boy_Eyes_Cafe", AvatarPart.HAIR to "DQ_Boy_Hair_Cafe",
            AvatarPart.TOP to "DQ_Boy_Top_Cafe", AvatarPart.BOTTOM to "DQ_Boy_Bottom_Cafe", AvatarPart.SHOES to "DQ_Boy_Shoes_Cafe",
            AvatarPart.HAT to "DQ_Boy_Hat_Cafe",
        ),
    ),
    TestOutfit(
        "여 기본", TestBody.GIRL,
        mapOf(
            AvatarPart.SKIN to "DQ_Body", AvatarPart.EYES to "DQ_Eyes", AvatarPart.HAIR to "DQ_Hair",
            AvatarPart.TOP to "DQ_Top", AvatarPart.BOTTOM to "DQ_Bottom", AvatarPart.SHOES to "DQ_Shoes",
        ),
    ),
    // 서로 다른 테마를 섞어 입기 (메시가 뚫고 나오는지 확인)
    TestOutfit(
        "여 섞어 입기", TestBody.GIRL,
        mapOf(
            AvatarPart.SKIN to "DQ_Body", AvatarPart.EYES to "DQ_Eyes_Rose", AvatarPart.HAIR to "DQ_Hair_CopperPony",
            AvatarPart.TOP to "DQ_Top_BerryCardigan", AvatarPart.BOTTOM to "DQ_Bottom_PetalSkirt",
            AvatarPart.SHOES to "DQ_Shoes_BerryMaryJanes", AvatarPart.HAT to "DQ_Hat_LemonBucket",
        ),
    ),
)

/** 9-4: 한 벌을 띄운 상태에서 슬롯 하나만 바꿔 보는 데 쓰는 후보 (체형별). */
internal val TEST_HATS = mapOf(
    TestBody.BOY to listOf("DQ_Boy_Hat_Cafe", "DQ_Boy_Hat_Campus", "DQ_Boy_Hat_Marine", "DQ_Boy_Hat_Snow"),
    TestBody.GIRL to listOf("DQ_Hat_LemonBucket", "DQ_Hat_CloverBeret", "DQ_Hat_OrbitCap", "DQ_Hat_HollySanta"),
)
internal val TEST_TOPS = mapOf(
    TestBody.BOY to listOf("DQ_Boy_04_Top", "DQ_Boy_Top_Cafe", "DQ_Boy_Top_Marine", "DQ_Boy_Top_Sport"),
    TestBody.GIRL to listOf("DQ_Top", "DQ_Top_BerryCardigan", "DQ_Top_LemonMac", "DQ_Top_NoelCoat"),
)

/** [current]의 다음 후보. 목록에 없거나 마지막이면 처음으로 돌아간다. */
internal fun nextOf(candidates: List<String>, current: String?): String =
    candidates[(candidates.indexOf(current) + 1).mod(candidates.size)]

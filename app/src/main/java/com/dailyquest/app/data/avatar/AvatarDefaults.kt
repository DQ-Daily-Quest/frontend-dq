package com.dailyquest.app.data.avatar

/**
 * 캐릭터 만들기 화면(STEP 9-9)의 **미리보기 전용** 기본 한 벌.
 *
 * 성별을 정하기 전에는 서버가 아바타 아이템을 보여 주지 않는다(상점은 내 체형 아이템만, 성별 없으면 0개).
 * 그래서 고르는 동안 보여 줄 남/여 기본 한 벌의 코드와 S3 주소를 앱에 둔다. backend V10 시드와 같은 값이다.
 * **실제로 지급·착용되는 아이템은 서버가 정한다.** 서버 시드가 바뀌면 이 미리보기만 달라지고 저장은 영향받지 않는다.
 */
object AvatarDefaults {
    const val ASSET_BASE = "https://dq-uploads-prod.s3.ap-northeast-2.amazonaws.com/items/avatar"

    private fun folder(gender: AvatarGender) = if (gender == AvatarGender.MALE) "boy" else "girl"
    fun modelUrl(gender: AvatarGender, code: String) = "$ASSET_BASE/${folder(gender)}/$code.glb"

    /** 피부 3톤. 체형마다 같은 색의 코드가 다르다(서버가 처음 입히는 피부는 체형별 첫 번째: 남 아이보리, 여 피치). */
    enum class SkinTone(val label: String, val argb: Long, val maleCode: String, val femaleCode: String) {
        IVORY("아이보리", 0xFFF6E3D3, "DQ_Boy_01_Body", "DQ_Body_BoySkin"),
        PEACH("피치", 0xFFF3C4AE, "DQ_Boy_01_Body_GirlSkin", "DQ_Body"),
        BROWN("브라운", 0xFF9A6545, "DQ_Boy_01_Body_DeepBrown", "DQ_Body_DeepBrown");

        fun code(gender: AvatarGender) = if (gender == AvatarGender.MALE) maleCode else femaleCode
    }

    /** 서버가 성별 확정 시 처음 입혀 주는 피부(체형별 첫 톤) */
    fun serverDefaultSkin(gender: AvatarGender) = if (gender == AvatarGender.MALE) SkinTone.IVORY else SkinTone.PEACH

    /** 피부를 뺀 기본 한 벌(모자는 지급만 하고 입히지 않는다) */
    private val DEFAULT_CODES = mapOf(
        AvatarGender.MALE to mapOf(
            AvatarPart.EYES to "DQ_Boy_02_Eyes", AvatarPart.HAIR to "DQ_Boy_03_Hair", AvatarPart.TOP to "DQ_Boy_04_Top",
            AvatarPart.BOTTOM to "DQ_Boy_05_Bottom", AvatarPart.SHOES to "DQ_Boy_06_Shoes",
        ),
        AvatarGender.FEMALE to mapOf(
            AvatarPart.EYES to "DQ_Eyes", AvatarPart.HAIR to "DQ_Hair", AvatarPart.TOP to "DQ_Top",
            AvatarPart.BOTTOM to "DQ_Bottom", AvatarPart.SHOES to "DQ_Shoes",
        ),
    )

    /** 미리보기용 슬롯 → GLB 주소 (AvatarView에 넘긴다) */
    fun previewParts(gender: AvatarGender, skin: SkinTone): Map<AvatarPart, String> =
        (DEFAULT_CODES.getValue(gender) + (AvatarPart.SKIN to skin.code(gender))).mapValues { (_, code) -> modelUrl(gender, code) }
}

package com.dailyquest.app.data.customization

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

// Mock 내부 계약. 서버 DTO나 Blender 노드·본 규격을 정의하지 않는다.
enum class AvatarSlot(val label: String) {
    BODY("기본 몸체"), EYES("눈"), HAIR("머리카락"), TOP("상의"), BOTTOM("하의"), SHOES("신발"), HAT("모자")
}
enum class Acquisition { DEFAULT, COINS, REWARD }
data class AvatarItem(
    val itemId: String, val name: String, val slot: AvatarSlot,
    val acquisition: Acquisition, val price: Int = 0,
    val supportedBodies: Set<String>, val visualKey: String
)
data class CustomizationState(
    val coins: Int, val owned: Set<String>, val equipped: Map<AvatarSlot, String>
)
sealed interface CustomizationResult {
    data object Success : CustomizationResult
    data class Failure(val message: String) : CustomizationResult
}
interface CustomizationRepository {
    val catalog: List<AvatarItem>
    val state: CustomizationState
    fun preview(current: Map<AvatarSlot, String>, itemId: String): Map<AvatarSlot, String>
    fun save(equipment: Map<AvatarSlot, String>): CustomizationResult
    fun purchase(itemId: String): CustomizationResult
}

// 호환성은 Mock 테스트용이며 실제 남녀 모델 호환성을 보장하지 않는다.
class MockCustomizationStore : CustomizationRepository {
    override val catalog = AvatarSlot.entries.flatMap { slot ->
        val key = slot.name.lowercase()
        if (slot == AvatarSlot.BODY) listOf(
            AvatarItem("body_male", "기본 몸체 · 남", slot, Acquisition.DEFAULT, supportedBodies = setOf("male"), visualKey = "male"),
            AvatarItem("body_female", "기본 몸체 · 여", slot, Acquisition.DEFAULT, supportedBodies = setOf("female"), visualKey = "female")
        ) else listOf(
            AvatarItem("${key}_default", "기본 ${slot.label}", slot, Acquisition.DEFAULT, supportedBodies = setOf("male", "female"), visualKey = "${key}_default"),
            AvatarItem("${key}_premium", "민트 ${slot.label}", slot, Acquisition.COINS, 120, setOf("male", "female"), "${key}_premium"),
            AvatarItem("${key}_male", "남 전용 ${slot.label}", slot, Acquisition.COINS, 240, setOf("male"), "${key}_male"),
            AvatarItem("${key}_reward", "보상 ${slot.label}", slot, Acquisition.REWARD, supportedBodies = setOf("male", "female"), visualKey = "${key}_reward")
        )
    }
    private val defaults = AvatarSlot.entries.associateWith { slot ->
        if (slot == AvatarSlot.BODY) "body_male" else "${slot.name.lowercase()}_default"
    }
    override var state by mutableStateOf(CustomizationState(320, catalog.filter { it.acquisition == Acquisition.DEFAULT }.map { it.itemId }.toSet(), defaults))
        private set

    private fun validate(equipment: Map<AvatarSlot, String>, requireOwned: Boolean): String? {
        if (equipment.keys != AvatarSlot.entries.toSet()) return "모든 기본 파츠를 선택해 주세요."
        val body = catalog.find { it.itemId == equipment[AvatarSlot.BODY] && it.slot == AvatarSlot.BODY }
            ?: return "기본 몸체를 확인해 주세요."
        val bodyKey = body.visualKey
        for ((slot, id) in equipment) {
            val item = catalog.find { it.itemId == id } ?: return "상품을 찾을 수 없습니다."
            if (item.slot != slot) return "장착 슬롯이 올바르지 않습니다."
            if (bodyKey !in item.supportedBodies) return "호환되지 않는 파츠가 있습니다. 기본 파츠로 변경해 주세요."
            if (requireOwned && id !in state.owned) return "보유하지 않은 아이템은 저장할 수 없습니다."
        }
        return null
    }

    override fun preview(current: Map<AvatarSlot, String>, itemId: String): Map<AvatarSlot, String> {
        val item = catalog.find { it.itemId == itemId } ?: throw IllegalArgumentException("상품을 찾을 수 없습니다.")
        val next = current + (item.slot to itemId)
        require(validate(next, false) == null) { validate(next, false)!! }
        return next
    }

    @Synchronized
    override fun save(equipment: Map<AvatarSlot, String>): CustomizationResult {
        validate(equipment, true)?.let { return CustomizationResult.Failure(it) }
        state = state.copy(equipped = equipment.toMap())
        return CustomizationResult.Success
    }

    @Synchronized
    override fun purchase(itemId: String): CustomizationResult {
        val item = catalog.find { it.itemId == itemId } ?: return CustomizationResult.Failure("상품을 찾을 수 없습니다.")
        if (itemId in state.owned) return CustomizationResult.Failure("이미 보유 중인 아이템입니다.")
        if (item.acquisition != Acquisition.COINS) return CustomizationResult.Failure("코인으로 구매할 수 없는 아이템입니다.")
        if (state.coins < item.price) return CustomizationResult.Failure("코인이 부족합니다.")
        state = state.copy(coins = state.coins - item.price, owned = state.owned + itemId)
        return CustomizationResult.Success
    }
}

object CustomizationData {
    val store: CustomizationRepository = MockCustomizationStore()
}

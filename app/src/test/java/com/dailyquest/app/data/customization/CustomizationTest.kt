package com.dailyquest.app.data.customization

import org.junit.Assert.*
import org.junit.Test

class CustomizationTest {
    @Test fun purchaseIsAtomicAndRejectsDuplicateAndInsufficientFunds() {
        val store = MockCustomizationStore()
        assertEquals(CustomizationResult.Success, store.purchase("top_premium"))
        assertEquals(200, store.state.coins)
        assertTrue("top_premium" in store.state.owned)
        val before = store.state
        assertTrue(store.purchase("top_premium") is CustomizationResult.Failure)
        assertTrue(store.purchase("top_male") is CustomizationResult.Failure)
        assertEquals(before, store.state)
    }
    @Test fun invalidAndRewardPurchasesLeaveStateUntouched() {
        val store = MockCustomizationStore()
        val before = store.state
        listOf("missing", "top_reward", "top_default").forEach {
            assertTrue(store.purchase(it) is CustomizationResult.Failure)
            assertEquals(before, store.state)
        }
    }
    @Test fun previewDoesNotPersistAndUnownedSaveFails() {
        val store = MockCustomizationStore()
        val before = store.state
        val preview = store.preview(before.equipped, "top_premium")
        assertEquals(before, store.state)
        assertTrue(store.save(preview) is CustomizationResult.Failure)
        store.purchase("top_premium")
        assertEquals(CustomizationResult.Success, store.save(preview))
        assertEquals("top_premium", store.state.equipped[AvatarSlot.TOP])
        assertEquals(200, store.state.coins)
    }
    @Test fun incompatibleBodySwitchAndInvalidEquipmentAreRejected() {
        val store = MockCustomizationStore()
        store.purchase("top_male")
        val male = store.preview(store.state.equipped, "top_male")
        store.save(male)
        val before = store.state
        assertThrows(IllegalArgumentException::class.java) { store.preview(male, "body_female") }
        assertTrue(store.save(male + (AvatarSlot.BODY to "body_female")) is CustomizationResult.Failure)
        assertTrue(store.save(male - AvatarSlot.EYES) is CustomizationResult.Failure)
        assertTrue(store.save(male + (AvatarSlot.EYES to "top_default")) is CustomizationResult.Failure)
        assertEquals(before, store.state)
        val defaults = store.preview(male, "top_default")
        assertEquals(CustomizationResult.Success, store.save(store.preview(defaults, "body_female")))
    }
    @Test fun concurrentRepeatedPurchaseChargesOnlyOnce() {
        val store = MockCustomizationStore()
        val workers = List(8) { Thread { store.purchase("top_premium") } }
        workers.forEach { it.start() }
        workers.forEach { it.join() }
        assertEquals(200, store.state.coins)
        assertEquals(1, store.state.owned.count { it == "top_premium" })
    }
}

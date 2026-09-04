package com.aryan.reader.shared

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DeviceLimitPolicyTest {

    @Test
    fun `overlay hidden by default`() {
        assertFalse(shouldShowDeviceLimitOverlay(DeviceLimitReachedState()))
    }

    @Test
    fun `overlay shows when limit reached`() {
        assertTrue(
            shouldShowDeviceLimitOverlay(
                DeviceLimitReachedState(
                    isLimitReached = true,
                    registeredDevices = listOf(
                        DeviceItem("a", "Phone", 1L),
                        DeviceItem("b", "Tablet", 2L),
                    ),
                ),
            ),
        )
    }

    @Test
    fun `overlay hidden after replace clears limit`() {
        val cleared = DeviceLimitReachedState(
            isLimitReached = true,
            registeredDevices = listOf(DeviceItem("a", "Phone", 1L)),
        ).copy(isLimitReached = false, registeredDevices = emptyList())
        assertFalse(shouldShowDeviceLimitOverlay(cleared))
    }
}

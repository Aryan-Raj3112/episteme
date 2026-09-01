package com.aryan.reader.shared.ios

import com.aryan.reader.shared.AccountAuthProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IosAccountPresentationTest {
    @Test
    fun `account state retains Firebase photo URL and provider sync projection`() {
        val account = IosAccountState(
            uid = "ios-user",
            displayName = "Reader",
            email = "reader@example.test",
            photoUrl = "https://example.test/photo.jpg",
            providers = setOf(AccountAuthProvider.APPLE, AccountAuthProvider.GOOGLE),
            googleDriveAuthorized = true,
            hasLoaded = true,
        )

        assertEquals("https://example.test/photo.jpg", account.photoUrl)
        assertTrue(account.canSync)
    }
}

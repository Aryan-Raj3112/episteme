package com.aryan.reader.shared

import kotlin.test.Test
import kotlin.test.assertEquals

class AccountInitialTest {

    @Test
    fun `display name provides the uppercased initial`() {
        val user = UserData(uid = "u", displayName = "aryan", photoUrl = null, email = "a@example.com")

        assertEquals("A", user.accountInitial())
    }

    @Test
    fun `email backs up a missing display name`() {
        val user = UserData(uid = "u", displayName = null, photoUrl = null, email = "reader@example.com")

        assertEquals("R", user.accountInitial())
    }

    @Test
    fun `missing identity falls back to E`() {
        assertEquals("E", UserData(uid = "u", displayName = null, photoUrl = null, email = null).accountInitial())
        assertEquals("E", UserData(uid = "u", displayName = "   ", photoUrl = null, email = null).accountInitial())
    }
}

package com.aryan.reader.desktop

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MacOsKeychainSecretCodecTest {
    @Test
    fun `mac keychain codec stores references and reads the secret`() {
        val runner = RecordingMacSecurityRunner()
        val codec = MacOsKeychainSecretCodec(runner)

        val reference = codec.protect("geminiKeyProtected", "test-secret")
        val restored = codec.unprotect("geminiKeyProtected", reference)

        assertTrue(reference.startsWith("macos-keychain:Episteme.geminiKeyProtected"))
        assertEquals("test-secret", restored)
        assertEquals("/usr/bin/security", runner.commands.first().first())
    }

    @Test
    fun `mac keychain codec deletes the matching account`() {
        val runner = RecordingMacSecurityRunner()
        val codec = MacOsKeychainSecretCodec(runner)

        codec.protect("firebaseRefreshTokenProtected", "refresh-token")
        codec.delete("firebaseRefreshTokenProtected")

        assertTrue(runner.commands.last().contains("delete-generic-password"))
        assertTrue(runner.commands.last().contains("Episteme.firebaseRefreshTokenProtected"))
    }
}

private class RecordingMacSecurityRunner : DesktopSecretCommandRunner {
    val commands = mutableListOf<List<String>>()
    private val values = mutableMapOf<String, String>()

    override fun isExecutableAvailable(command: String): Boolean = true

    override fun run(
        command: List<String>,
        input: String?,
        timeoutMillis: Long
    ): DesktopSecretCommandResult {
        commands += command
        val account = command.valueAfter("-a")
        return when (command.getOrNull(1)) {
            "add-generic-password" -> {
                values[account] = command.valueAfter("-w")
                DesktopSecretCommandResult(0, "", "")
            }
            "find-generic-password" ->
                values[account]?.let { DesktopSecretCommandResult(0, "$it\n", "") }
                    ?: DesktopSecretCommandResult(44, "", "not found")
            "delete-generic-password" -> {
                values.remove(account)
                DesktopSecretCommandResult(0, "", "")
            }
            else -> DesktopSecretCommandResult(1, "", "unexpected command")
        }
    }
}

private fun List<String>.valueAfter(option: String): String {
    val index = indexOf(option)
    return getOrElse(index + 1) { "" }
}

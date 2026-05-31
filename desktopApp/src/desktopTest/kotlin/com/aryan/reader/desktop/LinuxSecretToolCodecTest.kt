package com.aryan.reader.desktop

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LinuxSecretToolCodecTest {
    @Test
    fun `secret tool codec stores looks up and clears secrets by key`() {
        val runner = FakeSecretCommandRunner()
        val codec = LinuxSecretToolCodec(runner)

        assertTrue(codec.isAvailable)
        val reference = codec.protect("firebaseRefreshTokenProtected", "linux_refresh")

        assertEquals("linux_refresh", codec.unprotect("firebaseRefreshTokenProtected", reference))
        codec.delete("firebaseRefreshTokenProtected")
        assertTrue(runner.storedSecrets.isEmpty())
    }

    private class FakeSecretCommandRunner : DesktopSecretCommandRunner {
        val storedSecrets = linkedMapOf<String, String>()

        override fun isExecutableAvailable(command: String): Boolean {
            return command == "secret-tool"
        }

        override fun run(
            command: List<String>,
            input: String?,
            timeoutMillis: Long
        ): DesktopSecretCommandResult {
            return when (command.getOrNull(1)) {
                "--help" -> DesktopSecretCommandResult(0, "usage", "")
                "store" -> {
                    storedSecrets[command.last()] = input.orEmpty()
                    DesktopSecretCommandResult(0, "", "")
                }
                "lookup" -> {
                    val secret = storedSecrets[command.last()]
                    if (secret == null) {
                        DesktopSecretCommandResult(1, "", "not found")
                    } else {
                        DesktopSecretCommandResult(0, "$secret\n", "")
                    }
                }
                "clear" -> {
                    storedSecrets.remove(command.last())
                    DesktopSecretCommandResult(0, "", "")
                }
                else -> DesktopSecretCommandResult(1, "", "unexpected command")
            }
        }
    }
}

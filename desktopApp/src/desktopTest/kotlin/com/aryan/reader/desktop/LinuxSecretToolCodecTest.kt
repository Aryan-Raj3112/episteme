package com.aryan.reader.desktop

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LinuxSecretToolCodecTest {
    @Test
    fun `libsecret codec stores looks up legacy secret tool references and clears secrets by key`() {
        val client = FakeLinuxSecretServiceClient()
        val codec = LinuxLibsecretCodec(client)

        assertTrue(codec.isAvailable)
        val reference = codec.protect("geminiKeyProtected", "linux_gemini_key")

        assertTrue(reference.startsWith("linux-libsecret:"))
        assertEquals("linux_gemini_key", codec.unprotect("geminiKeyProtected", reference))
        assertEquals(
            "linux_gemini_key",
            codec.unprotect("geminiKeyProtected", "secret-tool:Episteme.Reader.geminiKeyProtected")
        )
        codec.delete("geminiKeyProtected")
        assertTrue(client.storedSecrets.isEmpty())
    }

    @Test
    fun `linux secret service codec falls back to secret tool when libsecret is unavailable`() {
        val runner = FakeSecretCommandRunner()
        val codec = LinuxSecretServiceCodec(
            libsecretCodec = LinuxLibsecretCodec(FakeLinuxSecretServiceClient(available = false)),
            secretToolCodec = LinuxSecretToolCodec(runner)
        )

        assertTrue(codec.isAvailable)
        val reference = codec.protect("geminiKeyProtected", "linux_gemini_key")

        assertTrue(reference.startsWith("secret-tool:"))
        assertEquals("linux_gemini_key", codec.unprotect("geminiKeyProtected", reference))
        assertFalse(runner.storedSecrets.isEmpty())
    }

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

    private class FakeLinuxSecretServiceClient(
        private val available: Boolean = true
    ) : LinuxSecretServiceClient {
        val storedSecrets = linkedMapOf<String, String>()

        override val isAvailable: Boolean
            get() = available

        override fun store(key: String, label: String, password: String) {
            check(available) { "libsecret unavailable" }
            storedSecrets[key] = password
        }

        override fun lookup(key: String): String? {
            check(available) { "libsecret unavailable" }
            return storedSecrets[key]
        }

        override fun clear(key: String) {
            if (available) {
                storedSecrets.remove(key)
            }
        }
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

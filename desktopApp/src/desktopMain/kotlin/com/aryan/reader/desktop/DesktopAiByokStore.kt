package com.aryan.reader.desktop

import com.aryan.reader.shared.DEFAULT_CLOUD_TTS_SPEAKER_ID
import com.aryan.reader.shared.GEMINI_CLOUD_TTS_MODEL_ID
import com.aryan.reader.shared.ReaderAiByokSettings
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.win32.StdCallLibrary
import java.io.File
import java.util.Base64
import java.util.Properties

internal class DesktopAiByokStore(
    private val settingsFile: File = defaultSettingsFile(),
    private val secretCodec: DesktopSecretCodec = DesktopSecretCodec.platform()
) {
    val isSecureStorageAvailable: Boolean
        get() = secretCodec.isAvailable

    fun load(): ReaderAiByokSettings {
        if (!settingsFile.exists()) return ReaderAiByokSettings()
        val properties = Properties()
        return runCatching {
            settingsFile.inputStream().use(properties::load)
            val legacyGeminiKey = properties.getProperty(LegacyGeminiKey, "")
            val legacyGroqKey = properties.getProperty(LegacyGroqKey, "")
            val loadedSettings = ReaderAiByokSettings(
                geminiKey = loadSecret(properties, GeminiKey, legacyGeminiKey),
                groqKey = loadSecret(properties, GroqKey, legacyGroqKey),
                useOneModel = properties.getProperty("useOneModel", "true").toBooleanStrictOrNull() ?: true,
                modelForAll = properties.getProperty("modelForAll", ""),
                defineModel = properties.getProperty("defineModel", ""),
                summarizeModel = properties.getProperty("summarizeModel", ""),
                recapModel = properties.getProperty("recapModel", ""),
                ttsModel = properties.getProperty("ttsModel", ""),
                hideReaderAiFeatures = properties.getProperty("hideReaderAiFeatures", "false").toBooleanStrictOrNull() ?: false,
                ttsSpeakerId = properties.getProperty("ttsSpeakerId", DEFAULT_CLOUD_TTS_SPEAKER_ID)
            ).sanitized()
            val settings = if (loadedSettings.geminiKey.isNotBlank() && loadedSettings.ttsModel.isBlank()) {
                loadedSettings.copy(ttsModel = GEMINI_CLOUD_TTS_MODEL_ID)
            } else {
                loadedSettings
            }
            if (secretCodec.isAvailable &&
                (legacyGeminiKey.isNotBlank() || legacyGroqKey.isNotBlank() || settings != loadedSettings)
            ) {
                runCatching { save(settings) }
            }
            settings
        }.getOrDefault(ReaderAiByokSettings())
    }

    fun save(settings: ReaderAiByokSettings) {
        val sanitized = settings.sanitized()
        val properties = Properties().apply {
            setProtectedSecret(GeminiKey, sanitized.geminiKey)
            setProtectedSecret(GroqKey, sanitized.groqKey)
            setProperty("useOneModel", sanitized.useOneModel.toString())
            setProperty("modelForAll", sanitized.modelForAll)
            setProperty("defineModel", sanitized.defineModel)
            setProperty("summarizeModel", sanitized.summarizeModel)
            setProperty("recapModel", sanitized.recapModel)
            setProperty("ttsModel", sanitized.ttsModel)
            setProperty("hideReaderAiFeatures", sanitized.hideReaderAiFeatures.toString())
            setProperty("ttsSpeakerId", sanitized.ttsSpeakerId)
        }
        settingsFile.parentFile?.mkdirs()
        settingsFile.outputStream().use { output ->
            properties.store(output, "Episteme desktop AI keys and models")
        }
    }

    private fun loadSecret(properties: Properties, key: String, legacyPlaintext: String): String {
        val protectedValue = properties.getProperty(key, "")
        val decrypted = protectedValue
            .takeIf { it.isNotBlank() && secretCodec.isAvailable }
            ?.let { runCatching { secretCodec.unprotect(it) }.getOrDefault("") }
            .orEmpty()
        if (decrypted.isNotBlank()) return decrypted
        return legacyPlaintext.takeIf { secretCodec.isAvailable }.orEmpty()
    }

    private fun Properties.setProtectedSecret(key: String, value: String) {
        val trimmed = value.trim()
        if (trimmed.isBlank()) return
        if (secretCodec.isAvailable) {
            setProperty(key, secretCodec.protect(trimmed))
        }
    }

    companion object {
        private const val GeminiKey = "geminiKeyProtected"
        private const val GroqKey = "groqKeyProtected"
        private const val LegacyGeminiKey = "geminiKey"
        private const val LegacyGroqKey = "groqKey"

        fun defaultSettingsFile(): File {
            val baseDir = System.getenv("APPDATA")?.takeIf { it.isNotBlank() }
                ?: File(System.getProperty("user.home"), "AppData/Roaming").absolutePath
            return File(baseDir, "Episteme/ai-byok.properties")
        }
    }
}

internal interface DesktopSecretCodec {
    val isAvailable: Boolean
    fun protect(value: String): String
    fun unprotect(value: String): String

    companion object {
        fun platform(): DesktopSecretCodec {
            return if (System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)) {
                WindowsDpapiSecretCodec
            } else {
                UnavailableDesktopSecretCodec
            }
        }
    }
}

private object UnavailableDesktopSecretCodec : DesktopSecretCodec {
    override val isAvailable: Boolean = false
    override fun protect(value: String): String = ""
    override fun unprotect(value: String): String = ""
}

private object WindowsDpapiSecretCodec : DesktopSecretCodec {
    private const val Prefix = "dpapi:"

    override val isAvailable: Boolean
        get() = runCatching {
            Crypt32.INSTANCE
            Kernel32.INSTANCE
            true
        }.getOrDefault(false)

    override fun protect(value: String): String {
        val input = DataBlob(value.toByteArray(Charsets.UTF_8))
        val output = DataBlob()
        val ok = Crypt32.INSTANCE.CryptProtectData(input, null, null, null, null, 0, output)
        if (!ok) throw IllegalStateException("Windows DPAPI protect failed: ${Native.getLastError()}")
        return try {
            Prefix + Base64.getEncoder().encodeToString(output.toByteArray())
        } finally {
            output.free()
        }
    }

    override fun unprotect(value: String): String {
        if (!value.startsWith(Prefix)) return ""
        val encrypted = Base64.getDecoder().decode(value.removePrefix(Prefix))
        val input = DataBlob(encrypted)
        val output = DataBlob()
        val ok = Crypt32.INSTANCE.CryptUnprotectData(input, null, null, null, null, 0, output)
        if (!ok) return ""
        return try {
            String(output.toByteArray(), Charsets.UTF_8)
        } finally {
            output.free()
        }
    }
}

@Structure.FieldOrder("cbData", "pbData")
private open class DataBlob() : Structure() {
    @JvmField
    var cbData: Int = 0

    @JvmField
    var pbData: Pointer? = null

    private var memory: Memory? = null

    constructor(bytes: ByteArray) : this() {
        cbData = bytes.size
        memory = Memory(bytes.size.toLong()).also { allocated ->
            allocated.write(0, bytes, 0, bytes.size)
            pbData = allocated
        }
    }

    fun toByteArray(): ByteArray {
        read()
        return pbData?.getByteArray(0, cbData) ?: ByteArray(0)
    }

    fun free() {
        pbData?.let { Kernel32.INSTANCE.LocalFree(it) }
        pbData = null
        cbData = 0
    }
}

private interface Crypt32 : StdCallLibrary {
    fun CryptProtectData(
        pDataIn: DataBlob,
        szDataDescr: String?,
        pOptionalEntropy: DataBlob?,
        pvReserved: Pointer?,
        pPromptStruct: Pointer?,
        dwFlags: Int,
        pDataOut: DataBlob
    ): Boolean

    fun CryptUnprotectData(
        pDataIn: DataBlob,
        ppszDataDescr: Pointer?,
        pOptionalEntropy: DataBlob?,
        pvReserved: Pointer?,
        pPromptStruct: Pointer?,
        dwFlags: Int,
        pDataOut: DataBlob
    ): Boolean

    companion object {
        val INSTANCE: Crypt32 by lazy {
            Native.load("Crypt32", Crypt32::class.java) as Crypt32
        }
    }
}

private interface Kernel32 : StdCallLibrary {
    fun LocalFree(hMem: Pointer?): Pointer?

    companion object {
        val INSTANCE: Kernel32 by lazy {
            Native.load("Kernel32", Kernel32::class.java) as Kernel32
        }
    }
}

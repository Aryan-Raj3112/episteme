package com.aryan.reader.desktop

import com.aryan.reader.shared.DEFAULT_CLOUD_TTS_SPEAKER_ID
import com.aryan.reader.shared.GEMINI_CLOUD_TTS_MODEL_ID
import com.aryan.reader.shared.ReaderAiByokSettings
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.WString
import com.sun.jna.ptr.PointerByReference
import com.sun.jna.win32.StdCallLibrary
import java.io.File
import java.util.Base64
import java.util.Properties

private const val WINDOWS_CRED_TYPE_GENERIC = 1
private const val WINDOWS_CRED_PERSIST_LOCAL_MACHINE = 2
private const val WINDOWS_ERROR_NOT_FOUND = 1168

internal class DesktopAiByokStore(
    private val settingsFile: File = defaultSettingsFile(),
    private val secretCodec: DesktopSecretCodec = DesktopSecretCodec.platform()
) {
    val isSecureStorageAvailable: Boolean
        get() = secretCodec.isAvailable.also { available ->
            logDesktopTts("settings_secure_available codec=${secretCodec.name} available=$available")
        }

    fun load(): ReaderAiByokSettings {
        val settingsFileExists = settingsFile.exists()
        logDesktopTts(
            "settings_load_start file=\"${settingsFile.absolutePath.desktopTtsPreview(220)}\" " +
                "exists=$settingsFileExists secureStorage=${if (settingsFileExists) "checking" else "skipped"}"
        )
        if (!settingsFileExists) {
            logDesktopTts("settings_load_empty reason=file_missing")
            return ReaderAiByokSettings()
        }
        val secureStorageAvailable = secretCodec.isAvailable
        logDesktopTts("settings_load_secure_storage codec=${secretCodec.name} available=$secureStorageAvailable")
        val properties = Properties()
        return runCatching {
            settingsFile.inputStream().use(properties::load)
            val legacyGeminiKey = properties.getProperty(LegacyGeminiKey, "")
            val legacyGroqKey = properties.getProperty(LegacyGroqKey, "")
            val loadedSettings = ReaderAiByokSettings(
                geminiKey = loadSecret(properties, GeminiKey, legacyGeminiKey, secureStorageAvailable),
                groqKey = loadSecret(properties, GroqKey, legacyGroqKey, secureStorageAvailable),
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
            if (secureStorageAvailable &&
                (legacyGeminiKey.isNotBlank() || legacyGroqKey.isNotBlank() || settings != loadedSettings)
            ) {
                logDesktopTts(
                    "settings_load_migrate legacyGemini=${legacyGeminiKey.isNotBlank()} " +
                        "legacyGroq=${legacyGroqKey.isNotBlank()} autoTtsModel=${settings != loadedSettings}"
                )
                runCatching { save(settings) }
            }
            logDesktopTts(
                "settings_load_complete geminiKey=${settings.geminiKey.isNotBlank()} groqKey=${settings.groqKey.isNotBlank()} " +
                    "ttsModel=\"${settings.ttsModel.desktopTtsPreview()}\" cloudAvailable=${settings.isCloudTtsAvailable}"
            )
            settings
        }.getOrElse { error ->
            logDesktopTts("settings_load_failed error=\"${error.desktopTtsSummary()}\"")
            ReaderAiByokSettings()
        }
    }

    fun save(settings: ReaderAiByokSettings) {
        val sanitized = settings.sanitized()
        logDesktopTts(
            "settings_save_start file=\"${settingsFile.absolutePath.desktopTtsPreview(220)}\" " +
                "secureStorage=${secretCodec.isAvailable} geminiKey=${sanitized.geminiKey.isNotBlank()} " +
                "groqKey=${sanitized.groqKey.isNotBlank()} ttsModel=\"${sanitized.ttsModel.desktopTtsPreview()}\""
        )
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
        logDesktopTts(
            "settings_save_complete geminiProtected=${properties.getProperty(GeminiKey, "").isNotBlank()} " +
                "groqProtected=${properties.getProperty(GroqKey, "").isNotBlank()}"
        )
    }

    private fun loadSecret(
        properties: Properties,
        key: String,
        legacyPlaintext: String,
        secureStorageAvailable: Boolean
    ): String {
        val protectedValue = properties.getProperty(key, "")
        val decrypted = protectedValue
            .takeIf { it.isNotBlank() }
            ?.let {
                runCatching { secretCodec.unprotect(key, it) }
                    .onFailure { error -> logDesktopTts("settings_secret_unprotect_failed key=$key error=\"${error.desktopTtsSummary()}\"") }
                    .getOrDefault("")
            }
            .orEmpty()
        if (decrypted.isNotBlank()) return decrypted
        return legacyPlaintext.takeIf { secureStorageAvailable }.orEmpty()
    }

    private fun Properties.setProtectedSecret(key: String, value: String) {
        val trimmed = value.trim()
        if (trimmed.isBlank()) {
            secretCodec.delete(key)
            return
        }
        runCatching { secretCodec.protect(key, trimmed) }
            .onSuccess { protectedValue ->
                if (protectedValue.isBlank()) {
                    logDesktopTts("settings_secret_protect_empty key=$key codec=${secretCodec.name}")
                } else {
                    setProperty(key, protectedValue)
                    logDesktopTts("settings_secret_protect_success key=$key codec=${secretCodec.name} prefix=\"${protectedValue.substringBefore(':', protectedValue)}\"")
                }
            }
            .onFailure { error ->
                logDesktopTts("settings_secret_protect_failed key=$key codec=${secretCodec.name} error=\"${error.desktopTtsSummary()}\"")
            }
    }

    companion object {
        private const val GeminiKey = "geminiKeyProtected"
        private const val GroqKey = "groqKeyProtected"
        private const val LegacyGeminiKey = "geminiKey"
        private const val LegacyGroqKey = "groqKey"

        fun defaultSettingsFile(): File {
            return File(desktopUserConfigRoot(), "ai-byok.properties")
        }
    }
}

internal interface DesktopSecretCodec {
    val name: String get() = this::class.java.simpleName.ifBlank { "DesktopSecretCodec" }
    val isAvailable: Boolean
    fun protect(value: String): String
    fun unprotect(value: String): String
    fun protect(keyName: String, value: String): String = protect(value)
    fun unprotect(keyName: String, value: String): String = unprotect(value)
    fun delete(keyName: String) = Unit

    companion object {
        fun platform(): DesktopSecretCodec {
            val osName = System.getProperty("os.name").orEmpty()
            val codec = if (osName.startsWith("Windows", ignoreCase = true)) {
                WindowsSecretCodec
            } else {
                UnavailableDesktopSecretCodec
            }
            logDesktopTts("settings_platform os=\"${osName.desktopTtsPreview()}\" codec=${codec.name}")
            return codec
        }
    }
}

private object UnavailableDesktopSecretCodec : DesktopSecretCodec {
    override val name: String = "unavailable"
    override val isAvailable: Boolean = false
    override fun protect(value: String): String {
        throw IllegalStateException("Secure key storage is unavailable on this operating system.")
    }
    override fun unprotect(value: String): String = ""
}

private object WindowsSecretCodec : DesktopSecretCodec {
    override val name: String = "windows"

    override val isAvailable: Boolean
        get() {
            val wincred = WindowsCredentialSecretCodec.isAvailable
            val dpapi = WindowsDpapiSecretCodec.isAvailable
            logDesktopTts("settings_windows_available wincred=$wincred dpapi=$dpapi")
            return wincred || dpapi
        }

    override fun protect(value: String): String {
        return protect("secret", value)
    }

    override fun unprotect(value: String): String {
        return unprotect("secret", value)
    }

    override fun protect(keyName: String, value: String): String {
        val wincredFailure = runCatching { return WindowsCredentialSecretCodec.protect(keyName, value) }
            .exceptionOrNull()
            ?.also { error -> logDesktopTts("settings_wincred_protect_failed key=$keyName error=\"${error.desktopTtsSummary()}\"") }
        val dpapiFailure = runCatching { return WindowsDpapiSecretCodec.protect(value) }
            .exceptionOrNull()
            ?.also { error -> logDesktopTts("settings_dpapi_protect_failed key=$keyName error=\"${error.desktopTtsSummary()}\"") }
        throw IllegalStateException(
            "No Windows secure key store write succeeded. " +
                "Credential Manager: ${wincredFailure?.desktopTtsSummary() ?: "not attempted"}; " +
                "DPAPI: ${dpapiFailure?.desktopTtsSummary() ?: "not attempted"}"
        )
    }

    override fun unprotect(keyName: String, value: String): String {
        return when {
            value.startsWith(WindowsCredentialSecretCodec.Prefix) -> WindowsCredentialSecretCodec.unprotect(keyName, value)
            value.startsWith(WindowsDpapiSecretCodec.Prefix) -> WindowsDpapiSecretCodec.unprotect(value)
            else -> ""
        }
    }

    override fun delete(keyName: String) {
        if (WindowsCredentialSecretCodec.isAvailable) WindowsCredentialSecretCodec.delete(keyName)
    }
}

private object WindowsCredentialSecretCodec : DesktopSecretCodec {
    override val name: String = "wincred"

    const val Prefix = "wincred:"

    override val isAvailable: Boolean by lazy {
        val probeKey = "probe"
        val probe = "episteme-wincred-probe"
        logDesktopTts("settings_wincred_probe_start")
        runCatching {
            val reference = protect(probeKey, probe)
            val restored = unprotect(probeKey, reference)
            val matches = restored == probe
            logDesktopTts(
                "settings_wincred_probe_result matches=$matches referencePrefix=\"${reference.substringBefore(':', reference)}\" " +
                    "restoredChars=${restored.length}"
            )
            matches
        }.onFailure { error ->
            logDesktopTts("settings_wincred_unavailable error=\"${error.desktopTtsSummary()}\"")
        }.also {
            runCatching { delete(probeKey) }
        }.getOrDefault(false)
    }

    override fun protect(value: String): String {
        return protect("secret", value)
    }

    override fun unprotect(value: String): String {
        return unprotect("secret", value)
    }

    override fun protect(keyName: String, value: String): String {
        val target = credentialTarget(keyName)
        logDesktopTts("settings_wincred_write_start key=$keyName target=\"$target\" valueChars=${value.length}")
        val credential = WindowsCredential(target, value)
        credential.write()
        val ok = Advapi32.INSTANCE.CredWriteW(credential, 0)
        val errorCode = Native.getLastError()
        logDesktopTts("settings_wincred_write_result key=$keyName ok=$ok error=$errorCode")
        if (!ok) throw IllegalStateException("Windows Credential Manager write failed: $errorCode")
        return Prefix + target
    }

    override fun unprotect(keyName: String, value: String): String {
        if (!value.startsWith(Prefix)) return ""
        val target = value.removePrefix(Prefix).ifBlank { credentialTarget(keyName) }
        logDesktopTts("settings_wincred_read_start key=$keyName target=\"$target\"")
        val credentialPointer = PointerByReference()
        val ok = Advapi32.INSTANCE.CredReadW(WString(target), WINDOWS_CRED_TYPE_GENERIC, 0, credentialPointer)
        val errorCode = Native.getLastError()
        logDesktopTts("settings_wincred_read_result key=$keyName ok=$ok error=$errorCode hasPointer=${credentialPointer.value != null}")
        if (!ok) throw IllegalStateException("Windows Credential Manager read failed: $errorCode")
        val pointer = credentialPointer.value ?: return ""
        return try {
            val credential = WindowsCredential(pointer)
            val blobPointer = credential.CredentialBlob ?: return ""
            logDesktopTts("settings_wincred_read_blob key=$keyName bytes=${credential.CredentialBlobSize}")
            String(blobPointer.getByteArray(0, credential.CredentialBlobSize), Charsets.UTF_8)
        } finally {
            Advapi32.INSTANCE.CredFree(pointer)
        }
    }

    override fun delete(keyName: String) {
        val ok = Advapi32.INSTANCE.CredDeleteW(WString(credentialTarget(keyName)), WINDOWS_CRED_TYPE_GENERIC, 0)
        val errorCode = Native.getLastError()
        logDesktopTts("settings_wincred_delete_result key=$keyName ok=$ok error=$errorCode")
        if (!ok && errorCode != WINDOWS_ERROR_NOT_FOUND) {
            logDesktopTts("settings_wincred_delete_failed key=$keyName error=$errorCode")
        }
    }

    private fun credentialTarget(keyName: String): String {
        return "Episteme.Reader.AI.$keyName"
    }

    private interface Advapi32 : StdCallLibrary {
        fun CredWriteW(credential: WindowsCredential, flags: Int): Boolean
        fun CredReadW(targetName: WString, type: Int, flags: Int, credential: PointerByReference): Boolean
        fun CredDeleteW(targetName: WString, type: Int, flags: Int): Boolean
        fun CredFree(buffer: Pointer?)

        companion object {
            val INSTANCE: Advapi32 by lazy {
                Native.load("Advapi32", Advapi32::class.java) as Advapi32
            }
        }
    }
}

private object WindowsDpapiSecretCodec : DesktopSecretCodec {
    override val name: String = "dpapi"

    const val Prefix = "dpapi:"

    override val isAvailable: Boolean by lazy {
        logDesktopTts("settings_dpapi_probe_start")
        runCatching {
            Crypt32.INSTANCE
            Kernel32.INSTANCE
            val probe = "episteme-dpapi-probe"
            val encrypted = protect(probe)
            val restored = unprotect(encrypted)
            val matches = restored == probe
            logDesktopTts("settings_dpapi_probe_result matches=$matches encryptedChars=${encrypted.length} restoredChars=${restored.length}")
            matches
        }.onFailure { error ->
            logDesktopTts("settings_dpapi_unavailable error=\"${error.desktopTtsSummary()}\"")
        }.getOrDefault(false)
    }

    override fun protect(value: String): String {
        val input = DataBlob(value.toByteArray(Charsets.UTF_8))
        val output = DataBlob()
        logDesktopTts("settings_dpapi_protect_start bytes=${value.toByteArray(Charsets.UTF_8).size}")
        val ok = Crypt32.INSTANCE.CryptProtectData(input, null, null, null, null, 0, output)
        val errorCode = Native.getLastError()
        logDesktopTts("settings_dpapi_protect_result ok=$ok error=$errorCode")
        if (!ok) throw IllegalStateException("Windows DPAPI protect failed: $errorCode")
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
        logDesktopTts("settings_dpapi_unprotect_start bytes=${encrypted.size}")
        val ok = Crypt32.INSTANCE.CryptUnprotectData(input, null, null, null, null, 0, output)
        val errorCode = Native.getLastError()
        logDesktopTts("settings_dpapi_unprotect_result ok=$ok error=$errorCode")
        if (!ok) throw IllegalStateException("Windows DPAPI unprotect failed: $errorCode")
        return try {
            String(output.toByteArray(), Charsets.UTF_8)
        } finally {
            output.free()
        }
    }
}

@Structure.FieldOrder("dwLowDateTime", "dwHighDateTime")
internal class WindowsFileTime : Structure() {
    @JvmField
    var dwLowDateTime: Int = 0

    @JvmField
    var dwHighDateTime: Int = 0
}

@Structure.FieldOrder(
    "Flags",
    "Type",
    "TargetName",
    "Comment",
    "LastWritten",
    "CredentialBlobSize",
    "CredentialBlob",
    "Persist",
    "AttributeCount",
    "Attributes",
    "TargetAlias",
    "UserName"
)
internal open class WindowsCredential : Structure {
    @JvmField
    var Flags: Int = 0

    @JvmField
    var Type: Int = WINDOWS_CRED_TYPE_GENERIC

    @JvmField
    var TargetName: WString? = null

    @JvmField
    var Comment: WString? = null

    @JvmField
    var LastWritten: WindowsFileTime = WindowsFileTime()

    @JvmField
    var CredentialBlobSize: Int = 0

    @JvmField
    var CredentialBlob: Pointer? = null

    @JvmField
    var Persist: Int = WINDOWS_CRED_PERSIST_LOCAL_MACHINE

    @JvmField
    var AttributeCount: Int = 0

    @JvmField
    var Attributes: Pointer? = null

    @JvmField
    var TargetAlias: WString? = null

    @JvmField
    var UserName: WString? = WString("Episteme")

    private var blobMemory: Memory? = null

    constructor() : super()

    constructor(pointer: Pointer) : super(pointer) {
        read()
    }

    constructor(target: String, secret: String) : super() {
        val bytes = secret.toByteArray(Charsets.UTF_8)
        TargetName = WString(target)
        CredentialBlobSize = bytes.size
        blobMemory = Memory(bytes.size.toLong()).also { memory ->
            memory.write(0, bytes, 0, bytes.size)
            CredentialBlob = memory
        }
    }
}

@Structure.FieldOrder("cbData", "pbData")
internal open class DataBlob() : Structure() {
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

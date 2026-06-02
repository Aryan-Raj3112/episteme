package com.aryan.reader.desktop

import com.aryan.reader.shared.DEFAULT_CLOUD_TTS_SPEAKER_ID
import com.aryan.reader.shared.GEMINI_CLOUD_TTS_MODEL_ID
import com.aryan.reader.shared.ReaderAiByokSettings
import com.sun.jna.Library
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
import java.util.concurrent.TimeUnit

private const val WINDOWS_CRED_TYPE_GENERIC = 1
private const val WINDOWS_CRED_PERSIST_LOCAL_MACHINE = 2
private const val WINDOWS_ERROR_NOT_FOUND = 1168
private const val LINUX_SECRET_SCHEMA_DONT_MATCH_NAME = 2
private const val LINUX_SECRET_SCHEMA_ATTRIBUTE_STRING = 0
private const val LINUX_SECRET_SCHEMA_NAME = "com.aryan.reader.Secret"
private const val LINUX_SECRET_COLLECTION_DEFAULT = "default"
private const val LINUX_SECRET_SERVICE_APPLICATION_ATTRIBUTE = "application"
private const val LINUX_SECRET_SERVICE_APPLICATION_VALUE = "Episteme.Reader"
private const val LINUX_SECRET_SERVICE_KEY_ATTRIBUTE = "key"
private const val LINUX_LIBSECRET_PREFIX = "linux-libsecret:"
private const val LINUX_SECRET_TOOL_PREFIX = "secret-tool:"

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
            }.toDesktopPersistableAiSettings()
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
        val sanitized = settings.toDesktopPersistableAiSettings()
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
        settingsFile.storePropertiesAtomically(properties, "Episteme desktop AI keys and models")
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
            val codec = when {
                osName.startsWith("Windows", ignoreCase = true) -> WindowsSecretCodec
                osName.contains("Linux", ignoreCase = true) -> LinuxSecretServiceCodec()
                else -> UnavailableDesktopSecretCodec
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

internal data class DesktopSecretCommandResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String
) {
    val isSuccess: Boolean get() = exitCode == 0
    val errorSummary: String
        get() = stderr.ifBlank { stdout }.desktopTtsPreview(240).ifBlank { "exit code $exitCode" }
}

internal interface DesktopSecretCommandRunner {
    fun isExecutableAvailable(command: String): Boolean
    fun run(command: List<String>, input: String? = null, timeoutMillis: Long = 5_000L): DesktopSecretCommandResult
}

private object DesktopProcessSecretCommandRunner : DesktopSecretCommandRunner {
    override fun isExecutableAvailable(command: String): Boolean {
        val path = System.getenv("PATH").orEmpty()
        return path.split(File.pathSeparator)
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .any { directory ->
                File(directory, command).let { it.isFile && it.canExecute() }
            }
    }

    override fun run(command: List<String>, input: String?, timeoutMillis: Long): DesktopSecretCommandResult {
        require(command.isNotEmpty()) { "Secret command cannot be empty." }
        val process = ProcessBuilder(command).start()
        input?.let { value ->
            process.outputStream.use { output ->
                output.write(value.toByteArray(Charsets.UTF_8))
            }
        } ?: process.outputStream.close()

        val completed = process.waitFor(timeoutMillis, TimeUnit.MILLISECONDS)
        if (!completed) {
            process.destroyForcibly()
            throw IllegalStateException("Timed out waiting for ${command.first()} secure storage command.")
        }
        return DesktopSecretCommandResult(
            exitCode = process.exitValue(),
            stdout = process.inputStream.readBytes().toString(Charsets.UTF_8),
            stderr = process.errorStream.readBytes().toString(Charsets.UTF_8)
        )
    }
}

internal class LinuxSecretServiceCodec(
    private val libsecretCodec: DesktopSecretCodec = LinuxLibsecretCodec(),
    private val secretToolCodec: DesktopSecretCodec = LinuxSecretToolCodec()
) : DesktopSecretCodec {
    private val codecs: List<DesktopSecretCodec> = listOf(libsecretCodec, secretToolCodec)

    private val selectedCodec: DesktopSecretCodec? by lazy {
        codecs.firstOrNull { codec ->
            runCatching { codec.isAvailable }
                .onFailure { error ->
                    logDesktopTts("settings_linux_secret_service_probe_failed codec=${codec.name} error=\"${error.desktopTtsSummary()}\"")
                }
                .getOrDefault(false)
        }
    }

    override val name: String = "linux-secret-service"

    override val isAvailable: Boolean
        get() = selectedCodec != null

    override fun protect(value: String): String {
        return protect("secret", value)
    }

    override fun unprotect(value: String): String {
        return unprotect("secret", value)
    }

    override fun protect(keyName: String, value: String): String {
        val codec = selectedCodec ?: throw IllegalStateException(
            "Linux Secret Service is unavailable. Install gnome-keyring or another Secret Service provider."
        )
        return codec.protect(keyName, value)
    }

    override fun unprotect(keyName: String, value: String): String {
        val orderedCodecs = when {
            value.startsWith(LINUX_LIBSECRET_PREFIX) -> listOf(libsecretCodec, secretToolCodec)
            value.startsWith(LINUX_SECRET_TOOL_PREFIX) -> listOf(libsecretCodec, secretToolCodec)
            else -> codecs
        }
        for (codec in orderedCodecs) {
            val secret = runCatching {
                if (!codec.isAvailable) "" else codec.unprotect(keyName, value)
            }.onFailure { error ->
                logDesktopTts(
                    "settings_linux_secret_service_read_failed codec=${codec.name} key=$keyName " +
                        "error=\"${error.desktopTtsSummary()}\""
                )
            }.getOrDefault("")
            if (secret.isNotBlank()) return secret
        }
        return ""
    }

    override fun delete(keyName: String) {
        codecs.forEach { codec ->
            runCatching { codec.delete(keyName) }
                .onFailure { error ->
                    logDesktopTts(
                        "settings_linux_secret_service_delete_failed codec=${codec.name} key=$keyName " +
                            "error=\"${error.desktopTtsSummary()}\""
                    )
                }
        }
    }
}

internal interface LinuxSecretServiceClient {
    val isAvailable: Boolean
    fun store(key: String, label: String, password: String)
    fun lookup(key: String): String?
    fun clear(key: String)
}

internal class LinuxLibsecretCodec(
    private val client: LinuxSecretServiceClient = JnaLinuxSecretServiceClient
) : DesktopSecretCodec {
    override val name: String = "linux-libsecret"

    override val isAvailable: Boolean by lazy {
        val available = if (!client.isAvailable) {
            false
        } else {
            val probeKey = linuxSecretKey("probe")
            val probeSecret = "episteme-linux-libsecret-probe"
            runCatching {
                client.store(probeKey, "Episteme secure storage probe", probeSecret)
                client.lookup(probeKey) == probeSecret
            }.onFailure { error ->
                logDesktopTts("settings_linux_libsecret_unavailable error=\"${error.desktopTtsSummary()}\"")
            }.also {
                runCatching { client.clear(probeKey) }
            }.getOrDefault(false)
        }
        logDesktopTts("settings_linux_libsecret_available available=$available")
        available
    }

    override fun protect(value: String): String {
        return protect("secret", value)
    }

    override fun unprotect(value: String): String {
        return unprotect("secret", value)
    }

    override fun protect(keyName: String, value: String): String {
        if (!isAvailable) {
            throw IllegalStateException(
                "Linux Secret Service is unavailable. Install gnome-keyring or another Secret Service provider."
            )
        }
        val key = linuxSecretKey(keyName)
        logDesktopTts("settings_linux_libsecret_write_start key=$keyName valueChars=${value.length}")
        client.store(key, "Episteme $keyName", value)
        logDesktopTts("settings_linux_libsecret_write_result key=$keyName")
        return LINUX_LIBSECRET_PREFIX + key
    }

    override fun unprotect(keyName: String, value: String): String {
        if (!isAvailable) return ""
        val key = linuxSecretReferenceKey(keyName, value)
        logDesktopTts("settings_linux_libsecret_read_start key=$keyName")
        val secret = client.lookup(key).orEmpty()
        logDesktopTts("settings_linux_libsecret_read_result key=$keyName chars=${secret.length}")
        return secret
    }

    override fun delete(keyName: String) {
        if (!client.isAvailable) return
        val key = linuxSecretKey(keyName)
        runCatching { client.clear(key) }
            .onFailure { error ->
                logDesktopTts("settings_linux_libsecret_delete_failed key=$keyName error=\"${error.desktopTtsSummary()}\"")
            }
    }
}

private object JnaLinuxSecretServiceClient : LinuxSecretServiceClient {
    override val isAvailable: Boolean by lazy {
        runCatching {
            LinuxLibsecretNative.INSTANCE
            LinuxGlibNative.INSTANCE
            true
        }.onFailure { error ->
            logDesktopTts("settings_linux_libsecret_load_failed error=\"${error.desktopTtsSummary()}\"")
        }.getOrDefault(false)
    }

    private val schema: Pointer by lazy {
        LinuxLibsecretNative.INSTANCE.secret_schema_new(
            LINUX_SECRET_SCHEMA_NAME,
            LINUX_SECRET_SCHEMA_DONT_MATCH_NAME,
            LINUX_SECRET_SERVICE_APPLICATION_ATTRIBUTE,
            LINUX_SECRET_SCHEMA_ATTRIBUTE_STRING,
            LINUX_SECRET_SERVICE_KEY_ATTRIBUTE,
            LINUX_SECRET_SCHEMA_ATTRIBUTE_STRING,
            null
        ) ?: throw IllegalStateException("Linux Secret Service schema creation failed.")
    }

    override fun store(key: String, label: String, password: String) {
        val error = PointerByReference()
        val stored = LinuxLibsecretNative.INSTANCE.secret_password_store_sync(
            schema,
            LINUX_SECRET_COLLECTION_DEFAULT,
            label,
            password,
            null,
            error,
            LINUX_SECRET_SERVICE_APPLICATION_ATTRIBUTE,
            LINUX_SECRET_SERVICE_APPLICATION_VALUE,
            LINUX_SECRET_SERVICE_KEY_ATTRIBUTE,
            key,
            null
        )
        takeLibsecretError(error)?.let { message ->
            throw IllegalStateException("Linux Secret Service write failed: $message")
        }
        if (!stored) {
            throw IllegalStateException("Linux Secret Service write failed: libsecret returned false.")
        }
    }

    override fun lookup(key: String): String? {
        val error = PointerByReference()
        val passwordPointer = LinuxLibsecretNative.INSTANCE.secret_password_lookup_sync(
            schema,
            null,
            error,
            LINUX_SECRET_SERVICE_APPLICATION_ATTRIBUTE,
            LINUX_SECRET_SERVICE_APPLICATION_VALUE,
            LINUX_SECRET_SERVICE_KEY_ATTRIBUTE,
            key,
            null
        )
        val errorMessage = takeLibsecretError(error)
        if (errorMessage != null) {
            passwordPointer?.let { pointer -> LinuxLibsecretNative.INSTANCE.secret_password_free(pointer) }
            throw IllegalStateException("Linux Secret Service read failed: $errorMessage")
        }
        return passwordPointer?.let { pointer ->
            try {
                pointer.getString(0, Charsets.UTF_8.name())
            } finally {
                LinuxLibsecretNative.INSTANCE.secret_password_free(pointer)
            }
        }
    }

    override fun clear(key: String) {
        val error = PointerByReference()
        LinuxLibsecretNative.INSTANCE.secret_password_clear_sync(
            schema,
            null,
            error,
            LINUX_SECRET_SERVICE_APPLICATION_ATTRIBUTE,
            LINUX_SECRET_SERVICE_APPLICATION_VALUE,
            LINUX_SECRET_SERVICE_KEY_ATTRIBUTE,
            key,
            null
        )
        takeLibsecretError(error)?.let { message ->
            throw IllegalStateException("Linux Secret Service delete failed: $message")
        }
    }

    private fun takeLibsecretError(error: PointerByReference): String? {
        val errorPointer = error.value ?: return null
        return try {
            LinuxGError(errorPointer).message
                ?.getString(0, Charsets.UTF_8.name())
                ?.ifBlank { null }
                ?: "unknown libsecret error"
        } finally {
            LinuxGlibNative.INSTANCE.g_error_free(errorPointer)
        }
    }
}

private interface LinuxLibsecretNative : Library {
    fun secret_schema_new(name: String, flags: Int, vararg attributes: Any?): Pointer?

    fun secret_password_store_sync(
        schema: Pointer,
        collection: String,
        label: String,
        password: String,
        cancellable: Pointer?,
        error: PointerByReference,
        vararg attributes: Any?
    ): Boolean

    fun secret_password_lookup_sync(
        schema: Pointer,
        cancellable: Pointer?,
        error: PointerByReference,
        vararg attributes: Any?
    ): Pointer?

    fun secret_password_clear_sync(
        schema: Pointer,
        cancellable: Pointer?,
        error: PointerByReference,
        vararg attributes: Any?
    ): Boolean

    fun secret_password_free(password: Pointer?)

    companion object {
        val INSTANCE: LinuxLibsecretNative by lazy {
            loadLinuxNativeLibrary(
                LinuxLibsecretNative::class.java,
                "secret-1",
                "libsecret-1.so.0"
            )
        }
    }
}

private interface LinuxGlibNative : Library {
    fun g_error_free(error: Pointer?)

    companion object {
        val INSTANCE: LinuxGlibNative by lazy {
            loadLinuxNativeLibrary(
                LinuxGlibNative::class.java,
                "glib-2.0",
                "libglib-2.0.so.0"
            )
        }
    }
}

@Structure.FieldOrder("domain", "code", "message")
internal class LinuxGError(pointer: Pointer) : Structure(pointer) {
    @JvmField
    var domain: Int = 0

    @JvmField
    var code: Int = 0

    @JvmField
    var message: Pointer? = null

    init {
        read()
    }
}

private fun <T : Library> loadLinuxNativeLibrary(type: Class<T>, vararg names: String): T {
    var lastError: Throwable? = null
    for (name in names) {
        val loaded = runCatching { Native.load(name, type) as T }
            .onFailure { error -> lastError = error }
            .getOrNull()
        if (loaded != null) return loaded
    }
    throw IllegalStateException("Could not load Linux native library ${names.joinToString(" or ")}.", lastError)
}

internal class LinuxSecretToolCodec(
    private val commandRunner: DesktopSecretCommandRunner = DesktopProcessSecretCommandRunner
) : DesktopSecretCodec {
    override val name: String = "linux-secret-tool"

    override val isAvailable: Boolean by lazy {
        val available = commandRunner.isExecutableAvailable(SecretToolCommand) &&
            runCatching {
                commandRunner.run(listOf(SecretToolCommand, "--help"), timeoutMillis = 3_000L).isSuccess
            }.getOrDefault(false)
        logDesktopTts("settings_linux_secret_tool_available available=$available")
        available
    }

    override fun protect(value: String): String {
        return protect("secret", value)
    }

    override fun unprotect(value: String): String {
        return unprotect("secret", value)
    }

    override fun protect(keyName: String, value: String): String {
        if (!isAvailable) {
            throw IllegalStateException(
                "Linux Secret Service is unavailable. Install libsecret-tools and make sure a desktop keyring is running."
            )
        }
        val key = linuxSecretKey(keyName)
        logDesktopTts("settings_linux_secret_tool_write_start key=$keyName valueChars=${value.length}")
        val result = commandRunner.run(
            command = listOf(
                SecretToolCommand,
                "store",
                "--label",
                "Episteme $keyName",
                LINUX_SECRET_SERVICE_APPLICATION_ATTRIBUTE,
                LINUX_SECRET_SERVICE_APPLICATION_VALUE,
                LINUX_SECRET_SERVICE_KEY_ATTRIBUTE,
                key
            ),
            input = value,
            timeoutMillis = 15_000L
        )
        logDesktopTts("settings_linux_secret_tool_write_result key=$keyName exit=${result.exitCode}")
        if (!result.isSuccess) {
            throw IllegalStateException("Linux Secret Service write failed: ${result.errorSummary}")
        }
        return LINUX_SECRET_TOOL_PREFIX + key
    }

    override fun unprotect(keyName: String, value: String): String {
        if (!isAvailable) return ""
        val key = linuxSecretReferenceKey(keyName, value)
        logDesktopTts("settings_linux_secret_tool_read_start key=$keyName")
        val result = commandRunner.run(
            command = listOf(
                SecretToolCommand,
                "lookup",
                LINUX_SECRET_SERVICE_APPLICATION_ATTRIBUTE,
                LINUX_SECRET_SERVICE_APPLICATION_VALUE,
                LINUX_SECRET_SERVICE_KEY_ATTRIBUTE,
                key
            ),
            timeoutMillis = 8_000L
        )
        logDesktopTts("settings_linux_secret_tool_read_result key=$keyName exit=${result.exitCode} chars=${result.stdout.length}")
        if (!result.isSuccess) {
            throw IllegalStateException("Linux Secret Service read failed: ${result.errorSummary}")
        }
        return result.stdout.trimEnd('\r', '\n')
    }

    override fun delete(keyName: String) {
        val key = linuxSecretKey(keyName)
        runCatching {
            commandRunner.run(
                command = listOf(
                    SecretToolCommand,
                    "clear",
                    LINUX_SECRET_SERVICE_APPLICATION_ATTRIBUTE,
                    LINUX_SECRET_SERVICE_APPLICATION_VALUE,
                    LINUX_SECRET_SERVICE_KEY_ATTRIBUTE,
                    key
                ),
                timeoutMillis = 8_000L
            )
        }.onFailure { error ->
            logDesktopTts("settings_linux_secret_tool_delete_failed key=$keyName error=\"${error.desktopTtsSummary()}\"")
        }
    }

    private companion object {
        const val SecretToolCommand = "secret-tool"
    }
}

private fun linuxSecretKey(keyName: String): String {
    return "Episteme.Reader.$keyName"
}

private fun linuxSecretReferenceKey(keyName: String, reference: String): String {
    return when {
        reference.startsWith(LINUX_LIBSECRET_PREFIX) -> reference.removePrefix(LINUX_LIBSECRET_PREFIX)
        reference.startsWith(LINUX_SECRET_TOOL_PREFIX) -> reference.removePrefix(LINUX_SECRET_TOOL_PREFIX)
        else -> ""
    }.ifBlank { linuxSecretKey(keyName) }
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

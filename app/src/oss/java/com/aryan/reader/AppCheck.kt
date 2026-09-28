// src\oss
package com.aryan.reader

/** Header name the workers check for the Firebase App Check token. */
internal const val APP_CHECK_HEADER = "X-Firebase-AppCheck"

/** OSS has no Firebase: App Check is a no-op and no token is ever sent. */
fun installAppCheck() {}

/** OSS has no Firebase: always null. */
fun appCheckTokenBlocking(): String? = null

/** OSS has no Firebase: always empty. */
fun appCheckHeaderMap(): Map<String, String> = emptyMap()

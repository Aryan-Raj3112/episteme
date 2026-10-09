package com.aryan.reader.pdf

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.util.UUID

/**
 * Copies an asset out of an APK into the one part of the cache that `FileProvider` will share from,
 * and returns the file so the caller can delete it afterwards.
 *
 * `res/xml/provider_paths.xml` declares exactly one root,
 * `<cache-path name="shared_files" path="shared_files/"/>`, and
 * `app/src/test/.../AndroidFileProviderPathsContractTest` asserts the mapping stays that narrow on
 * purpose: a provider scoped to the whole cache directory would let any cached file be handed to
 * another application. A test that writes to `context.cacheDir` directly and then asks for a URI is
 * therefore asking for something the provider will never grant, and fails in `@Before` with
 * *"Failed to find configured root that contains /data/data/<pkg>/cache/<name>"*.
 *
 * `res/xml/file_paths.xml` maps the entire cache directory and is referenced by nothing — it looks
 * like the configuration these helpers were originally written against, left behind when the
 * provider was narrowed. Fix the caller; do not widen the provider.
 *
 * @param assetSource the context whose `assets/` the file is read from. This is the *instrumentation*
 *   context for fixtures under `src/androidTest/assets`, and the app context for files under
 *   `src/main/assets` — they are different APKs with different asset tables.
 * @param providerOwner the context whose `FileProvider` issues the URI, i.e. the app under test.
 * @param assetName the file to copy, which must exist in [assetSource]'s assets.
 *
 * The name is UUID-prefixed so concurrently-running tests in the same process cannot collide, and so
 * a URI from an earlier run cannot resolve to a different file.
 */
internal fun copyAssetToShareableCache(
    assetSource: Context,
    providerOwner: Context,
    assetName: String,
): File {
    val shareRoot = File(providerOwner.cacheDir, "shared_files").apply { mkdirs() }
    // assetName may be a path relative to the assets root ("epub/reader_test_book.epub"), but the
    // destination stays flat so the UUID cannot turn into a directory.
    val target = File(shareRoot, "${UUID.randomUUID()}_${assetName.substringAfterLast('/')}")
    assetSource.assets.open(assetName).use { input ->
        target.outputStream().use { output -> input.copyTo(output) }
    }
    return target
}

/** A `content://` URI for a file written by [copyAssetToShareableCache]. */
internal fun shareableCacheUri(providerOwner: Context, file: File): Uri =
    FileProvider.getUriForFile(providerOwner, "${providerOwner.packageName}.provider", file)
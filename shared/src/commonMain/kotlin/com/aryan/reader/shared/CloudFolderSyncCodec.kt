package com.aryan.reader.shared

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Shared Cloud Folder manifest codec.
 *
 * Android is the absolute benchmark and is NOT changed. Manifest bytes are the
 * cross-platform contract: the Drive object name embeds
 * `sha256:hex(UTF-8(codec bytes))` and Firestore heads pin that hash, so iOS
 * must encode byte-identical JSON. Both platforms therefore share this codec
 * instead of hand-rolling JSON per platform (the JSOn field order and defaults
 * come from the @Serializable model + this Json config, never from Swift).
 *
 * Swift entry points (via ReaderShared): `CloudFolderSyncCodecKt` top-level
 * functions `encodeCloudFolderManifest`, `decodeCloudFolderManifestOrNull`,
 * `cloudFolderManifestSha256Hex`, `cloudFolderManifestIssues`.
 */
/**
 * Byte-identical to Android's `CloudFolderManifestCodec`
 * (app/.../data/CloudFolderSyncRepository.kt:47-65): same Json config AND
 * `normalized()` before encode, otherwise the manifest hash pinned in Drive
 * names and Firestore heads would differ per platform.
 */
private val cloudFolderManifestJson = Json {
    encodeDefaults = true
    explicitNulls = false
    ignoreUnknownKeys = true
}

/** Canonical bytes uploaded to Drive for [manifest]. */
fun encodeCloudFolderManifest(manifest: CloudFolderManifest): String =
    cloudFolderManifestJson.encodeToString(manifest.normalized())

/**
 * Decode bytes downloaded from Drive; null when the payload is not a valid
 * manifest. Mirrors Android's decode (validate, then normalize).
 */
fun decodeCloudFolderManifestOrNull(rawJson: String): CloudFolderManifest? =
    runCatching {
        val decoded = cloudFolderManifestJson.decodeFromString<CloudFolderManifest>(rawJson)
        require(decoded.validationIssues().isEmpty())
        decoded.normalized()
    }.getOrNull()

/**
 * `sha256:hex` of the canonical bytes, exactly like Android's
 * `sha256CloudFolderManifest` (CloudFolderSyncWorker). The Drive object name
 * and the Firestore head both carry this value.
 */
fun cloudFolderManifestSha256Hex(canonicalJson: String): String =
    "sha256:${localFolderSyncSha256Hex(canonicalJson)}"

/** Read-only validation of the wire representation; never normalizes first. */
fun cloudFolderManifestIssues(manifest: CloudFolderManifest): List<CloudFolderManifestIssue> =
    manifest.validationIssues()

/** Canonical bytes for a conflict record kept in the durable conflict table. */
fun encodeCloudFolderConflictRecord(record: CloudFolderConflictRecord): String =
    cloudFolderManifestJson.encodeToString(record)

fun decodeCloudFolderConflictRecordOrNull(rawJson: String): CloudFolderConflictRecord? =
    runCatching { cloudFolderManifestJson.decodeFromString<CloudFolderConflictRecord>(rawJson) }
        .getOrNull()

/** Canonical bytes for a device binding kept in the durable binding table. */
fun encodeCloudFolderDeviceBinding(binding: CloudFolderDeviceBinding): String =
    cloudFolderManifestJson.encodeToString(binding)

fun decodeCloudFolderDeviceBindingOrNull(rawJson: String): CloudFolderDeviceBinding? =
    runCatching { cloudFolderManifestJson.decodeFromString<CloudFolderDeviceBinding>(rawJson) }
        .getOrNull()

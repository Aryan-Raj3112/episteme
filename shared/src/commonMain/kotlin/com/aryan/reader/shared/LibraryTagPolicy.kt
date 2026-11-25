package com.aryan.reader.shared

import kotlin.random.Random
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Tag identity, colour, ordering and first-run seed policy shared by every platform.
 *
 * Android is the benchmark: the same five default tags, the same palette and a fresh
 * random colour for every user-created tag. IDs are opaque UUIDs (never derived from the
 * tag name) so two names that normalise to the same slug can never collide and leave
 * `allTags` disagreeing with the tags stored on a book.
 *
 * Tags are device-local on both platforms. They are not part of the cloud snapshot; see
 * [withoutTags] and `mergeCloudLibrarySnapshotWithDownloadedBooks`.
 */
object LibraryTagPolicy {

    /** Android's `LibraryMutationController.TAG_COLORS`, in the same order. */
    val TAG_COLORS: List<Int> = listOf(
        0xFFE57373.toInt(), 0xFFF06292.toInt(), 0xFFBA68C8.toInt(), 0xFF9575CD.toInt(),
        0xFF7986CB.toInt(), 0xFF64B5F6.toInt(), 0xFF4FC3F7.toInt(), 0xFF4DD0E1.toInt(),
        0xFF4DB6AC.toInt(), 0xFF81C784.toInt(), 0xFFAED581.toInt(), 0xFFFF8A65.toInt(),
        0xFFA1887F.toInt(), 0xFF90A4AE.toInt(),
    )

    /**
     * The tags Android seeds once on a fresh install (`MainViewModel.buildDefaultTags`).
     * Names stay English literals so the seeded rows are identical on every platform; the
     * IDs are stable so repeated seeding or a future tag sync merge can never duplicate them.
     */
    val DEFAULT_TAGS: List<Tag> = listOf(
        Tag(id = "default_to_read", name = "To Read", color = 0xFF64B5F6.toInt()),
        Tag(id = "default_reading", name = "Reading", color = 0xFF81C784.toInt()),
        Tag(id = "default_finished", name = "Finished", color = 0xFFFFB74D.toInt()),
        Tag(id = "default_favorites", name = "Favorites", color = 0xFFF06292.toInt()),
        Tag(id = "default_reference", name = "Reference", color = 0xFF9575CD.toInt()),
    )

    /** Opaque, collision-free tag identity (Android used UUIDs; iOS used to derive them from names). */
    @OptIn(ExperimentalUuidApi::class)
    fun newTagId(): String = Uuid.random().toString()

    fun selectTagColor(random: Random = Random.Default): Int = TAG_COLORS.random(random)

    /**
     * Android's seed rule: only an empty tag list receives the defaults. Existing tags
     * (including a user who deleted every seeded tag) are left untouched.
     */
    fun seedDefaultTagsIfEmpty(tags: List<Tag>): List<Tag> =
        if (tags.isEmpty()) DEFAULT_TAGS else tags

    /** Case-insensitive natural ordering, matching the rest of the library. */
    fun sortTags(tags: List<Tag>): List<Tag> =
        tags.sortedWith(compareByNatural { it.name })
}

/** Natural tag order on a book's chips, independent of assignment or DB row order. */
fun BookItem.withNaturallySortedTags(): BookItem {
    if (tags.size < 2) return this
    val sorted = LibraryTagPolicy.sortTags(tags)
    return if (sorted == tags) this else copy(tags = sorted)
}

/**
 * A cloud snapshot with every tag removed.
 *
 * Android's Firestore documents never carry tags, so iOS must not upload them either:
 * tags stay device-local (like Android) instead of merging across devices without a
 * tombstone, which resurrected deleted tags.
 */
fun SharedLibrarySnapshot.withoutTags(): SharedLibrarySnapshot {
    if (tags.isEmpty() && books.none { it.tags.isNotEmpty() }) return this
    return copy(
        tags = emptyList(),
        books = books.map { book ->
            if (book.tags.isEmpty()) book else book.copy(tags = emptyList())
        },
    )
}

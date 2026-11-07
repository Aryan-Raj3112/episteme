package com.aryan.reader.mediaoverlay

import android.content.Context
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import com.aryan.reader.data.AppDatabase
import com.aryan.reader.data.RecentFileEntity
import com.aryan.reader.shared.reader.SharedMediaOverlayNarrationProgress
import com.aryan.reader.shared.reader.SharedMediaOverlayNarrationProgressStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Durable narration position, one row per narrated book.
 *
 * A sibling of `book_tts_listening_progress` rather than a column on it: that row is the *Listen*
 * player's record and is written with chunk indices from the synthesis engine, while this is the
 * publisher's own overlay position. Sharing one row would have each player overwrite the other's
 * notion of "where you were".
 *
 * The foreign key is what keeps this honest when a book leaves the library: deleting the book drops
 * the narration position with it, so a re-import of a file that happens to carry the same id cannot
 * resume into a passage from the old one.
 */
@Entity(
    tableName = "media_overlay_narration_progress",
    foreignKeys = [ForeignKey(
        entity = RecentFileEntity::class,
        parentColumns = ["bookId"],
        childColumns = ["bookId"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class MediaOverlayNarrationProgressEntity(
    @PrimaryKey val bookId: String,
    val spineItemIndex: Int,
    val chapterIndex: Int?,
    val clipIndex: Int,
    val updatedAt: Long
)

@Dao
interface MediaOverlayNarrationProgressDao {
    @Query("SELECT * FROM media_overlay_narration_progress WHERE bookId = :bookId")
    suspend fun get(bookId: String): MediaOverlayNarrationProgressEntity?

    @Upsert
    suspend fun upsert(progress: MediaOverlayNarrationProgressEntity)

    @Query("DELETE FROM media_overlay_narration_progress WHERE bookId = :bookId")
    suspend fun delete(bookId: String)

    @Query("DELETE FROM media_overlay_narration_progress")
    suspend fun clearAll()
}

/**
 * Room-backed [SharedMediaOverlayNarrationProgressStore].
 *
 * Reads suspend because a caller needs the answer before it can start playback. Writes are collected
 * onto an I/O scope instead of awaited: [record] is called from a player callback on every clip
 * boundary, and blocking the audio callback on a disk write would trade a rare lost clip for a
 * reliable stutter. Last write wins, and a lost write costs one clip of precision on the next open.
 */
class AndroidMediaOverlayNarrationProgressStore(
    private val dao: MediaOverlayNarrationProgressDao,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) : SharedMediaOverlayNarrationProgressStore {

    constructor(context: Context) : this(
        dao = AppDatabase.getDatabase(context.applicationContext).mediaOverlayNarrationProgressDao()
    )

    /**
     * The stored position, or null when the store cannot answer.
     *
     * `null` rather than a thrown query in both cases: the position is an optimisation on top of
     * "start where the reader is", and this read sits on the play press' coroutine, where an escaping
     * exception is a process crash. A database that is mid-migration, locked or corrupt costs the resume
     * and logs; it does not cost the play press.
     */
    override suspend fun load(bookId: String): SharedMediaOverlayNarrationProgress? =
        withContext(Dispatchers.IO) {
            try {
                dao.get(bookId)?.toShared()
            } catch (e: Exception) {
                Timber.tag(TAG).w(e, "Could not read narration position for book=$bookId")
                null
            }
        }

    override fun record(progress: SharedMediaOverlayNarrationProgress) {
        scope.launch { persist(progress) }
    }

    /** The write itself, for tests that need it ordered against the read. */
    internal suspend fun persist(progress: SharedMediaOverlayNarrationProgress) {
        try {
            dao.upsert(progress.toEntity())
        } catch (e: Exception) {
            // Recording is best-effort by contract; a failed write must never surface in the audio
            // callback that produced it.
            Timber.tag(TAG).w(e, "Could not record narration position for book=${progress.bookId}")
        }
    }

    private companion object {
        const val TAG = "MediaOverlayProgress"
    }
}

private fun MediaOverlayNarrationProgressEntity.toShared() = SharedMediaOverlayNarrationProgress(
    bookId = bookId,
    spineItemIndex = spineItemIndex,
    chapterIndex = chapterIndex,
    clipIndex = clipIndex,
    updatedAt = updatedAt,
)

private fun SharedMediaOverlayNarrationProgress.toEntity() = MediaOverlayNarrationProgressEntity(
    bookId = bookId,
    spineItemIndex = spineItemIndex,
    chapterIndex = chapterIndex,
    clipIndex = clipIndex,
    updatedAt = updatedAt,
)

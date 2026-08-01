package com.aryan.reader.data

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.withTransaction
import androidx.documentfile.provider.DocumentFile
import com.aryan.reader.FileType
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

@Entity(
    tableName = "audiobooks",
    foreignKeys = [ForeignKey(
        entity = RecentFileEntity::class,
        parentColumns = ["bookId"],
        childColumns = ["bookId"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class AudiobookEntity(
    @PrimaryKey val bookId: String,
    val filePath: String,
    val format: String,
    val title: String,
    val author: String?,
    val album: String?,
    val narrator: String?,
    val durationMs: Long,
    val positionMs: Long = 0L,
    val playbackSpeed: Float = 1f,
    val coverPath: String?,
    val addedAt: Long,
)

@Dao
interface AudiobookDao {
    @Query("SELECT * FROM audiobooks ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<AudiobookEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: AudiobookEntity)

    @Query("UPDATE audiobooks SET positionMs = :positionMs, playbackSpeed = :speed WHERE bookId = :bookId")
    suspend fun updatePlayback(bookId: String, positionMs: Long, speed: Float)
}

data class ImportedAudiobook(val book: RecentFileEntity, val audio: AudiobookEntity)

class AudiobookImporter(private val context: Context) {
    private val database = AppDatabase.getDatabase(context)

    suspend fun import(uri: Uri): Result<ImportedAudiobook> = withContext(Dispatchers.IO) {
        runCatching {
            val displayName = queryDisplayName(uri) ?: uri.lastPathSegment ?: "Audiobook"
            val extension = displayName.substringAfterLast('.', "").lowercase()
            require(isSupportedAudiobookFileName(displayName)) { "Unsupported audio format: .$extension" }
            val id = UUID.randomUUID().toString()
            val directory = File(context.filesDir, "audiobooks").apply { mkdirs() }
            val destination = File(directory, "$id.$extension")
            context.contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "Unable to open the selected file" }
                destination.outputStream().use(input::copyTo)
            }

            val retriever = MediaMetadataRetriever()
            val metadata = try {
                retriever.setDataSource(destination.absolutePath)
                ParsedMetadata(
                    title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE),
                    author = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                        ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_AUTHOR),
                    album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM),
                    durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L,
                    cover = retriever.embeddedPicture
                )
            } finally {
                retriever.release()
            }
            val coverPath = metadata.cover?.let { bytes ->
                File(directory, "$id-cover.jpg").also { it.writeBytes(bytes) }.absolutePath
            }
            val now = System.currentTimeMillis()
            val title = metadata.title?.takeIf(String::isNotBlank) ?: displayName.substringBeforeLast('.')
            val recent = RecentFileEntity(
                bookId = id, uriString = destination.toURI().toString(), type = FileType.AUDIOBOOK,
                displayName = displayName, timestamp = now, coverImagePath = coverPath,
                title = title, author = metadata.author, lastChapterIndex = null, lastPage = null,
                lastPositionCfi = null, progressPercentage = 0f, isRecent = true, isAvailable = true,
                lastModifiedTimestamp = now, isDeleted = false, locatorBlockIndex = null,
                locatorCharOffset = null, bookmarks = null, sourceFolderUri = null,
                isReflowPreferred = false, customName = null, highlights = null,
                fileSize = destination.length(), fileContentModifiedTimestamp = destination.lastModified(),
                seriesName = metadata.album, seriesIndex = null, description = null,
                folderTextMetadataParsed = true, folderCoverMetadataParsed = true
            )
            val audio = AudiobookEntity(id, destination.absolutePath, extension.uppercase(), title, metadata.author, metadata.album, metadata.author, metadata.durationMs, coverPath = coverPath, addedAt = now)
            database.withTransaction {
                database.recentFileDao().insertOrUpdateFile(recent)
                database.audiobookDao().upsert(audio)
            }
            ImportedAudiobook(recent, audio)
        }
    }

    suspend fun importAll(uris: List<Uri>): List<Result<ImportedAudiobook>> =
        uris.distinct().map { import(it) }

    suspend fun importFolder(treeUri: Uri): List<Result<ImportedAudiobook>> {
        val root = DocumentFile.fromTreeUri(context, treeUri) ?: return emptyList()
        val audioFiles = buildList {
            fun collect(directory: DocumentFile) {
                directory.listFiles().sortedBy { it.name.orEmpty() }.forEach { child ->
                    when {
                        child.isDirectory -> collect(child)
                        child.isFile && child.name?.let(::isSupportedAudiobookFileName) == true -> add(child.uri)
                    }
                }
            }
            collect(root)
        }
        return importAll(audioFiles)
    }

    private fun queryDisplayName(uri: Uri): String? = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
    }

    private data class ParsedMetadata(val title: String?, val author: String?, val album: String?, val durationMs: Long, val cover: ByteArray?)

    companion object {
        val SUPPORTED_EXTENSIONS = setOf("mp3", "m4a", "m4b", "aac", "ogg", "opus", "flac")
        fun isSupportedAudiobookFileName(name: String): Boolean = name.substringAfterLast('.', "").lowercase() in SUPPORTED_EXTENSIONS
    }
}

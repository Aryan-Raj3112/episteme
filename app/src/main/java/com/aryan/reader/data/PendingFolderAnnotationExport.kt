package com.aryan.reader.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

@Entity(tableName = "pending_folder_annotation_exports")
data class PendingFolderAnnotationExportEntity(
    @PrimaryKey val bookId: String,
    val revision: Long,
    val dirtySince: Long,
    val updatedAt: Long,
    val lastAttemptAt: Long = 0L,
    val attemptCount: Int = 0,
    val reason: String,
)

@Dao
interface PendingFolderAnnotationExportDao {
    @Query("SELECT * FROM pending_folder_annotation_exports WHERE bookId = :bookId")
    suspend fun get(bookId: String): PendingFolderAnnotationExportEntity?

    @Query("SELECT * FROM pending_folder_annotation_exports ORDER BY dirtySince")
    suspend fun getAll(): List<PendingFolderAnnotationExportEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(entity: PendingFolderAnnotationExportEntity)

    @Query(
        "UPDATE pending_folder_annotation_exports " +
            "SET lastAttemptAt = :attemptedAt, attemptCount = attemptCount + 1 " +
            "WHERE bookId = :bookId AND revision = :revision"
    )
    suspend fun recordAttempt(bookId: String, revision: Long, attemptedAt: Long)

    @Query("DELETE FROM pending_folder_annotation_exports WHERE bookId = :bookId AND revision = :revision")
    suspend fun deleteRevision(bookId: String, revision: Long): Int

    @Query("DELETE FROM pending_folder_annotation_exports WHERE bookId = :bookId")
    suspend fun delete(bookId: String)

    @Query("DELETE FROM pending_folder_annotation_exports")
    suspend fun clearAll()
}

package com.aryan.reader.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "shelves")
data class ShelfEntity(
    @PrimaryKey val id: String,
    val name: String,
    val isSmart: Boolean = false,
    val smartRulesJson: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val isDeleted: Boolean = false
)

@Entity(
    tableName = "book_shelf_cross_ref",
    primaryKeys =["bookId", "shelfId"],
    foreignKeys =[
        ForeignKey(
            entity = RecentFileEntity::class,
            parentColumns = ["bookId"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ShelfEntity::class,
            parentColumns = ["id"],
            childColumns = ["shelfId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["shelfId"]), Index(value = ["bookId"])]
)
data class BookShelfCrossRef(
    val bookId: String,
    val shelfId: String,
    val addedAt: Long
)

@Entity(tableName = "tags")
data class TagEntity(
    @PrimaryKey val id: String,
    val name: String,
    val color: Int? = null,
    val createdAt: Long
)

@Entity(
    tableName = "book_tag_cross_ref",
    primaryKeys = ["bookId", "tagId"],
    foreignKeys =[
        ForeignKey(
            entity = RecentFileEntity::class,
            parentColumns = ["bookId"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = TagEntity::class,
            parentColumns = ["id"],
            childColumns =["tagId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices =[Index(value = ["tagId"]), Index(value = ["bookId"])]
)
data class BookTagCrossRef(
    val bookId: String,
    val tagId: String
)
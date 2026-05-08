package com.aryan.reader.shared

data class CustomFontItem(
    val id: String,
    val displayName: String,
    val fileName: String,
    val fileExtension: String,
    val path: String,
    val timestamp: Long,
    val isDeleted: Boolean = false
)


package com.aryan.reader

import android.content.Context
import com.aryan.reader.data.RecentFilesRepository
import com.aryan.reader.shared.LibraryMutationController
import java.util.UUID

/** Android composition root. Feature bindings move here as their shared controllers become production-owned. */
internal class AndroidAppGraph(context: Context) {
    val recentFilesRepository = RecentFilesRepository(context)

    fun libraryMutationController(
        onShelfChanged: suspend (String) -> Unit,
    ): LibraryMutationController = LibraryMutationController(
        store = AndroidLibraryMutationStore(recentFilesRepository),
        newId = { UUID.randomUUID().toString() },
        nowMillis = System::currentTimeMillis,
        onShelfChanged = onShelfChanged,
    )
}

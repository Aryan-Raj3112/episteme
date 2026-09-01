package com.aryan.reader

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Lightweight in-process invalidation for cloud-folder state.
 *
 * WorkManager persists the actual state, so missing an emission across a
 * process death is harmless: the next ViewModel startup performs a full
 * refresh.  While the app is alive, this removes the old "open Settings to
 * discover a folder" delay after a worker finds a remote root.
 */
object CloudFolderSyncEvents {
    private val _stateChanged = MutableSharedFlow<Unit>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    val stateChanged = _stateChanged.asSharedFlow()

    fun notifyStateChanged() {
        _stateChanged.tryEmit(Unit)
    }
}

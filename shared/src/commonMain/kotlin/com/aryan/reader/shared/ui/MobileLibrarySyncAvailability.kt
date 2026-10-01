package com.aryan.reader.shared.ui

/**
 * Whether the mobile Library Beta library surface offers pull-to-sync.
 *
 * Pull-to-refresh is only offered when a sync mechanism can actually do work, so
 * the gesture is never presented as a no-op. Android is the benchmark; iOS uses
 * the same policy so both hosts gate the gesture identically.
 */
fun canPullToSyncLibrary(
    cloudSyncEnabled: Boolean,
    foldersWithLocalSyncEnabled: List<Boolean>,
): Boolean = cloudSyncEnabled || foldersWithLocalSyncEnabled.any { it }
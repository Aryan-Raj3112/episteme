package com.aryan.reader.shared

/**
 * Firebase ID-token freshness policy, shared first per AGENTS.md.
 *
 * Android fetches a fresh token per API call with auto-refresh
 * (`Auth.getIdToken`, `MainViewModel.getAuthToken`). iOS publishes the token
 * once per auth event and consumes it synchronously from
 * `IosAccountState.authToken`, so tokens older than the Firebase ~1h TTL go
 * stale mid-session. The native iOS auth boundary proactively force-refreshes
 * on this schedule and republishes through `updateAccountAuthToken`, which
 * keeps every sync consumer (AI, cloud TTS) on a live token with no call-site
 * changes.
 */
const val SHARED_AUTH_TOKEN_TTL_MILLIS = 3_600_000L

/** Refresh this far before expiry so a slow network still lands in time. */
const val SHARED_AUTH_TOKEN_REFRESH_LEAD_MILLIS = 600_000L

/**
 * Milliseconds from [nowMs] until the token fetched at [fetchedAtMs] should
 * be force-refreshed. Returns 0 when the refresh point already passed.
 */
fun sharedAuthTokenRefreshDelayMs(nowMs: Long, fetchedAtMs: Long): Long {
    val refreshAtMs = fetchedAtMs + SHARED_AUTH_TOKEN_TTL_MILLIS - SHARED_AUTH_TOKEN_REFRESH_LEAD_MILLIS
    return (refreshAtMs - nowMs).coerceAtLeast(0L)
}

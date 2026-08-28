package com.aryan.reader

import android.app.Activity
import android.app.Application
import android.content.Context
import com.aryan.reader.data.CloudFolderSyncRepository
import com.aryan.reader.data.FirestoreRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * The small, untrusted projection delivered by the Firestore head listener.
 *
 * The listener never treats a head as a manifest.  A matching WorkManager
 * pull rereads the authenticated Firestore pointer and the immutable Drive
 * manifest before doing any local work.
 */
internal data class CloudFolderHeadUpdate(
    val rootId: String,
    val revision: Long,
    val state: String? = null,
    val writerDeviceId: String? = null,
    val declaredRootId: String? = null,
    val schemaVersion: Long? = null,
)

internal data class CloudFolderHeadSnapshot(
    val updates: List<CloudFolderHeadUpdate>,
    val initial: Boolean,
)

/** Pure validation kept separate so malformed remote heads can be rejected without I/O. */
internal fun isValidCloudFolderHeadUpdate(update: CloudFolderHeadUpdate): Boolean {
    val rootId = update.rootId.trim()
    if (
        rootId.isBlank() ||
        rootId == "." ||
        rootId == ".." ||
        rootId.length > 256 ||
        rootId.any { character ->
            character == '/' || character == '\\' || character == '\u0000'
        }
    ) {
        return false
    }
    if (update.declaredRootId?.trim()?.takeIf { it.isNotBlank() }?.let { it != rootId } == true) {
        return false
    }
    if (update.revision < 0L) return false
    if (update.schemaVersion != null && update.schemaVersion != 1L) return false
    val state = update.state?.trim().orEmpty()
    return state.isBlank() || state.uppercase(Locale.US) == CLOUD_FOLDER_HEAD_COMMITTED_STATE
}

/**
 * A head should wake a pull only when it is newer than the durable local
 * knowledge.  Unbound roots are intentionally allowed: a targeted pull then
 * records the manifest and creates the incoming-folder placeholder without
 * materializing files.
 */
internal fun shouldScheduleCloudFolderHeadPull(
    remoteRevision: Long,
    knownRevision: Long,
    hasBinding: Boolean,
    isIncluded: Boolean,
): Boolean =
    remoteRevision >= 0L &&
        remoteRevision > knownRevision &&
        (!hasBinding || isIncluded)

internal const val CLOUD_FOLDER_HEAD_COMMITTED_STATE = "COMMITTED"

/**
 * Owns the foreground-only Firestore listener.  It is application scoped and
 * never captures an Activity or Compose object.  MainViewModel supplies the
 * current entitlement/account/sync state; the coordinator supplies only the
 * process foreground signal and the durable local revision comparison.
 */
internal object CloudFolderHeadListenerCoordinator {
    private const val DEBOUNCE_MILLIS = 500L

    private var application: Application? = null
    private var lifecycleCallbacksRegistered = false
    private var startedActivityCount = 0
    private var isForeground = false

    private var accountId: String? = null
    private var isPro = false
    private var syncEnabled = false
    private var listener: Any? = null
    private var listenerAccountId: String? = null
    private var listenerGeneration = 0L

    private val pendingRevisions = mutableMapOf<String, Long>()
    private val pendingJobs = mutableMapOf<String, Job>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val firestoreRepository by lazy { FirestoreRepository() }

    private val activityLifecycleCallbacks = object : Application.ActivityLifecycleCallbacks {
        override fun onActivityCreated(activity: Activity, savedInstanceState: android.os.Bundle?) = Unit
        override fun onActivityStarted(activity: Activity) {
            scope.launch {
                startedActivityCount++
                if (startedActivityCount == 1) {
                    isForeground = true
                    cloudFolderLogD("event=head_listener_foreground result=entered")
                    reconcileListener("foreground")
                }
            }
        }

        override fun onActivityResumed(activity: Activity) = Unit
        override fun onActivityPaused(activity: Activity) = Unit
        override fun onActivityStopped(activity: Activity) {
            scope.launch {
                startedActivityCount = (startedActivityCount - 1).coerceAtLeast(0)
                if (startedActivityCount == 0 && isForeground) {
                    isForeground = false
                    cloudFolderLogD("event=head_listener_foreground result=exited")
                    reconcileListener("background")
                }
            }
        }

        override fun onActivitySaveInstanceState(activity: Activity, outState: android.os.Bundle) = Unit
        override fun onActivityDestroyed(activity: Activity) = Unit
    }

    fun install(context: Context) {
        if (!BuildConfig.IS_PRO) return
        val app = context.applicationContext as? Application ?: return
        scope.launch {
            if (application == null) application = app
            if (!lifecycleCallbacksRegistered) {
                app.registerActivityLifecycleCallbacks(activityLifecycleCallbacks)
                lifecycleCallbacksRegistered = true
                cloudFolderLogD("event=head_listener_install result=success")
            }
            reconcileListener("install")
        }
    }

    fun updateEligibility(
        context: Context,
        accountId: String?,
        isPro: Boolean,
        syncEnabled: Boolean,
    ) {
        install(context)
        scope.launch {
            val normalizedAccountId = accountId?.trim()?.takeIf { it.isNotBlank() }
            val accountChanged = this@CloudFolderHeadListenerCoordinator.accountId != normalizedAccountId
            this@CloudFolderHeadListenerCoordinator.accountId = normalizedAccountId
            this@CloudFolderHeadListenerCoordinator.isPro = isPro
            this@CloudFolderHeadListenerCoordinator.syncEnabled = syncEnabled
            if (accountChanged) {
                detachListener("account_changed")
            }
            reconcileListener("eligibility")
        }
    }

    fun clearEligibility(context: Context) {
        updateEligibility(context, accountId = null, isPro = false, syncEnabled = false)
    }

    private fun eligible(): Boolean =
        BuildConfig.IS_PRO &&
            isPro &&
            syncEnabled &&
            !accountId.isNullOrBlank()

    private fun reconcileListener(reason: String) {
        val shouldAttach = isForeground && eligible()
        if (!shouldAttach) {
            if (listener != null) detachListener(reason)
            return
        }
        val normalizedAccountId = accountId ?: return
        if (listener != null && listenerAccountId == normalizedAccountId) return
        if (listener != null) detachListener("replace")
        attachListener(normalizedAccountId, reason)
    }

    private fun attachListener(normalizedAccountId: String, reason: String) {
        if (application == null) return
        val generation = listenerGeneration
        cloudFolderLogI(
            "event=head_listener_attach account=${cloudFolderSafeId(normalizedAccountId)} " +
                "reason=$reason generation=$generation",
        )
        // Firestore may deliver the cached initial snapshot synchronously from
        // addSnapshotListener. Publish the account before registering so that
        // such a callback is accepted; the registration itself is assigned as
        // soon as the SDK returns.
        listenerAccountId = normalizedAccountId
        val registration = runCatching {
            firestoreRepository.listenToCloudFolderHeads(
                userId = normalizedAccountId,
                onUpdate = { snapshot ->
                    scope.launch {
                        handleSnapshot(normalizedAccountId, generation, snapshot)
                    }
                },
                onError = { error ->
                    scope.launch {
                        if (isCurrentListener(normalizedAccountId, generation)) {
                            cloudFolderLogError(
                                event = "head_listener_error",
                                error = error,
                                details = "account=${cloudFolderSafeId(normalizedAccountId)} generation=$generation",
                            )
                        }
                    }
                },
            )
        }.getOrElse { error ->
            cloudFolderLogError(
                event = "head_listener_attach",
                error = error,
                details = "account=${cloudFolderSafeId(normalizedAccountId)} generation=$generation result=failure",
            )
            listenerAccountId = null
            null
        }
        if (registration == null) {
            listenerAccountId = null
            cloudFolderLogW(
                "event=head_listener_attach account=${cloudFolderSafeId(normalizedAccountId)} " +
                    "generation=$generation result=unavailable",
            )
            return
        }
        listener = registration
        listenerAccountId = normalizedAccountId
        // A synchronous initial callback is legal.  The registration fields
        // are installed immediately after this call and callbacks validate
        // the generation/account before scheduling work.
        cloudFolderLogI(
            "event=head_listener_attach account=${cloudFolderSafeId(normalizedAccountId)} " +
                "generation=$generation result=active",
        )
    }

    private fun detachListener(reason: String) {
        listenerGeneration++
        pendingJobs.values.forEach(Job::cancel)
        pendingJobs.clear()
        pendingRevisions.clear()
        val oldListener = listener
        val oldAccount = listenerAccountId
        listener = null
        listenerAccountId = null
        if (oldListener != null) {
            firestoreRepository.removeListener(oldListener)
            cloudFolderLogI(
                "event=head_listener_detach account=${cloudFolderSafeId(oldAccount)} " +
                    "reason=$reason generation=$listenerGeneration",
            )
        }
    }

    private fun isCurrentListener(expectedAccountId: String, generation: Long): Boolean =
        isForeground &&
            eligible() &&
            accountId == expectedAccountId &&
            listenerAccountId == expectedAccountId &&
            listenerGeneration == generation

    private suspend fun handleSnapshot(
        expectedAccountId: String,
        generation: Long,
        snapshot: CloudFolderHeadSnapshot,
    ) {
        if (!isCurrentListener(expectedAccountId, generation)) {
            cloudFolderLogD(
                "event=head_snapshot_ignore reason=stale_listener " +
                    "account=${cloudFolderSafeId(expectedAccountId)} generation=$generation",
            )
            return
        }
        cloudFolderLogD(
            "event=head_snapshot account=${cloudFolderSafeId(expectedAccountId)} " +
                "initial=${snapshot.initial} updates=${snapshot.updates.size} generation=$generation",
        )
        snapshot.updates.forEach { update ->
            handleUpdate(expectedAccountId, generation, update)
        }
    }

    private fun handleUpdate(
        expectedAccountId: String,
        generation: Long,
        update: CloudFolderHeadUpdate,
    ) {
        if (!isValidCloudFolderHeadUpdate(update)) {
            cloudFolderLogW(
                "event=head_update_ignore reason=invalid_head " +
                    "account=${cloudFolderSafeId(expectedAccountId)} root=${cloudFolderSafeId(update.rootId)} " +
                    "revision=${update.revision} generation=$generation",
            )
            return
        }
        val rootId = update.rootId.trim()
        scope.launch {
            val decision = runCatching {
                withContext(Dispatchers.IO) {
                    val repository = CloudFolderSyncRepository(appContext(), expectedAccountId)
                    val root = repository.getRoot(rootId)
                    val binding = repository.getBinding(rootId)
                    val knownRevision = maxOf(
                        root?.manifestRevision ?: -1L,
                        binding?.lastAcknowledgedRevision ?: -1L,
                    )
                    val shouldSchedule = shouldScheduleCloudFolderHeadPull(
                        remoteRevision = update.revision,
                        knownRevision = knownRevision,
                        hasBinding = binding != null,
                        isIncluded = repository.isIncluded(rootId),
                    )
                    HeadDecision(
                        knownRevision = knownRevision,
                        hasBinding = binding != null,
                        isIncluded = repository.isIncluded(rootId),
                        writerIsSelf = update.writerDeviceId?.trim() == repository.deviceId,
                        shouldSchedule = shouldSchedule,
                    )
                }
            }.getOrElse { error ->
                cloudFolderLogError(
                    event = "head_update_state",
                    error = error,
                    details = "account=${cloudFolderSafeId(expectedAccountId)} root=${cloudFolderSafeId(rootId)} " +
                        "remoteRevision=${update.revision} result=unavailable",
                )
                // A state read failure must not lose the wake.  The worker
                // performs the same account/selection/binding checks again.
                HeadDecision(
                    knownRevision = -1L,
                    hasBinding = false,
                    isIncluded = true,
                    writerIsSelf = false,
                    shouldSchedule = true,
                )
            }
            if (!isCurrentListener(expectedAccountId, generation)) return@launch
            if (!decision.shouldSchedule) {
                val reason = when {
                    decision.hasBinding && !decision.isIncluded -> "excluded"
                    update.revision <= decision.knownRevision && decision.writerIsSelf -> "writer_echo"
                    update.revision <= decision.knownRevision -> "known_revision"
                    else -> "worker_gate"
                }
                cloudFolderLogD(
                    "event=head_update_ignore reason=$reason account=${cloudFolderSafeId(expectedAccountId)} " +
                        "root=${cloudFolderSafeId(rootId)} remoteRevision=${update.revision} " +
                        "knownRevision=${decision.knownRevision} bound=${decision.hasBinding} " +
                        "included=${decision.isIncluded} writerSelf=${decision.writerIsSelf}",
                )
                return@launch
            }
            schedulePull(
                accountId = expectedAccountId,
                rootId = rootId,
                revision = update.revision,
                generation = generation,
                knownRevision = decision.knownRevision,
                writerIsSelf = decision.writerIsSelf,
            )
        }
    }

    private fun schedulePull(
        accountId: String,
        rootId: String,
        revision: Long,
        generation: Long,
        knownRevision: Long,
        writerIsSelf: Boolean,
    ) {
        pendingRevisions[rootId] = maxOf(pendingRevisions[rootId] ?: -1L, revision)
        pendingJobs.remove(rootId)?.cancel()
        pendingJobs[rootId] = scope.launch {
            delay(DEBOUNCE_MILLIS)
            val targetRevision = pendingRevisions.remove(rootId) ?: return@launch
            pendingJobs.remove(rootId)
            if (!isCurrentListener(accountId, generation)) return@launch
            runCatching {
                withContext(Dispatchers.IO) {
                    CloudFolderSyncWorker.enqueuePull(
                        context = appContext(),
                        accountId = accountId,
                        rootId = rootId,
                        replace = false,
                    )
                }
            }.onSuccess {
                cloudFolderLogI(
                    "event=head_pull_enqueue account=${cloudFolderSafeId(accountId)} " +
                        "root=${cloudFolderSafeId(rootId)} remoteRevision=$targetRevision " +
                        "knownRevision=$knownRevision writerSelf=$writerIsSelf debounceMs=$DEBOUNCE_MILLIS " +
                        "result=queued",
                )
            }.onFailure { error ->
                cloudFolderLogError(
                    event = "head_pull_enqueue",
                    error = error,
                    details = "account=${cloudFolderSafeId(accountId)} root=${cloudFolderSafeId(rootId)} " +
                        "remoteRevision=$targetRevision result=failure",
                )
            }
        }
    }

    private fun appContext(): Context = requireNotNull(application).applicationContext

    private data class HeadDecision(
        val knownRevision: Long,
        val hasBinding: Boolean,
        val isIncluded: Boolean,
        val writerIsSelf: Boolean,
        val shouldSchedule: Boolean,
    )
}

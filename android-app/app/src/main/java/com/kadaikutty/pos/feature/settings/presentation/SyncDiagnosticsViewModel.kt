package com.kadaikutty.pos.feature.settings.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kadaikutty.pos.core.auth.SessionStore
import com.kadaikutty.pos.core.database.BillingDatabase
import com.kadaikutty.pos.core.database.LocalOperationEntity
import com.kadaikutty.pos.core.database.SyncDeadLetterEntity
import com.kadaikutty.pos.core.database.SyncQueueEntity
import com.kadaikutty.pos.core.network.BackendApiClient
import com.kadaikutty.pos.core.network.BackendApiException
import com.kadaikutty.pos.core.preferences.AppPreferences
import com.kadaikutty.pos.core.sync.SyncScheduler
import com.kadaikutty.pos.core.sync.SyncStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class SyncDiagnosticsViewModel @Inject constructor(
    private val database: BillingDatabase,
    private val sessionStore: SessionStore,
    private val syncScheduler: SyncScheduler,
    private val backendApiClient: BackendApiClient,
    private val appPreferences: AppPreferences
) : ViewModel() {

    private val _deadLetters = MutableStateFlow<List<SyncDeadLetterEntity>>(emptyList())
    val deadLetters: StateFlow<List<SyncDeadLetterEntity>> = _deadLetters
    private val _unresolvedItems = MutableStateFlow<List<SyncQueueEntity>>(emptyList())
    val unresolvedItems: StateFlow<List<SyncQueueEntity>> = _unresolvedItems

    val activeSession: StateFlow<com.kadaikutty.pos.core.auth.Session?> = sessionStore.activeSession
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _isDirectSyncing = MutableStateFlow(false)

    val isSyncing: StateFlow<Boolean> = combine(
        _isDirectSyncing,
        syncScheduler.isSyncingFlow
    ) { direct, background -> direct || background }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _syncErrorMessage = MutableStateFlow<String?>(null)
    val syncErrorMessage: StateFlow<String?> = _syncErrorMessage

    init {
        loadDeadLetters()
        viewModelScope.launch {
            sessionStore.activeSession
                .flatMapLatest { session ->
                    if (session == null) flowOf(emptyList())
                    else {
                        runCatching { com.kadaikutty.pos.core.sync.LegacyTenantMigration.runOnce(database, session.companyId) }
                        database.syncQueueDao().unresolved(session.companyId, 100)
                    }
                }
                .collect { _unresolvedItems.value = it }
        }
    }

    fun loadDeadLetters() {
        viewModelScope.launch {
            val session = sessionStore.activeSession.first() ?: return@launch
            _deadLetters.value = database.syncDeadLetterDao().getDeadLetters(session.companyId, 100)
        }
    }

    /**
     * Retry goes through SyncWorker like every other sync, so a conflict hit here is resolved by
     * the same ConflictResolver instead of being parked. This screen used to run its own copy of
     * the push loop, which marked conflicts and left them stuck.
     */
    fun retryUnresolved() {
        if (_isDirectSyncing.value) return
        viewModelScope.launch(Dispatchers.IO) {
            _isDirectSyncing.value = true
            _syncErrorMessage.value = null
            try {
                val session = sessionStore.activeSession.first()
                if (session == null) {
                    _syncErrorMessage.value = "Sign in to sync."
                    return@launch
                }
                // Manual retry: ignore the dead-letter cap the background worker honours.
                database.syncQueueDao().retryFailed(session.companyId, System.currentTimeMillis(), Int.MAX_VALUE)
                syncScheduler.request(replaceExisting = true)
                syncScheduler.requestPull()
            } catch (e: Exception) {
                _syncErrorMessage.value = e.message ?: "Sync failed"
            } finally {
                _isDirectSyncing.value = false
                loadDeadLetters()
            }
        }
    }

    /** Every conflict and how it was settled, newest first. */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val conflicts: StateFlow<List<com.kadaikutty.pos.core.database.SyncConflictEntity>> = sessionStore.activeSession
        .flatMapLatest { session -> if (session == null) flowOf(emptyList()) else database.syncConflictDao().recent(session.companyId, 200) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun clearConflictLog() {
        viewModelScope.launch(Dispatchers.IO) {
            sessionStore.activeSession.first()?.let { database.syncConflictDao().clear(it.companyId) }
        }
    }

    fun retryItem(item: SyncDeadLetterEntity) {
        viewModelScope.launch {
            val queueItem = SyncQueueEntity(
                id = UUID.randomUUID().toString(),
                companyId = item.companyId,
                entityType = item.entityType,
                entityId = item.entityId,
                operation = item.operation,
                payload = item.payload,
                status = SyncStatus.PENDING,
                attemptCount = 0,
                createdAtEpochMs = System.currentTimeMillis(),
                updatedAtEpochMs = System.currentTimeMillis()
            )
            database.syncQueueDao().enqueue(queueItem)
            database.syncDeadLetterDao().deleteById(item.id)
            retryUnresolved()
        }
    }

    fun retryAll() {
        viewModelScope.launch {
            val list = _deadLetters.value
            for (item in list) {
                val queueItem = SyncQueueEntity(
                    id = UUID.randomUUID().toString(),
                    companyId = item.companyId,
                    entityType = item.entityType,
                    entityId = item.entityId,
                    operation = item.operation,
                    payload = item.payload,
                    status = SyncStatus.PENDING,
                    attemptCount = 0,
                    createdAtEpochMs = System.currentTimeMillis(),
                    updatedAtEpochMs = System.currentTimeMillis()
                )
                database.syncQueueDao().enqueue(queueItem)
                database.syncDeadLetterDao().deleteById(item.id)
            }
            retryUnresolved()
        }
    }

    fun clearAll() {
        viewModelScope.launch {
            val session = sessionStore.activeSession.first() ?: return@launch
            database.syncDeadLetterDao().deleteAllForCompany(session.companyId)
            loadDeadLetters()
        }
    }

    private val _isResyncing = MutableStateFlow(false)
    val isResyncing: StateFlow<Boolean> = _isResyncing

    private val _resyncMessage = MutableStateFlow<String?>(null)
    val resyncMessage: StateFlow<String?> = _resyncMessage

    // Replays the server's complete current cloud state onto this device by resetting the pull
    // cursor to "0" and re-pulling. Since the sync fix, a fresh pull always reconstructs full
    // state from durable records (not just the change log), so this is now a safe, complete
    // "resync from cloud" rather than the lossy tool it used to be.
    fun forceFullResync(onFinished: (Boolean) -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            _isResyncing.value = true
            _resyncMessage.value = "Preparing full resync..."
            val session = sessionStore.activeSession.first()
            if (session == null) {
                _resyncMessage.value = "Resync failed: User not logged in."
                _isResyncing.value = false
                kotlinx.coroutines.withContext(Dispatchers.Main) { onFinished(false) }
                return@launch
            }
            try {
                database.localOperationDao().put(
                    LocalOperationEntity(session.companyId, "sync_pull_cursor", "0")
                )
                _resyncMessage.value = "Resync started. Re-fetching all records from cloud..."
                kotlinx.coroutines.withContext(Dispatchers.Main) { syncScheduler.requestPull() }
                _resyncMessage.value = "Full resync triggered successfully. Your data will refresh momentarily."
                kotlinx.coroutines.withContext(Dispatchers.Main) { onFinished(true) }
            } catch (e: Exception) {
                _resyncMessage.value = "Resync failed: ${e.message}"
                kotlinx.coroutines.withContext(Dispatchers.Main) { onFinished(false) }
            } finally {
                _isResyncing.value = false
            }
        }
    }
}

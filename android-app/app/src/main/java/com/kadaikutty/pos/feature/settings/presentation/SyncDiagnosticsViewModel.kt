package com.kadaikutty.pos.feature.settings.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kadaikutty.pos.core.auth.SessionStore
import com.kadaikutty.pos.core.database.BillingDatabase
import com.kadaikutty.pos.core.database.SyncDeadLetterEntity
import com.kadaikutty.pos.core.database.SyncQueueEntity
import com.kadaikutty.pos.core.sync.SyncStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import javax.inject.Inject
import java.util.UUID

@HiltViewModel
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class SyncDiagnosticsViewModel @Inject constructor(
    private val database: BillingDatabase,
    private val sessionStore: SessionStore,
    private val syncScheduler: com.kadaikutty.pos.core.sync.SyncScheduler
) : ViewModel() {

    private val _deadLetters = MutableStateFlow<List<SyncDeadLetterEntity>>(emptyList())
    val deadLetters: StateFlow<List<SyncDeadLetterEntity>> = _deadLetters
    private val _unresolvedItems = MutableStateFlow<List<SyncQueueEntity>>(emptyList())
    val unresolvedItems: StateFlow<List<SyncQueueEntity>> = _unresolvedItems

    init {
        loadDeadLetters()
        viewModelScope.launch {
            sessionStore.activeSession
                .flatMapLatest { session ->
                    if (session == null) flowOf(emptyList())
                    else database.syncQueueDao().unresolved(session.companyId, 100)
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

    fun retryUnresolved() {
        viewModelScope.launch {
            val session = sessionStore.activeSession.first() ?: return@launch
            database.syncQueueDao().retryFailed(session.companyId, System.currentTimeMillis())
            syncScheduler.request(replaceExisting = true)
            loadDeadLetters()
        }
    }

    fun retryItem(item: SyncDeadLetterEntity) {
        viewModelScope.launch {
            // Re-enqueue it into sync_queue
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
            syncScheduler.request(replaceExisting = true)
            loadDeadLetters()
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
            syncScheduler.request(replaceExisting = true)
            loadDeadLetters()
        }
    }

    fun clearAll() {
        viewModelScope.launch {
            val session = sessionStore.activeSession.first() ?: return@launch
            database.syncDeadLetterDao().deleteAllForCompany(session.companyId)
            loadDeadLetters()
        }
    }
}

package com.company.billing.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.company.billing.core.auth.Session
import com.company.billing.core.auth.SessionStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val sessionStore: SessionStore
) : ViewModel() {
    val activeSession: StateFlow<Session?> = sessionStore.activeSession.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    fun logout() {
        viewModelScope.launch {
            sessionStore.clear()
        }
    }
}

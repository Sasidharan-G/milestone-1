package com.company.billing.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.company.billing.core.auth.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SetNewPasswordUiState(
    val password: String = "",
    val loading: Boolean = false,
    val error: String? = null,
    val success: Boolean = false
)

@HiltViewModel
class SetNewPasswordViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {
    private val _state = MutableStateFlow(SetNewPasswordUiState())
    val state = _state.asStateFlow()

    fun updatePassword(value: String) {
        _state.update { it.copy(password = value, error = null) }
    }

    fun submitNewPassword() {
        val currentPassword = state.value.password
        if (currentPassword.length < 6) {
            _state.update { it.copy(error = "Password must be at least 6 characters") }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            val res = com.company.billing.core.auth.RecoveryResult.Failure("Obsolete")
            when (res) {
                is com.company.billing.core.auth.RecoveryResult.Success -> {
                    _state.update { it.copy(loading = false, success = true) }
                }
                is com.company.billing.core.auth.RecoveryResult.Failure -> {
                    _state.update { it.copy(loading = false, error = res.message) }
                }
            }
        }
    }
}

package com.company.billing.feature.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.company.billing.core.auth.AuthRepository
import com.company.billing.core.auth.RegisterResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject


data class RegisterUiState(
    val mobileNumber: String = "",
    val passwordString: String = "",
    val confirmPasswordString: String = "",
    val ownerName: String = "",
    val businessName: String = "",
    val loading: Boolean = false,
    val error: String? = null,
    val complete: Boolean = false,
    val isGoogleSignIn: Boolean = false,
    val showOtpDialog: Boolean = false,
    val otp: String = "",
    val verificationId: String? = null
)

@HiltViewModel
class RegisterViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {
    private val _state = MutableStateFlow(RegisterUiState())
    val state = _state.asStateFlow()

    fun updateMobileNumber(value: String) = _state.update { it.copy(mobileNumber = value, error = null) }
    fun updateOwnerName(value: String) = _state.update { it.copy(ownerName = value, error = null) }
    fun updateBusinessName(value: String) = _state.update { it.copy(businessName = value, error = null) }
    fun updatePassword(value: String) = _state.update { it.copy(passwordString = value, error = null) }
    fun updateConfirmPassword(value: String) = _state.update { it.copy(confirmPasswordString = value, error = null) }
    fun updateOtp(value: String) = _state.update { it.copy(otp = value, error = null) }
    fun dismissOtpDialog() = _state.update { it.copy(showOtpDialog = false, otp = "", verificationId = null) }

    fun register(activity: android.app.Activity) {
        val current = state.value
        if (current.mobileNumber.isBlank() || current.ownerName.isBlank() || current.businessName.isBlank() || current.passwordString.isBlank() || current.confirmPasswordString.isBlank()) {
            _state.update { it.copy(error = "All fields are required") }
            return
        }
        if (current.passwordString != current.confirmPasswordString) {
            _state.update { it.copy(error = "Passwords do not match") }
            return
        }
        if (current.passwordString.length < 6) {
            _state.update { it.copy(error = "Password must be at least 6 characters") }
            return
        }
        val cleanPhone = current.mobileNumber.trim().replace(" ", "").replace("-", "")
        if (cleanPhone.length < 10 || !cleanPhone.all { it.isDigit() || it == '+' }) {
            _state.update { it.copy(error = "Please provide a valid mobile number") }
            return
        }
        
        // Ensure phone has country code for Firebase
        val phoneWithCode = if (cleanPhone.startsWith("+")) cleanPhone else "+91$cleanPhone"

        _state.update { it.copy(loading = true, error = null) }
        
        authRepository.sendRegistrationOtp(
            mobileNumber = phoneWithCode,
            activity = activity,
            onCodeSent = { verificationId ->
                _state.update { it.copy(loading = false, showOtpDialog = true, verificationId = verificationId) }
            },
            onVerificationFailed = { error ->
                _state.update { it.copy(loading = false, error = error) }
            }
        )
    }

    fun verifyOtpAndCompleteRegistration(onSuccess: (String) -> Unit) {
        val current = state.value
        val verificationId = current.verificationId
        if (verificationId == null || current.otp.isBlank()) {
            _state.update { it.copy(error = "Please enter the OTP") }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            val passChars = current.passwordString.toCharArray()
            
            val cleanPhone = current.mobileNumber.trim().replace(" ", "").replace("-", "")
            val phoneWithCode = if (cleanPhone.startsWith("+")) cleanPhone else "+91$cleanPhone"

            val result = authRepository.verifyRegistrationOtpAndRegister(
                verificationId = verificationId,
                otp = current.otp,
                mobileNumber = phoneWithCode,
                password = passChars,
                ownerName = current.ownerName,
                businessName = current.businessName
            )
            passChars.fill('\u0000')
            
            _state.update {
                when (result) {
                    is RegisterResult.Success -> {
                        onSuccess(result.companyId)
                        it.copy(loading = false, showOtpDialog = false, complete = true, passwordString = "", confirmPasswordString = "")
                    }
                    is RegisterResult.Failure -> {
                        it.copy(loading = false, error = result.message)
                    }
                }
            }
        }
    }

    fun signInWithGoogle() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                authRepository.signInWithGoogle()
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.message) }
            }
        }
    }

    fun handleGoogleSignInSuccess(onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            try {
                val res = authRepository.handleGoogleSignInSuccess()
                when (res) {
                    is com.company.billing.core.auth.GoogleSignInResult.Success -> {
                        // User already has an account, they shouldn't be registering!
                        _state.update { it.copy(loading = false, error = "Account already exists. Please login instead.") }
                        onResult(false, "Account already exists")
                    }
                    is com.company.billing.core.auth.GoogleSignInResult.NewUserNeedsCompanyDetails -> {
                        // Perfect, it's a new user! Move them to step 2 of registration.
                        _state.update { it.copy(loading = false, isGoogleSignIn = true) }
                        onResult(true, null)
                    }
                    is com.company.billing.core.auth.GoogleSignInResult.Failure -> {
                        _state.update { it.copy(loading = false, error = res.message) }
                        onResult(false, res.message)
                    }
                }
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.message) }
                onResult(false, e.message)
            }
        }
    }

    fun completeGoogleRegistration(onSuccess: (String) -> Unit) {
        val current = state.value
        if (current.ownerName.isBlank() || current.businessName.isBlank()) {
            _state.update { it.copy(error = "Owner Name and Business Name are required") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            val result = authRepository.completeGoogleRegistration(
                ownerName = current.ownerName,
                businessName = current.businessName
            )
            _state.update {
                when (result) {
                    is RegisterResult.Success -> {
                        onSuccess(result.companyId)
                        it.copy(loading = false, complete = true)
                    }
                    is RegisterResult.Failure -> {
                        it.copy(loading = false, error = result.message)
                    }
                }
            }
        }
    }
}

@Composable
fun RegisterScreen(
    viewModel: RegisterViewModel,
    onNavigateBackToLogin: () -> Unit,
    onRegisterSuccess: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val scrollState = rememberScrollState()
    var message by remember { mutableStateOf("") }
    val activity = androidx.compose.ui.platform.LocalContext.current as? android.app.Activity


    LaunchedEffect(state.complete) {
        if (state.complete) {
            onRegisterSuccess()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                        MaterialTheme.colorScheme.background
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 450.dp)
                .padding(24.dp)
                .verticalScroll(scrollState)
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(24.dp)
                ),
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .padding(32.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // System Logo Placeholder
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(16.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Home,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Create Business Account",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Register a new business and admin owner profile",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                        value = state.mobileNumber,
                        onValueChange = { viewModel.updateMobileNumber(it) },
                        label = { Text("Mobile Number") },
                        leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                OutlinedTextField(
                    value = state.ownerName,
                    onValueChange = { viewModel.updateOwnerName(it) },
                    label = { Text("Owner Name") },
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = state.businessName,
                    onValueChange = { viewModel.updateBusinessName(it) },
                    label = { Text("Business Name") },
                    leadingIcon = { Icon(Icons.Default.Home, contentDescription = null) },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                        value = state.passwordString,
                        onValueChange = { viewModel.updatePassword(it) },
                        label = { Text("Password") },
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = state.confirmPasswordString,
                        onValueChange = { viewModel.updateConfirmPassword(it) },
                        label = { Text("Confirm Password") },
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                if (!state.error.isNullOrBlank()) {
                    Text(
                        text = state.error ?: "",
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.align(Alignment.Start)
                    )
                }
                if (message.isNotBlank()) {
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.align(Alignment.Start)
                    )
                }

                Button(
                    onClick = { 
                        if (activity != null) {
                            viewModel.register(activity)
                        } else {
                            message = "Could not get Activity context"
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    enabled = !state.loading
                ) {
                    if (state.loading && !state.showOtpDialog) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Text("Create Account", fontWeight = FontWeight.Bold)
                    }
                }



                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Already have an account? Sign In",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clickable { onNavigateBackToLogin() }
                        .padding(vertical = 4.dp)
                )
            }
        }
        
        if (state.showOtpDialog) {
            AlertDialog(
                onDismissRequest = { viewModel.dismissOtpDialog() },
                title = { Text("Enter OTP") },
                text = {
                    Column {
                        Text("Please enter the OTP sent to your mobile number.")
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = state.otp,
                            onValueChange = { viewModel.updateOtp(it) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (state.loading) {
                            Spacer(modifier = Modifier.height(8.dp))
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { viewModel.verifyOtpAndCompleteRegistration { onRegisterSuccess() } }, enabled = !state.loading) {
                        Text("Verify")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.dismissOtpDialog() }, enabled = !state.loading) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

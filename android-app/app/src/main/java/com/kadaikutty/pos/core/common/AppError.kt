package com.kadaikutty.pos.core.common

sealed interface AppError { val userMessage: String
    data class Validation(override val userMessage: String) : AppError
    data object Authentication : AppError { override val userMessage = "Authentication failed" }
    data object Network : AppError { override val userMessage = "Network unavailable" }
    data object Database : AppError { override val userMessage = "Local data could not be saved" }
    data class Unexpected(override val userMessage: String = "Something went wrong") : AppError
}
sealed interface AppResult<out T> { data class Success<T>(val value: T) : AppResult<T>; data class Failure(val error: AppError) : AppResult<Nothing> }


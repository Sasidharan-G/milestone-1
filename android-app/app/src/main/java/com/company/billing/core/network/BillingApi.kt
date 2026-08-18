package com.company.billing.core.network

import retrofit2.http.GET
import retrofit2.http.Body
import retrofit2.http.POST
import com.company.billing.core.security.Permission

interface BillingApi {
    @GET("health")
    suspend fun health(): HealthResponse
    @POST("auth/login") suspend fun login(@Body request: LoginRequest): LoginResponse

    @POST("sync/batch")
    suspend fun syncBatch(@Body items: List<SyncBatchItem>): retrofit2.Response<Unit>
}

data class SyncBatchItem(
    val id: String,
    val entityType: String,
    val entityId: String,
    val operation: String,
    val payload: String
)

data class HealthResponse(val status: String)
data class LoginRequest(val username: String, val password: String)
data class LoginResponse(val userId: String, val displayName: String, val permissions: List<Permission>, val accessToken: String)

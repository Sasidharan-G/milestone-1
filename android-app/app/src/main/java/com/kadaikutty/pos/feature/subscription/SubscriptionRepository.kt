package com.kadaikutty.pos.feature.subscription

import com.google.firebase.firestore.FirebaseFirestore
import com.kadaikutty.pos.core.auth.SessionStore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await

@Singleton
class SubscriptionRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val sessionStore: SessionStore
) {
    
    fun getSubscriptionStatus(): Flow<SubscriptionStatus> {
        return sessionStore.activeSession.flatMapLatest { session ->
            if (session == null) {
                return@flatMapLatest flowOf(mockSubscriptionStatus.copy(status = "expired", expiresAt = 0L))
            }
            
            val companyId = session.companyId
            
            callbackFlow {
                val listener = firestore.collection("companies").document(companyId)
                    .addSnapshotListener { snapshot, error ->
                        if (error != null) {
                            // If network error, don't lock out the user immediately. 
                            // They might be offline. Assume active trial temporarily to prevent flashing lockouts.
                            trySend(mockSubscriptionStatus.copy(companyId = companyId))
                            return@addSnapshotListener
                        }
                        
                        if (snapshot == null || !snapshot.exists()) {
                            // Document might not be created yet, or still loading from network.
                            // Default to a free trial instead of instantly expiring and blocking the user.
                            trySend(mockSubscriptionStatus.copy(companyId = companyId))
                            return@addSnapshotListener
                        }
                        
                        val planId = snapshot.getString("subscription_plan") ?: "free_trial"
                        val expiresAt = snapshot.getLong("subscription_expires_at") ?: (System.currentTimeMillis() + 30L * 24 * 60 * 60 * 1000)
                        
                        val status = SubscriptionStatus(
                            companyId = companyId,
                            planId = planId,
                            status = if (System.currentTimeMillis() > expiresAt) "expired" else "active",
                            expiresAt = expiresAt
                        )
                        
                        trySend(status)
                    }
                    
                awaitClose { listener.remove() }
            }
        }
    }

    // Mocks a successful Razorpay payment callback for testing
    suspend fun simulatePayment(planId: String) {
        val session = sessionStore.activeSession.firstOrNull() ?: return
        val companyId = session.companyId
        
        val durationDays = when (planId) {
            "monthly" -> 30L
            "yearly" -> 365L
            else -> 14L // Trial
        }
        
        val newExpiry = System.currentTimeMillis() + (durationDays * 24 * 60 * 60 * 1000)
        
        val updates = mapOf(
            "subscription_plan" to planId,
            "subscription_expires_at" to newExpiry
        )
        
        try {
            firestore.collection("companies").document(companyId).update(updates).await()
        } catch (e: Exception) {
            // Document might not exist yet if created differently, use set with merge
            firestore.collection("companies").document(companyId).set(updates, com.google.firebase.firestore.SetOptions.merge()).await()
        }
    }
}

package com.company.billing.core.auth

import com.company.billing.core.database.BillingDatabase
import com.company.billing.core.security.Permission
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import com.company.billing.core.sync.*
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException

class DefaultAuthRepository(
    private val supabase: SupabaseClient,
    private val sessions: SessionStore,
    private val offlineCredentials: OfflineCredentialStore,
    private val verifier: OfflineCredentialVerifier,
    private val database: BillingDatabase
) : AuthRepository {
    override suspend fun loginOnline(username: String, password: CharArray): LoginResult = try {
        val emailClean = username.trim()
        supabase.auth.signInWith(Email) {
            this.email = emailClean
            this.password = password.concatToString()
        }

        val sessionOrNull = supabase.auth.currentSessionOrNull() ?: throw IOException("Authentication failed: session not established")
        val user = sessionOrNull.user ?: throw IOException("Authentication failed: user not established")

        if (user.emailConfirmedAt == null) {
            throw IOException("EMAIL_NOT_VERIFIED")
        }

        // Resolve company membership and check company status
        var memberships = supabase.from("company_users").select {
            filter {
                eq("user_id", user.id)
                eq("status", "active")
            }
        }.decodeList<SupabaseCompanyUser>()

        if (memberships.isEmpty()) {
            val metadata = user.userMetadata
            val metaBusinessName = metadata?.get("business_name")?.jsonPrimitive?.content
            val metaDisplayName = metadata?.get("display_name")?.jsonPrimitive?.content

            if (!metaBusinessName.isNullOrBlank() && !metaDisplayName.isNullOrBlank()) {
                supabase.postgrest.rpc(
                    function = "initialize_new_company",
                    parameters = buildJsonObject {
                        put("business_name", metaBusinessName)
                        put("owner_name", metaDisplayName)
                    }
                )
                memberships = supabase.from("company_users").select {
                    filter {
                        eq("user_id", user.id)
                        eq("status", "active")
                    }
                }.decodeList<SupabaseCompanyUser>()
            } else {
                throw IOException("NO_COMPANY_MEMBERSHIP")
            }
        }

        if (memberships.isEmpty()) {
            throw IOException("NO_COMPANY_MEMBERSHIP")
        }

        val activeMembership = memberships.first()
        val companyId = activeMembership.company_id
        val role = activeMembership.role

        // Verify company status
        val company = supabase.from("companies").select {
            filter {
                eq("id", companyId)
            }
        }.decodeSingleOrNull<SupabaseCompany>() ?: throw IOException("NO_COMPANY_MEMBERSHIP")

        if (company.status == "suspended") {
            throw IOException("COMPANY_SUSPENDED")
        }

        val perms = activeMembership.permissions.mapNotNull {
            try { Permission.valueOf(it) } catch (e: Exception) { null }
        }.toSet()

        // Resolve profile display name
        val profile = supabase.from("profiles").select {
            filter {
                eq("id", user.id)
            }
        }.decodeSingleOrNull<SupabaseProfile>()
        val displayName = profile?.full_name ?: user.userMetadata?.get("display_name")?.jsonPrimitive?.content ?: emailClean.substringBefore("@")

        val token = sessionOrNull.accessToken
        val session = Session(
            userId = user.id,
            displayName = displayName,
            permissions = perms,
            accessToken = token,
            companyId = companyId,
            role = role
        )
        sessions.save(session)

        val nowMs = System.currentTimeMillis()
        val offlineValidityMs = 7 * 24 * 60 * 60 * 1000L // 7 days validity window
        val offlineValidUntil = nowMs + offlineValidityMs

        val offlineCred = verifier.create(username, password, session.userId, session.displayName)
        offlineCredentials.save(offlineCred)

        // Cache in SQLite database users table to allow offline login later
        val saltStr = java.util.Base64.getEncoder().encodeToString(offlineCred.salt)
        val verifierStr = java.util.Base64.getEncoder().encodeToString(offlineCred.verifier)
        val userEntity = UserEntity(
            id = session.userId,
            username = username,
            displayName = session.displayName,
            salt = saltStr,
            verifier = verifierStr,
            permissions = perms.joinToString(",") { it.name },
            companyId = session.companyId,
            role = session.role,
            lastOnlineVerifiedAt = nowMs,
            offlineValidUntil = offlineValidUntil
        )
        database.userDao().insertUser(userEntity)

        try {
            pullAllDataFromCloud(supabase, database, companyId)
        } catch (syncError: Exception) {
            syncError.printStackTrace()
        }

        LoginResult.Success(session)
    } catch (ioe: IOException) {
        val msg = ioe.message ?: ""
        if (msg == "EMAIL_NOT_VERIFIED" || msg == "NO_COMPANY_MEMBERSHIP" || msg == "COMPANY_SUSPENDED") {
            LoginResult.Failure(
                when (msg) {
                    "EMAIL_NOT_VERIFIED" -> "Email verification is pending. Please verify your email first."
                    "COMPANY_SUSPENDED" -> "Your company account has been suspended."
                    else -> "No active company membership found."
                }
            )
        } else {
            val localResult = loginOffline(username, password)
            if (localResult is LoginResult.Success) {
                localResult
            } else {
                LoginResult.Failure("Network unreachable. Cached login not found or expired.")
            }
        }
    } catch (e: Exception) {
        val msg = e.message ?: ""
        when {
            msg.contains("invalid_credentials", ignoreCase = true) || msg.contains("invalid login", ignoreCase = true) -> LoginResult.Failure("Invalid email or password.")
            else -> LoginResult.Failure(e.message ?: "Authentication failed")
        }
    }

    override suspend fun loginOffline(username: String, password: CharArray): LoginResult {
        val userDao = database.userDao()
        val userEntity = userDao.getUserByUsername(username) ?: return LoginResult.Failure("Invalid username or password")

        // Offline validity check removed as per user request
        // eppo offline la login pannalum work aaganum

        val saltBytes = java.util.Base64.getDecoder().decode(userEntity.salt)
        val verifierBytes = java.util.Base64.getDecoder().decode(userEntity.verifier)

        val offlineCred = OfflineCredential(
            username = userEntity.username,
            userId = userEntity.id,
            displayName = userEntity.displayName,
            salt = saltBytes,
            verifier = verifierBytes
        )

        if (verifier.matches(offlineCred, password)) {
            val permissions = userEntity.toPermissionsSet()
            val session = Session(
                userId = userEntity.id,
                displayName = userEntity.displayName,
                permissions = permissions,
                companyId = userEntity.companyId,
                role = userEntity.role
            )
            sessions.save(session)
            return LoginResult.Success(session)
        }
        return LoginResult.Failure("Invalid username or password")
    }

    override suspend fun logout() {
        try {
            supabase.auth.signOut()
        } catch (_: Exception) {}
        sessions.clear()
    }

    override suspend fun registerCompany(email: String, password: CharArray, ownerName: String, businessName: String): RegisterResult = try {
        val emailClean = email.trim()
        val meta = kotlinx.serialization.json.buildJsonObject {
            put("display_name", kotlinx.serialization.json.JsonPrimitive(ownerName))
            put("business_name", kotlinx.serialization.json.JsonPrimitive(businessName))
        }
        supabase.auth.signUpWith(Email) {
            this.email = emailClean
            this.password = password.concatToString()
            this.data = meta
        }

        val sessionOrNull = supabase.auth.currentSessionOrNull()
        if (sessionOrNull != null) {
            val companyId = supabase.postgrest.rpc(
                function = "initialize_new_company",
                parameters = buildJsonObject {
                    put("business_name", businessName)
                    put("owner_name", ownerName)
                }
            ).decodeAs<String>()
            RegisterResult.Success(companyId)
        } else {
            RegisterResult.Success("")
        }
    } catch (e: Exception) {
        RegisterResult.Failure(e.message ?: "Registration failed")
    }

    override suspend fun recoverPassword(email: String): RecoveryResult = try {
        supabase.auth.resetPasswordForEmail(email = email.trim())
        RecoveryResult.Success
    } catch (e: Exception) {
        RecoveryResult.Failure(e.message ?: "Password recovery failed")
    }

    private suspend fun pullAllDataFromCloud(supabase: SupabaseClient, database: BillingDatabase, companyId: String) {
        val cloudCategories = supabase.from("categories").select { filter { eq("company_id", companyId) } }.decodeList<SupabaseCategory>()
        val cloudProducts = supabase.from("products").select { filter { eq("company_id", companyId) } }.decodeList<SupabaseProduct>()
        val cloudCustomers = supabase.from("customers").select { filter { eq("company_id", companyId) } }.decodeList<SupabaseCustomer>()
        val cloudSuppliers = supabase.from("suppliers").select { filter { eq("company_id", companyId) } }.decodeList<SupabaseSupplier>()
        val cloudExpenses = supabase.from("expenses").select { filter { eq("company_id", companyId) } }.decodeList<SupabaseExpense>()
        val cloudSales = supabase.from("sales").select { filter { eq("company_id", companyId) } }.decodeList<SupabaseSale>()
        val cloudSaleItems = supabase.from("sale_items").select { filter { eq("company_id", companyId) } }.decodeList<SupabaseSaleItem>()
        val cloudPurchases = supabase.from("purchases").select { filter { eq("company_id", companyId) } }.decodeList<SupabasePurchase>()
        val cloudPurchaseItems = supabase.from("purchase_items").select { filter { eq("company_id", companyId) } }.decodeList<SupabasePurchaseItem>()
        val cloudCustomerCredits = supabase.from("customer_credits").select { filter { eq("company_id", companyId) } }.decodeList<SupabaseCustomerCredit>()
        val cloudSupplierCredits = supabase.from("supplier_credits").select { filter { eq("company_id", companyId) } }.decodeList<SupabaseSupplierCredit>()

        database.runInTransaction {
            database.openHelper.writableDatabase.execSQL("DELETE FROM categories WHERE companyId = '$companyId'")
            database.openHelper.writableDatabase.execSQL("DELETE FROM products WHERE companyId = '$companyId'")
            database.openHelper.writableDatabase.execSQL("DELETE FROM customers WHERE companyId = '$companyId'")
            database.openHelper.writableDatabase.execSQL("DELETE FROM suppliers WHERE companyId = '$companyId'")
            database.openHelper.writableDatabase.execSQL("DELETE FROM expenses WHERE companyId = '$companyId'")
            database.openHelper.writableDatabase.execSQL("DELETE FROM sales WHERE companyId = '$companyId'")
            database.openHelper.writableDatabase.execSQL("DELETE FROM sale_items WHERE companyId = '$companyId'")
            database.openHelper.writableDatabase.execSQL("DELETE FROM purchases WHERE companyId = '$companyId'")
            database.openHelper.writableDatabase.execSQL("DELETE FROM purchase_items WHERE companyId = '$companyId'")
            database.openHelper.writableDatabase.execSQL("DELETE FROM stock_movements WHERE companyId = '$companyId'")
            database.openHelper.writableDatabase.execSQL("DELETE FROM customer_credits WHERE companyId = '$companyId'")
            database.openHelper.writableDatabase.execSQL("DELETE FROM supplier_credits WHERE companyId = '$companyId'")

            cloudCategories.forEach { c ->
                database.openHelper.writableDatabase.execSQL(
                    "INSERT INTO categories (id, companyId, name, createdAtEpochMs, updatedAtEpochMs, syncStatus) VALUES ('${c.id}', '$companyId', '${c.name.replace("'", "''")}', ${c.createdAtEpochMs}, ${c.updatedAtEpochMs}, 'SYNCED')"
                )
            }
            cloudProducts.forEach { p ->
                database.openHelper.writableDatabase.execSQL(
                    "INSERT INTO products (id, companyId, name, categoryId, purchasePriceMinorUnits, salePriceMinorUnits, unitType, createdAtEpochMs, updatedAtEpochMs, syncStatus) VALUES ('${p.id}', '$companyId', '${p.name.replace("'", "''")}', '${p.categoryId}', ${p.purchasePriceMinorUnits}, ${p.salePriceMinorUnits}, '${p.unitType}', ${p.createdAtEpochMs}, ${p.updatedAtEpochMs}, 'SYNCED')"
                )
            }
            cloudCustomers.forEach { cust ->
                val phoneVal = cust.phone?.let { "'${it.replace("'", "''")}'" } ?: "NULL"
                val addressVal = cust.address?.let { "'${it.replace("'", "''")}'" } ?: "NULL"
                database.openHelper.writableDatabase.execSQL(
                    "INSERT INTO customers (id, companyId, name, phone, address, createdAtEpochMs, updatedAtEpochMs, syncStatus) VALUES ('${cust.id}', '$companyId', '${cust.name.replace("'", "''")}', $phoneVal, $addressVal, ${cust.createdAtEpochMs}, ${cust.updatedAtEpochMs}, 'SYNCED')"
                )
            }
            cloudSuppliers.forEach { sup ->
                val phoneVal = sup.phone?.let { "'${it.replace("'", "''")}'" } ?: "NULL"
                val addressVal = sup.address?.let { "'${it.replace("'", "''")}'" } ?: "NULL"
                database.openHelper.writableDatabase.execSQL(
                    "INSERT INTO suppliers (id, companyId, name, phone, address, createdAtEpochMs, updatedAtEpochMs, syncStatus) VALUES ('${sup.id}', '$companyId', '${sup.name.replace("'", "''")}', $phoneVal, $addressVal, ${sup.createdAtEpochMs}, ${sup.updatedAtEpochMs}, 'SYNCED')"
                )
            }
            cloudExpenses.forEach { exp ->
                database.openHelper.writableDatabase.execSQL(
                    "INSERT INTO expenses (id, companyId, amountMinorUnits, description, createdAtEpochMs, updatedAtEpochMs, syncStatus) VALUES ('${exp.id}', '$companyId', ${exp.amountMinorUnits}, '${exp.description.replace("'", "''")}', ${exp.createdAtEpochMs}, ${exp.updatedAtEpochMs}, 'SYNCED')"
                )
            }
            cloudSales.forEach { s ->
                val custIdVal = s.customerId?.let { "'$it'" } ?: "NULL"
                database.openHelper.writableDatabase.execSQL(
                    "INSERT INTO sales (id, companyId, billNumber, totalMinorUnits, createdAtEpochMs, syncStatus, customerId) VALUES ('${s.id}', '$companyId', '${s.billNumber}', ${s.totalMinorUnits}, ${s.createdAtEpochMs}, 'SYNCED', $custIdVal)"
                )
            }
            cloudSaleItems.forEach { si ->
                database.openHelper.writableDatabase.execSQL(
                    "INSERT INTO sale_items (companyId, saleId, productId, quantity, unitPriceMinorUnits, lineTotalMinorUnits) VALUES ('$companyId', '${si.saleId}', '${si.productId}', ${si.quantity}, ${si.unitPriceMinorUnits}, ${si.lineTotalMinorUnits})"
                )
            }
            cloudPurchases.forEach { pur ->
                database.openHelper.writableDatabase.execSQL(
                    "INSERT INTO purchases (id, companyId, supplierId, totalMinorUnits, createdAtEpochMs, syncStatus) VALUES ('${pur.id}', '$companyId', '${pur.supplierId}', ${pur.totalMinorUnits}, ${pur.createdAtEpochMs}, 'SYNCED')"
                )
            }
            cloudPurchaseItems.forEach { pi ->
                database.openHelper.writableDatabase.execSQL(
                    "INSERT INTO purchase_items (companyId, purchaseId, productId, quantity, unitValueMinorUnits, lineTotalMinorUnits) VALUES ('$companyId', '${pi.purchaseId}', '${pi.productId}', ${pi.quantity}, ${pi.unitValueMinorUnits}, ${pi.lineTotalMinorUnits})"
                )
            }
            cloudCustomerCredits.forEach { c ->
                val reasonVal = "'${c.reason.replace("'", "''")}'"
                database.openHelper.writableDatabase.execSQL(
                    "INSERT INTO customer_credits (id, companyId, customerId, amountMinorUnits, reason, dateEpochMs, syncStatus) VALUES ('${c.id}', '$companyId', '${c.customerId}', ${c.amountMinorUnits}, $reasonVal, ${c.dateEpochMs}, 'SYNCED')"
                )
            }
            cloudSupplierCredits.forEach { s ->
                val termsVal = "'${s.terms.replace("'", "''")}'"
                database.openHelper.writableDatabase.execSQL(
                    "INSERT INTO supplier_credits (id, companyId, supplierId, amountMinorUnits, terms, dueDateEpochMs, dateEpochMs, syncStatus) VALUES ('${s.id}', '$companyId', '${s.supplierId}', ${s.amountMinorUnits}, $termsVal, ${s.dueDateEpochMs}, ${s.dateEpochMs}, 'SYNCED')"
                )
            }
        }
    }

    @kotlinx.serialization.Serializable
    private data class SupabaseCompanyUser(
        val company_id: String,
        val user_id: String,
        val role: String,
        val status: String,
        val permissions: List<String>
    )

    @kotlinx.serialization.Serializable
    private data class SupabaseCompany(
        val id: String,
        val name: String,
        val owner_user_id: String,
        val status: String
    )

    @kotlinx.serialization.Serializable
    private data class SupabaseProfile(
        val id: String,
        val full_name: String
    )
}

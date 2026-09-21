package com.kadaikutty.pos.core.database

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import com.kadaikutty.pos.core.auth.SessionStore
import com.kadaikutty.pos.core.security.SecurityShield
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TenantDatabaseManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sessionStore: SessionStore
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val databaseCache = ConcurrentHashMap<String, BillingDatabase>()

    @Volatile
    private var activeCompanyId: String = "company_main"

    init {
        scope.launch {
            sessionStore.activeSession.collect { session ->
                val company = session?.companyId?.trim()
                if (!company.isNullOrBlank()) {
                    activeCompanyId = company
                }
            }
        }
    }

    fun setActiveCompany(companyId: String) {
        val trimmed = companyId.trim()
        if (trimmed.isNotBlank()) {
            activeCompanyId = trimmed
        }
    }

    fun getActiveCompany(): String = activeCompanyId

    fun getDatabase(companyId: String? = null): BillingDatabase {
        val targetCompany = companyId?.trim()?.takeIf { it.isNotBlank() }
            ?: activeCompanyId.takeIf { it.isNotBlank() }
            ?: "company_main"

        val safeName = sanitizeCompanyId(targetCompany)
        val dbFileName = "billing_$safeName.db"

        return databaseCache.computeIfAbsent(dbFileName) {
            buildDatabase(context, dbFileName)
        }
    }

    private fun sanitizeCompanyId(companyId: String): String {
        val sanitized = companyId.replace(Regex("[^a-zA-Z0-9_]"), "_").trim('_')
        return if (sanitized.isBlank()) "company_main" else sanitized.take(48)
    }

    private fun buildDatabase(context: Context, dbFileName: String): BillingDatabase {
        migrateLegacyDatabaseIfNeeded(context, dbFileName)

        val keyBytes = SecurityShield.getOrCreateDatabaseKey(context)
        val factory = SupportOpenHelperFactory(keyBytes)

        return Room.databaseBuilder(context, BillingDatabase::class.java, dbFileName)
            .openHelperFactory(factory)
            .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
            .addMigrations(
                migration1To2, migration2To3, migration3To4, migration4To5,
                migration5To6, migration6To7, migration7To8, migration8To9,
                migration9To10, migration10To11, migration11To12, migration12To13,
                migration13To14, migration14To15, migration15To16, migration16To17,
                migration17To18, migration18To19, migration19To20, migration20To21,
                migration21To22, migration22To23, migration23To24, migration24To25,
                migration25To26
            )
            .fallbackToDestructiveMigrationOnDowngrade()
            .build()
    }

    private fun migrateLegacyDatabaseIfNeeded(context: Context, newDbName: String) {
        try {
            val newDbFile = context.getDatabasePath(newDbName)
            if (newDbFile.exists()) return

            val legacyDbFile = context.getDatabasePath("billing.db")
            if (!legacyDbFile.exists()) return

            newDbFile.parentFile?.mkdirs()
            legacyDbFile.copyTo(newDbFile, overwrite = false)

            val legacyWal = File(legacyDbFile.parentFile, "billing.db-wal")
            if (legacyWal.exists()) {
                legacyWal.copyTo(File(newDbFile.parentFile, "$newDbName-wal"), overwrite = false)
            }
            val legacyShm = File(legacyDbFile.parentFile, "billing.db-shm")
            if (legacyShm.exists()) {
                legacyShm.copyTo(File(newDbFile.parentFile, "$newDbName-shm"), overwrite = false)
            }
            android.util.Log.i("TenantDatabaseManager", "Migrated legacy billing.db into $newDbName")
        } catch (e: Exception) {
            android.util.Log.w("TenantDatabaseManager", "Could not copy legacy database: ${e.message}")
        }
    }

    fun closeAll() {
        databaseCache.values.forEach { runCatching { it.close() } }
        databaseCache.clear()
    }
}

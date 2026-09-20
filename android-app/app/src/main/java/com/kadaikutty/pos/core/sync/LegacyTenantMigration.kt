package com.kadaikutty.pos.core.sync

import com.kadaikutty.pos.core.database.BillingDatabase
import com.kadaikutty.pos.core.database.LocalOperationEntity

/**
 * Rewrites sync_queue rows left behind by builds that wrote everything under the placeholder
 * tenant "company_main" before real company ids existed.
 *
 * It used to run from five places - every sync cycle, every manual retry and every emission of
 * the diagnostics flow - each one a write query over the queue table to find rows that, after the
 * first pass, are never there again. It now records that it has run for a company and returns
 * immediately afterwards.
 */
object LegacyTenantMigration {
    const val DONE_KEY = "legacy_tenant_migration_done"
    private const val LEGACY_COMPANY_ID = "company_main"

    suspend fun runOnce(database: BillingDatabase, companyId: String) {
        // Migrating company_main onto itself would rewrite nothing and mark the work done for a
        // session that has not resolved its real tenant yet.
        if (companyId.isBlank() || companyId == LEGACY_COMPANY_ID) return
        val operations = database.localOperationDao()
        if (operations.get(companyId, DONE_KEY) != null) return
        database.syncQueueDao().migrateTenantData(LEGACY_COMPANY_ID, companyId)
        operations.put(LocalOperationEntity(companyId, DONE_KEY, "1"))
    }
}

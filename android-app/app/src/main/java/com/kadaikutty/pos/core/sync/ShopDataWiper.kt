package com.kadaikutty.pos.core.sync

import com.kadaikutty.pos.core.database.BillingDatabase

/**
 * Empties one tenant database of shop data, sync state included. Used by Clear Data on this
 * device and by the pull when another device has cleared the cloud copy (see [PullWorker]).
 *
 * The caller must hold [SyncLock]: a push or pull still running would write into the tables
 * while they are being emptied.
 */
object ShopDataWiper {
    private val TABLES = listOf(
        "draft_cart", "sale_items", "sales", "purchase_items", "purchases", "stock_movements",
        "customer_credits", "supplier_credits", "products", "categories", "customers", "suppliers",
        "expenses", "sync_queue", "sync_dead_letter", "local_operations", "audit_logs", "shifts"
    )

    // What "delete my account" removes on top of the shop's business records: the sign-in
    // accounts, the license row, conflict history and any parked carts.
    private val ACCOUNT_TABLES = listOf("users", "company_licenses", "sync_conflicts", "draft_cart_items")

    /** [includeAccount] also removes the users and license rows, for account deletion. */
    fun wipe(database: BillingDatabase, includeAccount: Boolean = false) {
        val sqlite = database.openHelper.writableDatabase
        sqlite.execSQL("PRAGMA foreign_keys = OFF")
        sqlite.beginTransaction()
        try {
            for (table in if (includeAccount) TABLES + ACCOUNT_TABLES else TABLES) {
                runCatching { sqlite.execSQL("DELETE FROM `$table`") }
            }
            runCatching { sqlite.execSQL("DELETE FROM `sqlite_sequence`") }
            sqlite.setTransactionSuccessful()
        } finally {
            sqlite.endTransaction()
            sqlite.execSQL("PRAGMA foreign_keys = ON")
        }
        runCatching { sqlite.execSQL("VACUUM") }
    }
}

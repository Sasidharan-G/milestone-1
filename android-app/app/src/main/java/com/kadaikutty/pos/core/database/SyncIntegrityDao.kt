package com.kadaikutty.pos.core.database

import androidx.room.Dao
import androidx.room.Query

/**
 * Cross-table checks the sync conflict resolver needs: what still points at a record, and which
 * records are pointed at but missing. See ConflictResolver.
 */
@Dao
interface SyncIntegrityDao {
    @Query("SELECT COUNT(*) FROM sale_items WHERE companyId = :companyId AND productId = :productId")
    suspend fun saleItemCount(companyId: String, productId: String): Int

    @Query("SELECT COUNT(*) FROM purchase_items WHERE companyId = :companyId AND productId = :productId")
    suspend fun purchaseItemCount(companyId: String, productId: String): Int

    /** Customers that dues are still recorded against but that no longer exist on this device. */
    @Query("SELECT customerId FROM customer_credits WHERE companyId = :companyId AND customerId NOT IN (SELECT id FROM customers WHERE companyId = :companyId) GROUP BY customerId HAVING SUM(amountMinorUnits) != 0")
    suspend fun orphanCustomerIds(companyId: String): List<String>

    @Query("SELECT supplierId FROM supplier_credits WHERE companyId = :companyId AND supplierId NOT IN (SELECT id FROM suppliers WHERE companyId = :companyId) GROUP BY supplierId HAVING SUM(amountMinorUnits) != 0")
    suspend fun orphanSupplierIds(companyId: String): List<String>

    /** Products that stock or purchase lines still refer to but that no longer exist on this device. */
    @Query("SELECT productId FROM stock_movements WHERE companyId = :companyId AND productId NOT IN (SELECT id FROM products WHERE companyId = :companyId) GROUP BY productId HAVING SUM(quantityDelta) != 0 UNION SELECT DISTINCT productId FROM purchase_items WHERE companyId = :companyId AND productId NOT IN (SELECT id FROM products WHERE companyId = :companyId)")
    suspend fun orphanProductIds(companyId: String): List<String>

    @Query("SELECT id FROM products WHERE companyId = :companyId AND barcode = :barcode AND id != :exceptId LIMIT 1")
    suspend fun otherProductWithBarcode(companyId: String, barcode: String, exceptId: String): String?

    @Query("SELECT id FROM categories WHERE companyId = :companyId ORDER BY createdAtEpochMs LIMIT 1")
    suspend fun anyCategoryId(companyId: String): String?
}

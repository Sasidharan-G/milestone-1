package com.company.billing.core.sync

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// --- Categories ---
@Serializable
data class CategoryLocal(
    val id: String,
    val name: String,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long
) {
    fun toSupabase() = SupabaseCategory(id, name, createdAtEpochMs, updatedAtEpochMs)
}

@Serializable
data class SupabaseCategory(
    val id: String,
    val name: String,
    @SerialName("created_at_epoch_ms") val createdAtEpochMs: Long,
    @SerialName("updated_at_epoch_ms") val updatedAtEpochMs: Long
)

// --- Products ---
@Serializable
data class ProductLocal(
    val id: String,
    val name: String,
    val categoryId: String,
    val purchasePriceMinorUnits: Long = 0L,
    val salePriceMinorUnits: Long = 0L,
    val unitType: String = "PIECE",
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long
) {
    fun toSupabase() = SupabaseProduct(id, name, categoryId, purchasePriceMinorUnits, salePriceMinorUnits, unitType, createdAtEpochMs, updatedAtEpochMs)
}

@Serializable
data class SupabaseProduct(
    val id: String,
    val name: String,
    @SerialName("category_id") val categoryId: String,
    @SerialName("purchase_price_minor_units") val purchasePriceMinorUnits: Long,
    @SerialName("sale_price_minor_units") val salePriceMinorUnits: Long,
    @SerialName("unit_type") val unitType: String,
    @SerialName("created_at_epoch_ms") val createdAtEpochMs: Long,
    @SerialName("updated_at_epoch_ms") val updatedAtEpochMs: Long
)

// --- Customers ---
@Serializable
data class CustomerLocal(
    val id: String,
    val name: String,
    val phone: String? = null,
    val address: String? = null,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long
) {
    fun toSupabase() = SupabaseCustomer(id, name, phone, address, createdAtEpochMs, updatedAtEpochMs)
}

@Serializable
data class SupabaseCustomer(
    val id: String,
    val name: String,
    val phone: String? = null,
    val address: String? = null,
    @SerialName("created_at_epoch_ms") val createdAtEpochMs: Long,
    @SerialName("updated_at_epoch_ms") val updatedAtEpochMs: Long
)

// --- Suppliers ---
@Serializable
data class SupplierLocal(
    val id: String,
    val name: String,
    val phone: String? = null,
    val address: String? = null,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long
) {
    fun toSupabase() = SupabaseSupplier(id, name, phone, address, createdAtEpochMs, updatedAtEpochMs)
}

@Serializable
data class SupabaseSupplier(
    val id: String,
    val name: String,
    val phone: String? = null,
    val address: String? = null,
    @SerialName("created_at_epoch_ms") val createdAtEpochMs: Long,
    @SerialName("updated_at_epoch_ms") val updatedAtEpochMs: Long
)

// --- Expenses ---
@Serializable
data class ExpenseLocal(
    val id: String,
    val amountMinorUnits: Long,
    val description: String,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long
) {
    fun toSupabase() = SupabaseExpense(id, amountMinorUnits, description, createdAtEpochMs, updatedAtEpochMs)
}

@Serializable
data class SupabaseExpense(
    val id: String,
    @SerialName("amount_minor_units") val amountMinorUnits: Long,
    val description: String,
    @SerialName("created_at_epoch_ms") val createdAtEpochMs: Long,
    @SerialName("updated_at_epoch_ms") val updatedAtEpochMs: Long
)

// --- Sales ---
@Serializable
data class SaleItemLocal(
    val productId: String,
    val quantity: Long,
    val unitPriceMinorUnits: Long,
    val lineTotalMinorUnits: Long
)

@Serializable
data class SaleLocal(
    val id: String,
    val billNumber: String,
    val totalMinorUnits: Long,
    val createdAtEpochMs: Long,
    val customerId: String? = null,
    val items: List<SaleItemLocal>
)

@Serializable
data class SupabaseSale(
    val id: String,
    @SerialName("bill_number") val billNumber: String,
    @SerialName("total_minor_units") val totalMinorUnits: Long,
    @SerialName("created_at_epoch_ms") val createdAtEpochMs: Long,
    @SerialName("customer_id") val customerId: String? = null
)

@Serializable
data class SupabaseSaleItem(
    @SerialName("sale_id") val saleId: String,
    @SerialName("product_id") val productId: String,
    val quantity: Long,
    @SerialName("unit_price_minor_units") val unitPriceMinorUnits: Long,
    @SerialName("line_total_minor_units") val lineTotalMinorUnits: Long
)

// --- Purchases ---
@Serializable
data class PurchaseItemLocal(
    val productId: String,
    val quantity: Long,
    val unitValueMinorUnits: Long,
    val lineTotalMinorUnits: Long
)

@Serializable
data class PurchaseLocal(
    val id: String,
    val supplierId: String,
    val totalMinorUnits: Long,
    val createdAtEpochMs: Long,
    val items: List<PurchaseItemLocal>
)

@Serializable
data class SupabasePurchase(
    val id: String,
    @SerialName("supplier_id") val supplierId: String,
    @SerialName("total_minor_units") val totalMinorUnits: Long,
    @SerialName("created_at_epoch_ms") val createdAtEpochMs: Long
)

@Serializable
data class SupabasePurchaseItem(
    @SerialName("purchase_id") val purchaseId: String,
    @SerialName("product_id") val productId: String,
    val quantity: Long,
    @SerialName("unit_value_minor_units") val unitValueMinorUnits: Long,
    @SerialName("line_total_minor_units") val lineTotalMinorUnits: Long
)

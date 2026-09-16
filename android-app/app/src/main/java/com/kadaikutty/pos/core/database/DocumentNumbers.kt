package com.kadaikutty.pos.core.database

suspend fun BillingDatabase.nextDocumentNumber(company: String, kind: String, prefix: String): String {
    val key = "counter:$kind:$prefix"
    val persisted = localOperationDao().get(company, key)?.toLongOrNull()
    val seed = persisted ?: (if (kind == "sale") saleDao().getAllBillNumbers(company)
        else purchaseDao().getAllOrderNumbers(company).filterNotNull()).mapNotNull {
            it.substringAfterLast('-').toLongOrNull()
        }.maxOrNull().orZero()
    val next = Math.addExact(seed, 1L)
    localOperationDao().put(LocalOperationEntity(company, key, next.toString()))
    return "$prefix-${next.toString().padStart(4, '0')}"
}
private fun Long?.orZero(): Long = this ?: 0L

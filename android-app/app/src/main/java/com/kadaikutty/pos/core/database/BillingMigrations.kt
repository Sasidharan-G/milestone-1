package com.kadaikutty.pos.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val migration1To2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `categories` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `createdAtEpochMs` INTEGER NOT NULL, `updatedAtEpochMs` INTEGER NOT NULL, `syncStatus` TEXT NOT NULL, PRIMARY KEY(`id`))")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_categories_name` ON `categories` (`name`)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `products` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `categoryId` TEXT NOT NULL, `createdAtEpochMs` INTEGER NOT NULL, `updatedAtEpochMs` INTEGER NOT NULL, `syncStatus` TEXT NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`categoryId`) REFERENCES `categories`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_products_name` ON `products` (`name`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_products_categoryId` ON `products` (`categoryId`)")
        listOf("customers", "suppliers").forEach { table -> db.execSQL("CREATE TABLE IF NOT EXISTS `$table` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `createdAtEpochMs` INTEGER NOT NULL, `updatedAtEpochMs` INTEGER NOT NULL, `syncStatus` TEXT NOT NULL, PRIMARY KEY(`id`))"); db.execSQL("CREATE INDEX IF NOT EXISTS `index_${table}_name` ON `$table` (`name`)") }
        db.execSQL("CREATE TABLE IF NOT EXISTS `expenses` (`id` TEXT NOT NULL, `createdAtEpochMs` INTEGER NOT NULL, `updatedAtEpochMs` INTEGER NOT NULL, `syncStatus` TEXT NOT NULL, PRIMARY KEY(`id`))")
    }
}
val migration2To3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `sales` (`id` TEXT NOT NULL, `billNumber` TEXT NOT NULL, `totalMinorUnits` INTEGER NOT NULL, `createdAtEpochMs` INTEGER NOT NULL, `syncStatus` TEXT NOT NULL, PRIMARY KEY(`id`))")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_sales_billNumber` ON `sales` (`billNumber`)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `sale_items` (`saleId` TEXT NOT NULL, `productId` TEXT NOT NULL, `quantity` INTEGER NOT NULL, `unitPriceMinorUnits` INTEGER NOT NULL, `lineTotalMinorUnits` INTEGER NOT NULL, PRIMARY KEY(`saleId`, `productId`), FOREIGN KEY(`saleId`) REFERENCES `sales`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(`productId`) REFERENCES `products`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_sale_items_productId` ON `sale_items` (`productId`)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `stock_movements` (`id` TEXT NOT NULL, `productId` TEXT NOT NULL, `quantityDelta` INTEGER NOT NULL, `type` TEXT NOT NULL, `referenceId` TEXT NOT NULL, `createdAtEpochMs` INTEGER NOT NULL, PRIMARY KEY(`id`))")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_stock_movements_productId` ON `stock_movements` (`productId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_stock_movements_referenceId` ON `stock_movements` (`referenceId`)")
    }
}
val migration3To4 = object : Migration(3, 4) { override fun migrate(db: SupportSQLiteDatabase) { db.execSQL("CREATE TABLE IF NOT EXISTS `purchases` (`id` TEXT NOT NULL, `supplierId` TEXT NOT NULL, `totalMinorUnits` INTEGER NOT NULL, `createdAtEpochMs` INTEGER NOT NULL, `syncStatus` TEXT NOT NULL, PRIMARY KEY(`id`))"); db.execSQL("CREATE TABLE IF NOT EXISTS `purchase_items` (`purchaseId` TEXT NOT NULL, `productId` TEXT NOT NULL, `quantity` INTEGER NOT NULL, `unitValueMinorUnits` INTEGER NOT NULL, `lineTotalMinorUnits` INTEGER NOT NULL, PRIMARY KEY(`purchaseId`, `productId`))") } }
val migration4To5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `sales` ADD COLUMN `customerId` TEXT")
        db.execSQL("ALTER TABLE `expenses` ADD COLUMN `amountMinorUnits` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `expenses` ADD COLUMN `description` TEXT NOT NULL DEFAULT ''")
    }
}
val migration5To6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `users` (`id` TEXT NOT NULL, `username` TEXT NOT NULL, `displayName` TEXT NOT NULL, `salt` TEXT NOT NULL, `verifier` TEXT NOT NULL, `permissions` TEXT NOT NULL, PRIMARY KEY(`id`))")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_users_username` ON `users` (`username`)")
    }
}
val migration6To7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `customers` ADD COLUMN `phone` TEXT")
        db.execSQL("ALTER TABLE `customers` ADD COLUMN `address` TEXT")
        db.execSQL("ALTER TABLE `suppliers` ADD COLUMN `phone` TEXT")
        db.execSQL("ALTER TABLE `suppliers` ADD COLUMN `address` TEXT")
    }
}
val migration7To8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `products` ADD COLUMN `purchasePriceMinorUnits` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `products` ADD COLUMN `salePriceMinorUnits` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `products` ADD COLUMN `unitType` TEXT NOT NULL DEFAULT 'PIECE'")
    }
}

val migration8To9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `customers` ADD COLUMN `creditLimitMinorUnits` INTEGER NOT NULL DEFAULT 0")
        
        db.execSQL("CREATE TABLE IF NOT EXISTS `customer_credits` (`id` TEXT NOT NULL, `customerId` TEXT NOT NULL, `amountMinorUnits` INTEGER NOT NULL, `reason` TEXT NOT NULL, `dateEpochMs` INTEGER NOT NULL, `syncStatus` TEXT NOT NULL, PRIMARY KEY(`id`))")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_customer_credits_customerId` ON `customer_credits` (`customerId`)")
        
        db.execSQL("CREATE TABLE IF NOT EXISTS `supplier_credits` (`id` TEXT NOT NULL, `supplierId` TEXT NOT NULL, `amountMinorUnits` INTEGER NOT NULL, `terms` TEXT NOT NULL, `dueDateEpochMs` INTEGER NOT NULL, `dateEpochMs` INTEGER NOT NULL, `syncStatus` TEXT NOT NULL, PRIMARY KEY(`id`))")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_supplier_credits_supplierId` ON `supplier_credits` (`supplierId`)")
    }
}

val migration9To10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        val defaultCompany = "00000000-0000-0000-0000-000000000000"

        // 1. Categories
        db.execSQL("ALTER TABLE `categories` RENAME TO `temp_categories`")
        db.execSQL("CREATE TABLE `categories` (`id` TEXT NOT NULL, `companyId` TEXT NOT NULL, `name` TEXT NOT NULL, `createdAtEpochMs` INTEGER NOT NULL, `updatedAtEpochMs` INTEGER NOT NULL, `syncStatus` TEXT NOT NULL, PRIMARY KEY(`id`))")
        db.execSQL("INSERT INTO `categories` SELECT id, '$defaultCompany', name, createdAtEpochMs, updatedAtEpochMs, syncStatus FROM temp_categories")
        db.execSQL("CREATE UNIQUE INDEX `index_categories_companyId_name` ON `categories` (`companyId`, `name`)")
        db.execSQL("DROP TABLE `temp_categories`")

        // 2. Products
        db.execSQL("ALTER TABLE `products` RENAME TO `temp_products`")
        db.execSQL("CREATE TABLE `products` (`id` TEXT NOT NULL, `companyId` TEXT NOT NULL, `name` TEXT NOT NULL, `categoryId` TEXT NOT NULL, `purchasePriceMinorUnits` INTEGER NOT NULL, `salePriceMinorUnits` INTEGER NOT NULL, `unitType` TEXT NOT NULL, `createdAtEpochMs` INTEGER NOT NULL, `updatedAtEpochMs` INTEGER NOT NULL, `syncStatus` TEXT NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`categoryId`) REFERENCES `categories`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT)")
        db.execSQL("INSERT INTO `products` SELECT id, '$defaultCompany', name, categoryId, purchasePriceMinorUnits, salePriceMinorUnits, unitType, createdAtEpochMs, updatedAtEpochMs, syncStatus FROM temp_products")
        db.execSQL("CREATE UNIQUE INDEX `index_products_companyId_name` ON `products` (`companyId`, `name`)")
        db.execSQL("CREATE INDEX `index_products_categoryId` ON `products` (`categoryId`)")
        db.execSQL("DROP TABLE `temp_products`")

        // 3. Customers
        db.execSQL("ALTER TABLE `customers` RENAME TO `temp_customers`")
        db.execSQL("CREATE TABLE `customers` (`id` TEXT NOT NULL, `companyId` TEXT NOT NULL, `name` TEXT NOT NULL, `phone` TEXT, `address` TEXT, `creditLimitMinorUnits` INTEGER NOT NULL DEFAULT 0, `createdAtEpochMs` INTEGER NOT NULL, `updatedAtEpochMs` INTEGER NOT NULL, `syncStatus` TEXT NOT NULL, PRIMARY KEY(`id`))")
        db.execSQL("INSERT INTO `customers` SELECT id, '$defaultCompany', name, phone, address, creditLimitMinorUnits, createdAtEpochMs, updatedAtEpochMs, syncStatus FROM temp_customers")
        db.execSQL("CREATE INDEX `index_customers_companyId_name` ON `customers` (`companyId`, `name`)")
        db.execSQL("CREATE INDEX `index_customers_companyId` ON `customers` (`companyId`)")
        db.execSQL("DROP TABLE `temp_customers`")

        // 4. Suppliers
        db.execSQL("ALTER TABLE `suppliers` RENAME TO `temp_suppliers`")
        db.execSQL("CREATE TABLE `suppliers` (`id` TEXT NOT NULL, `companyId` TEXT NOT NULL, `name` TEXT NOT NULL, `phone` TEXT, `address` TEXT, `createdAtEpochMs` INTEGER NOT NULL, `updatedAtEpochMs` INTEGER NOT NULL, `syncStatus` TEXT NOT NULL, PRIMARY KEY(`id`))")
        db.execSQL("INSERT INTO `suppliers` SELECT id, '$defaultCompany', name, phone, address, createdAtEpochMs, updatedAtEpochMs, syncStatus FROM temp_suppliers")
        db.execSQL("CREATE INDEX `index_suppliers_companyId_name` ON `suppliers` (`companyId`, `name`)")
        db.execSQL("CREATE INDEX `index_suppliers_companyId` ON `suppliers` (`companyId`)")
        db.execSQL("DROP TABLE `temp_suppliers`")

        // 5. Expenses
        db.execSQL("ALTER TABLE `expenses` RENAME TO `temp_expenses`")
        db.execSQL("CREATE TABLE `expenses` (`id` TEXT NOT NULL, `companyId` TEXT NOT NULL, `amountMinorUnits` INTEGER NOT NULL, `description` TEXT NOT NULL, `createdAtEpochMs` INTEGER NOT NULL, `updatedAtEpochMs` INTEGER NOT NULL, `syncStatus` TEXT NOT NULL, PRIMARY KEY(`id`))")
        db.execSQL("INSERT INTO `expenses` SELECT id, '$defaultCompany', amountMinorUnits, description, createdAtEpochMs, updatedAtEpochMs, syncStatus FROM temp_expenses")
        db.execSQL("CREATE INDEX `index_expenses_companyId` ON `expenses` (`companyId`)")
        db.execSQL("DROP TABLE `temp_expenses`")

        // 6. Sales
        db.execSQL("ALTER TABLE `sales` RENAME TO `temp_sales`")
        db.execSQL("CREATE TABLE `sales` (`id` TEXT NOT NULL, `companyId` TEXT NOT NULL, `billNumber` TEXT NOT NULL, `totalMinorUnits` INTEGER NOT NULL, `createdAtEpochMs` INTEGER NOT NULL, `syncStatus` TEXT NOT NULL, `customerId` TEXT, PRIMARY KEY(`id`))")
        db.execSQL("INSERT INTO `sales` SELECT id, '$defaultCompany', billNumber, totalMinorUnits, createdAtEpochMs, syncStatus, customerId FROM temp_sales")
        db.execSQL("CREATE UNIQUE INDEX `index_sales_companyId_billNumber` ON `sales` (`companyId`, `billNumber`)")
        db.execSQL("CREATE INDEX `index_sales_companyId` ON `sales` (`companyId`)")
        db.execSQL("DROP TABLE `temp_sales`")

        // 7. Sale Items
        db.execSQL("ALTER TABLE `sale_items` RENAME TO `temp_sale_items`")
        db.execSQL("CREATE TABLE `sale_items` (`companyId` TEXT NOT NULL, `saleId` TEXT NOT NULL, `productId` TEXT NOT NULL, `quantity` INTEGER NOT NULL, `unitPriceMinorUnits` INTEGER NOT NULL, `lineTotalMinorUnits` INTEGER NOT NULL, PRIMARY KEY(`saleId`, `productId`), FOREIGN KEY(`saleId`) REFERENCES `sales`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(`productId`) REFERENCES `products`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT)")
        db.execSQL("INSERT INTO `sale_items` SELECT '$defaultCompany', saleId, productId, quantity, unitPriceMinorUnits, lineTotalMinorUnits FROM temp_sale_items")
        db.execSQL("CREATE INDEX `index_sale_items_productId` ON `sale_items` (`productId`)")
        db.execSQL("CREATE INDEX `index_sale_items_companyId` ON `sale_items` (`companyId`)")
        db.execSQL("DROP TABLE `temp_sale_items`")

        // 8. Stock Movements
        db.execSQL("ALTER TABLE `stock_movements` RENAME TO `temp_stock_movements`")
        db.execSQL("CREATE TABLE `stock_movements` (`id` TEXT NOT NULL, `companyId` TEXT NOT NULL, `productId` TEXT NOT NULL, `quantityDelta` INTEGER NOT NULL, `type` TEXT NOT NULL, `referenceId` TEXT NOT NULL, `createdAtEpochMs` INTEGER NOT NULL, PRIMARY KEY(`id`))")
        db.execSQL("INSERT INTO `stock_movements` SELECT id, '$defaultCompany', productId, quantityDelta, type, referenceId, createdAtEpochMs FROM temp_stock_movements")
        db.execSQL("CREATE INDEX `index_stock_movements_companyId_productId` ON `stock_movements` (`companyId`, `productId`)")
        db.execSQL("CREATE INDEX `index_stock_movements_companyId_referenceId` ON `stock_movements` (`companyId`, `referenceId`)")
        db.execSQL("CREATE INDEX `index_stock_movements_companyId` ON `stock_movements` (`companyId`)")
        db.execSQL("DROP TABLE `temp_stock_movements`")

        // 9. Purchases
        db.execSQL("ALTER TABLE `purchases` RENAME TO `temp_purchases`")
        db.execSQL("CREATE TABLE `purchases` (`id` TEXT NOT NULL, `companyId` TEXT NOT NULL, `supplierId` TEXT NOT NULL, `totalMinorUnits` INTEGER NOT NULL, `createdAtEpochMs` INTEGER NOT NULL, `syncStatus` TEXT NOT NULL, PRIMARY KEY(`id`))")
        db.execSQL("INSERT INTO `purchases` SELECT id, '$defaultCompany', supplierId, totalMinorUnits, createdAtEpochMs, syncStatus FROM temp_purchases")
        db.execSQL("CREATE INDEX `index_purchases_companyId` ON `purchases` (`companyId`)")
        db.execSQL("DROP TABLE `temp_purchases`")

        // 10. Purchase Items
        db.execSQL("ALTER TABLE `purchase_items` RENAME TO `temp_purchase_items`")
        db.execSQL("CREATE TABLE `purchase_items` (`companyId` TEXT NOT NULL, `purchaseId` TEXT NOT NULL, `productId` TEXT NOT NULL, `quantity` INTEGER NOT NULL, `unitValueMinorUnits` INTEGER NOT NULL, `lineTotalMinorUnits` INTEGER NOT NULL, PRIMARY KEY(`purchaseId`, `productId`))")
        db.execSQL("INSERT INTO `purchase_items` SELECT '$defaultCompany', purchaseId, productId, quantity, unitValueMinorUnits, lineTotalMinorUnits FROM temp_purchase_items")
        db.execSQL("CREATE INDEX `index_purchase_items_companyId` ON `purchase_items` (`companyId`)")
        db.execSQL("DROP TABLE `temp_purchase_items`")

        // 11. Customer Credits
        db.execSQL("ALTER TABLE `customer_credits` RENAME TO `temp_customer_credits`")
        db.execSQL("CREATE TABLE `customer_credits` (`id` TEXT NOT NULL, `companyId` TEXT NOT NULL, `customerId` TEXT NOT NULL, `amountMinorUnits` INTEGER NOT NULL, `reason` TEXT NOT NULL, `dateEpochMs` INTEGER NOT NULL, `syncStatus` TEXT NOT NULL, PRIMARY KEY(`id`))")
        db.execSQL("INSERT INTO `customer_credits` SELECT id, '$defaultCompany', customerId, amountMinorUnits, reason, dateEpochMs, syncStatus FROM temp_customer_credits")
        db.execSQL("CREATE INDEX `index_customer_credits_companyId_customerId` ON `customer_credits` (`companyId`, `customerId`)")
        db.execSQL("CREATE INDEX `index_customer_credits_companyId` ON `customer_credits` (`companyId`)")
        db.execSQL("DROP TABLE `temp_customer_credits`")

        // 12. Supplier Credits
        db.execSQL("ALTER TABLE `supplier_credits` RENAME TO `temp_supplier_credits`")
        db.execSQL("CREATE TABLE `supplier_credits` (`id` TEXT NOT NULL, `companyId` TEXT NOT NULL, `supplierId` TEXT NOT NULL, `amountMinorUnits` INTEGER NOT NULL, `terms` TEXT NOT NULL, `dueDateEpochMs` INTEGER NOT NULL, `dateEpochMs` INTEGER NOT NULL, `syncStatus` TEXT NOT NULL, PRIMARY KEY(`id`))")
        db.execSQL("INSERT INTO `supplier_credits` SELECT id, '$defaultCompany', supplierId, amountMinorUnits, terms, dueDateEpochMs, dateEpochMs, syncStatus FROM temp_supplier_credits")
        db.execSQL("CREATE INDEX `index_supplier_credits_companyId_supplierId` ON `supplier_credits` (`companyId`, `supplierId`)")
        db.execSQL("CREATE INDEX `index_supplier_credits_companyId` ON `supplier_credits` (`companyId`)")
        db.execSQL("DROP TABLE `temp_supplier_credits`")

        // 13. Sync Queue
        db.execSQL("ALTER TABLE `sync_queue` RENAME TO `temp_sync_queue`")
        db.execSQL("CREATE TABLE `sync_queue` (`id` TEXT NOT NULL, `companyId` TEXT NOT NULL, `entityType` TEXT NOT NULL, `entityId` TEXT NOT NULL, `operation` TEXT NOT NULL, `payload` TEXT NOT NULL, `status` TEXT NOT NULL, `attemptCount` INTEGER NOT NULL, `createdAtEpochMs` INTEGER NOT NULL, `updatedAtEpochMs` INTEGER NOT NULL, `lastError` TEXT, PRIMARY KEY(`id`))")
        db.execSQL("INSERT INTO `sync_queue` SELECT id, '$defaultCompany', entityType, entityId, operation, payload, status, attemptCount, createdAtEpochMs, updatedAtEpochMs, lastError FROM temp_sync_queue")
        db.execSQL("CREATE INDEX `index_sync_queue_companyId` ON `sync_queue` (`companyId`)")
        db.execSQL("DROP TABLE `temp_sync_queue`")

        // 14. Users Table Alteration (Add companyId, role, lastOnlineVerifiedAt, offlineValidUntil)
        db.execSQL("ALTER TABLE `users` ADD COLUMN `companyId` TEXT NOT NULL DEFAULT '$defaultCompany'")
        db.execSQL("ALTER TABLE `users` ADD COLUMN `role` TEXT NOT NULL DEFAULT 'CASHIER'")
        db.execSQL("ALTER TABLE `users` ADD COLUMN `lastOnlineVerifiedAt` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `users` ADD COLUMN `offlineValidUntil` INTEGER NOT NULL DEFAULT 0")
    }
}

val migration10To11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `sales` ADD COLUMN `paymentMode` TEXT NOT NULL DEFAULT 'CASH'")
    }
}

val migration11To12 = object : Migration(11, 12) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `sales` ADD COLUMN `paidCashMinorUnits` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `sales` ADD COLUMN `paidUpiMinorUnits` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `sales` ADD COLUMN `creditAppliedMinorUnits` INTEGER NOT NULL DEFAULT 0")
    }
}

val migration12To13 = object : Migration(12, 13) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `draft_cart_items` (`id` TEXT NOT NULL, `companyId` TEXT NOT NULL, `productId` TEXT NOT NULL, `productName` TEXT NOT NULL, `quantity` INTEGER NOT NULL, `unitPriceMinorUnits` INTEGER NOT NULL, `unitType` TEXT NOT NULL, PRIMARY KEY(`id`))")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_draft_cart_items_companyId` ON `draft_cart_items` (`companyId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_draft_cart_items_productId` ON `draft_cart_items` (`productId`)")
        
        db.execSQL("CREATE TABLE IF NOT EXISTS `shifts` (`id` TEXT NOT NULL, `companyId` TEXT NOT NULL, `closedAtEpochMs` INTEGER NOT NULL, `expectedCashMinorUnits` INTEGER NOT NULL, `declaredCashMinorUnits` INTEGER NOT NULL, `discrepancyMinorUnits` INTEGER NOT NULL, `closedByUserId` TEXT NOT NULL, PRIMARY KEY(`id`))")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_shifts_companyId` ON `shifts` (`companyId`)")
    }
}

val migration13To14 = object : Migration(13, 14) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `sync_queue` ADD COLUMN `lastSyncedAtEpochMs` INTEGER NOT NULL DEFAULT 0")
    }
}

val migration14To15 = object : Migration(14, 15) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `sync_dead_letter` (
                `id` TEXT NOT NULL,
                `companyId` TEXT NOT NULL,
                `entityType` TEXT NOT NULL,
                `entityId` TEXT NOT NULL,
                `operation` TEXT NOT NULL,
                `payload` TEXT NOT NULL,
                `lastError` TEXT NOT NULL,
                `attemptCount` INTEGER NOT NULL,
                `createdAtEpochMs` INTEGER NOT NULL,
                `lastAttemptAtEpochMs` INTEGER NOT NULL,
                `originalQueueId` TEXT NOT NULL,
                PRIMARY KEY(`id`)
            )
        """)
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_sync_dead_letter_companyId` ON `sync_dead_letter` (`companyId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_sync_dead_letter_entityType` ON `sync_dead_letter` (`entityType`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_sync_dead_letter_entityId` ON `sync_dead_letter` (`entityId`)")
    }
}

val migration15To16 = object : Migration(15, 16) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Covering indexes for report queries
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_sales_companyId_createdAt` ON `sales` (`companyId`, `createdAtEpochMs`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_sale_items_companyId_saleId` ON `sale_items` (`companyId`, `saleId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_purchases_companyId_createdAt` ON `purchases` (`companyId`, `createdAtEpochMs`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_purchase_items_companyId_purchaseId` ON `purchase_items` (`companyId`, `purchaseId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_stock_movements_companyId_productId_createdAt` ON `stock_movements` (`companyId`, `productId`, `createdAtEpochMs`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_categories_companyId_syncStatus` ON `categories` (`companyId`, `syncStatus`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_products_companyId_syncStatus` ON `products` (`companyId`, `syncStatus`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_customers_companyId_syncStatus` ON `customers` (`companyId`, `syncStatus`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_suppliers_companyId_syncStatus` ON `suppliers` (`companyId`, `syncStatus`)")
    }
}

val migration16To17 = object : Migration(16, 17) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `products` ADD COLUMN `barcode` TEXT")
        db.execSQL("ALTER TABLE `products` ADD COLUMN `minStockLevel` REAL NOT NULL DEFAULT 0.0")
        
        db.execSQL("ALTER TABLE `sales` ADD COLUMN `discountMinorUnits` INTEGER NOT NULL DEFAULT 0")
        
        db.execSQL("ALTER TABLE `sale_items` ADD COLUMN `discountMinorUnits` INTEGER NOT NULL DEFAULT 0")
    }
}

val migration17To18 = object : Migration(17, 18) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `purchases` ADD COLUMN `invoiceNumber` TEXT")
        db.execSQL("ALTER TABLE `purchases` ADD COLUMN `notes` TEXT")
        db.execSQL("ALTER TABLE `purchases` ADD COLUMN `paymentMode` TEXT NOT NULL DEFAULT 'CASH'")
        db.execSQL("ALTER TABLE `purchases` ADD COLUMN `paidCashMinorUnits` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `purchases` ADD COLUMN `paidUpiMinorUnits` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `purchases` ADD COLUMN `creditAppliedMinorUnits` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `purchases` ADD COLUMN `orderNumber` TEXT")
    }
}


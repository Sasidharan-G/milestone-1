package com.company.billing.core.database

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


package com.kadaikutty.pos.core.sample

import com.kadaikutty.pos.core.database.BillingDatabase
import com.kadaikutty.pos.core.common.newRecordId
import com.kadaikutty.pos.core.sync.SyncStatus
import com.kadaikutty.pos.feature.masters.data.*
import com.kadaikutty.pos.feature.billing.data.*
import com.kadaikutty.pos.feature.purchase.data.*
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.tasks.await

class SampleDataGenerator(
    private val database: BillingDatabase,
    private val firestore: FirebaseFirestore
) {

    suspend fun insert100DemoRecords(companyId: String): Int = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        var count = 0

        // 1. Categories (10 items)
        val catNames = listOf(
            "Beverages", "Snacks & Biscuits", "Dairy & Milk", "Staples & Grains",
            "Personal Care", "Household & Cleaning", "Fruits & Veggies",
            "Spices & Masalas", "Bakery & Breads", "Edible Oils & Ghee"
        )
        val categories = catNames.map { name ->
            CategoryEntity(
                id = newRecordId(),
                companyId = companyId,
                name = name,
                createdAtEpochMs = now,
                updatedAtEpochMs = now,
                syncStatus = SyncStatus.LOCAL_ONLY
            )
        }
        database.masterDao().insertCategories(categories)
        count += categories.size

        val catMap = categories.associateBy { it.name }

        // 2. Products (50 items)
        val rawProducts = listOf(
            Triple("Tata Salt 1kg", "Staples & Grains", Pair(2200L, 2800L)),
            Triple("Aashirvaad Shudh Chakki Atta 5kg", "Staples & Grains", Pair(21000L, 26500L)),
            Triple("Fortune Sunlite Refined Sunflower Oil 1L", "Edible Oils & Ghee", Pair(11500L, 14000L)),
            Triple("Gold Winner Sunflower Oil 1L", "Edible Oils & Ghee", Pair(11000L, 13500L)),
            Triple("India Gate Basmati Rice Feast Rozzana 1kg", "Staples & Grains", Pair(8000L, 10500L)),
            Triple("Toor Dal Premium 1kg", "Staples & Grains", Pair(13500L, 16000L)),
            Triple("Moong Dal Washed 1kg", "Staples & Grains", Pair(11000L, 13500L)),
            Triple("Urad Dal White Whole 1kg", "Staples & Grains", Pair(12000L, 14500L)),
            Triple("Sugar Crystal Clean 1kg", "Staples & Grains", Pair(3800L, 4400L)),
            Triple("Aachi Chilli Powder 500g", "Spices & Masalas", Pair(14000L, 17500L)),
            Triple("Aachi Turmeric Powder 200g", "Spices & Masalas", Pair(4500L, 6000L)),
            Triple("Everest Garam Masala 100g", "Spices & Masalas", Pair(6500L, 8200L)),
            Triple("Sakthi Sambar Powder 200g", "Spices & Masalas", Pair(4800L, 6200L)),
            Triple("Britannia Good Day Butter 200g", "Snacks & Biscuits", Pair(3500L, 4500L)),
            Triple("Parle-G Gold Biscuits 1kg", "Snacks & Biscuits", Pair(11000L, 13000L)),
            Triple("Oreo Vanilla Creme Biscuits 120g", "Snacks & Biscuits", Pair(2800L, 3500L)),
            Triple("Sunfeast Dark Fantasy Choco Fills 300g", "Snacks & Biscuits", Pair(11500L, 15000L)),
            Triple("Lays Classic Salted Potato Chips 50g", "Snacks & Biscuits", Pair(1600L, 2000L)),
            Triple("Kurkure Masala Munch 90g", "Snacks & Biscuits", Pair(1600L, 2000L)),
            Triple("Bingo Mad Angles Achaari Masti 66g", "Snacks & Biscuits", Pair(1600L, 2000L)),
            Triple("Amul Butter 500g", "Dairy & Milk", Pair(24000L, 27500L)),
            Triple("Amul Cheese Slices 200g (10 Slices)", "Dairy & Milk", Pair(12000L, 14500L)),
            Triple("Nandini Pasteurized Toned Milk 500ml", "Dairy & Milk", Pair(2200L, 2500L)),
            Triple("Aavin Standardized Milk Green 500ml", "Dairy & Milk", Pair(2100L, 2400L)),
            Triple("Milky Mist Paneer 200g", "Dairy & Milk", Pair(8500L, 11000L)),
            Triple("Milky Mist Set Curd 500g", "Dairy & Milk", Pair(3200L, 4000L)),
            Triple("Coca-Cola Soft Drink 750ml", "Beverages", Pair(3500L, 4500L)),
            Triple("Sprite Lime Flavoured Drink 750ml", "Beverages", Pair(3500L, 4500L)),
            Triple("Thums Up Charged 250ml Can", "Beverages", Pair(3000L, 4000L)),
            Triple("Red Bull Energy Drink 250ml", "Beverages", Pair(10500L, 12500L)),
            Triple("Bru Instant Coffee 200g Pouch", "Beverages", Pair(28000L, 34000L)),
            Triple("Tata Tea Gold 500g", "Beverages", Pair(26000L, 31000L)),
            Triple("Colgate Strong Teeth Toothpaste 150g", "Personal Care", Pair(7800L, 9800L)),
            Triple("Dettol Original Bathing Soap (Pack of 3)", "Personal Care", Pair(11000L, 14000L)),
            Triple("Head & Shoulders Anti-Dandruff Shampoo 180ml", "Personal Care", Pair(14000L, 18000L)),
            Triple("Dove Cream Beauty Bathing Bar 100g", "Personal Care", Pair(4800L, 6200L)),
            Triple("Pears Pure & Gentle Soap 125g", "Personal Care", Pair(6000L, 7800L)),
            Triple("Surf Excel Easy Wash Detergent Powder 1kg", "Household & Cleaning", Pair(11000L, 14000L)),
            Triple("Ariel Matic Front Load Detergent 1kg", "Household & Cleaning", Pair(19000L, 24000L)),
            Triple("Vim Dishwash Liquid Gel 500ml", "Household & Cleaning", Pair(9500L, 12500L)),
            Triple("Harpic Power Plus Disinfectant Toilet Cleaner 1L", "Household & Cleaning", Pair(16500L, 20500L)),
            Triple("Lizol Disinfectant Floor Cleaner Citrus 1L", "Household & Cleaning", Pair(17000L, 21500L)),
            Triple("Comfort After Wash Fabric Conditioner 860ml", "Household & Cleaning", Pair(19000L, 23500L)),
            Triple("Fresh Sweet Potato 1kg", "Fruits & Veggies", Pair(3000L, 4500L)),
            Triple("Fresh Red Onions 1kg", "Fruits & Veggies", Pair(2500L, 3500L)),
            Triple("Fresh Bangalore Tomatoes 1kg", "Fruits & Veggies", Pair(2000L, 3000L)),
            Triple("Modern 100% Whole Wheat Bread 400g", "Bakery & Breads", Pair(3800L, 4800L)),
            Triple("Britannia Milk Bread 400g", "Bakery & Breads", Pair(3500L, 4500L)),
            Triple("Maggi 2-Minute Masala Instant Noodles 70g", "Snacks & Biscuits", Pair(1100L, 1400L)),
            Triple("Yippee Magic Masala Noodles 240g", "Snacks & Biscuits", Pair(3800L, 4800L))
        )

        val products = rawProducts.mapIndexed { idx, (name, cat, prices) ->
            val catId = catMap[cat]?.id ?: categories.first().id
            ProductEntity(
                id = newRecordId(),
                companyId = companyId,
                name = name,
                categoryId = catId,
                purchasePriceMinorUnits = prices.first,
                salePriceMinorUnits = prices.second,
                unitType = if (name.contains("Onions") || name.contains("Tomatoes") || name.contains("Potato")) "KG" else "PIECE",
                barcode = "8901030000%02d".format(idx + 1),
                minStockLevel = 10.0,
                createdAtEpochMs = now,
                updatedAtEpochMs = now,
                syncStatus = SyncStatus.LOCAL_ONLY
            )
        }
        database.masterDao().insertProducts(products)
        count += products.size

        // 3. Customers (15 items)
        val rawCustomers = listOf(
            Pair("Karthik Ramanathan", "9840112345"),
            Pair("Anitha Sundaram", "9841223456"),
            Pair("Vijay Kumar", "9444334567"),
            Pair("Priya Natarajan", "9884445678"),
            Pair("Saravanan Meenakshi", "9790556789"),
            Pair("Deepa Venkatesh", "9840667890"),
            Pair("Manikandan Rajan", "9445778901"),
            Pair("Sangeetha Balaji", "9842889012"),
            Pair("Murugan Palanivel", "9894990123"),
            Pair("Divya Selvakumar", "9789112233"),
            Pair("Praveen Chandran", "9940223344"),
            Pair("Kavitha Ramesh", "9840334455"),
            Pair("Senthil Velan", "9444445566"),
            Pair("Revathi Shankar", "9884556677"),
            Pair("Ganesh Moorthy", "9790667788")
        )
        val customers = rawCustomers.map { (name, phone) ->
            CustomerEntity(
                id = newRecordId(),
                companyId = companyId,
                name = name,
                phone = phone,
                address = "Chennai, Tamil Nadu",
                creditLimitMinorUnits = 500000L,
                createdAtEpochMs = now,
                updatedAtEpochMs = now,
                syncStatus = SyncStatus.LOCAL_ONLY
            )
        }
        database.masterDao().insertCustomers(customers)
        count += customers.size

        // 4. Suppliers (10 items)
        val rawSuppliers = listOf(
            Pair("Metro Mega Wholesale Ltd", "9840998877"),
            Pair("Reliance Distribution Agency", "9841887766"),
            Pair("ITC Fast-Moving Depot", "9444776655"),
            Pair("Hindustan Unilever Stockist", "9884665544"),
            Pair("Nestle Regional Distributors", "9790554433"),
            Pair("Amul Dairy Supply Hub", "9840443322"),
            Pair("Britannia Bakeries Agency", "9445332211"),
            Pair("Tata Consumer Logistics", "9842221100"),
            Pair("Sakthi Spices Whole Hub", "9894110099"),
            Pair("Parle Agro Beverages Depot", "9789009988")
        )
        val suppliers = rawSuppliers.map { (name, phone) ->
            SupplierEntity(
                id = newRecordId(),
                companyId = companyId,
                name = name,
                phone = phone,
                address = "Tamil Nadu, India",
                createdAtEpochMs = now,
                updatedAtEpochMs = now,
                syncStatus = SyncStatus.LOCAL_ONLY
            )
        }
        database.masterDao().insertSuppliers(suppliers)
        count += suppliers.size

        // 5. Initial Inward Stock for all 50 products (50 units each)
        val movements = products.map { p ->
            StockMovementEntity(
                id = newRecordId(),
                companyId = companyId,
                productId = p.id,
                quantityDelta = 50L,
                type = "INWARD_PURCHASE",
                referenceId = "INITIAL-DEMO-STOCK",
                createdAtEpochMs = now
            )
        }
        database.saleDao().insertStockMovements(movements)

        // 6. Expenses (5 sample expenses)
        val expenses = listOf(
            Pair("Shop Electrical Utility Bill", 245000L),
            Pair("Staff Refreshments & Tea", 45000L),
            Pair("Commercial Garbage Disposal Fee", 30000L),
            Pair("Store Thermal Paper Rolls (Pack of 10)", 65000L),
            Pair("Counter Cleaning Supplies", 28000L)
        ).map { (desc, amt) ->
            ExpenseEntity(
                id = newRecordId(),
                companyId = companyId,
                amountMinorUnits = amt,
                description = desc,
                createdAtEpochMs = now,
                updatedAtEpochMs = now,
                syncStatus = SyncStatus.LOCAL_ONLY
            )
        }
        database.masterDao().insertExpenses(expenses)
        count += expenses.size

        // 7. Completed Sales Invoices (10 Realistic Bills)
        for (i in 1..10) {
            val saleId = newRecordId()
            val billNumber = "DEMO-INV-%04d".format(i)
            val p1 = products[(i * 3) % products.size]
            val p2 = products[(i * 3 + 1) % products.size]
            
            val item1 = SaleItemEntity(
                companyId = companyId,
                saleId = saleId,
                productId = p1.id,
                quantity = 2L,
                unitPriceMinorUnits = p1.salePriceMinorUnits,
                lineTotalMinorUnits = p1.salePriceMinorUnits * 2L
            )
            val item2 = SaleItemEntity(
                companyId = companyId,
                saleId = saleId,
                productId = p2.id,
                quantity = 1L,
                unitPriceMinorUnits = p2.salePriceMinorUnits,
                lineTotalMinorUnits = p2.salePriceMinorUnits
            )
            val total = item1.lineTotalMinorUnits + item2.lineTotalMinorUnits
            val isUpi = (i % 2 == 0)
            
            val sale = SaleEntity(
                id = saleId,
                companyId = companyId,
                billNumber = billNumber,
                totalMinorUnits = total,
                discountMinorUnits = 0L,
                customerId = if (i % 3 == 0) customers[i % customers.size].id else null,
                paymentMode = if (isUpi) "UPI" else "CASH",
                paidCashMinorUnits = if (isUpi) 0L else total,
                paidUpiMinorUnits = if (isUpi) total else 0L,
                creditAppliedMinorUnits = 0L,
                createdAtEpochMs = now - (i * 3600000L),
                syncStatus = SyncStatus.LOCAL_ONLY
            )

            val saleMovements = listOf(
                StockMovementEntity(
                    id = newRecordId(),
                    companyId = companyId,
                    productId = p1.id,
                    quantityDelta = -2L,
                    type = "OUTWARD_SALE",
                    referenceId = saleId,
                    createdAtEpochMs = now - (i * 3600000L)
                ),
                StockMovementEntity(
                    id = newRecordId(),
                    companyId = companyId,
                    productId = p2.id,
                    quantityDelta = -1L,
                    type = "OUTWARD_SALE",
                    referenceId = saleId,
                    createdAtEpochMs = now - (i * 3600000L)
                )
            )

            database.saleDao().saveSale(sale, listOf(item1, item2), saleMovements)
            count += 1
        }

        database.invalidationTracker.refreshVersionsAsync()
        count
    }

    suspend fun clearAllData(companyId: String, clearCloud: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        try {
            // 1. Wipe Room Local SQLite Tables
            database.masterDao().deleteCategoriesByCompany(companyId)
            database.masterDao().deleteProductsByCompany(companyId)
            database.masterDao().deleteCustomersByCompany(companyId)
            database.masterDao().deleteSuppliersByCompany(companyId)
            database.masterDao().deleteExpensesByCompany(companyId)
            database.masterDao().deleteCustomerCreditsByCompany(companyId)
            database.masterDao().deleteSupplierCreditsByCompany(companyId)

            database.saleDao().deleteSalesByCompany(companyId)
            database.saleDao().deleteSaleItemsByCompany(companyId)
            database.saleDao().deleteStockMovementsByCompany(companyId)

            database.purchaseDao().deletePurchasesByCompany(companyId)
            database.purchaseDao().deletePurchaseItemsByCompany(companyId)
            database.draftCartDao().clearCart(companyId)

            // Clear sync queues
            database.syncQueueDao().clearByCompany(companyId)

            // 2. Optionally wipe Cloud Firestore data safely if requested
            if (clearCloud && companyId.isNotBlank()) {
                val collections = listOf("categories", "products", "customers", "suppliers", "expenses", "sales", "purchases", "customer_credits", "supplier_credits")
                for (col in collections) {
                    try {
                        val snapshot = firestore.collection("users").document(companyId).collection(col).get().await()
                        for (doc in snapshot.documents) {
                            doc.reference.delete().await()
                        }
                    } catch (ignored: Exception) {}
                }
            }

            database.invalidationTracker.refreshVersionsAsync()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}

package com.company.billing.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

@RunWith(AndroidJUnit4::class)
class MigrationTest {
    private val TEST_DB = "migration-test"

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        BillingDatabase::class.java.canonicalName,
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun migrate3To4() {
        var db = helper.createDatabase(TEST_DB, 3)

        // Add some dummy data to ensure migration doesn't fail with data present
        db.execSQL("INSERT INTO products (id, name, categoryId, createdAtEpochMs, updatedAtEpochMs, syncStatus) VALUES ('prod1', 'Product 1', 'cat1', 0, 0, 'PENDING')")
        
        // Prepare for the next version.
        db.close()

        // Re-open the database with version 4 and provide MIGRATION_3_4
        // as the migration process.
        db = helper.runMigrationsAndValidate(TEST_DB, 4, true, migration3To4)

        // Verify the tables exist by querying them
        val cursor = db.query("SELECT * FROM purchases")
        assertTrue(cursor.columnCount > 0)
        
        val itemsCursor = db.query("SELECT * FROM purchase_items")
        assertTrue(itemsCursor.columnCount > 0)
        
        cursor.close()
        itemsCursor.close()
    }
}

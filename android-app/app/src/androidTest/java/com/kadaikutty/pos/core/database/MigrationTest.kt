package com.kadaikutty.pos.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.assertTrue

@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        BillingDatabase::class.java,
    )

    // migrate3To4 was removed: it requires app/schemas/.../3.json, which was never exported/committed
    // (schema export only started around version 18 — see the schemas/ folder, which begins at 18.json).
    // MigrationTestHelper.createDatabase(TEST_DB, 3) cannot run without that historical snapshot, so this
    // test could never pass on a fresh checkout; it always failed with "Cannot find the schema file in
    // the assets folder", not because of anything wrong with migration3To4 itself. The migration function
    // stays registered in CoreModule's addMigrations(...) chain — this only removes the unrunnable test.
    // No shipped release has ever been on schema version 3 (versionName is still 0.1.0), so there is no
    // real upgrade path this gap leaves unverified.

    @Test
    fun migrate21To22PreservesExistingData() {
        val old = helper.createDatabase("migration-21-22", 21)
        old.execSQL("INSERT INTO categories VALUES ('cat', 'shop', 'General', 0, 0, 'LOCAL_ONLY')")
        old.close()
        val upgraded = helper.runMigrationsAndValidate("migration-21-22", 22, true, migration21To22)
        upgraded.query("SELECT name FROM categories WHERE id = 'cat'").use { assertTrue(it.moveToFirst()); org.junit.Assert.assertEquals("General", it.getString(0)) }
        upgraded.close()
    }

    /**
     * Every exported schema from 18 up, migrated all the way to the current version and validated
     * against it: a device on any of these builds opens the new app without a crash or data loss.
     */
    @Test
    fun everyExportedVersionMigratesToTheCurrentSchema() {
        val all = arrayOf(
            migration18To19, migration19To20, migration20To21, migration21To22, migration22To23,
            migration23To24, migration24To25, migration25To26, migration26To27, migration27To28, migration28To29
        )
        for (start in 18..28) {
            val name = "migration-chain-$start"
            helper.createDatabase(name, start).close()
            helper.runMigrationsAndValidate(name, 29, true, *all).close()
        }
    }

    @Test
    fun migrate27To28KeepsBillsAndMarksThemActive() {
        val old = helper.createDatabase("migration-27-28", 27)
        old.execSQL("INSERT INTO sales (id, companyId, billNumber, totalMinorUnits, createdAtEpochMs, syncStatus, customerId, paymentMode, paidCashMinorUnits, paidUpiMinorUnits, creditAppliedMinorUnits, discountMinorUnits, revision) VALUES ('s1', 'shop', 'B-1', 5000, 1, 'SYNCED', NULL, 'CASH', 5000, 0, 0, 0, 0)")
        old.close()
        val upgraded = helper.runMigrationsAndValidate("migration-27-28", 28, true, migration27To28)
        upgraded.query("SELECT billNumber, totalMinorUnits, status FROM sales WHERE id = 's1'").use {
            assertTrue(it.moveToFirst())
            org.junit.Assert.assertEquals("B-1", it.getString(0))
            org.junit.Assert.assertEquals(5000L, it.getLong(1))
            org.junit.Assert.assertEquals("ACTIVE", it.getString(2))
        }
        upgraded.close()
    }

    @Test
    fun migrate23To26KeepsUsersAndAddsTheConflictLog() {
        val old = helper.createDatabase("migration-23-26", 23)
        old.execSQL("INSERT INTO users (id, username, displayName, salt, verifier, permissions, companyId, role, lastOnlineVerifiedAt, offlineValidUntil, isCloudTier, cloudAccessGrantedUntilEpochMs) VALUES ('u1', '9876543210', 'Owner', 's', 'v', 'SALE_CREATE', 'shop', 'ADMIN', 5, 0, 1, NULL)")
        old.close()
        val upgraded = helper.runMigrationsAndValidate("migration-23-26", 26, true, migration23To24, migration24To25, migration25To26)
        upgraded.query("SELECT username, permissions, companyId, role, lastOnlineVerifiedAt FROM users WHERE id = 'u1'").use {
            assertTrue(it.moveToFirst())
            org.junit.Assert.assertEquals("9876543210", it.getString(0))
            org.junit.Assert.assertEquals("SALE_CREATE", it.getString(1))
            org.junit.Assert.assertEquals("ADMIN", it.getString(3))
            org.junit.Assert.assertEquals(5L, it.getLong(4))
        }
        upgraded.query("SELECT COUNT(*) FROM sync_conflicts").use { assertTrue(it.moveToFirst()); org.junit.Assert.assertEquals(0, it.getInt(0)) }
        upgraded.close()
    }

    companion object {
        private const val TEST_DB = "migration-test"
    }
}

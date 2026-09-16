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

    companion object {
        private const val TEST_DB = "migration-test"
    }
}

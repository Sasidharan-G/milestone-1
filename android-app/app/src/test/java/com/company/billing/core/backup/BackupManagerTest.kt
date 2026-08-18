package com.company.billing.core.backup

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import com.company.billing.core.database.BillingDatabase
import com.company.billing.core.backup.data.BackupManager
import com.company.billing.core.backup.domain.BackupResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.io.ByteArrayInputStream
import java.io.File
import java.util.zip.ZipInputStream

class BackupManagerTest {
    @Test
    fun `createBackup runs checkpoint and packages database bytes into zip with json metadata`() {
        runBlocking {
            val mockContext = mock(Context::class.java)
        val mockDb = mock(BillingDatabase::class.java)
        val mockOpenHelper = mock(SupportSQLiteOpenHelper::class.java)
        val mockSqliteDb = mock(SupportSQLiteDatabase::class.java)

        `when`(mockDb.openHelper).thenReturn(mockOpenHelper)
        `when`(mockOpenHelper.writableDatabase).thenReturn(mockSqliteDb)
        `when`(mockOpenHelper.readableDatabase).thenReturn(mockSqliteDb)
        `when`(mockSqliteDb.version).thenReturn(5)

        // Create temporary billing.db file
        val tempFile = File.createTempFile("billing_test", ".db")
        tempFile.writeBytes(byteArrayOf(1, 2, 3, 4, 5))
        `when`(mockContext.getDatabasePath("billing.db")).thenReturn(tempFile)

        val manager = BackupManager(mockContext, mockDb)
        val result = manager.createBackup()

        assertTrue(result is BackupResult.Success)
        val zipBytes = (result as BackupResult.Success).zipBytes
        assertNotNull(zipBytes)

        // Inspect zip content
        var hasDb = false
        var hasMetadata = false
        ZipInputStream(ByteArrayInputStream(zipBytes)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (entry.name == "billing.db") hasDb = true
                if (entry.name == "metadata.json") hasMetadata = true
                entry = zis.nextEntry
            }
        }
        assertTrue(hasDb)
        assertTrue(hasMetadata)

        // Test Restore
        val restoreSuccess = manager.restoreBackup(zipBytes)
        assertTrue(restoreSuccess)

        tempFile.delete()
        }
    }
}

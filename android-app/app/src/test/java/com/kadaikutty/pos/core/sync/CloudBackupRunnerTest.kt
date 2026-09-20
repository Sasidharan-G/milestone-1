package com.kadaikutty.pos.core.sync

import com.kadaikutty.pos.core.backup.data.BackupManager
import com.kadaikutty.pos.core.backup.domain.BackupResult
import com.kadaikutty.pos.core.network.BackendApiClient
import com.kadaikutty.pos.core.preferences.AppPreferences
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`

class CloudBackupRunnerTest {

    private lateinit var backupManager: BackupManager
    private lateinit var backendApi: BackendApiClient
    private lateinit var appPreferences: AppPreferences
    private val deletedBackupIds = mutableListOf<String>()

    private fun backupsList(count: Int): JSONArray {
        val array = JSONArray()
        repeat(count) { index ->
            array.put(JSONObject().put("backupId", "b$index").put("createdAtEpochMs", 1_000_000L - index))
        }
        return array
    }

    @Before
    fun setUp() {
        backupManager = mock(BackupManager::class.java)
        backendApi = mock(BackendApiClient::class.java)
        appPreferences = mock(AppPreferences::class.java)
        deletedBackupIds.clear()
    }

    // Stubbing a suspend function must happen from a coroutine, so this runs inside each test's
    // runBlocking body rather than the plain (non-suspend) JUnit @Before. uploadBackup is left
    // unstubbed deliberately: its return value is discarded by CloudBackupRunner, and mockito-core
    // (no mockito-kotlin here) can't safely matcher-stub a ByteArray parameter from Kotlin, so the
    // upload itself is verified indirectly via saveLastBackupTimestamp instead.
    private suspend fun stubDeleteCapture() {
        doAnswer { invocation ->
            deletedBackupIds += invocation.getArgument<String>(1)
            null
        }.`when`(backendApi).deleteBackup(anyString(), anyString())
    }

    @Test
    fun `run uploads a new backup and prunes backups beyond the retention limit`() = runBlocking {
        stubDeleteCapture()
        `when`(backupManager.createBackup()).thenReturn(BackupResult.Success(byteArrayOf(1, 2, 3), schemaVersion = 22))
        `when`(backendApi.listBackups("token_abc")).thenReturn(backupsList(9))

        CloudBackupRunner.run("token_abc", backupManager, backendApi, appPreferences, maxRetained = 7)

        verify(appPreferences).saveLastBackupTimestamp(anyLong())
        assertEquals(setOf("b7", "b8"), deletedBackupIds.toSet())
    }

    @Test
    fun `run does not prune when backup count is within the retention limit`() = runBlocking {
        stubDeleteCapture()
        `when`(backupManager.createBackup()).thenReturn(BackupResult.Success(byteArrayOf(1), schemaVersion = 22))
        `when`(backendApi.listBackups("token_abc")).thenReturn(backupsList(5))

        CloudBackupRunner.run("token_abc", backupManager, backendApi, appPreferences, maxRetained = 7)

        assertEquals(0, deletedBackupIds.size)
    }

    @Test
    fun `a failed backup creation never touches existing cloud backups`() = runBlocking {
        `when`(backupManager.createBackup()).thenReturn(BackupResult.Failure(RuntimeException("disk full")))

        var threw = false
        try {
            CloudBackupRunner.run("token_abc", backupManager, backendApi, appPreferences, maxRetained = 7)
        } catch (e: RuntimeException) {
            threw = true
        }

        assertTrue(threw)
        verifyNoInteractions(backendApi)
        assertEquals(0, deletedBackupIds.size)
    }
}

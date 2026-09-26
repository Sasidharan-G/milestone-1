package com.kadaikutty.pos.feature.settings.presentation

import android.content.Context
import com.kadaikutty.pos.core.auth.Session
import com.kadaikutty.pos.core.auth.SessionSecurityManager
import com.kadaikutty.pos.core.auth.SessionStore
import com.kadaikutty.pos.core.backup.data.BackupManager
import com.kadaikutty.pos.core.backup.data.LiveBackupWriter
import com.kadaikutty.pos.core.backup.domain.BackupResult
import com.kadaikutty.pos.core.database.BillingDatabase
import com.kadaikutty.pos.core.database.TenantDatabaseManager
import com.kadaikutty.pos.core.license.LicenseDao
import com.kadaikutty.pos.core.license.LicenseEntity
import com.kadaikutty.pos.core.license.LicenseManager
import com.kadaikutty.pos.core.network.BackendApiClient
import com.kadaikutty.pos.core.preferences.AppPreferences
import com.kadaikutty.pos.core.printer.data.PrinterManager
import com.kadaikutty.pos.core.sharing.ShareManager
import com.kadaikutty.pos.core.sync.SyncManager
import com.kadaikutty.pos.core.sync.SyncNotificationState
import com.kadaikutty.pos.core.sync.SyncScheduler
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.nullable
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

// Mockito's own any()/eq() are Java statics returning a platform type, so Kotlin inserts a
// not-null assertion when that value flows into a non-null Kotlin parameter (like ByteArray) —
// crashing before the stub is even registered. Routing through a generic Kotlin function sidesteps it.
@Suppress("UNCHECKED_CAST")
private fun <T> anyNonNull(): T {
    org.mockito.Mockito.any<T>()
    return null as T
}

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private lateinit var context: Context
    private lateinit var appPreferences: AppPreferences
    private lateinit var printerManager: PrinterManager
    private lateinit var backupManager: BackupManager
    private lateinit var syncScheduler: SyncScheduler
    private lateinit var syncManager: SyncManager
    private lateinit var database: BillingDatabase
    private lateinit var sessionStore: SessionStore
    private lateinit var licenseManager: LicenseManager
    private lateinit var sessionSecurityManager: SessionSecurityManager
    private lateinit var backendApi: BackendApiClient
    private lateinit var tenantDatabaseManager: TenantDatabaseManager
    private lateinit var liveBackupWriter: LiveBackupWriter
    private lateinit var shareManager: ShareManager
    private lateinit var offlineCredentialStore: com.kadaikutty.pos.core.auth.OfflineCredentialStore
    private lateinit var webSocketManager: com.kadaikutty.pos.core.network.WebSocketManager

    private val testSession = Session(
        userId = "user_1",
        displayName = "Owner",
        permissions = emptySet(),
        accessToken = "token_abc",
        companyId = "company_1",
        role = "ADMIN",
        sessionToken = "session_abc"
    )

    private fun buildViewModel(loggedIn: Boolean = true): SettingsViewModel {
        context = mock(Context::class.java)
        appPreferences = mock(AppPreferences::class.java)
        printerManager = mock(PrinterManager::class.java)
        backupManager = mock(BackupManager::class.java)
        syncScheduler = mock(SyncScheduler::class.java)
        syncManager = mock(SyncManager::class.java)
        database = mock(BillingDatabase::class.java)
        sessionStore = mock(SessionStore::class.java)
        licenseManager = mock(LicenseManager::class.java)
        sessionSecurityManager = mock(SessionSecurityManager::class.java)
        backendApi = mock(BackendApiClient::class.java)
        tenantDatabaseManager = mock(TenantDatabaseManager::class.java)
        liveBackupWriter = mock(LiveBackupWriter::class.java)
        shareManager = mock(ShareManager::class.java)
        offlineCredentialStore = mock(com.kadaikutty.pos.core.auth.OfflineCredentialStore::class.java)
        webSocketManager = mock(com.kadaikutty.pos.core.network.WebSocketManager::class.java)

        `when`(sessionStore.activeSession).thenReturn(flowOf(if (loggedIn) testSession else null))
        `when`(licenseManager.currentLicense).thenReturn(MutableStateFlow<LicenseEntity?>(null))
        `when`(licenseManager.isLicenseLoaded).thenReturn(MutableStateFlow(false))
        `when`(licenseManager.isClockTampered).thenReturn(MutableStateFlow(false))
        `when`(sessionSecurityManager.isSessionTerminated).thenReturn(MutableStateFlow(false))
        `when`(sessionSecurityManager.terminationReason).thenReturn(MutableStateFlow<String?>(null))
        `when`(syncScheduler.syncNotificationFlow).thenReturn(flowOf(SyncNotificationState.Idle))
        `when`(appPreferences.lastBackupAtEpochMs).thenReturn(flowOf(null))
        `when`(appPreferences.liveBackupFolderUri).thenReturn(flowOf(null))
        `when`(appPreferences.liveBackupLastWriteAtEpochMs).thenReturn(flowOf(null))
        // Silences the ViewModel's init-block license-sync side effect, which is unrelated to backup/restore.
        val licenseDao = mock(LicenseDao::class.java)
        `when`(tenantDatabaseManager.getDatabase(anyString())).thenReturn(database)
        `when`(database.licenseDao()).thenReturn(licenseDao)

        return SettingsViewModel(
            context, appPreferences, printerManager, backupManager, syncScheduler, syncManager,
            database, sessionStore, licenseManager, sessionSecurityManager, backendApi,
            tenantDatabaseManager, liveBackupWriter,
            offlineCredentialStore, shareManager, webSocketManager,
            mock(com.kadaikutty.pos.core.network.ConnectivityMonitor::class.java).also { `when`(it.isOnline).thenReturn(MutableStateFlow(true)) },
        )
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `runCloudBackup uploads created backup and records last-backup timestamp`() = runBlocking {
        val viewModel = buildViewModel(loggedIn = true)
        `when`(backupManager.createBackup()).thenReturn(BackupResult.Success(byteArrayOf(1, 2, 3), schemaVersion = 22))

        val deferred = CompletableDeferred<Boolean>()
        viewModel.runCloudBackup { deferred.complete(it) }
        val success = deferred.await()

        assertTrue(success)
        assertTrue(viewModel.backupStatus.value.orEmpty().contains("successfully", ignoreCase = true))
        verify(appPreferences).saveLastBackupTimestamp(anyLong())
    }

    @Test
    fun `exportBackupNow shares the created backup and records last-backup timestamp`() = runBlocking {
        val viewModel = buildViewModel(loggedIn = true)
        `when`(backupManager.createBackup()).thenReturn(BackupResult.Success(byteArrayOf(1, 2, 3), schemaVersion = 22))
        `when`(shareManager.shareFile(anyNonNull(), anyString(), anyString(), nullable(String::class.java))).thenReturn(true)

        val deferred = CompletableDeferred<Boolean>()
        viewModel.exportBackupNow { deferred.complete(it) }
        val shared = deferred.await()

        assertTrue(shared)
        assertTrue(viewModel.backupStatus.value.orEmpty().contains("ready", ignoreCase = true))
        verify(appPreferences).saveLastBackupTimestamp(anyLong())
    }

    @Test
    fun `exportBackupNow does not record a timestamp when the share sheet fails to open`() = runBlocking {
        val viewModel = buildViewModel(loggedIn = true)
        `when`(backupManager.createBackup()).thenReturn(BackupResult.Success(byteArrayOf(1, 2, 3), schemaVersion = 22))
        `when`(shareManager.shareFile(anyNonNull(), anyString(), anyString(), nullable(String::class.java))).thenReturn(false)

        val deferred = CompletableDeferred<Boolean>()
        viewModel.exportBackupNow { deferred.complete(it) }
        val shared = deferred.await()

        assertFalse(shared)
        assertTrue(viewModel.backupStatus.value.orEmpty().contains("Could not open", ignoreCase = true))
        verify(appPreferences, never()).saveLastBackupTimestamp(anyLong())
    }

    @Test
    fun `runCloudBackup fails cleanly when nobody is logged in`() = runBlocking {
        val viewModel = buildViewModel(loggedIn = false)

        val deferred = CompletableDeferred<Boolean>()
        viewModel.runCloudBackup { deferred.complete(it) }
        val success = deferred.await()

        assertEquals(false, success)
        assertTrue(viewModel.backupStatus.value.orEmpty().contains("not logged in", ignoreCase = true))
    }

    @Test
    fun `runCloudRestore surfaces a friendly message when no cloud backup exists yet`() = runBlocking {
        val viewModel = buildViewModel(loggedIn = true)
        `when`(backendApi.downloadLatestBackup("token_abc")).thenAnswer {
            throw IllegalArgumentException("No cloud backups are available")
        }

        val deferred = CompletableDeferred<Boolean>()
        viewModel.runCloudRestore { deferred.complete(it) }
        // requireBiometricAuth only stores the pending action; simulate a successful prompt.
        viewModel.biometricAuthPending.value?.invoke()
        val success = deferred.await()

        assertEquals(false, success)
        assertTrue(viewModel.restoreStatus.value.orEmpty().contains("No cloud backup found yet"))
    }

    @Test
    fun `runCloudRestore restores and requires restart on success`() = runBlocking {
        val viewModel = buildViewModel(loggedIn = true)
        `when`(backendApi.downloadLatestBackup("token_abc")).thenReturn(byteArrayOf(9, 9, 9))
        `when`(backupManager.restoreBackup(byteArrayOf(9, 9, 9))).thenReturn(true)

        val deferred = CompletableDeferred<Boolean>()
        viewModel.runCloudRestore { deferred.complete(it) }
        viewModel.biometricAuthPending.value?.invoke()
        val success = deferred.await()

        assertTrue(success)
        assertTrue(viewModel.requireRestart.value)
    }

    @Test
    fun `hasRecentSafetyBackup is true when a manual backup is fresh`() = runBlocking {
        val viewModel = buildViewModel(loggedIn = true)
        `when`(appPreferences.lastBackupAtEpochMs).thenReturn(flowOf(System.currentTimeMillis() - 1_000L))

        assertTrue(viewModel.hasRecentSafetyBackup())
    }

    @Test
    fun `hasRecentSafetyBackup is true when live local backup is actively mirroring`() = runBlocking {
        val viewModel = buildViewModel(loggedIn = true)
        `when`(appPreferences.liveBackupLastWriteAtEpochMs).thenReturn(flowOf(System.currentTimeMillis() - 1_000L))

        assertTrue(viewModel.hasRecentSafetyBackup())
    }

    @Test
    fun `hasRecentSafetyBackup is true when a fresh cloud backup exists`() = runBlocking {
        val viewModel = buildViewModel(loggedIn = true)
        val backups = JSONArray().put(JSONObject().put("backupId", "b0").put("createdAtEpochMs", System.currentTimeMillis() - 1_000L))
        `when`(backendApi.listBackups("token_abc")).thenReturn(backups)

        assertTrue(viewModel.hasRecentSafetyBackup())
    }

    @Test
    fun `hasRecentSafetyBackup is false when every source is missing or stale`() = runBlocking {
        val viewModel = buildViewModel(loggedIn = true)
        val staleTime = System.currentTimeMillis() - (4 * 24 * 60 * 60 * 1_000L)
        `when`(appPreferences.lastBackupAtEpochMs).thenReturn(flowOf(staleTime))
        `when`(appPreferences.liveBackupLastWriteAtEpochMs).thenReturn(flowOf(staleTime))
        `when`(backendApi.listBackups("token_abc")).thenReturn(
            JSONArray().put(JSONObject().put("backupId", "b0").put("createdAtEpochMs", staleTime))
        )

        assertFalse(viewModel.hasRecentSafetyBackup())
    }

    @Test
    fun `clearAllDatabase refuses to purge cloud data without a recent backup, before touching local data`() = runBlocking {
        val viewModel = buildViewModel(loggedIn = true)
        `when`(backendApi.listBackups("token_abc")).thenReturn(JSONArray())

        val deferred = CompletableDeferred<Boolean>()
        viewModel.clearAllDatabase(true) { deferred.complete(it) }
        // requireBiometricAuth only stores the pending action; simulate a successful prompt.
        viewModel.biometricAuthPending.value?.invoke()
        val success = deferred.await()

        assertFalse(success)
        assertTrue(viewModel.restoreStatus.value.orEmpty().contains("Refusing to clear cloud data without a recent backup"))
        // The guard must fire before any destructive step, not just report failure afterward.
        verify(syncScheduler, never()).cancelAllWork()
    }

    // Account deletion: cloud first, then this phone. A refused request must leave everything alone.

    private fun withEmptyDeviceDatabase() {
        val sqlite = mock(androidx.sqlite.db.SupportSQLiteDatabase::class.java)
        val helper = mock(androidx.sqlite.db.SupportSQLiteOpenHelper::class.java)
        `when`(helper.writableDatabase).thenReturn(sqlite)
        `when`(database.openHelper).thenReturn(helper)
    }

    @Test
    fun `deleteAccount with a wrong PIN reports it and leaves the phone signed in`() = runBlocking {
        val viewModel = buildViewModel(loggedIn = true)
        withEmptyDeviceDatabase()
        `when`(backendApi.deleteAccount("token_abc", "000000"))
            // thenAnswer, not thenThrow: Kotlin's suspend functions declare no checked exceptions for Mockito to allow.
            .thenAnswer { throw com.kadaikutty.pos.core.network.BackendApiException("ACCOUNT_DELETE_PIN_INVALID", "The PIN is incorrect", false, 403) }

        val result = CompletableDeferred<String?>()
        viewModel.deleteAccount("000000") { result.complete(it) }

        assertEquals("The PIN is incorrect.", result.await())
        verify(sessionStore, never()).clear()
        verify(offlineCredentialStore, never()).removeByCompanyId(anyString())
        assertFalse(viewModel.isDeletingAccount.value)
    }

    @Test
    fun `deleteAccount success signs out and forgets the shop on this phone`() = runBlocking {
        val viewModel = buildViewModel(loggedIn = true)
        withEmptyDeviceDatabase()
        `when`(backendApi.deleteAccount("token_abc", "123456")).thenReturn(JSONObject().put("success", true))

        val result = CompletableDeferred<String?>()
        viewModel.deleteAccount("123456") { result.complete(it) }

        assertEquals(null, result.await())
        verify(webSocketManager).disconnect()
        verify(syncScheduler).cancelAllWork()
        verify(offlineCredentialStore).removeByCompanyId("company_1")
        verify(offlineCredentialStore).removeByUserId("user_1")
        verify(sessionStore).clear()
    }
}

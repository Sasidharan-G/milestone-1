package com.kadaikutty.pos.core.di

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import com.kadaikutty.pos.core.database.BillingDatabase
import com.kadaikutty.pos.core.database.migration10To11
import com.kadaikutty.pos.core.database.migration11To12
import com.kadaikutty.pos.core.database.migration12To13
import com.kadaikutty.pos.core.database.migration13To14
import com.kadaikutty.pos.core.database.migration14To15
import com.kadaikutty.pos.core.database.migration15To16
import com.kadaikutty.pos.core.database.migration16To17
import com.kadaikutty.pos.core.database.migration17To18
import com.kadaikutty.pos.core.database.migration18To19
import com.kadaikutty.pos.core.database.migration19To20
import com.kadaikutty.pos.core.database.migration21To22
import com.kadaikutty.pos.core.database.migration20To21
import com.kadaikutty.pos.core.database.migration1To2
import com.kadaikutty.pos.core.database.migration2To3
import com.kadaikutty.pos.core.database.migration3To4
import com.kadaikutty.pos.core.database.migration4To5
import com.kadaikutty.pos.core.database.migration5To6
import com.kadaikutty.pos.core.database.migration6To7
import com.kadaikutty.pos.core.database.migration7To8
import com.kadaikutty.pos.core.database.migration8To9
import com.kadaikutty.pos.core.database.migration9To10
import com.kadaikutty.pos.core.auth.AuthRepository
import com.kadaikutty.pos.core.auth.DefaultAuthRepository
import com.kadaikutty.pos.core.auth.OfflineCredentialStore
import com.kadaikutty.pos.core.auth.OfflineCredentialVerifier
import com.kadaikutty.pos.core.auth.SessionStore
import com.kadaikutty.pos.core.logging.AndroidLogger
import com.kadaikutty.pos.core.logging.AppLogger
import com.kadaikutty.pos.core.preferences.AppPreferences
import com.kadaikutty.pos.core.sync.SyncScheduler
import com.kadaikutty.pos.core.printer.data.BluetoothPrinterDriver
import com.kadaikutty.pos.core.printer.data.UsbPrinterDriver
import com.kadaikutty.pos.core.sharing.ShareManager
import com.kadaikutty.pos.core.printer.data.PrinterManager
import com.kadaikutty.pos.core.backup.data.BackupManager
import com.kadaikutty.pos.core.sync.SyncManager
import com.kadaikutty.pos.feature.billing.domain.SaleRepository
import com.kadaikutty.pos.feature.billing.data.SaleRepositoryImpl
import com.kadaikutty.pos.feature.purchase.domain.PurchaseRepository
import com.kadaikutty.pos.feature.purchase.data.PurchaseRepositoryImpl
import com.kadaikutty.pos.core.export.data.AndroidPdfExporter
import com.kadaikutty.pos.core.export.data.CsvExcelExporter
import com.kadaikutty.pos.core.export.domain.ExcelExporter
import com.kadaikutty.pos.core.export.domain.PdfExporter
import com.kadaikutty.pos.feature.reports.domain.CostingStrategy
import com.kadaikutty.pos.feature.reports.domain.ReportRepository
import com.kadaikutty.pos.feature.reports.domain.ReportService
import com.kadaikutty.pos.feature.reports.domain.DefaultReportService
import com.kadaikutty.pos.feature.reports.data.DefaultCostingStrategy
import com.kadaikutty.pos.feature.reports.data.ReportRepositoryImpl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

private val Context.billingDataStore by preferencesDataStore("billing_preferences")

@Module
@InstallIn(SingletonComponent::class)
object CoreModule {
    @Provides @Singleton fun tenantDatabaseManager(@ApplicationContext context: Context, sessionStore: SessionStore): com.kadaikutty.pos.core.database.TenantDatabaseManager =
        com.kadaikutty.pos.core.database.TenantDatabaseManager(context, sessionStore)

    @Provides fun database(tenantDatabaseManager: com.kadaikutty.pos.core.database.TenantDatabaseManager): BillingDatabase =
        tenantDatabaseManager.getDatabase()

    @Provides @Singleton fun preferences(@ApplicationContext context: Context) = AppPreferences(context.billingDataStore)
    @Provides @Singleton fun sessionStore(@ApplicationContext context: Context) = SessionStore(context.billingDataStore)
    @Provides @Singleton fun offlineCredentialStore(@ApplicationContext context: Context) = OfflineCredentialStore(context.billingDataStore)
    @Provides @Singleton fun offlineCredentialVerifier() = OfflineCredentialVerifier()
    @Provides @Singleton fun sessionSecurityManager(
        backendApi: com.kadaikutty.pos.core.network.BackendApiClient,
        sessionStore: SessionStore,
        appPreferences: AppPreferences,
        webSocketManager: com.kadaikutty.pos.core.network.WebSocketManager,
    ): com.kadaikutty.pos.core.auth.SessionSecurityManager =
        com.kadaikutty.pos.core.auth.SessionSecurityManager(backendApi, sessionStore, appPreferences, webSocketManager)

    @Provides @Singleton fun authRepository(
        sessions: SessionStore, 
        credentials: OfflineCredentialStore, 
        verifier: OfflineCredentialVerifier, 
        tenantDatabaseManager: com.kadaikutty.pos.core.database.TenantDatabaseManager,
        backendApi: com.kadaikutty.pos.core.network.BackendApiClient,
        sessionSecurityManager: com.kadaikutty.pos.core.auth.SessionSecurityManager,
        appPreferences: AppPreferences,
        syncScheduler: SyncScheduler,
    ): AuthRepository = DefaultAuthRepository(sessions, credentials, verifier, tenantDatabaseManager, backendApi, sessionSecurityManager, appPreferences, syncScheduler)
    @Provides @Singleton fun logger(): AppLogger = AndroidLogger()
    @Provides @Singleton fun analyticsManager() = com.kadaikutty.pos.core.analytics.AnalyticsManager()
    @Provides @Singleton fun syncScheduler(@ApplicationContext context: Context) = SyncScheduler(context)
    @Provides @Singleton fun syncManager(
        tenantDatabaseManager: com.kadaikutty.pos.core.database.TenantDatabaseManager,
        syncScheduler: SyncScheduler,
        sessionStore: SessionStore,
        liveBackupWriter: com.kadaikutty.pos.core.backup.data.LiveBackupWriter,
    ) = SyncManager(tenantDatabaseManager, syncScheduler, sessionStore, liveBackupWriter)

    @Provides @Singleton fun saleRepository(
        tenantDatabaseManager: com.kadaikutty.pos.core.database.TenantDatabaseManager,
        syncManager: SyncManager,
        sessionStore: SessionStore,
        appPreferences: AppPreferences
    ): SaleRepository = SaleRepositoryImpl(tenantDatabaseManager, syncManager, sessionStore, appPreferences)

    @Provides @Singleton fun purchaseRepository(
        tenantDatabaseManager: com.kadaikutty.pos.core.database.TenantDatabaseManager,
        syncManager: SyncManager,
        sessionStore: SessionStore,
        appPreferences: AppPreferences
    ): PurchaseRepository = PurchaseRepositoryImpl(tenantDatabaseManager, syncManager, sessionStore, appPreferences)

    @Provides @Singleton fun costingStrategy(
        tenantDatabaseManager: com.kadaikutty.pos.core.database.TenantDatabaseManager,
        sessionStore: SessionStore
    ): CostingStrategy = DefaultCostingStrategy(tenantDatabaseManager, sessionStore)

    @Provides @Singleton fun reportRepository(
        tenantDatabaseManager: com.kadaikutty.pos.core.database.TenantDatabaseManager,
        costingStrategy: CostingStrategy,
        sessionStore: SessionStore
    ): ReportRepository = ReportRepositoryImpl(tenantDatabaseManager, costingStrategy, sessionStore)

    @Provides @Singleton fun reportService(reportRepository: ReportRepository): ReportService = DefaultReportService(reportRepository)
    @Provides @Singleton fun pdfExporter(): PdfExporter = AndroidPdfExporter()
    @Provides @Singleton fun excelExporter(): ExcelExporter = CsvExcelExporter()
    @Provides @Singleton fun bluetoothPrinterDriver(@ApplicationContext context: Context): BluetoothPrinterDriver = BluetoothPrinterDriver(context)
    @Provides @Singleton fun usbPrinterDriver(@ApplicationContext context: Context): UsbPrinterDriver = UsbPrinterDriver(context)
    @Provides @Singleton fun printerManager(btDriver: BluetoothPrinterDriver, usbDriver: UsbPrinterDriver): PrinterManager = PrinterManager(btDriver, usbDriver)
    @Provides @Singleton fun shareManager(@ApplicationContext context: Context) = ShareManager(context)
    @Provides @Singleton fun backupManager(
        @ApplicationContext context: Context,
        tenantDatabaseManager: com.kadaikutty.pos.core.database.TenantDatabaseManager
    ) = BackupManager(context, tenantDatabaseManager)
}

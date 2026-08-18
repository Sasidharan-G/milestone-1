package com.company.billing.core.di

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import com.company.billing.core.database.BillingDatabase
import com.company.billing.core.database.migration1To2
import com.company.billing.core.database.migration2To3
import com.company.billing.core.database.migration3To4
import com.company.billing.core.database.migration4To5
import com.company.billing.core.database.migration5To6
import com.company.billing.core.auth.AuthRepository
import com.company.billing.core.auth.DefaultAuthRepository
import com.company.billing.core.auth.OfflineCredentialStore
import com.company.billing.core.auth.OfflineCredentialVerifier
import com.company.billing.core.auth.SessionStore
import com.company.billing.core.logging.AndroidLogger
import com.company.billing.core.logging.AppLogger
import com.company.billing.core.network.BillingApi
import com.company.billing.core.preferences.AppPreferences
import com.company.billing.core.sync.SyncScheduler
import com.company.billing.core.printer.data.BluetoothPrinterDriver
import com.company.billing.core.printer.data.UsbPrinterDriver
import com.company.billing.core.sharing.ShareManager
import com.company.billing.core.printer.data.PrinterManager
import com.company.billing.core.backup.data.BackupManager
import com.company.billing.core.sync.SyncManager
import com.company.billing.feature.billing.domain.SaleRepository
import com.company.billing.feature.billing.data.SaleRepositoryImpl
import com.company.billing.feature.purchase.domain.PurchaseRepository
import com.company.billing.feature.purchase.data.PurchaseRepositoryImpl
import com.company.billing.core.export.data.AndroidPdfExporter
import com.company.billing.core.export.data.CsvExcelExporter
import com.company.billing.core.export.domain.ExcelExporter
import com.company.billing.core.export.domain.PdfExporter
import com.company.billing.feature.reports.domain.CostingStrategy
import com.company.billing.feature.reports.domain.ReportRepository
import com.company.billing.feature.reports.domain.ReportService
import com.company.billing.feature.reports.domain.DefaultReportService
import com.company.billing.feature.reports.data.DefaultCostingStrategy
import com.company.billing.feature.reports.data.ReportRepositoryImpl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import javax.inject.Singleton

private val Context.billingDataStore by preferencesDataStore("billing_preferences")

@Module
@InstallIn(SingletonComponent::class)
object CoreModule {
    @Provides @Singleton fun database(@ApplicationContext context: Context): BillingDatabase = Room.databaseBuilder(context, BillingDatabase::class.java, "billing.db").addMigrations(migration1To2, migration2To3, migration3To4, migration4To5, migration5To6).build()
    @Provides @Singleton fun api(): BillingApi = Retrofit.Builder().baseUrl("https://REQUIRES_CLIENT_CONFIRMATION.invalid/").addConverterFactory(MoshiConverterFactory.create()).build().create(BillingApi::class.java)
    @Provides @Singleton fun preferences(@ApplicationContext context: Context) = AppPreferences(context.billingDataStore)
    @Provides @Singleton fun sessionStore(@ApplicationContext context: Context) = SessionStore(context.billingDataStore)
    @Provides @Singleton fun offlineCredentialStore(@ApplicationContext context: Context) = OfflineCredentialStore(context.billingDataStore)
    @Provides @Singleton fun offlineCredentialVerifier() = OfflineCredentialVerifier()
    @Provides @Singleton fun authRepository(api: BillingApi, sessions: SessionStore, credentials: OfflineCredentialStore, verifier: OfflineCredentialVerifier, database: BillingDatabase): AuthRepository = DefaultAuthRepository(api, sessions, credentials, verifier, database)
    @Provides @Singleton fun logger(): AppLogger = AndroidLogger()
    @Provides @Singleton fun syncScheduler(@ApplicationContext context: Context) = SyncScheduler(context)
    @Provides @Singleton fun syncManager(database: BillingDatabase, syncScheduler: SyncScheduler) = SyncManager(database, syncScheduler)

    @Provides @Singleton fun saleRepository(database: BillingDatabase, syncManager: SyncManager): SaleRepository = SaleRepositoryImpl(database.saleDao(), syncManager)
    @Provides @Singleton fun purchaseRepository(database: BillingDatabase, syncManager: SyncManager): PurchaseRepository = PurchaseRepositoryImpl(database.purchaseDao(), syncManager)
    @Provides @Singleton fun costingStrategy(database: BillingDatabase): CostingStrategy = DefaultCostingStrategy(database.purchaseDao())
    @Provides @Singleton fun reportRepository(database: BillingDatabase, costingStrategy: CostingStrategy): ReportRepository = ReportRepositoryImpl(database.reportDao(), costingStrategy)
    @Provides @Singleton fun reportService(reportRepository: ReportRepository): ReportService = DefaultReportService(reportRepository)
    @Provides @Singleton fun pdfExporter(): PdfExporter = AndroidPdfExporter()
    @Provides @Singleton fun excelExporter(): ExcelExporter = CsvExcelExporter()
    @Provides @Singleton fun bluetoothPrinterDriver(@ApplicationContext context: Context): BluetoothPrinterDriver = BluetoothPrinterDriver(context)
    @Provides @Singleton fun usbPrinterDriver(@ApplicationContext context: Context): UsbPrinterDriver = UsbPrinterDriver(context)
    @Provides @Singleton fun printerManager(btDriver: BluetoothPrinterDriver, usbDriver: UsbPrinterDriver): PrinterManager = PrinterManager(btDriver, usbDriver)
    @Provides @Singleton fun shareManager(@ApplicationContext context: Context) = ShareManager(context)
    @Provides @Singleton fun backupManager(@ApplicationContext context: Context, database: BillingDatabase) = BackupManager(context, database)
    @Provides @Singleton fun googleDriveBackupManager(@ApplicationContext context: Context, appPreferences: AppPreferences, backupManager: BackupManager) = com.company.billing.core.backup.data.GoogleDriveBackupManager(context, appPreferences, backupManager)
}

package com.company.billing.core.di

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import com.company.billing.core.database.BillingDatabase
import com.company.billing.core.database.migration10To11
import com.company.billing.core.database.migration11To12
import com.company.billing.core.database.migration12To13
import com.company.billing.core.database.migration13To14
import com.company.billing.core.database.migration14To15
import com.company.billing.core.database.migration15To16
import com.company.billing.core.database.migration16To17
import com.company.billing.core.database.migration17To18
import com.company.billing.core.database.migration1To2
import com.company.billing.core.database.migration2To3
import com.company.billing.core.database.migration3To4
import com.company.billing.core.database.migration4To5
import com.company.billing.core.database.migration5To6
import com.company.billing.core.database.migration6To7
import com.company.billing.core.database.migration7To8
import com.company.billing.core.database.migration8To9
import com.company.billing.core.database.migration9To10
import com.company.billing.core.auth.AuthRepository
import com.company.billing.core.auth.DefaultAuthRepository
import com.company.billing.core.auth.OfflineCredentialStore
import com.company.billing.core.auth.OfflineCredentialVerifier
import com.company.billing.core.auth.SessionStore
import com.company.billing.core.logging.AndroidLogger
import com.company.billing.core.logging.AppLogger
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
import javax.inject.Singleton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
// import com.company.billing.core.backup.data.FirebaseBackupManager // To be created

private val Context.billingDataStore by preferencesDataStore("billing_preferences")

@Module
@InstallIn(SingletonComponent::class)
object CoreModule {
    @Provides @Singleton fun database(@ApplicationContext context: Context): BillingDatabase {
        val keyBytes = com.company.billing.core.security.SecurityShield.getOrCreateDatabaseKey(context)
        val factory = net.sqlcipher.database.SupportFactory(keyBytes)
        
        var db = Room.databaseBuilder(context, BillingDatabase::class.java, "billing.db")
            .openHelperFactory(factory)
            .addMigrations(migration1To2, migration2To3, migration3To4, migration4To5, migration5To6, migration6To7, migration7To8, migration8To9, migration9To10, migration10To11, migration11To12, migration12To13, migration13To14, migration14To15, migration15To16, migration16To17, migration17To18)
            .build()
            
        try {
            // Eagerly verify the database integrity. 
            // If the key is wrong or the file is corrupted (e.g. bad restore), this throws an exception.
            db.openHelper.writableDatabase.query("SELECT 1").use { it.moveToFirst() }
        } catch (e: Exception) {
            e.printStackTrace()
            // Delete the corrupted database safely
            db.close()
            context.deleteDatabase("billing.db")
            
            // Re-build a fresh database instance
            db = Room.databaseBuilder(context, BillingDatabase::class.java, "billing.db")
                .openHelperFactory(factory)
                .addMigrations(migration1To2, migration2To3, migration3To4, migration4To5, migration5To6, migration6To7, migration7To8, migration8To9, migration9To10, migration10To11, migration11To12, migration12To13, migration13To14, migration14To15, migration15To16, migration16To17, migration17To18)
                .build()
        }
        return db
    }
    @Provides @Singleton fun preferences(@ApplicationContext context: Context) = AppPreferences(context.billingDataStore)
    @Provides @Singleton fun sessionStore(@ApplicationContext context: Context) = SessionStore(context.billingDataStore)
    @Provides @Singleton fun offlineCredentialStore(@ApplicationContext context: Context) = OfflineCredentialStore(context.billingDataStore)
    @Provides @Singleton fun offlineCredentialVerifier() = OfflineCredentialVerifier()
    @Provides @Singleton fun firebaseAuth(): FirebaseAuth = FirebaseAuth.getInstance()
    @Provides @Singleton fun firebaseFirestore(): FirebaseFirestore = FirebaseFirestore.getInstance()
    @Provides @Singleton fun firebaseStorage(): FirebaseStorage = FirebaseStorage.getInstance()
    
    @Provides @Singleton fun authRepository(
        firebaseAuth: FirebaseAuth, 
        firestore: FirebaseFirestore,
        sessions: SessionStore, 
        credentials: OfflineCredentialStore, 
        verifier: OfflineCredentialVerifier, 
        database: BillingDatabase
    ): AuthRepository = DefaultAuthRepository(firebaseAuth, firestore, sessions, credentials, verifier, database)
    @Provides @Singleton fun logger(): AppLogger = AndroidLogger()
    @Provides @Singleton fun syncScheduler(@ApplicationContext context: Context) = SyncScheduler(context)
    @Provides @Singleton fun syncManager(database: BillingDatabase, syncScheduler: SyncScheduler, sessionStore: SessionStore) = SyncManager(database, syncScheduler, sessionStore)

    @Provides @Singleton fun saleRepository(database: BillingDatabase, syncManager: SyncManager, sessionStore: SessionStore): SaleRepository = SaleRepositoryImpl(database.saleDao(), syncManager, sessionStore)
    @Provides @Singleton fun purchaseRepository(database: BillingDatabase, syncManager: SyncManager, sessionStore: SessionStore): PurchaseRepository = PurchaseRepositoryImpl(database.purchaseDao(), syncManager, sessionStore)
    @Provides @Singleton fun costingStrategy(database: BillingDatabase, sessionStore: SessionStore): CostingStrategy = DefaultCostingStrategy(database.purchaseDao(), database.masterDao(), sessionStore)
    @Provides @Singleton fun reportRepository(database: BillingDatabase, costingStrategy: CostingStrategy, sessionStore: SessionStore): ReportRepository = ReportRepositoryImpl(database.reportDao(), costingStrategy, sessionStore)
    @Provides @Singleton fun reportService(reportRepository: ReportRepository): ReportService = DefaultReportService(reportRepository)
    @Provides @Singleton fun pdfExporter(): PdfExporter = AndroidPdfExporter()
    @Provides @Singleton fun excelExporter(): ExcelExporter = CsvExcelExporter()
    @Provides @Singleton fun bluetoothPrinterDriver(@ApplicationContext context: Context): BluetoothPrinterDriver = BluetoothPrinterDriver(context)
    @Provides @Singleton fun usbPrinterDriver(@ApplicationContext context: Context): UsbPrinterDriver = UsbPrinterDriver(context)
    @Provides @Singleton fun printerManager(btDriver: BluetoothPrinterDriver, usbDriver: UsbPrinterDriver): PrinterManager = PrinterManager(btDriver, usbDriver)
    @Provides @Singleton fun shareManager(@ApplicationContext context: Context) = ShareManager(context)
    @Provides @Singleton fun backupManager(@ApplicationContext context: Context, database: BillingDatabase) = BackupManager(context, database)
    // @Provides @Singleton fun firebaseBackupManager(@ApplicationContext context: Context, storage: FirebaseStorage, backupManager: BackupManager) = FirebaseBackupManager(context, storage, backupManager)
}

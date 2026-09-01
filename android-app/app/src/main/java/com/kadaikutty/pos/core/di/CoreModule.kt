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
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
// import com.kadaikutty.pos.core.backup.data.FirebaseBackupManager // To be created

private val Context.billingDataStore by preferencesDataStore("billing_preferences")

@Module
@InstallIn(SingletonComponent::class)
object CoreModule {
    @Provides @Singleton fun database(@ApplicationContext context: Context): BillingDatabase {
        val keyBytes = com.kadaikutty.pos.core.security.SecurityShield.getOrCreateDatabaseKey(context)
        val factory = net.sqlcipher.database.SupportFactory(keyBytes)
        
        var db = Room.databaseBuilder(context, BillingDatabase::class.java, "billing.db")
            .openHelperFactory(factory)
            .addMigrations(migration1To2, migration2To3, migration3To4, migration4To5, migration5To6, migration6To7, migration7To8, migration8To9, migration9To10, migration10To11, migration11To12, migration12To13, migration13To14, migration14To15, migration15To16, migration16To17, migration17To18, migration18To19, migration19To20)
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
                .addMigrations(migration1To2, migration2To3, migration3To4, migration4To5, migration5To6, migration6To7, migration7To8, migration8To9, migration9To10, migration10To11, migration11To12, migration12To13, migration13To14, migration14To15, migration15To16, migration16To17, migration17To18, migration18To19, migration19To20)
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
        database: BillingDatabase,
    ): AuthRepository = DefaultAuthRepository(firebaseAuth, firestore, sessions, credentials, verifier, database)
    @Provides @Singleton fun logger(): AppLogger = AndroidLogger()
    @Provides @Singleton fun analyticsManager(@ApplicationContext context: Context) = com.kadaikutty.pos.core.analytics.AnalyticsManager(context)
    @Provides @Singleton fun syncScheduler(@ApplicationContext context: Context) = SyncScheduler(context)
    @Provides @Singleton fun syncManager(database: BillingDatabase, syncScheduler: SyncScheduler, sessionStore: SessionStore) = SyncManager(database, syncScheduler, sessionStore)

    @Provides @Singleton fun saleRepository(database: BillingDatabase, syncManager: SyncManager, sessionStore: SessionStore, appPreferences: AppPreferences): SaleRepository = SaleRepositoryImpl(database.saleDao(), syncManager, sessionStore, appPreferences)
    @Provides @Singleton fun purchaseRepository(database: BillingDatabase, syncManager: SyncManager, sessionStore: SessionStore, appPreferences: AppPreferences): PurchaseRepository = PurchaseRepositoryImpl(database.purchaseDao(), syncManager, sessionStore, appPreferences)
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
    @Provides @Singleton fun sampleDataGenerator(database: BillingDatabase, firestore: FirebaseFirestore) = com.kadaikutty.pos.core.sample.SampleDataGenerator(database, firestore)
}

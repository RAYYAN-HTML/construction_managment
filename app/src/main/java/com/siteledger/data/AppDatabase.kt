package com.siteledger.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

class Converters {
    @TypeConverter fun attendanceStatus(value: String) = AttendanceStatus.valueOf(value)
    @TypeConverter fun attendanceStatus(value: AttendanceStatus) = value.name
    @TypeConverter fun workerEntryType(value: String) = WorkerEntryType.valueOf(value)
    @TypeConverter fun workerEntryType(value: WorkerEntryType) = value.name
    @TypeConverter fun counterpartyType(value: String) = CounterpartyType.valueOf(value)
    @TypeConverter fun counterpartyType(value: CounterpartyType) = value.name
    @TypeConverter fun accountDirection(value: String) = AccountDirection.valueOf(value)
    @TypeConverter fun accountDirection(value: AccountDirection) = value.name
}

@Dao
interface LedgerDao {
    @Query("SELECT * FROM workers WHERE active = 1 ORDER BY fullName") fun workers(): Flow<List<WorkerEntity>>
    @Query("SELECT * FROM workers ORDER BY fullName") fun allWorkers(): Flow<List<WorkerEntity>>
    @Query("SELECT * FROM workers WHERE id = :workerId") fun worker(workerId: String): Flow<WorkerEntity?>
    @Query("SELECT * FROM workers WHERE active = 1 ORDER BY fullName") suspend fun activeWorkers(): List<WorkerEntity>
    @Query("SELECT * FROM workers") suspend fun workersOnce(): List<WorkerEntity>
    @Query("SELECT * FROM workers WHERE id = :workerId") suspend fun workerOnce(workerId: String): WorkerEntity?
    @Query("SELECT * FROM attendance WHERE workDate = :date") fun attendanceForDate(date: String): Flow<List<AttendanceEntity>>
    @Query("SELECT * FROM attendance WHERE workDate = :date") suspend fun attendanceForDateOnce(date: String): List<AttendanceEntity>
    @Query("DELETE FROM attendance WHERE id = :id") suspend fun deleteAttendance(id: String)
    @Query("SELECT (SELECT COALESCE(SUM(earnedMinor), 0) FROM attendance) AS totalEarnedMinor, (SELECT COALESCE(SUM(amountMinor), 0) FROM worker_ledger_entries) AS totalPaidMinor")
    fun labourTotals(): Flow<LabourTotals>
    @Query("SELECT * FROM attendance WHERE workerId = :workerId ORDER BY workDate DESC") fun workerAttendance(workerId: String): Flow<List<AttendanceEntity>>
    @Query("SELECT * FROM worker_ledger_entries WHERE workerId = :workerId ORDER BY entryDate DESC, createdAt DESC") fun workerEntries(workerId: String): Flow<List<WorkerLedgerEntryEntity>>
    @Query("SELECT * FROM worker_ledger_entries WHERE id = :id") suspend fun workerEntryOnce(id: String): WorkerLedgerEntryEntity?
    @Query("DELETE FROM worker_ledger_entries WHERE id = :id") suspend fun deleteWorkerEntry(id: String)
    @Query("SELECT * FROM counterparties WHERE active = 1 ORDER BY name") fun counterparties(): Flow<List<CounterpartyEntity>>
    @Query("SELECT * FROM counterparties ORDER BY name") fun allCounterparties(): Flow<List<CounterpartyEntity>>
    @Query("SELECT * FROM counterparties WHERE id = :id") fun counterparty(id: String): Flow<CounterpartyEntity?>
    @Query("SELECT * FROM counterparties WHERE id = :id") suspend fun counterpartyOnce(id: String): CounterpartyEntity?
    @Query("SELECT * FROM counterparties") suspend fun counterpartiesOnce(): List<CounterpartyEntity>
    @Query("UPDATE counterparties SET name = :name, type = :type, phone = :phone WHERE id = :id") suspend fun updateCounterparty(id: String, name: String, type: CounterpartyType, phone: String?)
    @Query("UPDATE counterparties SET active = :active WHERE id = :id") suspend fun setCounterpartyActive(id: String, active: Boolean)
    @Query("SELECT c.id, c.name, c.type, COALESCE(SUM(CASE WHEN e.direction = 'THEY_OWE_US' THEN e.amountMinor ELSE -e.amountMinor END), 0) AS balanceMinor FROM counterparties c LEFT JOIN counterparty_ledger_entries e ON e.counterpartyId = c.id WHERE c.active = 1 GROUP BY c.id ORDER BY c.name") fun counterpartyBalances(): Flow<List<CounterpartyBalance>>
    @Query("SELECT * FROM counterparty_ledger_entries WHERE counterpartyId = :id ORDER BY entryDate DESC, createdAt DESC") fun counterpartyEntries(id: String): Flow<List<CounterpartyLedgerEntryEntity>>
    @Query("SELECT * FROM counterparty_ledger_entries WHERE id = :id") suspend fun counterpartyEntryOnce(id: String): CounterpartyLedgerEntryEntity?
    @Query("DELETE FROM counterparty_ledger_entries WHERE id = :id") suspend fun deleteCounterpartyEntry(id: String)

    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertWorker(worker: WorkerEntity)
    @Query("UPDATE workers SET fullName = :name, phone = :phone, trade = :trade, dailyWageMinor = :dailyWageMinor, halfDayWageMinor = :halfDayWageMinor WHERE id = :id") suspend fun updateWorker(id: String, name: String, phone: String?, trade: String, dailyWageMinor: Long, halfDayWageMinor: Long)
    @Query("UPDATE workers SET active = :active WHERE id = :id") suspend fun setWorkerActive(id: String, active: Boolean)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveAttendance(records: List<AttendanceEntity>)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertWorkerEntry(entry: WorkerLedgerEntryEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertCounterparty(counterparty: CounterpartyEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertCounterpartyEntry(entry: CounterpartyLedgerEntryEntity)
}

@Database(
    entities = [WorkerEntity::class, AttendanceEntity::class, WorkerLedgerEntryEntity::class, CounterpartyEntity::class, CounterpartyLedgerEntryEntity::class],
    version = 4,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun ledgerDao(): LedgerDao

    companion object {
        /** Version 2 was the site/material prototype. The requested clean start removes those records once. */
        internal val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                listOf("stock_movements", "attendance", "worker_assignments", "expenses", "materials", "projects", "workers")
                    .forEach { database.execSQL("DROP TABLE IF EXISTS `$it`") }
                createCurrentTables(database)
            }
        }

        internal val MIGRATION_1_3 = object : Migration(1, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                listOf("stock_movements", "attendance", "worker_assignments", "expenses", "materials", "projects", "workers")
                    .forEach { database.execSQL("DROP TABLE IF EXISTS `$it`") }
                createCurrentTables(database)
            }
        }

        internal val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE `workers` ADD COLUMN `halfDayWageMinor` INTEGER NOT NULL DEFAULT 0")
                database.execSQL("UPDATE `workers` SET `halfDayWageMinor` = `dailyWageMinor` / 2")
                database.execSQL("ALTER TABLE `attendance` ADD COLUMN `halfDayRateMinor` INTEGER NOT NULL DEFAULT 0")
                database.execSQL("UPDATE `attendance` SET `halfDayRateMinor` = `dailyRateMinor` / 2")
            }
        }

        private fun createCurrentTables(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `workers` (`id` TEXT NOT NULL, `fullName` TEXT NOT NULL, `phone` TEXT, `trade` TEXT NOT NULL, `dailyWageMinor` INTEGER NOT NULL, `active` INTEGER NOT NULL, `joinedOn` TEXT NOT NULL, PRIMARY KEY(`id`))")
            db.execSQL("CREATE TABLE IF NOT EXISTS `attendance` (`id` TEXT NOT NULL, `workerId` TEXT NOT NULL, `workDate` TEXT NOT NULL, `status` TEXT NOT NULL, `dailyRateMinor` INTEGER NOT NULL, `earnedMinor` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`workerId`) REFERENCES `workers`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION)")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_attendance_workerId_workDate` ON `attendance` (`workerId`, `workDate`)")
            db.execSQL("CREATE TABLE IF NOT EXISTS `worker_ledger_entries` (`id` TEXT NOT NULL, `workerId` TEXT NOT NULL, `entryType` TEXT NOT NULL, `amountMinor` INTEGER NOT NULL, `entryDate` TEXT NOT NULL, `note` TEXT NOT NULL, `photoPath` TEXT, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`workerId`) REFERENCES `workers`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_worker_ledger_entries_workerId` ON `worker_ledger_entries` (`workerId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_worker_ledger_entries_entryDate` ON `worker_ledger_entries` (`entryDate`)")
            db.execSQL("CREATE TABLE IF NOT EXISTS `counterparties` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `type` TEXT NOT NULL, `phone` TEXT, `active` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_counterparties_name` ON `counterparties` (`name`)")
            db.execSQL("CREATE TABLE IF NOT EXISTS `counterparty_ledger_entries` (`id` TEXT NOT NULL, `counterpartyId` TEXT NOT NULL, `direction` TEXT NOT NULL, `amountMinor` INTEGER NOT NULL, `entryDate` TEXT NOT NULL, `note` TEXT NOT NULL, `photoPath` TEXT, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`counterpartyId`) REFERENCES `counterparties`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_counterparty_ledger_entries_counterpartyId` ON `counterparty_ledger_entries` (`counterpartyId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_counterparty_ledger_entries_entryDate` ON `counterparty_ledger_entries` (`entryDate`)")
        }

        @Volatile private var instance: AppDatabase? = null
        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "siteledger.db")
                .addMigrations(MIGRATION_1_3, MIGRATION_2_3)
                .addMigrations(MIGRATION_3_4)
                .build().also { instance = it }
        }
    }
}

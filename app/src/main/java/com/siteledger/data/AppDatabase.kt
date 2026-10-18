package com.siteledger.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

class Converters { @androidx.room.TypeConverter fun toStatus(v: String) = ProjectStatus.valueOf(v); @androidx.room.TypeConverter fun fromStatus(v: ProjectStatus) = v.name; @androidx.room.TypeConverter fun toAttendance(v: String) = AttendanceStatus.valueOf(v); @androidx.room.TypeConverter fun fromAttendance(v: AttendanceStatus) = v.name; @androidx.room.TypeConverter fun toMove(v: String) = StockMovementType.valueOf(v); @androidx.room.TypeConverter fun fromMove(v: StockMovementType) = v.name }

@Dao interface LedgerDao {
    @Query("SELECT * FROM projects ORDER BY createdAt DESC") fun projects(): Flow<List<ProjectEntity>>
    @Query("SELECT w.* FROM workers w INNER JOIN worker_assignments a ON a.workerId = w.id WHERE w.active = 1 AND a.projectId = :projectId AND a.endedOn IS NULL ORDER BY w.fullName") fun workersForProject(projectId: String): Flow<List<WorkerEntity>>
    @Query("SELECT * FROM workers w WHERE w.active = 1 AND NOT EXISTS (SELECT 1 FROM worker_assignments a WHERE a.workerId = w.id AND a.endedOn IS NULL) ORDER BY w.fullName") fun unassignedWorkers(): Flow<List<WorkerEntity>>
    @Query("SELECT EXISTS(SELECT 1 FROM worker_assignments WHERE workerId = :workerId AND projectId = :projectId AND endedOn IS NULL)") suspend fun hasActiveAssignment(workerId: String, projectId: String): Boolean
    @Query("SELECT * FROM expenses ORDER BY expenseDate DESC, createdAt DESC") fun expenses(): Flow<List<ExpenseEntity>>
    @Query("SELECT * FROM materials ORDER BY name") fun materials(): Flow<List<MaterialEntity>>
    @Query("SELECT COUNT(*) FROM attendance WHERE workDate = :date AND status IN ('PRESENT','HALF_DAY')") fun presentCount(date: String): Flow<Int>
    @Query("SELECT COALESCE(SUM(amountMinor), 0) FROM expenses WHERE expenseDate = :date") fun todayExpense(date: String): Flow<Long>
    @Query("SELECT projectId, COALESCE(SUM(amountMinor), 0) AS totalMinor FROM expenses GROUP BY projectId") fun projectCosts(): Flow<List<ProjectCost>>
    @Query("SELECT m.id AS materialId, m.name, m.unit, m.minimumQuantity, COALESCE(SUM(CASE WHEN sm.movementType IN ('OPENING','RECEIPT','TRANSFER_IN','ADJUSTMENT') THEN sm.quantity ELSE -sm.quantity END), 0) AS quantity FROM materials m LEFT JOIN stock_movements sm ON sm.materialId=m.id GROUP BY m.id ORDER BY m.name") fun materialStock(): Flow<List<MaterialStock>>
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertProject(project: ProjectEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertWorker(worker: WorkerEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertWorkerAssignment(assignment: WorkerAssignmentEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertAttendance(record: AttendanceEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertMaterial(material: MaterialEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertStockMovement(movement: StockMovementEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertExpense(expense: ExpenseEntity)

    @Query("UPDATE worker_assignments SET endedOn = :endedOn WHERE workerId = :workerId AND endedOn IS NULL") suspend fun endActiveAssignments(workerId: String, endedOn: String)
    @Transaction suspend fun insertWorkerWithAssignment(worker: WorkerEntity, assignment: WorkerAssignmentEntity) {
        insertWorker(worker)
        insertWorkerAssignment(assignment)
    }
    @Transaction suspend fun assignWorkerToProject(workerId: String, projectId: String, assignedOn: String) {
        endActiveAssignments(workerId, assignedOn)
        insertWorkerAssignment(WorkerAssignmentEntity(workerId = workerId, projectId = projectId, assignedOn = assignedOn))
    }
}

@Database(entities = [ProjectEntity::class, WorkerEntity::class, WorkerAssignmentEntity::class, AttendanceEntity::class, MaterialEntity::class, StockMovementEntity::class, ExpenseEntity::class], version = 2, exportSchema = true)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun ledgerDao(): LedgerDao
    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE TABLE IF NOT EXISTS `worker_assignments` (`id` TEXT NOT NULL, `workerId` TEXT NOT NULL, `projectId` TEXT NOT NULL, `assignedOn` TEXT NOT NULL, `endedOn` TEXT, PRIMARY KEY(`id`), FOREIGN KEY(`workerId`) REFERENCES `workers`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION, FOREIGN KEY(`projectId`) REFERENCES `projects`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_worker_assignments_workerId` ON `worker_assignments` (`workerId`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_worker_assignments_projectId_endedOn` ON `worker_assignments` (`projectId`, `endedOn`)")
                database.execSQL("INSERT INTO `worker_assignments` (`id`, `workerId`, `projectId`, `assignedOn`, `endedOn`) SELECT lower(hex(randomblob(16))), w.`id`, (SELECT a.`projectId` FROM `attendance` a WHERE a.`workerId` = w.`id` ORDER BY a.`workDate` DESC, a.`projectId` DESC LIMIT 1), (SELECT a.`workDate` FROM `attendance` a WHERE a.`workerId` = w.`id` ORDER BY a.`workDate` DESC, a.`projectId` DESC LIMIT 1), NULL FROM `workers` w WHERE EXISTS (SELECT 1 FROM `attendance` a WHERE a.`workerId` = w.`id`)")
            }
        }

        @Volatile private var instance: AppDatabase? = null
        fun get(context: Context) = instance ?: synchronized(this) { instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "siteledger.db").addMigrations(MIGRATION_1_2).build().also { instance = it } }
    }
}


package com.siteledger.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate
import java.util.UUID

enum class ProjectStatus { PLANNED, ACTIVE, PAUSED, COMPLETED, ARCHIVED }
enum class AttendanceStatus { PRESENT, ABSENT, HALF_DAY, LEAVE }
enum class StockMovementType { OPENING, RECEIPT, ISSUE, WASTAGE, TRANSFER_IN, TRANSFER_OUT, ADJUSTMENT }

@Entity(tableName = "projects", indices = [Index(value = ["reference"], unique = true)])
data class ProjectEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(), val reference: String,
    val name: String, val clientName: String, val siteAddress: String,
    val contractAmountMinor: Long, val budgetAmountMinor: Long,
    val startDate: String, val expectedCompletionDate: String? = null, val status: ProjectStatus = ProjectStatus.ACTIVE,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "workers")
data class WorkerEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(), val fullName: String,
    val phone: String?, val trade: String, val dailyWageMinor: Long, val active: Boolean = true,
    val joinedOn: String = LocalDate.now().toString()
)

@Entity(tableName = "worker_assignments", foreignKeys = [
    ForeignKey(entity = WorkerEntity::class, parentColumns = ["id"], childColumns = ["workerId"]),
    ForeignKey(entity = ProjectEntity::class, parentColumns = ["id"], childColumns = ["projectId"])
], indices = [Index("workerId"), Index(value = ["projectId", "endedOn"])])
data class WorkerAssignmentEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val workerId: String,
    val projectId: String,
    val assignedOn: String = LocalDate.now().toString(),
    val endedOn: String? = null
)

@Entity(tableName = "attendance", foreignKeys = [
    ForeignKey(entity = WorkerEntity::class, parentColumns = ["id"], childColumns = ["workerId"]),
    ForeignKey(entity = ProjectEntity::class, parentColumns = ["id"], childColumns = ["projectId"])
], indices = [Index(value = ["workerId", "projectId", "workDate"], unique = true), Index(value = ["projectId"])])
data class AttendanceEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(), val workerId: String, val projectId: String,
    val workDate: String, val status: AttendanceStatus, val overtimeMinutes: Int = 0, val note: String? = null
)

@Entity(tableName = "materials", indices = [Index(value = ["code"], unique = true)])
data class MaterialEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(), val code: String, val name: String,
    val unit: String, val minimumQuantity: Double, val defaultRateMinor: Long
)

@Entity(tableName = "stock_movements", foreignKeys = [
    ForeignKey(entity = MaterialEntity::class, parentColumns = ["id"], childColumns = ["materialId"]),
    ForeignKey(entity = ProjectEntity::class, parentColumns = ["id"], childColumns = ["projectId"])
], indices = [Index("materialId"), Index("projectId")])
data class StockMovementEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(), val materialId: String, val projectId: String?,
    val movementType: StockMovementType, val quantity: Double, val rateMinor: Long,
    val movementDate: String, val note: String? = null, val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "expenses", foreignKeys = [ForeignKey(entity = ProjectEntity::class, parentColumns = ["id"], childColumns = ["projectId"])], indices = [Index("projectId"), Index("expenseDate")])
data class ExpenseEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(), val projectId: String, val category: String,
    val description: String, val amountMinor: Long, val expenseDate: String,
    val paymentMethod: String = "Cash", val createdAt: Long = System.currentTimeMillis()
)

data class MaterialStock(val materialId: String, val name: String, val unit: String, val minimumQuantity: Double, val quantity: Double)
data class ProjectCost(val projectId: String, val totalMinor: Long)


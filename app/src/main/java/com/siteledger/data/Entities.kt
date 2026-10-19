package com.siteledger.data

import androidx.room.Entity
import androidx.room.ColumnInfo
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate
import java.util.UUID

enum class AttendanceStatus { PRESENT, HALF_DAY, ABSENT }
enum class WorkerEntryType { ADVANCE, WAGE_PAYMENT }
enum class CounterpartyType { SITE_OWNER, SUPPLIER }
enum class AccountDirection { THEY_OWE_US, WE_OWE_THEM }

@Entity(tableName = "workers")
data class WorkerEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val fullName: String,
    val phone: String? = null,
    val trade: String = "",
    val dailyWageMinor: Long,
    @ColumnInfo(defaultValue = "0") val halfDayWageMinor: Long = dailyWageMinor / 2,
    val active: Boolean = true,
    val joinedOn: String = LocalDate.now().toString()
)

@Entity(
    tableName = "attendance",
    foreignKeys = [ForeignKey(entity = WorkerEntity::class, parentColumns = ["id"], childColumns = ["workerId"])],
    indices = [Index(value = ["workerId", "workDate"], unique = true)]
)
data class AttendanceEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val workerId: String,
    val workDate: String,
    val status: AttendanceStatus,
    val dailyRateMinor: Long,
    @ColumnInfo(defaultValue = "0") val halfDayRateMinor: Long,
    val earnedMinor: Long
)

@Entity(
    tableName = "worker_ledger_entries",
    foreignKeys = [ForeignKey(entity = WorkerEntity::class, parentColumns = ["id"], childColumns = ["workerId"])],
    indices = [Index("workerId"), Index("entryDate")]
)
data class WorkerLedgerEntryEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val workerId: String,
    val entryType: WorkerEntryType,
    val amountMinor: Long,
    val entryDate: String = LocalDate.now().toString(),
    val note: String = "",
    val photoPath: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "counterparties", indices = [Index("name")])
data class CounterpartyEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val type: CounterpartyType,
    val phone: String? = null,
    val active: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "counterparty_ledger_entries",
    foreignKeys = [ForeignKey(entity = CounterpartyEntity::class, parentColumns = ["id"], childColumns = ["counterpartyId"])],
    indices = [Index("counterpartyId"), Index("entryDate")]
)
data class CounterpartyLedgerEntryEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val counterpartyId: String,
    val direction: AccountDirection,
    val amountMinor: Long,
    val entryDate: String = LocalDate.now().toString(),
    val note: String = "",
    val photoPath: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

data class CounterpartyBalance(val id: String, val name: String, val type: CounterpartyType, val balanceMinor: Long)

data class LabourTotals(val totalEarnedMinor: Long, val totalPaidMinor: Long)

data class WorkerBalance(
    val id: String,
    val name: String,
    val active: Boolean,
    val totalEarnedMinor: Long,
    val totalPaidMinor: Long,
    val balanceMinor: Long
)

data class AttendanceSaveSummary(
    val date: String,
    val workerCount: Int,
    val newRecordCount: Int,
    val changedRecordCount: Int
)

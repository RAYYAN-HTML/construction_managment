package com.siteledger.data

import com.siteledger.domain.WageCalculator
import kotlinx.coroutines.flow.Flow
import java.io.File
import java.time.LocalDate
import java.util.UUID

class LedgerRepository(private val dao: LedgerDao) {
    val workers = dao.workers()
    val allWorkers = dao.allWorkers()
    val labourTotals = dao.labourTotals()
    val counterparties = dao.counterparties()
    val counterpartyBalances = dao.counterpartyBalances()

    fun worker(id: String) = dao.worker(id)
    fun attendanceForDate(date: String): Flow<List<AttendanceEntity>> = dao.attendanceForDate(date)
    fun workerAttendance(workerId: String) = dao.workerAttendance(workerId)
    fun workerEntries(workerId: String) = dao.workerEntries(workerId)
    fun counterparty(id: String) = dao.counterparty(id)
    fun counterpartyEntries(id: String) = dao.counterpartyEntries(id)

    suspend fun addWorker(name: String, dailyWageMinor: Long, halfDayWageMinor: Long, phone: String?, trade: String = "") {
        require(name.isNotBlank()) { "Worker name is required." }
        require(dailyWageMinor > 0) { "Daily wage must be greater than zero." }
        require(halfDayWageMinor >= 0) { "Half-day wage cannot be negative." }
        dao.insertWorker(WorkerEntity(fullName = name.trim(), dailyWageMinor = dailyWageMinor, halfDayWageMinor = halfDayWageMinor, phone = phone?.trim()?.ifBlank { null }, trade = trade.trim()))
    }

    suspend fun updateWorker(workerId: String, name: String, dailyWageMinor: Long, halfDayWageMinor: Long, phone: String?, trade: String = "") {
        require(name.isNotBlank()) { "Worker name is required." }
        require(dailyWageMinor > 0) { "Daily wage must be greater than zero." }
        require(halfDayWageMinor >= 0) { "Half-day wage cannot be negative." }
        dao.updateWorker(workerId, name.trim(), phone?.trim()?.ifBlank { null }, trade.trim(), dailyWageMinor, halfDayWageMinor)
    }

    suspend fun saveAttendanceSheet(date: String, statuses: Map<String, AttendanceStatus>) {
        val workDate = LocalDate.parse(date).toString()
        val workers = dao.activeWorkers()
        require(workers.isNotEmpty()) { "Add a worker before recording attendance." }
        require(statuses.keys == workers.map { it.id }.toSet()) { "Choose attendance for every worker." }
        val existing = dao.attendanceForDateOnce(workDate).associateBy { it.workerId }
        val records = workers.map { worker ->
            val old = existing[worker.id]
            val rate = old?.dailyRateMinor ?: worker.dailyWageMinor
            val halfDayRate = old?.halfDayRateMinor ?: worker.halfDayWageMinor
            val status = requireNotNull(statuses[worker.id])
            AttendanceEntity(
                id = old?.id ?: UUID.randomUUID().toString(),
                workerId = worker.id,
                workDate = workDate,
                status = status,
                dailyRateMinor = rate,
                halfDayRateMinor = halfDayRate,
                earnedMinor = WageCalculator.earnedMinor(status, rate, halfDayRate)
            )
        }
        dao.saveAttendance(records)
    }

    suspend fun deleteAttendance(attendanceId: String) {
        dao.deleteAttendance(attendanceId)
    }

    suspend fun addWorkerEntry(workerId: String, type: WorkerEntryType, amountMinor: Long, date: String, note: String, photoPath: String?) {
        LocalDate.parse(date)
        require(dao.workerOnce(workerId) != null) { "Worker no longer exists." }
        require(amountMinor > 0) { "Amount must be greater than zero." }
        dao.insertWorkerEntry(WorkerLedgerEntryEntity(workerId = workerId, entryType = type, amountMinor = amountMinor, entryDate = date, note = note.trim(), photoPath = photoPath))
    }

    suspend fun deleteWorkerEntry(entryId: String) {
        val entry = dao.workerEntryOnce(entryId) ?: return
        dao.deleteWorkerEntry(entryId)
        deleteLedgerPhoto(entry.photoPath)
    }

    suspend fun addCounterparty(name: String, type: CounterpartyType, phone: String?) {
        require(name.isNotBlank()) { "Name is required." }
        dao.insertCounterparty(CounterpartyEntity(name = name.trim(), type = type, phone = phone?.trim()?.ifBlank { null }))
    }

    suspend fun updateCounterparty(id: String, name: String, type: CounterpartyType, phone: String?) {
        require(name.isNotBlank()) { "Name is required." }
        require(dao.counterpartyOnce(id) != null) { "Account no longer exists." }
        dao.updateCounterparty(id, name.trim(), type, phone?.trim()?.ifBlank { null })
    }

    suspend fun addCounterpartyEntry(counterpartyId: String, direction: AccountDirection, amountMinor: Long, date: String, note: String, photoPath: String?) {
        LocalDate.parse(date)
        require(amountMinor > 0) { "Amount must be greater than zero." }
        require(dao.counterpartyOnce(counterpartyId) != null) { "Account no longer exists." }
        dao.insertCounterpartyEntry(CounterpartyLedgerEntryEntity(counterpartyId = counterpartyId, direction = direction, amountMinor = amountMinor, entryDate = date, note = note.trim(), photoPath = photoPath))
    }

    suspend fun deleteCounterpartyEntry(entryId: String) {
        val entry = dao.counterpartyEntryOnce(entryId) ?: return
        dao.deleteCounterpartyEntry(entryId)
        deleteLedgerPhoto(entry.photoPath)
    }

    private fun deleteLedgerPhoto(path: String?) {
        val photo = path?.let(::File) ?: return
        if (photo.parentFile?.name == "ledger_photos") photo.delete()
    }
}

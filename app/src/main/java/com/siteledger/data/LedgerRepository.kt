package com.siteledger.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import java.time.LocalDate

class LedgerRepository(private val dao: LedgerDao) {
    val projects = dao.projects(); val unassignedWorkers = dao.unassignedWorkers(); val expenses = dao.expenses(); val materials = dao.materials(); val stock = dao.materialStock()
    fun workersForProject(projectId: String?): Flow<List<WorkerEntity>> = projectId?.let(dao::workersForProject) ?: flowOf(emptyList())
    fun presentCount(date: String = LocalDate.now().toString()) = dao.presentCount(date)
    fun todayExpense(date: String = LocalDate.now().toString()) = dao.todayExpense(date)
    fun projectCosts(): Flow<List<ProjectCost>> = dao.projectCosts()
    suspend fun addProject(reference: String, name: String, client: String, address: String, contractMinor: Long, budgetMinor: Long) { require(reference.isNotBlank() && name.isNotBlank()) { "Project reference and name are required." }; require(contractMinor >= 0 && budgetMinor >= 0) { "Amounts cannot be negative." }; dao.insertProject(ProjectEntity(reference = reference.trim().uppercase(), name = name.trim(), clientName = client.trim(), siteAddress = address.trim(), contractAmountMinor = contractMinor, budgetAmountMinor = budgetMinor, startDate = LocalDate.now().toString())) }
    suspend fun addWorker(name: String, trade: String, wageMinor: Long, phone: String?, projectId: String?) {
        require(name.isNotBlank() && trade.isNotBlank()) { "Worker name and trade are required." }
        require(wageMinor > 0) { "Daily wage must be greater than zero." }
        val assignedProjectId = requireNotNull(projectId?.takeIf { it.isNotBlank() }) { "Select a project for this worker." }
        val worker = WorkerEntity(fullName = name.trim(), trade = trade.trim(), dailyWageMinor = wageMinor, phone = phone?.trim()?.ifBlank { null })
        val assignment = WorkerAssignmentEntity(workerId = worker.id, projectId = assignedProjectId)
        dao.insertWorkerWithAssignment(worker, assignment)
    }
    suspend fun assignWorker(workerId: String, projectId: String) {
        require(projectId.isNotBlank()) { "Select a project." }
        dao.assignWorkerToProject(workerId, projectId, LocalDate.now().toString())
    }
    suspend fun markAttendance(workerId: String, projectId: String, status: AttendanceStatus, overtimeMinutes: Int) {
        require(overtimeMinutes >= 0) { "Overtime cannot be negative." }
        require(dao.hasActiveAssignment(workerId, projectId)) { "This worker is not assigned to the selected project." }
        dao.insertAttendance(AttendanceEntity(workerId = workerId, projectId = projectId, workDate = LocalDate.now().toString(), status = status, overtimeMinutes = overtimeMinutes))
    }
    suspend fun addExpense(projectId: String, category: String, description: String, amountMinor: Long) { require(category.isNotBlank()) { "Expense category is required." }; require(amountMinor > 0) { "Amount must be greater than zero." }; dao.insertExpense(ExpenseEntity(projectId = projectId, category = category.trim(), description = description.trim(), amountMinor = amountMinor, expenseDate = LocalDate.now().toString())) }
    suspend fun addMaterial(code: String, name: String, unit: String, minimum: Double, rateMinor: Long) { require(code.isNotBlank() && name.isNotBlank() && unit.isNotBlank()) { "Code, name, and unit are required." }; require(minimum >= 0 && rateMinor >= 0) { "Minimum stock and rate cannot be negative." }; dao.insertMaterial(MaterialEntity(code = code.trim().uppercase(), name = name.trim(), unit = unit.trim(), minimumQuantity = minimum, defaultRateMinor = rateMinor)) }
    suspend fun receiveMaterial(materialId: String, projectId: String?, quantity: Double, rateMinor: Long) { require(quantity > 0) { "Received quantity must be greater than zero." }; dao.insertStockMovement(StockMovementEntity(materialId = materialId, projectId = projectId, movementType = StockMovementType.RECEIPT, quantity = quantity, rateMinor = rateMinor, movementDate = LocalDate.now().toString())) }
}


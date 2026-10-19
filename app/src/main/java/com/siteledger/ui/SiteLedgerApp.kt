package com.siteledger.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.siteledger.data.*
import com.siteledger.domain.LedgerBalances
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale

private enum class Destination(val title: String) { LABOUR("Labour"), ACCOUNTS("Accounts") }
private fun money(value: Long): String {
    val formatter = NumberFormat.getNumberInstance(Locale.US).apply {
        minimumFractionDigits = if (value % 100L == 0L) 0 else 2
        maximumFractionDigits = 2
    }
    return "Rs. ${formatter.format(BigDecimal.valueOf(value, 2))}"
}
private fun moneyInput(value: Long) = BigDecimal.valueOf(value, 2).stripTrailingZeros().toPlainString()
private val gainColor = Color(0xFF16803C)
private fun parseMoney(text: String): Long? = runCatching {
    BigDecimal(text.trim()).setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact().takeIf { it > 0 }
}.getOrNull()
private fun parseNonNegativeMoney(text: String): Long? = runCatching {
    BigDecimal(text.trim()).setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact().takeIf { it >= 0 }
}.getOrNull()

@Composable
fun SiteLedgerApp(repository: LedgerRepository) {
    var destination by rememberSaveable { mutableStateOf(Destination.LABOUR) }
    Scaffold(bottomBar = {
        NavigationBar {
            listOf(Destination.LABOUR to Icons.Default.People, Destination.ACCOUNTS to Icons.Default.AccountBalance).forEach { (item, icon) ->
                NavigationBarItem(selected = destination == item, onClick = { destination = item }, icon = { Icon(icon, item.title) }, label = { Text(item.title) })
            }
        }
    }) { padding ->
        Box(Modifier.padding(padding)) {
            when (destination) {
                Destination.LABOUR -> LabourScreen(repository)
                Destination.ACCOUNTS -> AccountsScreen(repository)
            }
        }
    }
}

@Composable
private fun LabourScreen(repo: LedgerRepository) {
    var selectedWorkerId by rememberSaveable { mutableStateOf<String?>(null) }
    if (selectedWorkerId != null) {
        WorkerProfile(repo, selectedWorkerId!!, onBack = { selectedWorkerId = null })
        return
    }
    val workers by repo.workers.collectAsStateWithLifecycle(emptyList())
    val labourTotals by repo.labourTotals.collectAsStateWithLifecycle(LabourTotals(0L, 0L))
    var dateText by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    val attendance by repo.attendanceForDate(dateText).collectAsStateWithLifecycle(emptyList())
    var addWorker by remember { mutableStateOf(false) }
    var pickDate by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val workerIds = workers.map { it.id }
    var statuses by remember(dateText, workerIds, attendance) {
        mutableStateOf(attendance.associate { it.workerId to it.status })
    }
    Page("Labour", onAdd = { addWorker = true }) {
        val labourBalance = labourTotals.totalEarnedMinor - labourTotals.totalPaidMinor
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Labour totals", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                SummaryLine("Total earned", money(labourTotals.totalEarnedMinor))
                SummaryLine("Total paid", money(labourTotals.totalPaidMinor), MaterialTheme.colorScheme.error)
                if (labourBalance >= 0) {
                    SummaryLine("Pending wages", money(labourBalance), if (labourBalance == 0L) gainColor else MaterialTheme.colorScheme.error)
                } else {
                    SummaryLine("Advance paid ahead", money(-labourBalance), gainColor)
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Daily attendance", style = MaterialTheme.typography.titleMedium)
            OutlinedButton(onClick = { pickDate = true }) { Text(dateText) }
        }
        if (workers.isEmpty()) {
            Empty("No workers yet", "Add a worker to start recording attendance and wages.")
            OutlinedButton(onClick = { addWorker = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Add your first worker")
            }
        } else {
            Text("Choose one status for every worker. Wages are calculated automatically.", style = MaterialTheme.typography.bodySmall)
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(vertical = 8.dp)) {
                items(workers, key = { it.id }) { worker ->
                    AttendanceRow(worker, statuses[worker.id], onStatus = { statuses = statuses + (worker.id to it) }, onAccount = { selectedWorkerId = worker.id })
                    HorizontalDivider()
                }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(
                onClick = { scope.launch { runCatching { repo.saveAttendanceSheet(dateText, statuses) }.onSuccess { error = null }.onFailure { error = it.message } } },
                enabled = statuses.keys == workerIds.toSet() && workerIds.isNotEmpty(),
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (statuses.keys == workerIds.toSet() && workerIds.isNotEmpty()) "Save attendance" else "Choose status for every worker") }
        }
    }
    if (addWorker) WorkerForm(repo, null, onClose = { addWorker = false })
    if (pickDate) AttendanceDatePicker(dateText, onSelect = { dateText = it; error = null }, onClose = { pickDate = false })
}

@Composable
private fun AttendanceRow(worker: WorkerEntity, status: AttendanceStatus?, onStatus: (AttendanceStatus) -> Unit, onAccount: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).clickable(onClick = onAccount)) {
                Text(worker.fullName, fontWeight = FontWeight.SemiBold)
                Text("${money(worker.dailyWageMinor)} per day", style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = onAccount) { Text("Account") }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            AttendanceStatus.entries.forEach { choice ->
                FilterChip(selected = status == choice, onClick = { onStatus(choice) }, label = { Text(choice.label()) })
            }
        }
    }
}

private fun AttendanceStatus.label() = when (this) {
    AttendanceStatus.PRESENT -> "Present"
    AttendanceStatus.HALF_DAY -> "Half-day"
    AttendanceStatus.ABSENT -> "Absent"
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun AttendanceDatePicker(value: String, onSelect: (String) -> Unit, onClose: () -> Unit) {
    val selectedDate = runCatching { LocalDate.parse(value) }.getOrDefault(LocalDate.now())
    val state = rememberDatePickerState(initialSelectedDateMillis = selectedDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
    DatePickerDialog(
        onDismissRequest = onClose,
        confirmButton = { TextButton(onClick = { state.selectedDateMillis?.let { onSelect(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString()) }; onClose() }) { Text("Select") } },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancel") } }
    ) { DatePicker(state = state) }
}

@Composable
private fun WorkerProfile(repo: LedgerRepository, workerId: String, onBack: () -> Unit) {
    val worker by repo.worker(workerId).collectAsStateWithLifecycle(null)
    val attendance by repo.workerAttendance(workerId).collectAsStateWithLifecycle(emptyList())
    val entries by repo.workerEntries(workerId).collectAsStateWithLifecycle(emptyList())
    var entryType by remember { mutableStateOf<WorkerEntryType?>(null) }
    var editWorker by remember { mutableStateOf(false) }
    var photoToView by remember { mutableStateOf<String?>(null) }
    var attendanceToDelete by remember { mutableStateOf<AttendanceEntity?>(null) }
    var entryToDelete by remember { mutableStateOf<WorkerLedgerEntryEntity?>(null) }
    var deleteError by remember { mutableStateOf<String?>(null) }
    var isDeleting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val balance = LedgerBalances.workerBalance(attendance.map { it.earnedMinor }, entries.map { it.amountMinor })
    Page("Worker account", onBack = onBack) {
        if (worker == null) Text("Worker not found.") else {
            val current = worker!!
            Text(current.fullName, style = MaterialTheme.typography.headlineSmall)
            Text("Full day: ${money(current.dailyWageMinor)} • Half-day: ${money(current.halfDayWageMinor)}${current.phone?.let { " • $it" } ?: ""}")
            Text(
                if (balance > 0) "Wages due: ${money(balance)}" else if (balance < 0) "Advance paid ahead: ${money(-balance)}" else "Settled",
                color = when { balance > 0 -> MaterialTheme.colorScheme.error; balance < 0 -> gainColor; else -> MaterialTheme.colorScheme.onSurface },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { entryType = WorkerEntryType.ADVANCE }, modifier = Modifier.weight(1f)) { Text("Advance") }
                OutlinedButton(onClick = { entryType = WorkerEntryType.WAGE_PAYMENT }, modifier = Modifier.weight(1f)) { Text("Pay wage") }
            }
            TextButton(onClick = { editWorker = true }) { Text("Edit worker details or daily wage") }
            Text("Ledger", style = MaterialTheme.typography.titleMedium)
            deleteError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            LazyColumn(Modifier.weight(1f)) {
                items(attendance, key = { it.id }) { record ->
                    ListItem(
                        headlineContent = { Text("${record.status.label()} • earned ${money(record.earnedMinor)}") },
                        supportingContent = { Text(record.workDate) },
                        trailingContent = {
                            IconButton(onClick = { deleteError = null; attendanceToDelete = record }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete attendance")
                            }
                        }
                    )
                    HorizontalDivider()
                }
                items(entries, key = { it.id }) { entry ->
                    ListItem(
                        headlineContent = { Text("${if (entry.entryType == WorkerEntryType.ADVANCE) "Advance paid" else "Wage payment"} • ${money(entry.amountMinor)}") },
                        supportingContent = { Text("${entry.entryDate}${entry.note.takeIf { it.isNotBlank() }?.let { " • $it" } ?: ""}") },
                        trailingContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (entry.photoPath != null) TextButton(onClick = { photoToView = entry.photoPath }) { Text("Photo") }
                                IconButton(onClick = { deleteError = null; entryToDelete = entry }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete ledger entry")
                                }
                            }
                        }
                    )
                    HorizontalDivider()
                }
            }
        }
    }
    if (worker != null && editWorker) WorkerForm(repo, worker, onClose = { editWorker = false })
    if (worker != null && entryType != null) WorkerEntryForm(repo, workerId, entryType!!, onClose = { entryType = null })
    photoToView?.let { PhotoPreviewDialog(it) { photoToView = null } }
    attendanceToDelete?.let { record ->
        DeleteConfirmationDialog(
            title = "Delete attendance?",
            message = "Remove ${record.status.label().lowercase()} attendance for ${record.workDate}? The worker's balance will be recalculated.",
            error = deleteError,
            enabled = !isDeleting,
            onDismiss = { if (!isDeleting) attendanceToDelete = null },
            onConfirm = {
                if (!isDeleting) scope.launch {
                    isDeleting = true
                    runCatching { repo.deleteAttendance(record.id) }
                        .onSuccess { attendanceToDelete = null; deleteError = null }
                        .onFailure { deleteError = it.message ?: "Could not delete attendance." }
                    isDeleting = false
                }
            }
        )
    }
    entryToDelete?.let { entry ->
        DeleteConfirmationDialog(
            title = if (entry.entryType == WorkerEntryType.ADVANCE) "Delete advance?" else "Delete wage payment?",
            message = "Remove ${money(entry.amountMinor)} dated ${entry.entryDate}? The worker's balance will be recalculated.",
            error = deleteError,
            enabled = !isDeleting,
            onDismiss = { if (!isDeleting) entryToDelete = null },
            onConfirm = {
                if (!isDeleting) scope.launch {
                    isDeleting = true
                    runCatching { repo.deleteWorkerEntry(entry.id) }
                        .onSuccess { entryToDelete = null; deleteError = null }
                        .onFailure { deleteError = it.message ?: "Could not delete this entry." }
                    isDeleting = false
                }
            }
        )
    }
}

@Composable
private fun WorkerEntryForm(repo: LedgerRepository, workerId: String, type: WorkerEntryType, onClose: () -> Unit) {
    var amount by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(LocalDate.now().toString()) }
    var pickDate by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf("") }
    var photo by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    FormDialog(if (type == WorkerEntryType.ADVANCE) "Record advance" else "Record wage payment", onClose) {
        Field("Amount (Rs)", amount) { amount = it }
        OutlinedButton(onClick = { pickDate = true }, modifier = Modifier.fillMaxWidth()) { Text("Date: $date") }
        Field("Note (optional)", note) { note = it }
        PhotoAttachment(photo) { photo = it }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        SaveButton {
            val parsed = parseMoney(amount)
            if (parsed == null) error = "Enter an amount greater than zero." else scope.launch {
                runCatching { repo.addWorkerEntry(workerId, type, parsed, date, note, photo) }
                    .onSuccess { onClose() }.onFailure { error = it.message }
            }
        }
    }
    if (pickDate) AttendanceDatePicker(date, onSelect = { date = it }, onClose = { pickDate = false })
}

@Composable
private fun AccountsScreen(repo: LedgerRepository) {
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    if (selectedId != null) {
        CounterpartyProfile(repo, selectedId!!, onBack = { selectedId = null })
        return
    }
    val accounts by repo.counterpartyBalances.collectAsStateWithLifecycle(emptyList())
    var add by remember { mutableStateOf(false) }
    Page("Accounts", onAdd = { add = true }) {
        if (accounts.isEmpty()) Empty("No accounts yet", "Add a site owner or supplier to start a ledger.") else LazyColumn {
            items(accounts, key = { it.id }) { account ->
                ListItem(
                    modifier = Modifier.clickable { selectedId = account.id },
                    headlineContent = { Text(account.name) },
                    supportingContent = { Text(if (account.type == CounterpartyType.SITE_OWNER) "Site owner" else "Supplier") },
                    trailingContent = {
                        val balance = account.balanceMinor
                    Text(
                        if (balance > 0) "They owe ${money(balance)}" else if (balance < 0) "We owe ${money(-balance)}" else "Settled",
                        color = when { balance > 0 -> gainColor; balance < 0 -> MaterialTheme.colorScheme.error; else -> MaterialTheme.colorScheme.onSurface },
                        fontWeight = FontWeight.SemiBold
                    )
                    }
                )
                HorizontalDivider()
            }
        }
    }
    if (add) CounterpartyForm(repo) { add = false }
}

@Composable
private fun CounterpartyProfile(repo: LedgerRepository, id: String, onBack: () -> Unit) {
    val account by repo.counterparty(id).collectAsStateWithLifecycle(null)
    val entries by repo.counterpartyEntries(id).collectAsStateWithLifecycle(emptyList())
    var addEntry by remember { mutableStateOf(false) }
    var editAccount by remember { mutableStateOf(false) }
    var photoToView by remember { mutableStateOf<String?>(null) }
    var entryToDelete by remember { mutableStateOf<CounterpartyLedgerEntryEntity?>(null) }
    var deleteError by remember { mutableStateOf<String?>(null) }
    var isDeleting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val balance = LedgerBalances.counterpartyBalance(entries.map { it.direction to it.amountMinor })
    Page("Account details", onBack = onBack) {
        if (account == null) Text("Account not found.") else {
            Text(account!!.name, style = MaterialTheme.typography.headlineSmall)
            Text(if (account!!.type == CounterpartyType.SITE_OWNER) "Site owner" else "Supplier")
            TextButton(onClick = { editAccount = true }) { Text("Edit account details") }
            Text(
                if (balance > 0) "They owe us: ${money(balance)}" else if (balance < 0) "We owe them: ${money(-balance)}" else "Settled",
                color = when { balance > 0 -> gainColor; balance < 0 -> MaterialTheme.colorScheme.error; else -> MaterialTheme.colorScheme.onSurface },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Button(onClick = { addEntry = true }, modifier = Modifier.fillMaxWidth()) { Text("Add ledger entry") }
            deleteError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            LazyColumn(Modifier.weight(1f)) {
                items(entries, key = { it.id }) { entry ->
                    ListItem(
                        headlineContent = { Text("${if (entry.direction == AccountDirection.THEY_OWE_US) "They owe us" else "We owe them"} • ${money(entry.amountMinor)}", color = if (entry.direction == AccountDirection.THEY_OWE_US) gainColor else MaterialTheme.colorScheme.error) },
                        supportingContent = { Text("${entry.entryDate}${entry.note.takeIf { it.isNotBlank() }?.let { " • $it" } ?: ""}") },
                        trailingContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (entry.photoPath != null) TextButton(onClick = { photoToView = entry.photoPath }) { Text("Photo") }
                                IconButton(onClick = { deleteError = null; entryToDelete = entry }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete ledger entry")
                                }
                            }
                        }
                    )
                    HorizontalDivider()
                }
            }
        }
    }
    if (addEntry) CounterpartyEntryForm(repo, id) { addEntry = false }
    if (account != null && editAccount) CounterpartyForm(repo, account) { editAccount = false }
    photoToView?.let { PhotoPreviewDialog(it) { photoToView = null } }
    entryToDelete?.let { entry ->
        DeleteConfirmationDialog(
            title = "Delete ledger entry?",
            message = "Remove ${money(entry.amountMinor)} dated ${entry.entryDate}? The account balance will be recalculated.",
            error = deleteError,
            enabled = !isDeleting,
            onDismiss = { if (!isDeleting) entryToDelete = null },
            onConfirm = {
                if (!isDeleting) scope.launch {
                    isDeleting = true
                    runCatching { repo.deleteCounterpartyEntry(entry.id) }
                        .onSuccess { entryToDelete = null; deleteError = null }
                        .onFailure { deleteError = it.message ?: "Could not delete this entry." }
                    isDeleting = false
                }
            }
        )
    }
}

@Composable
private fun CounterpartyForm(repo: LedgerRepository, account: CounterpartyEntity? = null, onClose: () -> Unit) {
    var name by remember(account?.id) { mutableStateOf(account?.name.orEmpty()) }
    var phone by remember(account?.id) { mutableStateOf(account?.phone.orEmpty()) }
    var type by remember(account?.id) { mutableStateOf(account?.type ?: CounterpartyType.SITE_OWNER) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    FormDialog(if (account == null) "New account" else "Edit account", onClose) {
        Field("Name", name) { name = it }
        Field("Phone (optional)", phone) { phone = it }
        Row {
            FilterChip(type == CounterpartyType.SITE_OWNER, { type = CounterpartyType.SITE_OWNER }, label = { Text("Site owner") })
            Spacer(Modifier.width(8.dp))
            FilterChip(type == CounterpartyType.SUPPLIER, { type = CounterpartyType.SUPPLIER }, label = { Text("Supplier") })
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        SaveButton {
            scope.launch {
                runCatching {
                    if (account == null) repo.addCounterparty(name, type, phone)
                    else repo.updateCounterparty(account.id, name, type, phone)
                }.onSuccess { onClose() }.onFailure { error = it.message }
            }
        }
    }
}

@Composable
private fun CounterpartyEntryForm(repo: LedgerRepository, id: String, onClose: () -> Unit) {
    var amount by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(LocalDate.now().toString()) }
    var pickDate by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf("") }
    var direction by remember { mutableStateOf(AccountDirection.THEY_OWE_US) }
    var photo by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    FormDialog("New ledger entry", onClose) {
        Row {
            FilterChip(direction == AccountDirection.THEY_OWE_US, { direction = AccountDirection.THEY_OWE_US }, label = { Text("They owe us") })
            Spacer(Modifier.width(8.dp))
            FilterChip(direction == AccountDirection.WE_OWE_THEM, { direction = AccountDirection.WE_OWE_THEM }, label = { Text("We owe them") })
        }
        Text("Add the amount on the side that owes it. To reduce a balance, use the opposite side.", style = MaterialTheme.typography.bodySmall)
        Field("Amount (Rs)", amount) { amount = it }
        OutlinedButton(onClick = { pickDate = true }, modifier = Modifier.fillMaxWidth()) { Text("Date: $date") }
        Field("Note (optional)", note) { note = it }
        PhotoAttachment(photo) { photo = it }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        SaveButton {
            val parsed = parseMoney(amount)
            if (parsed == null) error = "Enter an amount greater than zero." else scope.launch {
                runCatching { repo.addCounterpartyEntry(id, direction, parsed, date, note, photo) }
                    .onSuccess { onClose() }.onFailure { error = it.message }
            }
        }
    }
    if (pickDate) AttendanceDatePicker(date, onSelect = { date = it }, onClose = { pickDate = false })
}

@Composable
private fun WorkerForm(repo: LedgerRepository, worker: WorkerEntity?, onClose: () -> Unit) {
    var name by remember(worker?.id) { mutableStateOf(worker?.fullName.orEmpty()) }
    var wage by remember(worker?.id) { mutableStateOf(worker?.dailyWageMinor?.let(::moneyInput).orEmpty()) }
    var halfDayWage by remember(worker?.id) { mutableStateOf(worker?.halfDayWageMinor?.let(::moneyInput).orEmpty()) }
    var phone by remember(worker?.id) { mutableStateOf(worker?.phone.orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    FormDialog(if (worker == null) "New worker" else "Edit worker", onClose) {
        Field("Full name", name) { name = it }
        Field("Full-day wage (Rs)", wage) { wage = it }
        Field("Half-day wage (Rs)", halfDayWage) { halfDayWage = it }
        Field("Phone (optional)", phone) { phone = it }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        SaveButton {
            val parsed = parseMoney(wage)
            val parsedHalfDay = if (halfDayWage.isBlank()) parsed?.div(2) else parseNonNegativeMoney(halfDayWage)
            if (parsed == null) error = "Enter a full-day wage greater than zero."
            else if (parsedHalfDay == null) error = "Enter a valid half-day wage (zero is allowed)."
            else scope.launch {
                runCatching {
                    if (worker == null) repo.addWorker(name, parsed, parsedHalfDay, phone) else repo.updateWorker(worker.id, name, parsed, parsedHalfDay, phone, worker.trade)
                }.onSuccess { onClose() }.onFailure { error = it.message }
            }
        }
    }
}

@Composable
private fun PhotoPreviewDialog(path: String, onClose: () -> Unit) {
    AlertDialog(onDismissRequest = onClose, confirmButton = { TextButton(onClick = onClose) { Text("Close") } }, text = {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val image by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, path) {
                value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    val options = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    android.graphics.BitmapFactory.decodeFile(path, options)
                    val sample = maxOf(1, maxOf(options.outWidth / 1400, options.outHeight / 1400))
                    android.graphics.BitmapFactory.decodeFile(path, android.graphics.BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
                }
            }
            image?.let { androidx.compose.foundation.Image(it, "Payment proof", Modifier.fillMaxWidth(), contentScale = androidx.compose.ui.layout.ContentScale.Fit) }
                ?: Text("Photo could not be opened.")
        }
    })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Page(title: String, onAdd: (() -> Unit)? = null, onBack: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    Scaffold(topBar = {
        TopAppBar(title = { Text(title) }, navigationIcon = { if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }, actions = {
            if (onAdd != null) IconButton(onClick = onAdd) { Icon(Icons.Default.Add, "Add") }
        })
    }) { padding -> Column(Modifier.padding(padding).padding(horizontal = 16.dp, vertical = 8.dp).fillMaxSize(), content = content) }
}

@Composable
private fun Empty(title: String, detail: String) {
    Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(detail, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun SummaryLine(label: String, amount: String, color: Color = MaterialTheme.colorScheme.onSurface) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label)
        Text(amount, color = color, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun Field(label: String, value: String, change: (String) -> Unit) {
    OutlinedTextField(value = value, onValueChange = change, label = { Text(label) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
}

@Composable
private fun SaveButton(save: () -> Unit) { Button(onClick = save, modifier = Modifier.fillMaxWidth()) { Text("Save") } }

@Composable
private fun DeleteConfirmationDialog(
    title: String,
    message: String,
    error: String?,
    enabled: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(message)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { TextButton(onClick = onConfirm, enabled = enabled) { Text("Delete") } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = enabled) { Text("Cancel") } }
    )
}

@Composable
private fun FormDialog(title: String, close: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    AlertDialog(onDismissRequest = close, title = { Text(title) }, text = {
        Column(Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }, confirmButton = {})
}

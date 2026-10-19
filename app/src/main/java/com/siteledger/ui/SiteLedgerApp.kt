package com.siteledger.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.siteledger.data.*
import com.siteledger.domain.LedgerBalances
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.ZoneOffset
import java.util.Locale

private enum class Destination(val title: String) { LABOUR("Labour"), ACCOUNTS("Accounts") }
private enum class WorkerBalanceFilter { ALL, DUE, PAID, PAID_AHEAD }
private val pendingColor = Color(0xFFF59E0B)
private val paidColor = Color(0xFF22C55E)
private fun money(value: Long): String {
    val formatter = NumberFormat.getNumberInstance(Locale.US).apply {
        minimumFractionDigits = if (value % 100L == 0L) 0 else 2
        maximumFractionDigits = 2
    }
    return "Rs. ${formatter.format(BigDecimal.valueOf(value, 2))}"
}
private fun moneyInput(value: Long) = BigDecimal.valueOf(value, 2).stripTrailingZeros().toPlainString()
private fun displayDate(value: String) = runCatching {
    LocalDate.parse(value).format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault()))
}.getOrDefault(value)
private fun parseMoney(text: String): Long? = runCatching {
    BigDecimal(text.trim().replace(',', '.')).setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact().takeIf { it > 0 }
}.getOrNull()
private fun parseNonNegativeMoney(text: String): Long? = runCatching {
    BigDecimal(text.trim().replace(',', '.')).setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact().takeIf { it >= 0 }
}.getOrNull()

@Composable
fun SiteLedgerApp(repository: LedgerRepository) {
    var destination by rememberSaveable { mutableStateOf(Destination.LABOUR) }
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val scope = rememberCoroutineScope()
    var showBackupOptions by remember { mutableStateOf(false) }
    var showRestoreConfirmation by remember { mutableStateOf(false) }
    var restoreUri by remember { mutableStateOf<Uri?>(null) }
    var transferBusy by remember { mutableStateOf(false) }
    var transferError by remember { mutableStateOf<String?>(null) }
    val createBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) scope.launch {
            transferBusy = true
            val result = runCatching { withContext(Dispatchers.IO) { LedgerBackupManager(context).export(uri) } }
            transferBusy = false
            result.onSuccess { Toast.makeText(context, "SiteLedger backup saved.", Toast.LENGTH_LONG).show() }
                .onFailure { transferError = it.message ?: "Could not create the backup." }
        }
    }
    val selectBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) { restoreUri = uri; showRestoreConfirmation = true }
    }
    Scaffold(containerColor = MaterialTheme.colorScheme.background, bottomBar = {
        NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
            listOf(Destination.LABOUR to Icons.Default.People, Destination.ACCOUNTS to Icons.Default.AccountBalance).forEach { (item, icon) ->
                NavigationBarItem(
                    selected = destination == item,
                    onClick = { destination = item },
                    icon = { Icon(icon, item.title) },
                    label = { Text(item.title) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primary,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }
        }
    }) { padding ->
        Box(Modifier.padding(padding)) {
            when (destination) {
                Destination.LABOUR -> LabourScreen(repository)
                Destination.ACCOUNTS -> AccountsScreen(repository, onManageBackups = { showBackupOptions = true })
            }
        }
    }
    if (showBackupOptions) AlertDialog(
        onDismissRequest = { showBackupOptions = false },
        title = { Text("Export or import data") },
        text = { Text("A backup includes your workers, attendance, wages, accounts, and attached proof photos. The ZIP is not encrypted and may contain private financial information, so store it somewhere safe. Restoring replaces the data currently on this phone.") },
        confirmButton = { TextButton(onClick = {
            showBackupOptions = false
            createBackup.launch("siteledger-backup-${LocalDate.now()}.zip")
        }) { Text("Export backup") } },
        dismissButton = { Row {
            TextButton(onClick = { showBackupOptions = false; selectBackup.launch(arrayOf("application/zip", "application/octet-stream", "application/x-zip-compressed")) }) { Text("Import backup") }
            TextButton(onClick = { showBackupOptions = false }) { Text("Cancel") }
        } }
    )
    if (showRestoreConfirmation) AlertDialog(
        onDismissRequest = { showRestoreConfirmation = false; restoreUri = null },
        title = { Text("Replace data with this import?") },
        text = { Text("Restoring replaces the workers, attendance, wages, accounts, and proof photos currently stored on this phone. This cannot be undone. Create a backup of the current phone first if you need to keep its data.") },
        confirmButton = { TextButton(onClick = {
            val uri = restoreUri
            showRestoreConfirmation = false
            restoreUri = null
            if (uri != null) scope.launch {
                transferBusy = true
                val result = runCatching { withContext(Dispatchers.IO) { LedgerBackupManager(context).restore(uri) } }
                transferBusy = false
                result.onSuccess {
                    Toast.makeText(context, "Backup restored.", Toast.LENGTH_LONG).show()
                    activity?.recreate()
                }.onFailure {
                    Toast.makeText(context, it.message ?: "Could not restore this backup.", Toast.LENGTH_LONG).show()
                    activity?.recreate()
                }
            }
        }) { Text("Import and replace") } },
        dismissButton = { TextButton(onClick = { showRestoreConfirmation = false; restoreUri = null }) { Text("Cancel") } }
    )
    if (transferBusy) AlertDialog(
        onDismissRequest = {},
        title = { Text("Working with backup…") },
        text = { Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) { CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp); Text("Keep SiteLedger open until this finishes.") } },
        confirmButton = {}
    )
    transferError?.let { message -> AlertDialog(
        onDismissRequest = { transferError = null },
        title = { Text("Backup failed") },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = { transferError = null }) { Text("OK") } }
    ) }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
private fun LabourScreen(repo: LedgerRepository) {
    var selectedWorkerId by rememberSaveable { mutableStateOf<String?>(null) }
    var showBalances by rememberSaveable { mutableStateOf(false) }
    var showAttendanceSheet by rememberSaveable { mutableStateOf(false) }
    var balanceFilter by rememberSaveable { mutableStateOf(WorkerBalanceFilter.ALL) }
    var showArchived by rememberSaveable { mutableStateOf(false) }
    val activeWorkers by repo.workers.collectAsStateWithLifecycle(emptyList())
    val allWorkers by repo.allWorkers.collectAsStateWithLifecycle(emptyList())
    val workers = if (showArchived) allWorkers.filterNot { it.active } else activeWorkers
    val labourTotals by repo.labourTotals.collectAsStateWithLifecycle(LabourTotals(0L, 0L))
    val workerBalances by repo.workerBalances.collectAsStateWithLifecycle(emptyList())
    var addWorker by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    BackHandler(selectedWorkerId != null || showBalances || showAttendanceSheet) {
        when {
            selectedWorkerId != null -> selectedWorkerId = null
            showBalances -> showBalances = false
            else -> showAttendanceSheet = false
        }
    }
    if (selectedWorkerId != null) WorkerProfile(repo, selectedWorkerId!!, onBack = { selectedWorkerId = null }) else if (showBalances) {
        WorkerBalancesScreen(repo, initialFilter = balanceFilter, onBack = { showBalances = false }, onSelectWorker = { selectedWorkerId = it })
    } else if (showAttendanceSheet) {
        AttendanceSheetScreen(repo, onBack = { showAttendanceSheet = false })
    } else {
    Page("Labour", onAdd = { addWorker = true }) {
        val wagesDue = workerBalances.filter { it.balanceMinor > 0 }.sumOf { it.balanceMinor }
        OutlinedButton(onClick = { showAttendanceSheet = true }, modifier = Modifier.fillMaxWidth()) { Text("Attendance sheet") }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LedgerSummaryCard(
                modifier = Modifier.weight(1f).clickable { balanceFilter = WorkerBalanceFilter.DUE; showBalances = true },
                label = "Total Pending",
                amount = money(wagesDue),
                detail = "${workerBalances.count { it.balanceMinor > 0 }} workers due",
                accent = pendingColor
            )
            LedgerSummaryCard(
                modifier = Modifier.weight(1f).clickable { balanceFilter = WorkerBalanceFilter.PAID; showBalances = true },
                label = "Total Paid",
                amount = money(labourTotals.totalPaidMinor),
                detail = "Wages and advances",
                accent = paidColor
            )
        }
        Spacer(Modifier.height(18.dp))
        SegmentedTabs(
            labels = listOf("Active (${activeWorkers.size})", "Archived (${allWorkers.size - activeWorkers.size})"),
            selectedIndex = if (showArchived) 1 else 0,
            onSelected = { showArchived = it == 1 }
        )
        if (workers.isEmpty()) {
            Empty(if (showArchived) "No archived workers" else "No workers yet", if (showArchived) "Archived workers will appear here." else "Add a worker to start tracking wages and attendance.")
            if (!showArchived) OutlinedButton(onClick = { addWorker = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Add your first worker")
            }
        } else {
            Text("${workers.size} ${if (workers.size == 1) "worker" else "workers"} · wage summary", style = MaterialTheme.typography.bodySmall)
            val balancesById = workerBalances.associateBy { it.id }
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(vertical = 4.dp)) {
                items(workers.filter { showArchived || it.active }, key = { it.id }) { worker ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    Modifier.size(42.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) { Icon(Icons.Default.People, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(20.dp)) }
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f).clickable { selectedWorkerId = worker.id }) {
                                    Text(worker.fullName, fontWeight = FontWeight.SemiBold)
                                    val balance = balancesById[worker.id]
                                    Text("${money(worker.dailyWageMinor)} per day · ${money(balance?.totalPaidMinor ?: 0L)} paid", style = MaterialTheme.typography.bodySmall)
                                }
                                TextButton(onClick = { selectedWorkerId = worker.id }) { Text("Account") }
                                TextButton(onClick = { scope.launch { repo.setWorkerActive(worker.id, !worker.active) } }) { Text(if (worker.active) "Archive" else "Restore") }
                            }
                            val balance = balancesById[worker.id]
                            Text("${if ((balance?.balanceMinor ?: 0L) > 0) "Pending" else if ((balance?.balanceMinor ?: 0L) < 0) "Paid ahead" else "Settled"}: ${money(kotlin.math.abs(balance?.balanceMinor ?: 0L))}", style = MaterialTheme.typography.bodySmall, color = if ((balance?.balanceMinor ?: 0L) > 0) pendingColor else paidColor)
                        }
                    }
                }
            }
        }
    }
    if (addWorker) WorkerForm(repo, null, onClose = { addWorker = false })
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
private fun AttendanceSheetScreen(repo: LedgerRepository, onBack: () -> Unit) {
    var monthText by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    val month = runCatching { YearMonth.parse(monthText) }.getOrDefault(YearMonth.now())
    val workers by repo.workers.collectAsStateWithLifecycle(emptyList())
    val records by repo.attendanceForRange(month.atDay(1).toString(), month.atEndOfMonth().toString()).collectAsStateWithLifecycle(emptyList())
    var selectedCell by remember { mutableStateOf<AttendanceCell?>(null) }
    val recordsByKey = records.associateBy { it.workerId to it.workDate }
    val dayWidth = 46.dp
    val nameWidth = 148.dp
    val daysScroll = rememberScrollState()
    Page("Attendance", onBack = onBack) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { monthText = month.minusMonths(1).toString() }) { Text("‹") }
            Text(month.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            TextButton(onClick = { monthText = month.plusMonths(1).toString() }) { Text("›") }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${workers.size} labourers", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("${records.size} days marked", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.weight(1f)) {
            LazyColumn(Modifier.fillMaxWidth()) {
                item {
                    Row {
                        SheetCell("LABOUR", nameWidth, true)
                        Row(Modifier.weight(1f).horizontalScroll(daysScroll)) {
                        (1..month.lengthOfMonth()).forEach { day ->
                            val date = month.atDay(day)
                            SheetCell("$day\n${date.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, Locale.getDefault())}", dayWidth, true)
                        }
                        }
                    }
                }
                items(workers, key = { it.id }) { worker ->
                    Row {
                        Box(Modifier.width(nameWidth).height(48.dp).border(1.dp, MaterialTheme.colorScheme.outline).padding(horizontal = 8.dp), contentAlignment = Alignment.CenterStart) {
                            Text(worker.fullName, style = MaterialTheme.typography.labelMedium, maxLines = 2)
                        }
                        Row(Modifier.weight(1f).horizontalScroll(daysScroll)) {
                            (1..month.lengthOfMonth()).forEach { day ->
                                val date = month.atDay(day).toString()
                                val record = recordsByKey[worker.id to date]
                                val status = record?.status
                                val tint = when (status) {
                                    AttendanceStatus.PRESENT -> Color(0xFFE3F7EA)
                                    AttendanceStatus.HALF_DAY -> Color(0xFFFFF2D8)
                                    AttendanceStatus.ABSENT -> Color(0xFFFDE8E7)
                                    null -> MaterialTheme.colorScheme.surface
                                }
                                Box(Modifier.width(dayWidth).height(48.dp).border(1.dp, MaterialTheme.colorScheme.outline).background(tint).clickable { selectedCell = AttendanceCell(worker.id, worker.fullName, date, record) }, contentAlignment = Alignment.Center) {
                                    Text(when (status) { AttendanceStatus.PRESENT -> "P"; AttendanceStatus.HALF_DAY -> "½"; AttendanceStatus.ABSENT -> "A"; null -> "" }, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text("P Present   ·   ½ Half-day   ·   A Absent   ·   Tap a cell to edit", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    selectedCell?.let { cell ->
        AttendanceCellDialog(cell, onDismiss = { selectedCell = null }, onSave = { status ->
            repo.saveAttendanceStatus(cell.workerId, cell.date, status)
        }, onClear = { record -> repo.deleteAttendance(record.id) })
    }
}

private data class AttendanceCell(val workerId: String, val workerName: String, val date: String, val record: AttendanceEntity?)

@Composable
private fun SheetCell(text: String, width: androidx.compose.ui.unit.Dp, header: Boolean) {
    Box(Modifier.width(width).height(48.dp).border(1.dp, MaterialTheme.colorScheme.outline).background(if (header) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface).padding(2.dp), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.labelSmall, fontWeight = if (header) FontWeight.Bold else FontWeight.Normal, textAlign = androidx.compose.ui.text.style.TextAlign.Center, lineHeight = 13.sp)
    }
}

@Composable
private fun AttendanceCellDialog(cell: AttendanceCell, onDismiss: () -> Unit, onSave: suspend (AttendanceStatus) -> Unit, onClear: suspend (AttendanceEntity) -> Unit) {
    var selected by remember(cell.date, cell.record?.id) { mutableStateOf(cell.record?.status ?: AttendanceStatus.PRESENT) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text(cell.workerName) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(displayDate(cell.date))
                AttendanceStatus.entries.forEach { status -> FilterChip(selected == status, onClick = { selected = status }, label = { Text(status.label()) }) }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { TextButton(enabled = !saving, onClick = {
            saving = true; error = null
            scope.launch {
                try { onSave(selected); onDismiss() } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled } catch (exception: Exception) { error = exception.message ?: "Could not save attendance." } finally { saving = false }
            }
        }) { Text(if (saving) "Saving…" else "Save") } },
        dismissButton = {
            Row {
                if (cell.record != null) TextButton(enabled = !saving, onClick = {
                    saving = true; error = null
                    scope.launch {
                        try { onClear(cell.record); onDismiss() } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled } catch (exception: Exception) { error = exception.message ?: "Could not remove attendance." } finally { saving = false }
                    }
                }) { Text("Clear") }
                TextButton(enabled = !saving, onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}

@Composable
private fun WorkerBalancesScreen(repo: LedgerRepository, initialFilter: WorkerBalanceFilter, onBack: () -> Unit, onSelectWorker: (String) -> Unit) {
    val balances by repo.workerBalances.collectAsStateWithLifecycle(emptyList())
    val owedToWorkers = balances.filter { it.balanceMinor > 0 }.sumOf { it.balanceMinor }
    val advancesAhead = balances.filter { it.balanceMinor < 0 }.sumOf { -it.balanceMinor }
    var filter by rememberSaveable { mutableStateOf(initialFilter) }
    val filtered = when (filter) {
        WorkerBalanceFilter.ALL -> balances
        WorkerBalanceFilter.DUE -> balances.filter { it.balanceMinor > 0 }
        WorkerBalanceFilter.PAID -> balances.filter { it.totalPaidMinor > 0 }
        WorkerBalanceFilter.PAID_AHEAD -> balances.filter { it.balanceMinor < 0 }
    }
    val ordered = filtered.sortedWith(
        compareBy<WorkerBalance> { when (filter) { WorkerBalanceFilter.PAID -> if (it.totalPaidMinor > 0) 0 else 1; else -> when { it.balanceMinor > 0 -> 0; it.balanceMinor < 0 -> 1; else -> 2 } } }
            .thenByDescending { if (filter == WorkerBalanceFilter.PAID) it.totalPaidMinor else 0L }
            .thenBy { !it.active }
            .thenBy { it.name }
    )
    Page("Worker balances", onBack = onBack) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LedgerSummaryCard(
                modifier = Modifier.weight(1f),
                label = "Wages due",
                amount = money(owedToWorkers),
                detail = "${balances.count { it.balanceMinor > 0 }} workers",
                accent = pendingColor
            )
            LedgerSummaryCard(
                modifier = Modifier.weight(1f),
                label = "Paid ahead",
                amount = money(advancesAhead),
                detail = "${balances.count { it.balanceMinor < 0 }} workers",
                accent = paidColor
            )
        }
        Spacer(Modifier.height(18.dp))
        SegmentedTabs(
            labels = listOf("All (${balances.size})", "Pending (${balances.count { it.balanceMinor > 0 }})", "Paid (${balances.count { it.totalPaidMinor > 0 }})", "Credit (${balances.count { it.balanceMinor < 0 }})"),
            selectedIndex = when (filter) { WorkerBalanceFilter.ALL -> 0; WorkerBalanceFilter.DUE -> 1; WorkerBalanceFilter.PAID -> 2; WorkerBalanceFilter.PAID_AHEAD -> 3 },
            onSelected = { filter = when (it) { 1 -> WorkerBalanceFilter.DUE; 2 -> WorkerBalanceFilter.PAID; 3 -> WorkerBalanceFilter.PAID_AHEAD; else -> WorkerBalanceFilter.ALL } }
        )
        Spacer(Modifier.height(12.dp))
        if (balances.isEmpty()) {
            Empty("No workers yet", "Add a worker to start tracking wages and advances.")
        } else if (filtered.isEmpty()) {
            Empty("Nothing to show", "No workers match this balance filter.")
        } else {
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
                items(ordered, key = { it.id }) { item ->
                    val (label, amount, color) = when {
                        filter == WorkerBalanceFilter.PAID -> Triple("Paid to date", money(item.totalPaidMinor), paidColor)
                        item.balanceMinor > 0 -> Triple("We owe", money(item.balanceMinor), pendingColor)
                        item.balanceMinor < 0 -> Triple("Paid ahead (worker owes us)", money(-item.balanceMinor), paidColor)
                        else -> Triple("Settled", money(0), MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { onSelectWorker(item.id) },
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(44.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape), contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.People, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(item.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                Text("$label${if (item.active) "" else " · Archived"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(amount, color = color, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WorkerProfile(repo: LedgerRepository, workerId: String, onBack: () -> Unit) {
    BackHandler { onBack() }
    val worker by repo.worker(workerId).collectAsStateWithLifecycle(null)
    val attendance by repo.workerAttendance(workerId).collectAsStateWithLifecycle(emptyList())
    val entries by repo.workerEntries(workerId).collectAsStateWithLifecycle(emptyList())
    var entryType by remember { mutableStateOf<WorkerEntryType?>(null) }
    var editWorker by remember { mutableStateOf(false) }
    var photoToView by remember { mutableStateOf<String?>(null) }
    var attendanceToEdit by remember { mutableStateOf<AttendanceEntity?>(null) }
    var attendanceToDelete by remember { mutableStateOf<AttendanceEntity?>(null) }
    var entryToDelete by remember { mutableStateOf<WorkerLedgerEntryEntity?>(null) }
    var deleteError by remember { mutableStateOf<String?>(null) }
    var attendanceEditError by remember { mutableStateOf<String?>(null) }
    var isDeleting by remember { mutableStateOf(false) }
    var isSavingAttendance by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val balance = LedgerBalances.workerBalance(attendance.map { it.earnedMinor }, entries.map { it.amountMinor })
    Page("Worker account", onBack = onBack) {
        if (worker == null) Text("Worker not found.") else {
            val current = worker!!
            Text(current.fullName, style = MaterialTheme.typography.headlineSmall)
            Text("Full day: ${money(current.dailyWageMinor)} • Half-day: ${money(current.halfDayWageMinor)}${current.phone?.let { " • $it" } ?: ""}")
            Text(
                if (balance > 0) "Wages due: ${money(balance)}" else if (balance < 0) "Advance paid ahead: ${money(-balance)}" else "Settled",
                color = when { balance > 0 -> pendingColor; balance < 0 -> paidColor; else -> MaterialTheme.colorScheme.onSurface },
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
                            Row {
                                IconButton(onClick = { attendanceEditError = null; attendanceToEdit = record }) {
                                    Icon(Icons.Default.Edit, contentDescription = "Edit attendance")
                                }
                                IconButton(onClick = { deleteError = null; attendanceToDelete = record }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete attendance")
                                }
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
    attendanceToEdit?.let { record ->
        AttendanceEditDialog(
            record = record,
            error = attendanceEditError,
            saving = isSavingAttendance,
            onDismiss = { if (!isSavingAttendance) attendanceToEdit = null },
            onSave = { status ->
                if (!isSavingAttendance) scope.launch {
                    isSavingAttendance = true
                    runCatching { repo.updateAttendanceStatus(record.id, status) }
                        .onSuccess { attendanceToEdit = null; attendanceEditError = null }
                        .onFailure { attendanceEditError = it.message ?: "Could not update attendance." }
                    isSavingAttendance = false
                }
            }
        )
    }
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
private fun AttendanceEditDialog(
    record: AttendanceEntity,
    error: String?,
    saving: Boolean,
    onDismiss: () -> Unit,
    onSave: (AttendanceStatus) -> Unit
) {
    var selected by remember(record.id) { mutableStateOf(record.status) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit attendance") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Attendance date: ${record.workDate}")
                AttendanceStatus.entries.forEach { status ->
                    FilterChip(
                        selected = selected == status,
                        onClick = { selected = status },
                        label = { Text(status.label()) }
                    )
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(selected) }, enabled = !saving) { Text(if (saving) "Saving…" else "Save") } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !saving) { Text("Cancel") } }
    )
}

@Composable
private fun WorkerEntryForm(repo: LedgerRepository, workerId: String, type: WorkerEntryType, onClose: () -> Unit) {
    var amount by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(LocalDate.now().toString()) }
    var pickDate by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf("") }
    var photo by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    FormDialog(if (type == WorkerEntryType.ADVANCE) "Record advance" else "Record wage payment", onClose) {
        Field("Amount (Rs)", amount) { amount = it }
        OutlinedButton(onClick = { pickDate = true }, modifier = Modifier.fillMaxWidth()) { Text("Date: $date") }
        Field("Note (optional)", note) { note = it }
        PhotoAttachment(photo) { photo = it }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        SaveButton(isSaving) {
            val parsed = parseMoney(amount)
            if (parsed == null) error = "Enter an amount greater than zero." else if (!isSaving) scope.launch {
                isSaving = true
                runCatching { repo.addWorkerEntry(workerId, type, parsed, date, note, photo) }
                    .onSuccess { onClose() }.onFailure { error = it.message }
                isSaving = false
            }
        }
    }
    if (pickDate) AttendanceDatePicker(date, onSelect = { date = it }, onClose = { pickDate = false })
}

@Composable
private fun AccountsScreen(repo: LedgerRepository, onManageBackups: () -> Unit) {
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    BackHandler(selectedId != null) { selectedId = null }
    var showArchived by rememberSaveable { mutableStateOf(false) }
    val allAccounts by repo.allCounterparties.collectAsStateWithLifecycle(emptyList())
    val activeBalances by repo.counterpartyBalances.collectAsStateWithLifecycle(emptyList())
    val accounts = if (showArchived) allAccounts.filterNot { it.active }.map { entity -> CounterpartyBalance(entity.id, entity.name, entity.type, activeBalances.firstOrNull { it.id == entity.id }?.balanceMinor ?: 0L) } else activeBalances
    val activeAccountCount = allAccounts.count { it.active }
    val scope = rememberCoroutineScope()
    var add by remember { mutableStateOf(false) }
    val receivable = activeBalances.filter { it.balanceMinor > 0 }.sumOf { it.balanceMinor }
    val payable = activeBalances.filter { it.balanceMinor < 0 }.sumOf { -it.balanceMinor }
    if (selectedId != null) CounterpartyProfile(repo, selectedId!!, onBack = { selectedId = null }) else Page("Accounts", onAdd = { add = true }) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LedgerSummaryCard(
                modifier = Modifier.weight(1f),
                label = "They owe us",
                amount = money(receivable),
                detail = "${activeBalances.count { it.balanceMinor > 0 }} accounts",
                accent = paidColor
            )
            LedgerSummaryCard(
                modifier = Modifier.weight(1f),
                label = "We owe",
                amount = money(payable),
                detail = "${activeBalances.count { it.balanceMinor < 0 }} accounts",
                accent = pendingColor
            )
        }
        Spacer(Modifier.height(18.dp))
        OutlinedButton(onClick = onManageBackups, modifier = Modifier.fillMaxWidth()) { Text("Export / import data") }
        Spacer(Modifier.height(12.dp))
        SegmentedTabs(
            labels = listOf("Active ($activeAccountCount)", "Archived (${allAccounts.size - activeAccountCount})"),
            selectedIndex = if (showArchived) 1 else 0,
            onSelected = { showArchived = it == 1 }
        )
        if (accounts.isEmpty()) Empty(if (showArchived) "No archived accounts" else "No accounts yet", if (showArchived) "Archived accounts will appear here." else "Add a site owner or supplier to start a ledger.") else LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(vertical = 8.dp)) {
            items(accounts, key = { it.id }) { account ->
                val entity = allAccounts.first { it.id == account.id }
                val balance = account.balanceMinor
                Card(
                    Modifier.fillMaxWidth().padding(vertical = 5.dp),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Row(
                            Modifier.fillMaxWidth().clickable { selectedId = account.id },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(account.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Text(if (account.type == CounterpartyType.SITE_OWNER) "Site owner" else "Supplier", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    if (balance > 0) "They owe" else if (balance < 0) "We owe" else "Settled",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (balance != 0L) Text(money(kotlin.math.abs(balance)), color = if (balance > 0) paidColor else pendingColor, fontWeight = FontWeight.SemiBold)
                            }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = { scope.launch { repo.setCounterpartyActive(account.id, !entity.active) } }) { Text(if (entity.active) "Archive" else "Restore") }
                        }
                    }
                }
            }
        }
    }
    if (add) CounterpartyForm(repo) { add = false }
}

@Composable
private fun CounterpartyProfile(repo: LedgerRepository, id: String, onBack: () -> Unit) {
    BackHandler { onBack() }
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
                color = when { balance > 0 -> paidColor; balance < 0 -> pendingColor; else -> MaterialTheme.colorScheme.onSurface },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Button(onClick = { addEntry = true }, modifier = Modifier.fillMaxWidth()) { Text("Add ledger entry") }
            deleteError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            LazyColumn(Modifier.weight(1f)) {
                items(entries, key = { it.id }) { entry ->
                    ListItem(
                        headlineContent = { Text("${if (entry.direction == AccountDirection.THEY_OWE_US) "They owe us" else "We owe them"} • ${money(entry.amountMinor)}", color = if (entry.direction == AccountDirection.THEY_OWE_US) paidColor else pendingColor) },
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
    var isSaving by remember { mutableStateOf(false) }
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
        SaveButton(isSaving) {
            if (!isSaving) scope.launch {
                isSaving = true
                runCatching {
                    if (account == null) repo.addCounterparty(name, type, phone)
                    else repo.updateCounterparty(account.id, name, type, phone)
                }.onSuccess { onClose() }.onFailure { error = it.message }
                isSaving = false
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
    var isSaving by remember { mutableStateOf(false) }
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
        SaveButton(isSaving) {
            val parsed = parseMoney(amount)
            if (parsed == null) error = "Enter an amount greater than zero." else if (!isSaving) scope.launch {
                isSaving = true
                runCatching { repo.addCounterpartyEntry(id, direction, parsed, date, note, photo) }
                    .onSuccess { onClose() }.onFailure { error = it.message }
                isSaving = false
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
    var trade by remember(worker?.id) { mutableStateOf(worker?.trade.orEmpty()) }
    var isSaving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    FormDialog(if (worker == null) "New worker" else "Edit worker", onClose) {
        Field("Full name", name) { name = it }
        Field("Full-day wage (Rs)", wage) { wage = it }
        Field("Half-day wage (Rs)", halfDayWage) { halfDayWage = it }
        Field("Phone (optional)", phone) { phone = it }
        Field("Trade (optional)", trade) { trade = it }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        SaveButton(isSaving) {
            val parsed = parseMoney(wage)
            val parsedHalfDay = if (halfDayWage.isBlank()) parsed?.div(2) else parseNonNegativeMoney(halfDayWage)
            if (parsed == null) error = "Enter a full-day wage greater than zero."
            else if (parsedHalfDay == null) error = "Enter a valid half-day wage (zero is allowed)."
            else if (!isSaving) scope.launch {
                isSaving = true
                runCatching {
                    if (worker == null) repo.addWorker(name, parsed, parsedHalfDay, phone, trade) else repo.updateWorker(worker.id, name, parsed, parsedHalfDay, phone, trade)
                }.onSuccess { onClose() }.onFailure { error = it.message }
                isSaving = false
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
    Scaffold(containerColor = MaterialTheme.colorScheme.background, topBar = {
        TopAppBar(
            title = { Text(title, fontSize = 26.sp, fontWeight = FontWeight.Bold) },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background,
                titleContentColor = MaterialTheme.colorScheme.onSurface,
                navigationIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                actionIconContentColor = MaterialTheme.colorScheme.primary
            ),
            navigationIcon = { if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            actions = {
            if (onAdd != null) IconButton(
                onClick = onAdd,
                modifier = Modifier.padding(end = 8.dp).size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary)
            ) { Icon(Icons.Default.Add, "Add", tint = MaterialTheme.colorScheme.onPrimary) }
        })
    }) { padding -> Column(Modifier.padding(padding).padding(horizontal = 16.dp, vertical = 8.dp).fillMaxSize(), content = content) }
}

@Composable
private fun LedgerSummaryCard(label: String, amount: String, detail: String, accent: Color, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            Text(amount, fontSize = 19.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(detail, style = MaterialTheme.typography.labelSmall, color = accent, fontWeight = FontWeight.Medium, maxLines = 1)
        }
    }
}

@Composable
private fun SegmentedTabs(labels: List<String>, selectedIndex: Int, onSelected: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        labels.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            val shape = RoundedCornerShape(10.dp)
            Box(
                Modifier.weight(1f)
                    .then(if (selected) Modifier.shadow(2.dp, shape) else Modifier)
                    .clip(shape)
                    .background(if (selected) MaterialTheme.colorScheme.surface else Color.Transparent)
                    .clickable { onSelected(index) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    fontSize = 13.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
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
private fun SaveButton(loading: Boolean = false, save: () -> Unit) { Button(onClick = save, enabled = !loading, modifier = Modifier.fillMaxWidth()) { if (loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Save") } }

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

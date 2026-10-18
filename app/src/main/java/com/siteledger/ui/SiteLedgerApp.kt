package com.siteledger.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.siteledger.data.*
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode

private enum class Destination(val title: String) { HOME("Home"), PROJECTS("Projects"), LABOUR("Labour"), MATERIALS("Materials"), ACCOUNTS("Accounts") }
private fun money(value: Long) = "Rs. %,d".format(value / 100)
private fun parseMoney(text: String) = runCatching {
    BigDecimal(text.trim()).setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact()
}.getOrDefault(0L)

@Composable fun SiteLedgerApp(repository: LedgerRepository) {
    var destination by rememberSaveable { mutableStateOf(Destination.HOME) }
    Scaffold(bottomBar = { NavigationBar { listOf(Destination.HOME to Icons.Default.Home, Destination.PROJECTS to Icons.Default.Business, Destination.LABOUR to Icons.Default.People, Destination.MATERIALS to Icons.Default.Inventory2, Destination.ACCOUNTS to Icons.Default.AccountBalance).forEach { (item, icon) -> NavigationBarItem(selected = destination == item, onClick = { destination = item }, icon = { Icon(icon, item.title) }, label = { Text(item.title) }) } } }) { padding -> Box(Modifier.padding(padding)) { when(destination) { Destination.HOME -> Dashboard(repository); Destination.PROJECTS -> Projects(repository); Destination.LABOUR -> Labour(repository); Destination.MATERIALS -> Materials(repository); Destination.ACCOUNTS -> Accounts(repository) } } }
}

@Composable private fun Dashboard(repo: LedgerRepository) {
    val projects by repo.projects.collectAsStateWithLifecycle(emptyList()); val present by repo.presentCount().collectAsStateWithLifecycle(0); val today by repo.todayExpense().collectAsStateWithLifecycle(0L); val stock by repo.stock.collectAsStateWithLifecycle(emptyList())
    Page("Good day") { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { Text("Today’s site overview", style = MaterialTheme.typography.titleMedium); Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) { Metric("Active projects", projects.count { it.status == ProjectStatus.ACTIVE }.toString(), Modifier.weight(1f)); Metric("Present today", present.toString(), Modifier.weight(1f)) }; Metric("Today’s expenses", money(today), Modifier.fillMaxWidth()); Text("Attention", style = MaterialTheme.typography.titleMedium); val low = stock.filter { it.quantity <= it.minimumQuantity }; if (low.isEmpty()) Text("No low-stock alerts. Add materials and receipts to start tracking inventory.") else low.forEach { ListItem(headlineContent = { Text(it.name) }, supportingContent = { Text("${it.quantity} ${it.unit} remaining • minimum ${it.minimumQuantity}") }, leadingContent = { Icon(Icons.Default.Warning, "Low stock") }) } } }
}

@Composable private fun Projects(repo: LedgerRepository) { val projects by repo.projects.collectAsStateWithLifecycle(emptyList()); var show by remember { mutableStateOf(false) }; Page("Projects", { show = true }) { if (projects.isEmpty()) Empty("No projects yet", "Create your first site to record costs, attendance, and stock.") else LazyColumn { items(projects, key = { it.id }) { p -> ListItem(headlineContent = { Text(p.name) }, supportingContent = { Text("${p.reference} • ${p.clientName.ifBlank { "No client" }} • ${p.status.name.lowercase()}") }, trailingContent = { Text(money(p.budgetAmountMinor), style = MaterialTheme.typography.labelMedium) }); HorizontalDivider() } } }; if(show) ProjectForm(repo) { show = false } }

@Composable private fun Labour(repo: LedgerRepository) {
    val projects by repo.projects.collectAsStateWithLifecycle(emptyList())
    val unassigned by repo.unassignedWorkers.collectAsStateWithLifecycle(emptyList())
    var selectedProjectId by remember { mutableStateOf<String?>(null) }
    var showUnassigned by remember { mutableStateOf(false) }
    var add by remember { mutableStateOf(false) }
    var attendance by remember { mutableStateOf<Pair<WorkerEntity, String>?>(null) }
    var assignment by remember { mutableStateOf<Pair<WorkerEntity, String?>?>(null) }
    val workers by repo.workersForProject(selectedProjectId).collectAsStateWithLifecycle(emptyList())
    LaunchedEffect(projects) {
        if (selectedProjectId == null && projects.size == 1) selectedProjectId = projects.single().id
    }
    Page("Labour", { add = true }) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ProjectPicker(projects, selectedProjectId, { selectedProjectId = it })
            FilterChip(selected = showUnassigned, onClick = { showUnassigned = !showUnassigned }, label = { Text("Unassigned workers") })
            when {
                projects.isEmpty() -> Text("Create a project or site before adding labour.")
                showUnassigned && unassigned.isEmpty() -> Text("All workers are assigned to a site.")
                showUnassigned -> LazyColumn { items(unassigned, key = { it.id }) { worker ->
                    ListItem(headlineContent = { Text(worker.fullName) }, supportingContent = { Text(worker.trade) }, trailingContent = {
                        TextButton(onClick = { assignment = worker to null }) { Text("Assign") }
                    })
                    HorizontalDivider()
                } }
                selectedProjectId == null -> Text("Select a project to see its labour and attendance.")
                workers.isEmpty() -> Text("No workers assigned to this site yet.")
                else -> LazyColumn { items(workers, key = { it.id }) { worker ->
                    val projectId = selectedProjectId
                    ListItem(headlineContent = { Text(worker.fullName) }, supportingContent = { Text("${worker.trade} • ${money(worker.dailyWageMinor)}/day") }, trailingContent = {
                        Row {
                            TextButton(onClick = { attendance = worker to projectId!! }) { Text("Attendance") }
                            TextButton(onClick = { assignment = worker to projectId }) { Text("Move") }
                        }
                    })
                    HorizontalDivider()
                } }
            }
        }
    }
    if (add) WorkerForm(repo, selectedProjectId) { add = false }
    attendance?.let { (worker, projectId) -> AttendanceForm(repo, worker, projectId) { attendance = null } }
    assignment?.let { (worker, currentProjectId) -> AssignWorkerForm(repo, worker, projects, currentProjectId) { assignment = null } }
}

@Composable private fun Materials(repo: LedgerRepository) { val materials by repo.materials.collectAsStateWithLifecycle(emptyList()); val stock by repo.stock.collectAsStateWithLifecycle(emptyList()); val projects by repo.projects.collectAsStateWithLifecycle(emptyList()); var add by remember{mutableStateOf(false)}; var receive by remember{mutableStateOf<MaterialEntity?>(null)}; Page("Materials", {add=true}) { if(materials.isEmpty()) Empty("No materials yet", "Add cement, steel, sand, or other tracked materials.") else LazyColumn { items(stock, key={it.materialId}) { s -> ListItem(headlineContent={Text(s.name)}, supportingContent={Text("${s.quantity} ${s.unit} • min ${s.minimumQuantity}")}, trailingContent={TextButton(onClick={receive=materials.first { it.id==s.materialId }}){Text("Receive")}}); HorizontalDivider() } } }; if(add) MaterialForm(repo){add=false}; receive?.let{ReceiveForm(repo,it,projects){receive=null}} }

@Composable private fun Accounts(repo: LedgerRepository) { val expenses by repo.expenses.collectAsStateWithLifecycle(emptyList()); val projects by repo.projects.collectAsStateWithLifecycle(emptyList()); var add by remember { mutableStateOf(false) }; Page("Accounts", {add=true}) { if(expenses.isEmpty()) Empty("No expenses yet", "Record an expense to see project costs here.") else LazyColumn { items(expenses, key={it.id}) { e -> ListItem(headlineContent={Text(e.category)}, supportingContent={Text("${e.description.ifBlank{"No description"}} • ${e.expenseDate}")}, trailingContent={Text(money(e.amountMinor))}); HorizontalDivider() } } }; if(add) ExpenseForm(repo,projects){add=false} }

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun Page(title:String, onAdd:(()->Unit)?=null, content:@Composable ColumnScope.()->Unit) { Scaffold(topBar={TopAppBar(title={Text(title)}, actions={if(onAdd!=null) IconButton(onClick=onAdd){Icon(Icons.Default.Add,"Add")}})}) { p -> Column(Modifier.padding(p).padding(16.dp).fillMaxSize(), content=content) } }
@Composable private fun Metric(label:String, value:String, modifier:Modifier=Modifier) { Card(modifier) { Column(Modifier.padding(16.dp)) { Text(label, style=MaterialTheme.typography.labelMedium); Text(value, style=MaterialTheme.typography.headlineSmall, fontWeight=FontWeight.Bold) } } }
@Composable private fun Empty(title:String, detail:String) { Column(Modifier.fillMaxSize(), verticalArrangement=Arrangement.Center, horizontalAlignment=Alignment.CenterHorizontally) { Icon(Icons.Default.Construction,"",Modifier.size(52.dp)); Spacer(Modifier.height(12.dp)); Text(title, style=MaterialTheme.typography.titleLarge); Text(detail, style=MaterialTheme.typography.bodyMedium) } }
@Composable private fun Field(label:String, value:String, change:(String)->Unit) { OutlinedTextField(value=value,onValueChange=change,label={Text(label)},modifier=Modifier.fillMaxWidth(),singleLine=true) }
@Composable private fun SaveButton(save:()->Unit) { Button(onClick=save,modifier=Modifier.fillMaxWidth()){Text("Save")}}
@Composable private fun ProjectPicker(projects: List<ProjectEntity>, selectedProjectId: String?, onSelect: (String?) -> Unit, allowCentral: Boolean = false) {
    var expanded by remember { mutableStateOf(false) }
    val selectedName = projects.firstOrNull { it.id == selectedProjectId }?.name
    Box {
        OutlinedButton(onClick = { expanded = true }, enabled = projects.isNotEmpty() || allowCentral) {
            Text(selectedName ?: if (allowCentral) "Central stock" else "Select project")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (allowCentral) DropdownMenuItem(text = { Text("Central stock") }, onClick = { onSelect(null); expanded = false })
            projects.forEach { project ->
                DropdownMenuItem(text = { Text(project.name) }, onClick = { onSelect(project.id); expanded = false })
            }
        }
    }
}

@Composable private fun ProjectForm(repo:LedgerRepository, close:()->Unit) { var ref by remember{mutableStateOf("")};var name by remember{mutableStateOf("")};var client by remember{mutableStateOf("")};var address by remember{mutableStateOf("")};var contract by remember{mutableStateOf("")};var budget by remember{mutableStateOf("")}; val scope=rememberCoroutineScope(); var error by remember{mutableStateOf<String?>(null)}; FormDialog("New project",close) { Field("Reference",ref){ref=it};Field("Project name",name){name=it};Field("Client",client){client=it};Field("Site address",address){address=it};Field("Contract amount (Rs)",contract){contract=it};Field("Approved budget (Rs)",budget){budget=it};error?.let{Text(it,color=MaterialTheme.colorScheme.error)};SaveButton { scope.launch { runCatching{repo.addProject(ref,name,client,address,parseMoney(contract),parseMoney(budget))}.onSuccess{close()}.onFailure{error=it.message} } } } }
@Composable private fun WorkerForm(repo:LedgerRepository,projectId:String?,close:()->Unit) { var name by remember{mutableStateOf("")};var trade by remember{mutableStateOf("")};var wage by remember{mutableStateOf("")};var phone by remember{mutableStateOf("")};val scope=rememberCoroutineScope();var error by remember{mutableStateOf<String?>(null)};FormDialog("New worker",close){if(projectId==null)Text("Select a project first.") else {Text("Assigned to the selected site");Field("Full name",name){name=it};Field("Trade",trade){trade=it};Field("Daily wage (Rs)",wage){wage=it};Field("Phone (optional)",phone){phone=it};error?.let{Text(it,color=MaterialTheme.colorScheme.error)};SaveButton{scope.launch{runCatching{repo.addWorker(name,trade,parseMoney(wage),phone,projectId)}.onSuccess{close()}.onFailure{error=it.message}}}}} }
@Composable private fun AttendanceForm(repo: LedgerRepository, worker: WorkerEntity, projectId: String, close: () -> Unit) {
    var status by remember { mutableStateOf(AttendanceStatus.PRESENT) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    FormDialog("Attendance: ${worker.fullName}", close) {
        Row { AttendanceStatus.entries.forEach { value ->
            FilterChip(selected = status == value, onClick = { status = value }, label = { Text(value.name.replace('_', ' ')) })
        } }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        SaveButton {
            scope.launch {
                runCatching { repo.markAttendance(worker.id, projectId, status, 0) }
                    .onSuccess { close() }
                    .onFailure { error = it.message }
            }
        }
    }
}
@Composable private fun AssignWorkerForm(repo: LedgerRepository, worker: WorkerEntity, projects: List<ProjectEntity>, currentProjectId: String?, close: () -> Unit) {
    var selectedProjectId by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val choices = projects.filter { it.id != currentProjectId }
    FormDialog("Assign ${worker.fullName}", close) {
        if (choices.isEmpty()) Text("Create another project before moving this worker.") else {
            ProjectPicker(choices, selectedProjectId, { selectedProjectId = it })
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            SaveButton {
                val projectId = selectedProjectId
                if (projectId == null) error = "Select a project." else scope.launch {
                    runCatching { repo.assignWorker(worker.id, projectId) }
                        .onSuccess { close() }
                        .onFailure { error = it.message }
                }
            }
        }
    }
}
@Composable private fun MaterialForm(repo:LedgerRepository,close:()->Unit){var code by remember{mutableStateOf("")};var name by remember{mutableStateOf("")};var unit by remember{mutableStateOf("Bags")};var min by remember{mutableStateOf("0")};var rate by remember{mutableStateOf("0")};val scope=rememberCoroutineScope();var error by remember{mutableStateOf<String?>(null)};FormDialog("New material",close){Field("Code",code){code=it};Field("Material name",name){name=it};Field("Unit",unit){unit=it};Field("Minimum stock",min){min=it};Field("Default rate (Rs)",rate){rate=it};error?.let{Text(it,color=MaterialTheme.colorScheme.error)};SaveButton{scope.launch{runCatching{repo.addMaterial(code,name,unit,min.toDoubleOrNull()?:-1,parseMoney(rate))}.onSuccess{close()}.onFailure{error=it.message}}}}}
@Composable private fun ReceiveForm(repo: LedgerRepository, material: MaterialEntity, projects: List<ProjectEntity>, close: () -> Unit) {
    var qty by remember { mutableStateOf("") }
    var rate by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var selectedProjectId by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    FormDialog("Receive ${material.name}", close) {
        ProjectPicker(projects, selectedProjectId, { selectedProjectId = it }, allowCentral = true)
        Field("Quantity (${material.unit})", qty) { qty = it }
        Field("Rate (Rs)", rate) { rate = it }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        SaveButton {
            scope.launch {
                runCatching { repo.receiveMaterial(material.id, selectedProjectId, qty.toDoubleOrNull() ?: 0.0, parseMoney(rate)) }
                    .onSuccess { close() }
                    .onFailure { error = it.message }
            }
        }
    }
}
@Composable private fun ExpenseForm(repo: LedgerRepository, projects: List<ProjectEntity>, close: () -> Unit) {
    var category by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var selectedProjectId by remember { mutableStateOf<String?>(projects.firstOrNull()?.id) }
    LaunchedEffect(projects) {
        if (selectedProjectId == null && projects.isNotEmpty()) {
            selectedProjectId = projects.first().id
        }
    }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    FormDialog("New expense", close) {
        if (projects.isEmpty()) Text("Create a project first.") else {
            ProjectPicker(projects, selectedProjectId, { selectedProjectId = it })
            Field("Category", category) { category = it }
            Field("Description", description) { description = it }
            Field("Amount (Rs)", amount) { amount = it }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            SaveButton {
                val projectId = selectedProjectId
                if (projectId == null) error = "Select a project." else {
                    scope.launch {
                        runCatching { repo.addExpense(projectId, category, description, parseMoney(amount)) }
                            .onSuccess { close() }
                            .onFailure { error = it.message }
                    }
                }
            }
        }
    }
}
@Composable private fun FormDialog(title:String,close:()->Unit,content:@Composable ColumnScope.()->Unit){AlertDialog(onDismissRequest=close,title={Text(title)},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp),content=content)},confirmButton={})}


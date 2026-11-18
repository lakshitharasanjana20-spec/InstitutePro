package com.example.institutepro.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.institutepro.data.db.UserEntity
import com.example.institutepro.data.repo.DomainRules
import com.example.institutepro.ui.components.*
import com.example.institutepro.ui.theme.CrimsonAlert
import com.example.institutepro.ui.viewmodel.AppDataSnapshot
import com.example.institutepro.ui.viewmodel.MainViewModel
import java.time.LocalDate

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CoursesScreen(
    vm: MainViewModel,
    user: UserEntity,
    data: AppDataSnapshot,
    onOpenBatch: (Int) -> Unit
) {
    var tab by remember { mutableStateOf(0) } // 0 Active, 1 Completed, 2 Catalog
    var query by remember { mutableStateOf("") }
    var showNewBatch by remember { mutableStateOf(false) }
    var showNewCourse by remember { mutableStateOf(false) }

    val canManage = DomainRules.can(user.role, "manage_batches")
    val branchScope = vm.effectiveBranchId()
    val isTeacher = DomainRules.isTeacher(user.role)

    val batches = remember(data.batches, tab, query, branchScope, isTeacher, user.id) {
        val targetStatus = if (tab == 1) "completed" else "active"
        data.batches.filter { b ->
            b.status == targetStatus &&
                (branchScope == null || b.branchId == branchScope) &&
                (!isTeacher || b.teacherId == user.id) &&
                (query.isBlank() || b.name.contains(query, true) ||
                    (data.courses.firstOrNull { it.id == b.courseId }?.name?.contains(query, true) == true))
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                val tabs = if (canManage) listOf("Active", "Completed", "Catalog") else listOf("Active", "Completed")
                TabRow(selectedTabIndex = tab.coerceAtMost(tabs.lastIndex)) {
                    tabs.forEachIndexed { i, title ->
                        Tab(selected = tab == i, onClick = { tab = i }, text = { Text(title) })
                    }
                }
            }
            item {
                SearchField(value = query, onValueChange = { query = it }, placeholder = if (tab == 2) "Search courses" else "Search batch or course")
            }

            if (tab == 2 && canManage) {
                val filteredCourses = data.courses.filter {
                    query.isBlank() || it.name.contains(query, true) || it.code.contains(query, true)
                }
                items(filteredCourses, key = { it.id }) { c ->
                    val ct = data.courseTypes.firstOrNull { it.id == c.typeId }
                    AppCard(modifier = Modifier.fillMaxWidth()) {
                        Row(horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(c.name, style = MaterialTheme.typography.titleMedium)
                                Text("${ct?.name ?: ""} · Code: ${c.code}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            StatusPill(ct?.code ?: "", MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            } else {
                if (batches.isEmpty()) {
                    item { EmptyStateCard("No batches found", "Create a new batch or switch tabs.") }
                } else {
                    items(batches, key = { it.id }) { b ->
                        val c = data.courses.firstOrNull { it.id == b.courseId }
                        val ct = data.courseTypes.firstOrNull { it.id == c?.typeId }
                        val br = data.branches.firstOrNull { it.id == b.branchId }
                        val teacher = data.users.firstOrNull { it.id == b.teacherId }
                        val enrCount = data.enrolments.count { it.batchId == b.id }
                        AppCard(
                            modifier = Modifier.fillMaxWidth().testTag("batch_card_${b.id}"),
                            onClick = { onOpenBatch(b.id) }
                        ) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("${b.name} (${b.batchYear})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                StatusPill(b.status, statusColorFor(b.status))
                            }
                            Text("${ct?.name ?: ""} · ${c?.name ?: ""}", style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "${br?.name ?: ""} · ${b.startTime}–${b.endTime} (${b.classDays})",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "Teacher: ${teacher?.fullName ?: "TBA"} · $enrCount${b.maxStudents?.let { "/$it" } ?: ""} students · ${DomainRules.formatMoney(b.fee, data.currency)} (${b.feePlan})",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }

        if (canManage) {
            ExtendedFloatingActionButton(
                onClick = { if (tab == 2) showNewCourse = true else showNewBatch = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(if (tab == 2) "New Course" else "New Batch") },
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp).testTag("new_course_batch_fab")
            )
        }
    }

    if (showNewCourse) {
        var typeId by remember { mutableStateOf(data.courseTypes.firstOrNull()?.id ?: 1) }
        var code by remember { mutableStateOf("") }
        var name by remember { mutableStateOf("") }
        var err by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { showNewCourse = false },
            title = { Text("Add Course") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (err != null) Text(err!!, color = CrimsonAlert)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        data.courseTypes.forEach { t ->
                            FilterChip(selected = typeId == t.id, onClick = { typeId = t.id }, label = { Text(t.name) })
                        }
                    }
                    OutlinedTextField(value = code, onValueChange = { code = it }, label = { Text("Short code (e.g. ENG)") }, singleLine = true)
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Course name") }, singleLine = true)
                }
            },
            confirmButton = {
                Button(onClick = {
                    vm.createCourse(typeId, code, name) { res ->
                        res.onSuccess { showNewCourse = false; vm.showToast("Course added.") }
                            .onFailure { err = it.message }
                    }
                }) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { showNewCourse = false }) { Text("Cancel") } }
        )
    }

    if (showNewBatch) {
        NewBatchDialog(vm, user, data) { showNewBatch = false }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun NewBatchDialog(vm: MainViewModel, user: UserEntity, data: AppDataSnapshot, onDismiss: () -> Unit) {
    val activeBranches = data.branches.filter { it.status == "active" }
    var branchId by remember { mutableStateOf(if (DomainRules.isOwner(user.role)) activeBranches.firstOrNull()?.id ?: 0 else user.branchId ?: 0) }
    var courseId by remember { mutableStateOf(data.courses.firstOrNull()?.id ?: 0) }
    var year by remember { mutableStateOf(LocalDate.now().year.toString()) }
    var name by remember { mutableStateOf("") }
    var startDate by remember { mutableStateOf(LocalDate.now().toString()) }
    var duration by remember { mutableStateOf("6") }
    var startTime by remember { mutableStateOf("09:00") }
    var endTime by remember { mutableStateOf("11:00") }
    val selectedDays = remember { mutableStateListOf("Mon", "Wed", "Fri") }
    var fee by remember { mutableStateOf("35000") }
    var feePlan by remember { mutableStateOf("installments_3") }
    var maxStudents by remember { mutableStateOf("25") }
    var room by remember { mutableStateOf("Hall A1") }
    val teachers = data.users.filter { it.role == "teacher" && it.status == "active" && it.branchId == branchId }
    var teacherId by remember(teachers) { mutableStateOf(teachers.firstOrNull()?.id) }
    var err by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Batch / Class") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (err != null) Text(err!!, color = CrimsonAlert)
                if (DomainRules.isOwner(user.role)) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        activeBranches.forEach { b ->
                            FilterChip(selected = branchId == b.id, onClick = { branchId = b.id }, label = { Text(b.code) })
                        }
                    }
                }
                var cExp by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(expanded = cExp, onExpandedChange = { cExp = it }) {
                    OutlinedTextField(
                        value = data.courses.firstOrNull { it.id == courseId }?.name ?: "Choose course",
                        onValueChange = {}, readOnly = true, label = { Text("Course") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(cExp) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = cExp, onDismissRequest = { cExp = false }) {
                        data.courses.forEach { c ->
                            DropdownMenuItem(text = { Text(c.name) }, onClick = { courseId = c.id; cExp = false })
                        }
                    }
                }
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Batch name *") }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = year, onValueChange = { year = it }, label = { Text("Year") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = duration, onValueChange = { duration = it }, label = { Text("Months") }, singleLine = true, modifier = Modifier.weight(1f))
                }
                OutlinedTextField(value = startDate, onValueChange = { startDate = it }, label = { Text("Start date (YYYY-MM-DD)") }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = startTime, onValueChange = { startTime = it }, label = { Text("Start HH:mm") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = endTime, onValueChange = { endTime = it }, label = { Text("End HH:mm") }, singleLine = true, modifier = Modifier.weight(1f))
                }
                Text("Class days", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun").forEach { d ->
                        FilterChip(
                            selected = d in selectedDays,
                            onClick = { if (d in selectedDays) selectedDays.remove(d) else selectedDays.add(d) },
                            label = { Text(d) }
                        )
                    }
                }
                if (teachers.isNotEmpty()) {
                    Text("Teacher", style = MaterialTheme.typography.labelMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        teachers.forEach { t ->
                            FilterChip(selected = teacherId == t.id, onClick = { teacherId = t.id }, label = { Text(t.fullName) })
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = fee, onValueChange = { fee = it }, label = { Text("Fee") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = maxStudents, onValueChange = { maxStudents = it }, label = { Text("Max students") }, singleLine = true, modifier = Modifier.weight(1f))
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("installments_3" to "3 Installments", "monthly" to "Monthly", "custom" to "Custom").forEach { (k, lbl) ->
                        FilterChip(selected = feePlan == k, onClick = { feePlan = k }, label = { Text(lbl) })
                    }
                }
                OutlinedTextField(value = room, onValueChange = { room = it }, label = { Text("Room") }, singleLine = true)
            }
        },
        confirmButton = {
            Button(onClick = {
                vm.createBatch(
                    branchId, courseId, year.toIntOrNull() ?: LocalDate.now().year,
                    name, startDate, duration.toIntOrNull() ?: 6, startTime, endTime,
                    selectedDays.toList(), fee.toDoubleOrNull() ?: 0.0, feePlan,
                    maxStudents.toIntOrNull(), teacherId, room
                ) { res ->
                    res.onSuccess { msg -> vm.showToast(msg); onDismiss() }
                        .onFailure { err = it.message }
                }
            }) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatchDetailScreen(
    data: AppDataSnapshot,
    batchId: Int,
    onBack: () -> Unit,
    onOpenStudent: (Int) -> Unit,
    onOpenLearningHub: (Int) -> Unit
) {
    BackHandler { onBack() }
    val batch = data.batches.firstOrNull { it.id == batchId }
    if (batch == null) {
        EmptyStateCard("Batch not found", "Return to courses.")
        return
    }
    val course = data.courses.firstOrNull { it.id == batch.courseId }
    val branch = data.branches.firstOrNull { it.id == batch.branchId }
    val teacher = data.users.firstOrNull { it.id == batch.teacherId }
    val enrolledStudents = data.enrolments.filter { it.batchId == batchId }
        .mapNotNull { enr -> data.students.firstOrNull { it.id == enr.studentId } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("${batch.name} (${batch.batchYear})") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                AppCard(modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(course?.name ?: "", style = MaterialTheme.typography.titleLarge)
                        StatusPill(batch.status, statusColorFor(batch.status))
                    }
                    Spacer(Modifier.height(6.dp))
                    Text("${branch?.name ?: ""} · ${batch.room.ifBlank { "Room TBA" }}", style = MaterialTheme.typography.bodyMedium)
                    Text("Dates: ${batch.startDate} → ${batch.endDate} (${batch.durationMonths} mos)", style = MaterialTheme.typography.bodyMedium)
                    Text("Schedule: ${batch.startTime}–${batch.endTime} · ${batch.classDays}", style = MaterialTheme.typography.bodyMedium)
                    Text("Teacher: ${teacher?.fullName ?: "Unassigned"}", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { onOpenLearningHub(batch.id) }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.FolderCopy, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Open Class Learning Hub")
                    }
                }
            }
            item {
                Text("Enrolled Students (${enrolledStudents.size})", style = MaterialTheme.typography.titleMedium)
            }
            items(enrolledStudents, key = { it.id }) { st ->
                AppCard(modifier = Modifier.fillMaxWidth(), onClick = { onOpenStudent(st.id) }) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        InitialAvatar(st.fullName)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(st.fullName, style = MaterialTheme.typography.titleMedium)
                            Text("${st.studentCode} · ${st.phone}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        StatusPill(st.status, statusColorFor(st.status))
                    }
                }
            }
        }
    }
}

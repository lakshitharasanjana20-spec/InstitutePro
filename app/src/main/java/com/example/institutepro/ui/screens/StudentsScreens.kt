package com.example.institutepro.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.example.institutepro.data.db.StudentEntity
import com.example.institutepro.data.db.UserEntity
import com.example.institutepro.data.repo.DomainRules
import com.example.institutepro.ui.components.*
import com.example.institutepro.ui.theme.CrimsonAlert
import com.example.institutepro.ui.theme.EmeraldSuccess
import com.example.institutepro.ui.viewmodel.AppDataSnapshot
import com.example.institutepro.ui.viewmodel.MainViewModel

@Composable
fun StudentsScreen(
    vm: MainViewModel,
    user: UserEntity,
    data: AppDataSnapshot,
    onOpenStudent: (Int) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var statusFilter by remember { mutableStateOf<String?>(null) }
    var showRegister by remember { mutableStateOf(false) }
    var regConfirmation by remember { mutableStateOf<Pair<StudentEntity, String?>?>(null) }

    val branchScope = vm.effectiveBranchId()
    val isTeacher = DomainRules.isTeacher(user.role)
    val teacherBatches = remember(data.batches, user.id) { data.batches.filter { it.teacherId == user.id }.map { it.id }.toSet() }

    val filtered = remember(data.students, query, statusFilter, branchScope, isTeacher, teacherBatches, data.enrolments) {
        data.students.filter { s ->
            (branchScope == null || s.branchId == branchScope) &&
                (!isTeacher || data.enrolments.any { it.studentId == s.id && it.batchId in teacherBatches }) &&
                (statusFilter == null || s.status == statusFilter) &&
                (query.isBlank() || s.fullName.contains(query, true) || s.studentCode.contains(query, true) || s.phone.contains(query, true))
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                SearchField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = "Search name, student ID, or phone",
                    testTag = "student_search_input"
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    FilterChip(selected = statusFilter == null, onClick = { statusFilter = null }, label = { Text("All (${filtered.size})") })
                    FilterChip(selected = statusFilter == "active", onClick = { statusFilter = "active" }, label = { Text("Active") })
                    FilterChip(selected = statusFilter == "inactive", onClick = { statusFilter = "inactive" }, label = { Text("Inactive") })
                }
            }
            if (filtered.isEmpty()) {
                item { EmptyStateCard("No students found", "Adjust your filters or register a new student.") }
            } else {
                items(filtered, key = { it.id }) { st ->
                    val batch = data.batches.firstOrNull { it.id == st.batchId }
                    val course = data.courses.firstOrNull { it.id == batch?.courseId }
                    AppCard(
                        modifier = Modifier.fillMaxWidth().testTag("student_card_${st.id}"),
                        onClick = { onOpenStudent(st.id) }
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            InitialAvatar(st.fullName)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(st.fullName, style = MaterialTheme.typography.titleMedium)
                                Text(st.studentCode, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                Text("${course?.name ?: ""} · ${batch?.name ?: ""} · ${st.phone}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            StatusPill(st.status, statusColorFor(st.status))
                        }
                    }
                }
            }
        }

        if (DomainRules.can(user.role, "register_student")) {
            ExtendedFloatingActionButton(
                onClick = { showRegister = true },
                icon = { Icon(Icons.Filled.PersonAdd, contentDescription = null) },
                text = { Text("Register") },
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp).testTag("register_student_fab")
            )
        }
    }

    if (showRegister) {
        RegisterStudentDialog(
            vm = vm,
            user = user,
            data = data,
            onDismiss = { showRegister = false },
            onRegistered = { student, tempPw ->
                showRegister = false
                regConfirmation = student to tempPw
            }
        )
    }

    regConfirmation?.let { (student, tempPw) ->
        AlertDialog(
            onDismissRequest = { regConfirmation = null },
            icon = { Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = EmeraldSuccess, modifier = Modifier.size(44.dp)) },
            title = { Text("Registered Successfully") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Student ID: ${student.studentCode}", fontWeight = FontWeight.Bold)
                    Text("Name: ${student.fullName}")
                    if (tempPw != null) {
                        Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(12.dp)) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("Student Portal Login Created", fontWeight = FontWeight.Bold)
                                Text("Email: ${student.email}")
                                Text("Temporary password: $tempPw", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { regConfirmation = null }) { Text("Done") }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RegisterStudentDialog(
    vm: MainViewModel,
    user: UserEntity,
    data: AppDataSnapshot,
    onDismiss: () -> Unit,
    onRegistered: (StudentEntity, String?) -> Unit
) {
    val activeBranches = data.branches.filter { it.status == "active" }
    var selectedBranchId by remember { mutableStateOf(if (DomainRules.isOwner(user.role)) activeBranches.firstOrNull()?.id ?: 0 else user.branchId ?: 0) }
    val activeBatches = remember(data.batches, selectedBranchId) {
        data.batches.filter { it.status == "active" && it.branchId == selectedBranchId }
    }
    var selectedBatchId by remember(activeBatches) { mutableStateOf(activeBatches.firstOrNull()?.id ?: 0) }
    var firstName by remember { mutableStateOf("") }
    var lastName by remember { mutableStateOf("") }
    var gender by remember { mutableStateOf("Male") }
    var dob by remember { mutableStateOf("2004-01-15") }
    var phone by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var nic by remember { mutableStateOf("") }
    var gName by remember { mutableStateOf("") }
    var gRel by remember { mutableStateOf("Father") }
    var gPhone by remember { mutableStateOf("") }
    var fpId by remember { mutableStateOf("") }
    var createLogin by remember { mutableStateOf(true) }
    var err by remember { mutableStateOf<String?>(null) }

    val age = remember(dob) { DomainRules.ageFromDob(dob) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Register Student") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (err != null) {
                    Text(err!!, color = CrimsonAlert, style = MaterialTheme.typography.bodyMedium)
                }
                if (DomainRules.isOwner(user.role)) {
                    var bExp by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(expanded = bExp, onExpandedChange = { bExp = it }) {
                        OutlinedTextField(
                            value = activeBranches.firstOrNull { it.id == selectedBranchId }?.name ?: "Select branch",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Branch") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(bExp) },
                            modifier = Modifier.menuAnchor().fillMaxWidth()
                        )
                        ExposedDropdownMenu(expanded = bExp, onDismissRequest = { bExp = false }) {
                            activeBranches.forEach { br ->
                                DropdownMenuItem(text = { Text(br.name) }, onClick = { selectedBranchId = br.id; bExp = false })
                            }
                        }
                    }
                }

                var btExp by remember { mutableStateOf(false) }
                val selBatch = activeBatches.firstOrNull { it.id == selectedBatchId }
                val selCourse = data.courses.firstOrNull { it.id == selBatch?.courseId }
                ExposedDropdownMenuBox(expanded = btExp, onExpandedChange = { btExp = it }) {
                    OutlinedTextField(
                        value = if (selBatch != null) "${selCourse?.name ?: ""} · ${selBatch.name}" else "No active batches",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Class / Batch") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(btExp) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = btExp, onDismissRequest = { btExp = false }) {
                        activeBatches.forEach { b ->
                            val c = data.courses.firstOrNull { it.id == b.courseId }
                            DropdownMenuItem(text = { Text("${c?.name ?: ""} · ${b.name} (${b.batchYear})") }, onClick = { selectedBatchId = b.id; btExp = false })
                        }
                    }
                }

                OutlinedTextField(value = firstName, onValueChange = { firstName = it }, label = { Text("First name *") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("reg_first_name"))
                OutlinedTextField(value = lastName, onValueChange = { lastName = it }, label = { Text("Last name *") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("reg_last_name"))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Gender:", style = MaterialTheme.typography.labelLarge)
                    listOf("Male", "Female").forEach { g ->
                        FilterChip(selected = gender == g, onClick = { gender = g }, label = { Text(g) })
                    }
                }
                DatePickerField(
                    value = dob,
                    onDateSelected = { dob = it },
                    label = "Date of birth *",
                    showStepButtons = false,
                    modifier = Modifier.fillMaxWidth()
                )
                if (age != null) {
                    Text("Calculated age: $age", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
                OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Phone *") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("reg_phone"))
                OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = address, onValueChange = { address = it }, label = { Text("Address") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = fpId, onValueChange = { fpId = it }, label = { Text("Fingerprint machine ID") }, singleLine = true, modifier = Modifier.fillMaxWidth())

                if (age != null && age >= 18) {
                    OutlinedTextField(value = nic, onValueChange = { nic = it }, label = { Text("NIC number (18+) *") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("reg_nic"))
                } else if (age != null) {
                    Text("Guardian details (under 18)", style = MaterialTheme.typography.labelLarge)
                    OutlinedTextField(value = gName, onValueChange = { gName = it }, label = { Text("Guardian name *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("Mother", "Father", "Guardian").forEach { rel ->
                            FilterChip(selected = gRel == rel, onClick = { gRel = rel }, label = { Text(rel) })
                        }
                    }
                    OutlinedTextField(value = gPhone, onValueChange = { gPhone = it }, label = { Text("Guardian phone *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = createLogin, onCheckedChange = { createLogin = it })
                    Text("Create student portal login", style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    vm.registerStudent(
                        selectedBranchId, selectedBatchId, firstName, lastName, gender, dob, phone,
                        email, address, nic, gName, gRel, gPhone, fpId, createLogin
                    ) { res ->
                        res.onSuccess { (st, pw) -> onRegistered(st, pw) }
                            .onFailure { err = it.message }
                    }
                },
                modifier = Modifier.testTag("reg_submit_button")
            ) { Text("Register") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentDetailScreen(
    vm: MainViewModel,
    user: UserEntity,
    data: AppDataSnapshot,
    studentId: Int,
    onBack: () -> Unit
) {
    BackHandler { onBack() }
    val student = data.students.firstOrNull { it.id == studentId }
    if (student == null) {
        EmptyStateCard("Student not found", "The requested record could not be loaded.")
        return
    }
    val branch = data.branches.firstOrNull { it.id == student.branchId }
    val enrolments = data.enrolments.filter { it.studentId == student.id }
    val attRecs = data.attendance.filter { it.studentId == student.id }
    val presentCount = attRecs.count { it.status == "present" }
    val pct = if (attRecs.isNotEmpty()) (presentCount * 100 / attRecs.size) else null
    val marks = data.examMarks.filter { it.studentId == student.id }
    val accounts = data.feeAccounts.filter { it.studentId == student.id }

    var showEdit by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Student Profile") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (DomainRules.can(user.role, "edit_student")) {
                        IconButton(onClick = { showEdit = true }) {
                            Icon(Icons.Filled.Edit, contentDescription = "Edit student")
                        }
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
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        InitialAvatar(student.fullName, sizeDp = 56)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(student.fullName, style = MaterialTheme.typography.headlineSmall)
                            Text(student.studentCode, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                            Text("${branch?.name ?: ""} · Registered ${student.registeredAt}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        StatusPill(student.status, statusColorFor(student.status))
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("Phone: ${student.phone} · Gender: ${student.gender}", style = MaterialTheme.typography.bodyMedium)
                    if (!student.email.isNullOrBlank()) Text("Email: ${student.email}", style = MaterialTheme.typography.bodyMedium)
                    Text("DOB: ${student.dateOfBirth} · ${if (student.nic != null) "NIC: ${student.nic}" else "Guardian: ${student.guardianName ?: "—"} (${student.guardianRelation ?: ""})"}", style = MaterialTheme.typography.bodyMedium)
                    if (student.address.isNotBlank()) Text("Address: ${student.address}", style = MaterialTheme.typography.bodyMedium)
                    if (DomainRules.can(user.role, "edit_student")) {
                        Spacer(Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = { vm.toggleStudentStatus(student.id) },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(if (student.status == "active") "Mark Inactive" else "Reactivate Student")
                        }
                    }
                }
            }

            item {
                AppCard(modifier = Modifier.fillMaxWidth()) {
                    Text("Enrolled Classes (${enrolments.size})", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    enrolments.forEach { enr ->
                        val b = data.batches.firstOrNull { it.id == enr.batchId }
                        val c = data.courses.firstOrNull { it.id == b?.courseId }
                        Text("• ${c?.name ?: ""} · ${b?.name ?: ""} (${b?.batchYear ?: ""}) — enrolled ${enr.enrolledOn}", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            item {
                AppCard(modifier = Modifier.fillMaxWidth()) {
                    Text("Attendance Summary", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (pct == null) "No attendance recorded yet."
                        else "$pct% present ($presentCount of ${attRecs.size} recorded sessions)",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            if (marks.isNotEmpty()) {
                item {
                    AppCard(modifier = Modifier.fillMaxWidth()) {
                        Text("Exam Results", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(6.dp))
                        marks.forEach { m ->
                            val ex = data.exams.firstOrNull { it.id == m.examId }
                            val scoreText = if (m.absent) "Absent" else "${m.marks ?: "—"} / ${ex?.maxMarks?.toInt() ?: 100}"
                            Text("• ${ex?.title ?: "Exam"}: $scoreText", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            if (accounts.isNotEmpty()) {
                item {
                    AppCard(modifier = Modifier.fillMaxWidth()) {
                        Text("Fee Accounts", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(6.dp))
                        accounts.forEach { acc ->
                            val b = data.batches.firstOrNull { it.id == acc.batchId }
                            val insts = data.installments.filter { it.accountId == acc.id }
                            val net = insts.sumOf { it.amount }
                            val paid = insts.sumOf { it.paid }
                            val bal = net - paid
                            Text(
                                "• ${b?.name ?: "Batch"}: Paid ${DomainRules.formatMoney(paid, data.currency)} / ${DomainRules.formatMoney(net, data.currency)} (Bal: ${DomainRules.formatMoney(bal, data.currency)})",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        }
    }

    if (showEdit) {
        var phone by remember { mutableStateOf(student.phone) }
        var address by remember { mutableStateOf(student.address) }
        var email by remember { mutableStateOf(student.email ?: "") }
        var fp by remember { mutableStateOf(student.fingerprintId ?: "") }
        AlertDialog(
            onDismissRequest = { showEdit = false },
            title = { Text("Edit Student") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Phone") }, singleLine = true)
                    OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email") }, singleLine = true)
                    OutlinedTextField(value = address, onValueChange = { address = it }, label = { Text("Address") }, singleLine = true)
                    OutlinedTextField(value = fp, onValueChange = { fp = it }, label = { Text("Fingerprint ID") }, singleLine = true)
                }
            },
            confirmButton = {
                Button(onClick = {
                    vm.updateStudent(student.copy(phone = phone.trim(), email = email.trim().ifBlank { null }, address = address.trim(), fingerprintId = fp.trim().ifBlank { null })) {
                        showEdit = false
                    }
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showEdit = false }) { Text("Cancel") } }
        )
    }
}

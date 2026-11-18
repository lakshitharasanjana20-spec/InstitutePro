package com.example.institutepro.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.institutepro.data.db.MaterialEntity
import com.example.institutepro.data.db.UserEntity
import com.example.institutepro.data.repo.DomainRules
import com.example.institutepro.ui.components.*
import com.example.institutepro.ui.theme.AmberTertiary
import com.example.institutepro.ui.theme.CrimsonAlert
import com.example.institutepro.ui.theme.EmeraldSuccess
import com.example.institutepro.ui.viewmodel.AppDataSnapshot
import com.example.institutepro.ui.viewmodel.MainViewModel
import java.time.LocalDate

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ExamsScreen(
    vm: MainViewModel,
    user: UserEntity,
    data: AppDataSnapshot,
    onOpenExamMarks: (Int) -> Unit
) {
    var tab by remember { mutableStateOf(0) } // 0 Upcoming, 1 Past
    var showSchedule by remember { mutableStateOf(false) }
    val todayStr = LocalDate.now().toString()
    val branchScope = vm.effectiveBranchId()
    val isTeacher = DomainRules.isTeacher(user.role)
    val canSched = DomainRules.can(user.role, "schedule_exam")

    val filtered = remember(data.exams, tab, branchScope, isTeacher, user.id, data.batches) {
        data.exams.filter { x ->
            val b = data.batches.firstOrNull { it.id == x.batchId }
            (branchScope == null || x.branchId == branchScope) &&
                (!isTeacher || b?.teacherId == user.id) &&
                (if (tab == 0) x.examDate >= todayStr else x.examDate < todayStr)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                TabRow(selectedTabIndex = tab) {
                    Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Upcoming") })
                    Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Past") })
                }
            }
            if (filtered.isEmpty()) {
                item { EmptyStateCard("No exams", "Schedule an exam or switch between Upcoming and Past.") }
            } else {
                items(filtered, key = { it.id }) { x ->
                    val b = data.batches.firstOrNull { it.id == x.batchId }
                    val c = data.courses.firstOrNull { it.id == b?.courseId }
                    val totalEnr = data.enrolments.count { it.batchId == x.batchId }
                    val done = data.examMarks.count { it.examId == x.id && (it.marks != null || it.absent) }
                    AppCard(
                        modifier = Modifier.fillMaxWidth().testTag("exam_card_${x.id}"),
                        onClick = { onOpenExamMarks(x.id) }
                    ) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(x.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text("${c?.name ?: ""} · ${b?.name ?: ""} (${b?.batchYear ?: ""})", style = MaterialTheme.typography.bodyMedium)
                                Text("${x.examDate} ${x.startTime ?: ""} · Max ${x.maxMarks.toInt()} · Type: ${x.examType}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            StatusPill("$done / $totalEnr", if (done == totalEnr && totalEnr > 0) EmeraldSuccess else AmberTertiary)
                        }
                    }
                }
            }
        }

        if (canSched) {
            ExtendedFloatingActionButton(
                onClick = { showSchedule = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Schedule Exam") },
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp).testTag("schedule_exam_fab")
            )
        }
    }

    if (showSchedule) {
        val activeBatches = data.batches.filter { it.status == "active" && (branchScope == null || it.branchId == branchScope) }
        var batchId by remember { mutableStateOf(activeBatches.firstOrNull()?.id ?: 0) }
        var title by remember { mutableStateOf("") }
        var examType by remember { mutableStateOf("term") }
        var date by remember { mutableStateOf(LocalDate.now().plusDays(7).toString()) }
        var time by remember { mutableStateOf("09:00") }
        var maxMarks by remember { mutableStateOf("100") }
        var err by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { showSchedule = false },
            title = { Text("Schedule Exam") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (err != null) Text(err!!, color = CrimsonAlert)
                    var bExp by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(expanded = bExp, onExpandedChange = { bExp = it }) {
                        OutlinedTextField(
                            value = activeBatches.firstOrNull { it.id == batchId }?.name ?: "Select batch",
                            onValueChange = {}, readOnly = true, label = { Text("Class / Batch") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(bExp) },
                            modifier = Modifier.menuAnchor().fillMaxWidth()
                        )
                        ExposedDropdownMenu(expanded = bExp, onDismissRequest = { bExp = false }) {
                            activeBatches.forEach { b ->
                                DropdownMenuItem(text = { Text("${b.name} (${b.batchYear})") }, onClick = { batchId = b.id; bExp = false })
                            }
                        }
                    }
                    OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Exam title *") }, singleLine = true)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        DomainRules.EXAM_TYPES.forEach { (k, lbl) ->
                            FilterChip(selected = examType == k, onClick = { examType = k }, label = { Text(lbl) })
                        }
                    }
                    OutlinedTextField(value = date, onValueChange = { date = it }, label = { Text("Date (YYYY-MM-DD)") }, singleLine = true)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = time, onValueChange = { time = it }, label = { Text("Time HH:mm") }, singleLine = true, modifier = Modifier.weight(1f))
                        OutlinedTextField(value = maxMarks, onValueChange = { maxMarks = it }, label = { Text("Max marks") }, singleLine = true, modifier = Modifier.weight(1f))
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    vm.scheduleExam(batchId, title, examType, date, time, maxMarks.toDoubleOrNull() ?: 100.0) { res ->
                        res.onSuccess { showSchedule = false; vm.showToast("Exam scheduled.") }
                            .onFailure { err = it.message }
                    }
                }) { Text("Schedule") }
            },
            dismissButton = { TextButton(onClick = { showSchedule = false }) { Text("Cancel") } }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExamMarksScreen(
    vm: MainViewModel,
    user: UserEntity,
    data: AppDataSnapshot,
    examId: Int,
    onBack: () -> Unit
) {
    BackHandler { onBack() }
    val exam = data.exams.firstOrNull { it.id == examId }
    if (exam == null) {
        EmptyStateCard("Exam not found", "Return to exams.")
        return
    }
    val batch = data.batches.firstOrNull { it.id == exam.batchId }
    val course = data.courses.firstOrNull { it.id == batch?.courseId }
    val cType = data.courseTypes.firstOrNull { it.id == course?.typeId }
    val canEnter = DomainRules.can(user.role, "enter_marks")
    val started = exam.examDate <= LocalDate.now().toString()
    val students = remember(data.enrolments, data.students, exam.batchId) {
        data.enrolments.filter { it.batchId == exam.batchId }
            .mapNotNull { e -> data.students.firstOrNull { it.id == e.studentId } }
            .sortedBy { it.fullName }
    }

    val marksState = remember(examId, data.examMarks, students) {
        mutableStateMapOf<Int, String>().apply {
            students.forEach { s ->
                val m = data.examMarks.firstOrNull { it.examId == examId && it.studentId == s.id }
                put(s.id, m?.marks?.toString() ?: "")
            }
        }
    }
    val absentState = remember(examId, data.examMarks, students) {
        mutableStateMapOf<Int, Boolean>().apply {
            students.forEach { s ->
                val m = data.examMarks.firstOrNull { it.examId == examId && it.studentId == s.id }
                put(s.id, m?.absent ?: false)
            }
        }
    }
    val remarksState = remember(examId, data.examMarks, students) {
        mutableStateMapOf<Int, String>().apply {
            students.forEach { s ->
                val m = data.examMarks.firstOrNull { it.examId == examId && it.studentId == s.id }
                put(s.id, m?.remarks ?: "")
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(exam.title) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                AppCard(modifier = Modifier.fillMaxWidth()) {
                    Text("${cType?.name ?: ""} · ${course?.name ?: ""} · ${batch?.name ?: ""}", style = MaterialTheme.typography.titleMedium)
                    Text("Date: ${exam.examDate} · Max marks: ${exam.maxMarks.toInt()} · Type: ${exam.examType}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (!started) {
                        Spacer(Modifier.height(6.dp))
                        StatusPill("Marks unlock on ${exam.examDate}", AmberTertiary)
                    }
                }
            }
            items(students, key = { it.id }) { st ->
                val mkStr = marksState[st.id] ?: ""
                val isAbs = absentState[st.id] ?: false
                val num = mkStr.toDoubleOrNull()
                val pct = if (!isAbs && num != null && exam.maxMarks > 0) (num / exam.maxMarks) * 100.0 else null
                val grade = pct?.let { DomainRules.gradeFor(it, data.settings) }
                val outcome = pct?.let { DomainRules.examOutcome(exam.examType, cType?.code ?: "", it, data.settings) } ?: ""

                AppCard(modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(st.fullName, style = MaterialTheme.typography.titleMedium)
                            Text(st.studentCode, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (grade != null) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                StatusPill(grade, if (DomainRules.isPass(pct, data.settings)) EmeraldSuccess else AmberTertiary)
                                if (outcome.isNotEmpty()) Text(outcome, style = MaterialTheme.typography.labelSmall)
                            }
                        } else if (isAbs) {
                            StatusPill("Absent", CrimsonAlert)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = mkStr,
                            onValueChange = { marksState[st.id] = it },
                            label = { Text("Marks") },
                            enabled = canEnter && started && !isAbs,
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = isAbs,
                                onCheckedChange = { if (canEnter && started) absentState[st.id] = it },
                                enabled = canEnter && started
                            )
                            Text("Absent", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = remarksState[st.id] ?: "",
                        onValueChange = { remarksState[st.id] = it },
                        label = { Text("Remarks") },
                        enabled = canEnter && started,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            if (canEnter && started && students.isNotEmpty()) {
                item {
                    Button(
                        onClick = {
                            val entries = students.associate { s ->
                                val mk = marksState[s.id]?.toDoubleOrNull()
                                val abs = absentState[s.id] ?: false
                                val rem = remarksState[s.id]
                                s.id to Triple(mk, abs, rem)
                            }
                            vm.saveExamMarks(examId, entries)
                        },
                        modifier = Modifier.fillMaxWidth().height(50.dp).testTag("save_exam_marks_button")
                    ) { Text("Save All Marks") }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LearningHubScreen(
    vm: MainViewModel,
    user: UserEntity,
    data: AppDataSnapshot,
    initialBatchId: Int?,
    onBack: (() -> Unit)? = null
) {
    val branchScope = vm.effectiveBranchId()
    val isTeacher = DomainRules.isTeacher(user.role)
    val batches = remember(data.batches, branchScope, isTeacher, user.id) {
        data.batches.filter { b -> (branchScope == null || b.branchId == branchScope) && (!isTeacher || b.teacherId == user.id) }
    }
    var selectedBatchId by remember(initialBatchId, batches) { mutableStateOf(initialBatchId ?: batches.firstOrNull()?.id ?: 0) }
    var selectedKind by remember { mutableStateOf("lecture") }
    var query by remember { mutableStateOf("") }
    var showAdd by remember { mutableStateOf(false) }
    var editingQuizId by remember { mutableStateOf<Int?>(null) }
    var trackingHomeworkId by remember { mutableStateOf<Int?>(null) }
    val ctx = LocalContext.current
    val canUpload = DomainRules.can(user.role, "upload_material")

    if (editingQuizId != null) {
        QuizEditorScreen(vm, data, editingQuizId!!, canUpload) { editingQuizId = null }
        return
    }
    if (trackingHomeworkId != null) {
        HomeworkTrackingScreen(vm, data, trackingHomeworkId!!) { trackingHomeworkId = null }
        return
    }

    val batch = data.batches.firstOrNull { it.id == selectedBatchId }
    val filteredMaterials = remember(data.materials, selectedBatchId, selectedKind, query) {
        data.materials.filter { m ->
            m.batchId == selectedBatchId && m.kind == selectedKind &&
                (query.isBlank() || m.title.contains(query, true) || (m.description?.contains(query, true) == true))
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (onBack != null) {
                item {
                    TextButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Back to class profile")
                    }
                }
            }
            item {
                AppCard(modifier = Modifier.fillMaxWidth()) {
                    var bExp by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(expanded = bExp, onExpandedChange = { bExp = it }) {
                        val c = data.courses.firstOrNull { it.id == batch?.courseId }
                        OutlinedTextField(
                            value = if (batch != null) "${c?.name ?: ""} · ${batch.name} (${batch.batchYear})" else "Select class",
                            onValueChange = {}, readOnly = true, label = { Text("Class Learning Hub") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(bExp) },
                            modifier = Modifier.menuAnchor().fillMaxWidth()
                        )
                        ExposedDropdownMenu(expanded = bExp, onDismissRequest = { bExp = false }) {
                            batches.forEach { b ->
                                val cs = data.courses.firstOrNull { it.id == b.courseId }
                                DropdownMenuItem(text = { Text("${cs?.name ?: ""} · ${b.name}") }, onClick = { selectedBatchId = b.id; bExp = false })
                            }
                        }
                    }
                    if (batch != null) {
                        Spacer(Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Student Portal Access", style = MaterialTheme.typography.labelLarge)
                                Text("Token: ${batch.accessToken.ifBlank { "none" }}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (canUpload) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    FilterChip(selected = batch.portalOn, onClick = { vm.toggleBatchPortal(batch.id) }, label = { Text(if (batch.portalOn) "ON" else "OFF") })
                                    TextButton(onClick = { vm.toggleBatchPortal(batch.id, regen = true) }) { Text("New link") }
                                }
                            }
                        }
                    }
                }
            }

            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(DomainRules.LEARN_TYPES) { (key, label) ->
                        val cnt = data.materials.count { it.batchId == selectedBatchId && it.kind == key }
                        FilterChip(
                            selected = selectedKind == key,
                            onClick = { selectedKind = key },
                            label = { Text("$label ($cnt)") }
                        )
                    }
                }
            }

            item {
                SearchField(value = query, onValueChange = { query = it }, placeholder = "Search items")
            }

            if (filteredMaterials.isEmpty()) {
                item { EmptyStateCard("Nothing added yet", "Tap Add to publish content for this class.") }
            } else {
                items(filteredMaterials, key = { it.id }) { m ->
                    val qCount = data.quizQuestions.count { it.materialId == m.id }
                    AppCard(modifier = Modifier.fillMaxWidth()) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(m.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            StatusPill(if (m.publish) "Visible" else "Hidden", if (m.publish) EmeraldSuccess else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (!m.description.isNullOrBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Text(m.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (m.kind == "live" && !m.startsAt.isNullOrBlank()) {
                            Text("Starts: ${m.startsAt} (${m.durationMin ?: 60} min) ${m.passcode?.let { "· Pass: $it" } ?: ""}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        }
                        if (m.kind == "quiz") {
                            Text("$qCount questions · ${m.timeLimitMin?.let { "$it min" } ?: "No time limit"} · ${m.maxAttempts} attempt(s)", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        }
                        if (m.kind == "homework" && (!m.dueDate.isNullOrBlank() || m.maxMarks != null)) {
                            Text("Due: ${m.dueDate ?: "None"} · Max marks: ${m.maxMarks?.toInt() ?: "—"}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (!m.url.isNullOrBlank()) {
                                FilledTonalButton(onClick = {
                                    runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(m.url))) }
                                }) { Text("Open link") }
                            }
                            if (m.kind == "quiz") {
                                FilledTonalButton(onClick = { editingQuizId = m.id }) { Text("Questions & Results") }
                            }
                            if (m.kind == "homework") {
                                FilledTonalButton(onClick = { trackingHomeworkId = m.id }) { Text("Submissions") }
                            }
                            if (canUpload) {
                                TextButton(onClick = { vm.toggleMaterialPublish(m.id) }) { Text(if (m.publish) "Hide" else "Publish") }
                                TextButton(onClick = { vm.deleteMaterial(m.id) }) { Text("Delete", color = CrimsonAlert) }
                            }
                        }
                    }
                }
            }
        }

        if (canUpload && batch != null) {
            ExtendedFloatingActionButton(
                onClick = { showAdd = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Add") },
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp).testTag("add_material_fab")
            )
        }
    }

    if (showAdd && batch != null) {
        var title by remember { mutableStateOf("") }
        var desc by remember { mutableStateOf("") }
        var url by remember { mutableStateOf("") }
        var dueDate by remember { mutableStateOf(LocalDate.now().plusDays(7).toString()) }
        var maxMarks by remember { mutableStateOf("20") }
        var timeLimit by remember { mutableStateOf("15") }
        var attempts by remember { mutableStateOf("2") }
        var startsAt by remember { mutableStateOf("${LocalDate.now()} 18:00") }
        var passcode by remember { mutableStateOf("") }
        var publish by remember { mutableStateOf(true) }

        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text("Add ${DomainRules.LEARN_TYPES.firstOrNull { it.first == selectedKind }?.second ?: "Item"}") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Title *") }, singleLine = true)
                    OutlinedTextField(value = desc, onValueChange = { desc = it }, label = { Text("Description / Notes") }, minLines = 2)
                    if (selectedKind != "quiz") {
                        OutlinedTextField(value = url, onValueChange = { url = it }, label = { Text("Link URL (optional)") }, singleLine = true)
                    }
                    if (selectedKind == "live") {
                        OutlinedTextField(value = startsAt, onValueChange = { startsAt = it }, label = { Text("Date & Time (YYYY-MM-DD HH:mm)") }, singleLine = true)
                        OutlinedTextField(value = passcode, onValueChange = { passcode = it }, label = { Text("Passcode (optional)") }, singleLine = true)
                    }
                    if (selectedKind == "homework") {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(value = dueDate, onValueChange = { dueDate = it }, label = { Text("Due YYYY-MM-DD") }, singleLine = true, modifier = Modifier.weight(1f))
                            OutlinedTextField(value = maxMarks, onValueChange = { maxMarks = it }, label = { Text("Max marks") }, singleLine = true, modifier = Modifier.weight(1f))
                        }
                    }
                    if (selectedKind == "quiz") {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(value = timeLimit, onValueChange = { timeLimit = it }, label = { Text("Minutes (0=none)") }, singleLine = true, modifier = Modifier.weight(1f))
                            OutlinedTextField(value = attempts, onValueChange = { attempts = it }, label = { Text("Max attempts") }, singleLine = true, modifier = Modifier.weight(1f))
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = publish, onCheckedChange = { publish = it })
                        Text("Visible to students now")
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (title.isNotBlank()) {
                        vm.addMaterial(
                            MaterialEntity(
                                batchId = batch.id, branchId = batch.branchId, kind = selectedKind,
                                title = title.trim(), description = desc.trim().ifBlank { null },
                                url = url.trim().ifBlank { null }, passcode = passcode.trim().ifBlank { null },
                                startsAt = if (selectedKind == "live") startsAt else null,
                                durationMin = if (selectedKind == "live") 60 else null,
                                dueDate = if (selectedKind == "homework") dueDate else null,
                                maxMarks = if (selectedKind == "homework") maxMarks.toDoubleOrNull() else null,
                                timeLimitMin = if (selectedKind == "quiz") timeLimit.toIntOrNull()?.takeIf { it > 0 } else null,
                                maxAttempts = if (selectedKind == "quiz") (attempts.toIntOrNull() ?: 1).coerceIn(1, 10) else 1,
                                publish = publish, uploadedBy = user.id, createdAt = LocalDate.now().toString()
                            )
                        ) { newId ->
                            showAdd = false
                            if (selectedKind == "quiz") editingQuizId = newId.toInt()
                        }
                    }
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text("Cancel") } }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuizEditorScreen(
    vm: MainViewModel,
    data: AppDataSnapshot,
    materialId: Int,
    canEdit: Boolean,
    onBack: () -> Unit
) {
    BackHandler { onBack() }
    val quiz = data.materials.firstOrNull { it.id == materialId } ?: return
    val questions = data.quizQuestions.filter { it.materialId == materialId }
    val attempts = data.quizAttempts.filter { it.materialId == materialId && it.submittedAt != null }
    var tab by remember { mutableStateOf(0) }

    var qText by remember { mutableStateOf("") }
    var optA by remember { mutableStateOf("") }
    var optB by remember { mutableStateOf("") }
    var optC by remember { mutableStateOf("") }
    var optD by remember { mutableStateOf("") }
    var correct by remember { mutableStateOf("A") }
    var exp by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Quiz: ${quiz.title}") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Questions (${questions.size})") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Results (${attempts.size})") })
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (tab == 0) {
                    if (canEdit) {
                        item {
                            AppCard(modifier = Modifier.fillMaxWidth()) {
                                Text("Add Question", style = MaterialTheme.typography.titleMedium)
                                Spacer(Modifier.height(6.dp))
                                OutlinedTextField(value = qText, onValueChange = { qText = it }, label = { Text("Question *") }, modifier = Modifier.fillMaxWidth())
                                OutlinedTextField(value = optA, onValueChange = { optA = it }, label = { Text("Option A *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                                OutlinedTextField(value = optB, onValueChange = { optB = it }, label = { Text("Option B *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                                OutlinedTextField(value = optC, onValueChange = { optC = it }, label = { Text("Option C *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                                OutlinedTextField(value = optD, onValueChange = { optD = it }, label = { Text("Option D *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    listOf("A", "B", "C", "D").forEach { l ->
                                        FilterChip(selected = correct == l, onClick = { correct = l }, label = { Text("Correct: $l") })
                                    }
                                }
                                OutlinedTextField(value = exp, onValueChange = { exp = it }, label = { Text("Explanation (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                                Spacer(Modifier.height(8.dp))
                                Button(
                                    onClick = {
                                        vm.addQuizQuestion(materialId, qText, optA, optB, optC, optD, correct, exp)
                                        qText = ""; optA = ""; optB = ""; optC = ""; optD = ""; exp = ""
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text("Add Question") }
                            }
                        }
                    }
                    items(questions, key = { it.id }) { q ->
                        AppCard(modifier = Modifier.fillMaxWidth()) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("${q.position}. ${q.question}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                                if (canEdit) {
                                    IconButton(onClick = { vm.deleteQuizQuestion(q.id) }) {
                                        Icon(Icons.Filled.Delete, contentDescription = "Delete question", tint = CrimsonAlert)
                                    }
                                }
                            }
                            listOf("A" to q.optA, "B" to q.optB, "C" to q.optC, "D" to q.optD).forEach { (l, txt) ->
                                Text(
                                    "$l. $txt" + if (l == q.correct) " ✓" else "",
                                    color = if (l == q.correct) EmeraldSuccess else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = if (l == q.correct) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                            if (!q.explanation.isNullOrBlank()) {
                                Text("Explanation: ${q.explanation}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                } else {
                    if (attempts.isEmpty()) {
                        item { EmptyStateCard("No attempts yet", "Student quiz attempts will appear here.") }
                    } else {
                        items(attempts, key = { it.id }) { att ->
                            val st = data.students.firstOrNull { it.id == att.studentId }
                            AppCard(modifier = Modifier.fillMaxWidth()) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Column {
                                        Text(st?.fullName ?: "Student", style = MaterialTheme.typography.titleMedium)
                                        Text("${st?.studentCode ?: ""} · ${att.submittedAt ?: ""}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    StatusPill("${att.score} / ${att.total}", MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeworkTrackingScreen(
    vm: MainViewModel,
    data: AppDataSnapshot,
    materialId: Int,
    onBack: () -> Unit
) {
    BackHandler { onBack() }
    val hw = data.materials.firstOrNull { it.id == materialId } ?: return
    val enrolled = data.enrolments.filter { it.batchId == hw.batchId }
        .mapNotNull { e -> data.students.firstOrNull { it.id == e.studentId } }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Submissions: ${hw.title}") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(enrolled, key = { it.id }) { st ->
                val sub = data.submissions.firstOrNull { it.materialId == materialId && it.studentId == st.id }
                var marks by remember(sub) { mutableStateOf(sub?.marks?.toString() ?: "") }
                var feedback by remember(sub) { mutableStateOf(sub?.feedback ?: "") }
                AppCard(modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(st.fullName, style = MaterialTheme.typography.titleMedium)
                        StatusPill(if (sub != null) "Submitted" else "Pending", if (sub != null) EmeraldSuccess else AmberTertiary)
                    }
                    if (sub != null) {
                        Text(sub.answerText, style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(value = marks, onValueChange = { marks = it }, label = { Text("Marks") }, singleLine = true, modifier = Modifier.weight(1f))
                            OutlinedTextField(value = feedback, onValueChange = { feedback = it }, label = { Text("Feedback") }, singleLine = true, modifier = Modifier.weight(2f))
                        }
                        Spacer(Modifier.height(6.dp))
                        Button(onClick = { vm.gradeSubmission(sub, marks.toDoubleOrNull(), feedback) }) { Text("Save Grade") }
                    }
                }
            }
        }
    }
}

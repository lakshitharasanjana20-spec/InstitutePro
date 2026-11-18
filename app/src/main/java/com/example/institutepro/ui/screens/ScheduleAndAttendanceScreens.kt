package com.example.institutepro.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
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
import com.example.institutepro.ui.theme.AmberTertiary
import com.example.institutepro.ui.theme.CobaltDark
import com.example.institutepro.ui.theme.CrimsonAlert
import com.example.institutepro.ui.theme.EmeraldSuccess
import com.example.institutepro.ui.viewmodel.AppDataSnapshot
import com.example.institutepro.ui.viewmodel.MainViewModel
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(
    vm: MainViewModel,
    user: UserEntity,
    data: AppDataSnapshot,
    onOpenBatch: (Int) -> Unit,
    onOpenExamMarks: (Int) -> Unit
) {
    var tab by remember { mutableStateOf(0) } // 0 Timetable, 1 Substitutes
    val canSubs = DomainRules.can(user.role, "manage_substitutes")
    var monday by remember { mutableStateOf(LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))) }
    val sunday = monday.plusDays(6)
    val branchScope = vm.effectiveBranchId()
    val isTeacher = DomainRules.isTeacher(user.role)
    var teacherFilter by remember { mutableStateOf<Int?>(if (isTeacher) user.id else null) }
    val todayStr = LocalDate.now().toString()

    Column(modifier = Modifier.fillMaxSize()) {
        if (canSubs) {
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Timetable") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Substitutes") })
            }
        }

        if (tab == 0 || !canSubs) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    AppCard(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            IconButton(onClick = { monday = monday.minusWeeks(1) }) {
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous week")
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    "${monday.format(DateTimeFormatter.ofPattern("d MMM"))} – ${sunday.format(DateTimeFormatter.ofPattern("d MMM yyyy"))}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                TextButton(onClick = { monday = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) }) {
                                    Text("Today")
                                }
                            }
                            IconButton(onClick = { monday = monday.plusWeeks(1) }) {
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next week")
                            }
                        }

                        if (!isTeacher) {
                            val teachers = data.users.filter { it.role == "teacher" && it.status == "active" && (branchScope == null || it.branchId == branchScope) }
                            var tExp by remember { mutableStateOf(false) }
                            ExposedDropdownMenuBox(expanded = tExp, onExpandedChange = { tExp = it }) {
                                OutlinedTextField(
                                    value = teachers.firstOrNull { it.id == teacherFilter }?.fullName ?: "All teachers",
                                    onValueChange = {}, readOnly = true, label = { Text("Filter by teacher") },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(tExp) },
                                    modifier = Modifier.menuAnchor().fillMaxWidth()
                                )
                                ExposedDropdownMenu(expanded = tExp, onDismissRequest = { tExp = false }) {
                                    DropdownMenuItem(text = { Text("All teachers") }, onClick = { teacherFilter = null; tExp = false })
                                    teachers.forEach { t ->
                                        DropdownMenuItem(text = { Text(t.fullName) }, onClick = { teacherFilter = t.id; tExp = false })
                                    }
                                }
                            }
                        }
                    }
                }

                items((0..6).toList()) { offset ->
                    val dayDate = monday.plusDays(offset.toLong())
                    val dayStr = dayDate.toString()
                    val wd = dayDate.dayOfWeek.name.take(3).lowercase().replaceFirstChar { it.uppercase() }
                    val isToday = dayStr == todayStr

                    val dayBatches = data.batches.filter { b ->
                        (branchScope == null || b.branchId == branchScope) &&
                            b.startDate <= dayStr && b.endDate >= dayStr &&
                            (b.classDays.isBlank() || wd in b.classDays.split(",")) &&
                            (teacherFilter == null || b.teacherId == teacherFilter || data.substitutions.any { it.batchId == b.id && it.classDate == dayStr && it.substituteId == teacherFilter })
                    }.sortedBy { it.startTime }

                    val dayExams = data.exams.filter { x ->
                        x.examDate == dayStr && (branchScope == null || x.branchId == branchScope)
                    }

                    AppCard(modifier = Modifier.fillMaxWidth()) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                dayDate.format(DateTimeFormatter.ofPattern("EEE, d MMM")),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            if (isToday) StatusPill("Today", MaterialTheme.colorScheme.primary)
                        }
                        if (dayBatches.isEmpty() && dayExams.isEmpty()) {
                            Spacer(Modifier.height(4.dp))
                            Text("No classes scheduled", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            dayExams.forEach { x ->
                                val b = data.batches.firstOrNull { it.id == x.batchId }
                                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
                                Row(
                                    modifier = Modifier.fillMaxWidth().clickable { onOpenExamMarks(x.id) },
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("${x.title} (${x.startTime ?: "TBA"})", style = MaterialTheme.typography.titleMedium)
                                        Text(b?.name ?: "", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    StatusPill("Exam", AmberTertiary)
                                }
                            }
                            dayBatches.forEach { b ->
                                val c = data.courses.firstOrNull { it.id == b.courseId }
                                val sub = data.substitutions.firstOrNull { it.batchId == b.id && it.classDate == dayStr }
                                val effTeacherId = sub?.substituteId ?: b.teacherId
                                val effTeacher = data.users.firstOrNull { it.id == effTeacherId }
                                val onLeave = effTeacherId != null && data.leaves.any {
                                    it.userId == effTeacherId && it.status == "approved" && it.fromDate <= dayStr && it.toDate >= dayStr
                                }
                                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
                                Column(modifier = Modifier.fillMaxWidth().clickable { onOpenBatch(b.id) }) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("${b.startTime}–${b.endTime}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            if (sub != null) StatusPill("Substitute", MaterialTheme.colorScheme.secondary)
                                            if (sub == null && onLeave) StatusPill("On leave – needs cover", CrimsonAlert)
                                        }
                                    }
                                    Text("${c?.name ?: ""} · ${b.name} (${b.batchYear})", style = MaterialTheme.typography.titleMedium)
                                    Text("${effTeacher?.fullName ?: "No teacher"} · ${b.room.ifBlank { "Room TBA" }}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        } else {
            SubstitutesTab(vm, data, branchScope)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SubstitutesTab(vm: MainViewModel, data: AppDataSnapshot, branchScope: Int?) {
    val activeBatches = data.batches.filter { it.status == "active" && (branchScope == null || it.branchId == branchScope) }
    val teachers = data.users.filter { it.role == "teacher" && it.status == "active" && (branchScope == null || it.branchId == branchScope) }
    var selBatchId by remember(activeBatches) { mutableStateOf(activeBatches.firstOrNull()?.id ?: 0) }
    var selDate by remember { mutableStateOf(LocalDate.now().toString()) }
    var selSubId by remember(teachers) { mutableStateOf(teachers.firstOrNull()?.id ?: 0) }
    var note by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            AppCard(modifier = Modifier.fillMaxWidth()) {
                Text("Assign Substitute Teacher", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                var bExp by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(expanded = bExp, onExpandedChange = { bExp = it }) {
                    OutlinedTextField(
                        value = activeBatches.firstOrNull { it.id == selBatchId }?.name ?: "Select class",
                        onValueChange = {}, readOnly = true, label = { Text("Class / Batch") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(bExp) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = bExp, onDismissRequest = { bExp = false }) {
                        activeBatches.forEach { b ->
                            DropdownMenuItem(text = { Text("${b.name} (${b.startTime}–${b.endTime})") }, onClick = { selBatchId = b.id; bExp = false })
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                DatePickerField(
                    value = selDate,
                    onDateSelected = { selDate = it },
                    label = "Class date",
                    showStepButtons = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                var tExp by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(expanded = tExp, onExpandedChange = { tExp = it }) {
                    OutlinedTextField(
                        value = teachers.firstOrNull { it.id == selSubId }?.fullName ?: "Select substitute",
                        onValueChange = {}, readOnly = true, label = { Text("Substitute teacher") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(tExp) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = tExp, onDismissRequest = { tExp = false }) {
                        teachers.forEach { t ->
                            DropdownMenuItem(text = { Text(t.fullName) }, onClick = { selSubId = t.id; tExp = false })
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("Note (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = { vm.assignSubstitute(selBatchId, selDate, selSubId, null, note) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Assign Substitute") }
            }
        }

        item { Text("Assigned Substitutes", style = MaterialTheme.typography.titleMedium) }
        val subs = data.substitutions.filter { s -> activeBatches.any { it.id == s.batchId } }
        if (subs.isEmpty()) {
            item { EmptyStateCard("No substitutes assigned", "All classes are covered by their regular teachers.") }
        } else {
            items(subs, key = { it.id }) { sub ->
                val b = data.batches.firstOrNull { it.id == sub.batchId }
                val orig = data.users.firstOrNull { it.id == sub.originalTeacherId }
                val cov = data.users.firstOrNull { it.id == sub.substituteId }
                AppCard(modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("${sub.classDate} · ${b?.name ?: ""}", style = MaterialTheme.typography.titleMedium)
                            Text("${orig?.fullName ?: "—"} → ${cov?.fullName ?: ""}", style = MaterialTheme.typography.bodyMedium)
                            if (!sub.note.isNullOrBlank()) Text(sub.note, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        TextButton(onClick = { vm.removeSubstitute(sub.id) }) { Text("Remove") }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttendanceScreen(
    vm: MainViewModel,
    user: UserEntity,
    data: AppDataSnapshot
) {
    val canMark = DomainRules.can(user.role, "mark_attendance")
    val canStaff = DomainRules.can(user.role, "mark_staff_attendance")
    var tab by remember { mutableStateOf(0) } // 0 Mark, 1 Fingerprint Import, 2 Staff
    val tabs = buildList {
        add(if (canMark) "Mark" else "View")
        if (canMark) add("Fingerprint Import")
        if (canStaff) add("Staff")
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (tabs.size > 1) {
            TabRow(selectedTabIndex = tab.coerceAtMost(tabs.lastIndex)) {
                tabs.forEachIndexed { idx, label ->
                    Tab(selected = tab == idx, onClick = { tab = idx }, text = { Text(label) })
                }
            }
        }
        val activeLabel = tabs.getOrElse(tab) { "Mark" }
        when (activeLabel) {
            "Fingerprint Import" -> FingerprintImportTab(vm, user, data)
            "Staff" -> StaffAttendanceTab(vm, user, data)
            else -> MarkStudentAttendanceTab(vm, user, data, canMark)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MarkStudentAttendanceTab(
    vm: MainViewModel,
    user: UserEntity,
    data: AppDataSnapshot,
    canMark: Boolean
) {
    val branchScope = vm.effectiveBranchId()
    val isTeacher = DomainRules.isTeacher(user.role)
    val batches = remember(data.batches, branchScope, isTeacher, user.id) {
        data.batches.filter { b ->
            b.status == "active" && (branchScope == null || b.branchId == branchScope) && (!isTeacher || b.teacherId == user.id)
        }
    }
    var selectedBatchId by remember(batches) { mutableStateOf(batches.firstOrNull()?.id ?: 0) }
    var attDate by remember { mutableStateOf(LocalDate.now().toString()) }

    val enrolled = remember(data.enrolments, data.students, selectedBatchId) {
        data.enrolments.filter { it.batchId == selectedBatchId }
            .mapNotNull { e -> data.students.firstOrNull { it.id == e.studentId } }
            .sortedBy { it.fullName }
    }

    val statusMap = remember(selectedBatchId, attDate, data.attendance, enrolled) {
        mutableStateMapOf<Int, String>().apply {
            enrolled.forEach { st ->
                val existing = data.attendance.firstOrNull { it.batchId == selectedBatchId && it.studentId == st.id && it.attDate == attDate }
                put(st.id, existing?.status ?: "present")
            }
        }
    }

    val visibleBatchIds = remember(batches) { batches.map { it.id }.toSet() }
    val dateAtt = remember(data.attendance, visibleBatchIds, attDate) {
        data.attendance.filter { it.attDate == attDate && it.batchId in visibleBatchIds }
    }
    val presOnDate = dateAtt.count { it.status == "present" }
    val absOnDate = dateAtt.count { it.status == "absent" }
    val pctOnDate = if (presOnDate + absOnDate > 0) (presOnDate * 100) / (presOnDate + absOnDate) else 0
    val boysGirls7d = remember(data.attendance, data.students, visibleBatchIds) {
        ChartBuilders.buildBoysGirlsAttendanceSeries(data.attendance, data.students, visibleBatchIds, 7)
    }

    var studentQuery by remember { mutableStateOf("") }
    val filteredEnrolled = remember(enrolled, studentQuery) {
        if (studentQuery.isBlank()) enrolled
        else enrolled.filter { it.fullName.contains(studentQuery, true) || it.studentCode.contains(studentQuery, true) }
    }
    val draftPresent = enrolled.count { (statusMap[it.id] ?: "present") == "present" }
    val draftAbsent = enrolled.size - draftPresent

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            AppCard(modifier = Modifier.fillMaxWidth()) {
                Text("Attendance Overview ($attDate)", style = MaterialTheme.typography.titleMedium, color = CobaltDark, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                DoughnutChart(
                    slices = ChartBuilders.buildDailyAttendanceSlices(presOnDate, absOnDate),
                    centerValue = "$pctOnDate%",
                    centerLabel = "Present"
                )
                Spacer(Modifier.height(14.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.height(10.dp))
                Text("Daily Boys & Girls Attendance", style = MaterialTheme.typography.titleSmall, color = CobaltDark, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                BoysGirlsAttendanceBarChart(days = boysGirls7d)
            }
        }
        item {
            AppCard(modifier = Modifier.fillMaxWidth()) {
                var bExp by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(expanded = bExp, onExpandedChange = { bExp = it }) {
                    val cur = batches.firstOrNull { it.id == selectedBatchId }
                    val c = data.courses.firstOrNull { it.id == cur?.courseId }
                    OutlinedTextField(
                        value = if (cur != null) "${c?.name ?: ""} · ${cur.name}" else "No active batch",
                        onValueChange = {}, readOnly = true, label = { Text("Class / Batch") },
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
                Spacer(Modifier.height(10.dp))
                DatePickerField(
                    value = attDate,
                    onDateSelected = { attDate = it },
                    label = "Attendance Date",
                    showStepButtons = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (enrolled.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    SearchField(
                        value = studentQuery,
                        onValueChange = { studentQuery = it },
                        placeholder = "Filter students in class..."
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatusPill("Present: $draftPresent", CobaltDark)
                        StatusPill("Absent: $draftAbsent", CrimsonAlert)
                    }
                }
                if (canMark && enrolled.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { enrolled.forEach { statusMap[it.id] = "present" } }) {
                            Text("All present")
                        }
                        Button(
                            onClick = { vm.markBatchAttendance(selectedBatchId, attDate, statusMap.toMap()) },
                            modifier = Modifier.weight(1f).testTag("save_attendance_button")
                        ) {
                            Text("Save attendance")
                        }
                    }
                }
            }
        }

        if (filteredEnrolled.isEmpty()) {
            item {
                EmptyStateCard(
                    if (enrolled.isEmpty()) "No enrolled students" else "No matching students",
                    if (enrolled.isEmpty()) "Enroll students into this batch first." else "Clear the filter above to see all enrolled students."
                )
            }
        } else {
            items(filteredEnrolled, key = { it.id }) { st ->
                val cur = statusMap[st.id] ?: "present"
                val rec = data.attendance.firstOrNull { it.batchId == selectedBatchId && it.studentId == st.id && it.attDate == attDate }
                AppCard(modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(st.fullName, style = MaterialTheme.typography.titleMedium)
                                if (st.status == "inactive") StatusPill("inactive", CrimsonAlert)
                            }
                            Text(
                                st.studentCode + (rec?.checkIn?.let { " · in $it" } ?: "") + (rec?.checkOut?.let { " · out $it" } ?: ""),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(
                                selected = cur == "present",
                                onClick = { if (canMark) statusMap[st.id] = "present" },
                                label = { Text("Present") },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = EmeraldSuccess.copy(alpha = 0.2f))
                            )
                            FilterChip(
                                selected = cur == "absent",
                                onClick = { if (canMark) statusMap[st.id] = "absent" },
                                label = { Text("Absent") },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = CrimsonAlert.copy(alpha = 0.2f))
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FingerprintImportTab(vm: MainViewModel, user: UserEntity, data: AppDataSnapshot) {
    val activeBranches = data.branches.filter { it.status == "active" }
    var branchId by remember { mutableStateOf(vm.effectiveBranchId() ?: user.branchId ?: activeBranches.firstOrNull()?.id ?: 1) }
    val today = LocalDate.now().toString()
    var rawText by remember {
        mutableStateOf("1001,$today 08:27:10\n1001,$today 10:32:00\n1003,$today 08:29:45")
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            AppCard(modifier = Modifier.fillMaxWidth()) {
                Text("Fingerprint Punch Import (CSV / TXT)", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Paste daily machine logs (Format: FingerprintID, YYYY-MM-DD HH:MM:SS). First punch = check-in, last = check-out. Unmatched active students in scheduled classes are marked absent and 2-day absence rules apply.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                if (DomainRules.isOwner(user.role)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        activeBranches.forEach { b ->
                            FilterChip(selected = branchId == b.id, onClick = { branchId = b.id }, label = { Text(b.code) })
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                OutlinedTextField(
                    value = rawText,
                    onValueChange = { rawText = it },
                    label = { Text("Punch log lines") },
                    minLines = 5,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { vm.importFingerprintPunches(branchId, rawText) },
                    modifier = Modifier.fillMaxWidth().testTag("import_punches_button")
                ) {
                    Icon(Icons.Filled.Fingerprint, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Process & Import Punches")
                }
            }
        }
    }
}

@Composable
private fun StaffAttendanceTab(vm: MainViewModel, user: UserEntity, data: AppDataSnapshot) {
    val branchScope = vm.effectiveBranchId()
    val staff = remember(data.users, branchScope) {
        data.users.filter { it.status == "active" && it.role != "student" && (branchScope == null || it.branchId == branchScope) }
    }
    var date by remember { mutableStateOf(LocalDate.now().toString()) }
    val statuses = remember(date, data.staffAttendance, staff) {
        mutableStateMapOf<Int, String>().apply {
            staff.forEach { u ->
                val ex = data.staffAttendance.firstOrNull { it.userId == u.id && it.attDate == date }
                put(u.id, ex?.status ?: "present")
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            AppCard(modifier = Modifier.fillMaxWidth()) {
                DatePickerField(
                    value = date,
                    onDateSelected = { date = it },
                    label = "Staff Attendance Date",
                    showStepButtons = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Button(onClick = { vm.markStaffAttendance(date, statuses.toMap()) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Save Staff Attendance")
                }
            }
        }
        items(staff, key = { it.id }) { u ->
            val cur = statuses[u.id] ?: "present"
            AppCard(modifier = Modifier.fillMaxWidth()) {
                Text(u.fullName, style = MaterialTheme.typography.titleMedium)
                Text(DomainRules.ROLE_LABELS[u.role] ?: u.role, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("present" to "Present", "half_day" to "Half Day", "absent" to "Absent").forEach { (k, lbl) ->
                        FilterChip(selected = cur == k, onClick = { statuses[u.id] = k }, label = { Text(lbl) })
                    }
                }
            }
        }
    }
}

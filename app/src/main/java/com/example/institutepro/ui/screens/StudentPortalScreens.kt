package com.example.institutepro.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.institutepro.data.db.QuizAttemptEntity
import com.example.institutepro.data.db.UserEntity
import com.example.institutepro.data.repo.DomainRules
import com.example.institutepro.ui.components.*
import com.example.institutepro.ui.theme.*
import com.example.institutepro.ui.viewmodel.AppDataSnapshot
import com.example.institutepro.ui.viewmodel.MainViewModel
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@Composable
fun StudentHomeScreen(
    user: UserEntity,
    data: AppDataSnapshot,
    onNavigate: (String) -> Unit,
    onOpenClass: (Int) -> Unit,
    onTakeQuiz: (Int) -> Unit
) {
    val sid = user.studentId ?: 0
    val student = data.students.firstOrNull { it.id == sid }
    val today = LocalDate.now()
    val todayStr = today.toString()
    val wd = today.dayOfWeek.name.take(3).lowercase().replaceFirstChar { it.uppercase() }
    val enrolledBatchIds = data.enrolments.filter { it.studentId == sid }.map { it.batchId }.toSet()
    val myBatches = data.batches.filter { it.id in enrolledBatchIds }
    val activeBatches = myBatches.filter { it.status == "active" }

    val attRecs = data.attendance.filter { it.studentId == sid }
    val attPct = if (attRecs.isNotEmpty()) (attRecs.count { it.status == "present" } * 100 / attRecs.size) else null

    val pendingAssignments = data.materials.filter { m ->
        m.batchId in enrolledBatchIds && m.kind == "homework" && m.publish &&
            data.submissions.none { it.materialId == m.id && it.studentId == sid }
    }

    val myAccounts = data.feeAccounts.filter { it.studentId == sid }
    val myInsts = data.installments.filter { inst -> myAccounts.any { it.id == inst.accountId } }
    val feeBal = DomainRules.round2(myInsts.sumOf { it.amount - it.paid })
    val overdueBal = DomainRules.round2(myInsts.filter { it.dueDate < todayStr && it.amount - it.paid > 0.004 }.sumOf { it.amount - it.paid })

    val todaysClasses = activeBatches.filter { b ->
        b.startDate <= todayStr && b.endDate >= todayStr && (b.classDays.isBlank() || wd in b.classDays.split(","))
    }.sortedBy { it.startTime }

    val liveClasses = data.materials.filter { it.batchId in enrolledBatchIds && it.kind == "live" && it.publish }
    val notices = data.materials.filter { it.batchId in enrolledBatchIds && it.kind == "notice" && it.publish }.take(4)
    val greeting = when (LocalTime.now().hour) { in 0..11 -> "Good morning"; in 12..16 -> "Good afternoon"; else -> "Good evening" }
    val ctx = LocalContext.current

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Surface(
                shape = RoundedCornerShape(22.dp),
                shadowElevation = 3.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Brush.linearGradient(listOf(CobaltDark, CobaltPrimary, OceanBlue)))
                        .padding(18.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            today.format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy")),
                            style = MaterialTheme.typography.labelMedium,
                            color = CobaltContainer
                        )
                        Text(
                            "$greeting, ${student?.firstName ?: user.fullName.substringBefore(' ')}",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            "${student?.studentCode ?: ""} · ${data.branches.firstOrNull { it.id == student?.branchId }?.name ?: ""}",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.88f)
                        )
                    }
                }
            }
        }

        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                val actions = listOf(
                    Triple("My Classes", Icons.Filled.Class, "sclasses"),
                    Triple("Timetable", Icons.Filled.CalendarMonth, "sschedule"),
                    Triple("Assignments", Icons.AutoMirrored.Filled.Assignment, "sassign"),
                    Triple("Quizzes", Icons.Filled.Quiz, "squiz"),
                    Triple("Results", Icons.Filled.Grade, "sresults"),
                    Triple("Fees", Icons.Filled.ReceiptLong, "sfees")
                )
                items(actions, key = { it.third }) { (lbl, ic, dest) ->
                    QuickActionPill(label = lbl, icon = ic, onClick = { onNavigate(dest) })
                }
            }
        }

        if (student?.status == "inactive") {
            item {
                Surface(color = OceanBlue.copy(alpha = 0.12f), shape = RoundedCornerShape(16.dp)) {
                    Text(
                        "Your student status is currently Inactive due to consecutive absences. Attending your next class automatically reactivates your status.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }
        }

        if (overdueBal > 0.004) {
            item {
                Surface(color = CrimsonAlert.copy(alpha = 0.12f), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().clickable { onNavigate("sfees") }) {
                    Text(
                        "Overdue fee balance: ${DomainRules.formatMoney(overdueBal, data.currency)}. Tap to view details.",
                        color = CrimsonAlert,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    KpiCard(
                        label = "Active classes",
                        value = activeBatches.size.toString(),
                        subtitle = "View batches →",
                        icon = Icons.Filled.MenuBook,
                        accent = CobaltPrimary,
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigate("sclasses") }
                    )
                    KpiCard(
                        label = "Attendance",
                        value = attPct?.let { "$it%" } ?: "—",
                        subtitle = "${attRecs.size} sessions",
                        icon = Icons.Filled.CheckCircle,
                        accent = OceanBlue,
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigate("sattendance") }
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    KpiCard(
                        label = "Assignments",
                        value = pendingAssignments.size.toString(),
                        subtitle = if (pendingAssignments.isEmpty()) "All caught up" else "Pending due",
                        icon = Icons.AutoMirrored.Filled.Assignment,
                        accent = IndigoBlue,
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigate("sassign") }
                    )
                    KpiCard(
                        label = "Fee balance",
                        value = if (feeBal > 0.004) DomainRules.formatMoney(feeBal, data.currency) else "Paid up",
                        subtitle = if (overdueBal > 0.004) "Overdue notice" else "View ledger →",
                        icon = Icons.Filled.AccountBalanceWallet,
                        accent = if (overdueBal > 0.004) CrimsonAlert else CobaltDark,
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigate("sfees") }
                    )
                }
            }
        }

        if (liveClasses.isNotEmpty()) {
            item {
                AppCard(modifier = Modifier.fillMaxWidth()) {
                    Text("Live Classes", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    liveClasses.forEach { m ->
                        val b = data.batches.firstOrNull { it.id == m.batchId }
                        HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp), color = MaterialTheme.colorScheme.outlineVariant)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(m.title, style = MaterialTheme.typography.titleSmall)
                                Text("${m.startsAt ?: ""} · ${b?.name ?: ""}${m.passcode?.let { " · Code: $it" } ?: ""}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (!m.url.isNullOrBlank()) {
                                FilledTonalButton(onClick = { runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(m.url))) } }) { Text("Join") }
                            }
                        }
                    }
                }
            }
        }

        item {
            AppCard(modifier = Modifier.fillMaxWidth()) {
                Text("Today's Classes", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                if (todaysClasses.isEmpty()) {
                    Text("No classes today. Enjoy your day!", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    todaysClasses.forEach { b ->
                        val c = data.courses.firstOrNull { it.id == b.courseId }
                        val t = data.users.firstOrNull { it.id == b.teacherId }
                        HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp), color = MaterialTheme.colorScheme.outlineVariant)
                        Column(modifier = Modifier.fillMaxWidth().clickable { onOpenClass(b.id) }) {
                            Text("${b.startTime}–${b.endTime}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                            Text("${c?.name ?: ""} · ${b.name}", style = MaterialTheme.typography.titleMedium)
                            Text("${t?.fullName ?: "Teacher TBA"} · ${b.room}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }

        if (pendingAssignments.isNotEmpty()) {
            item {
                AppCard(modifier = Modifier.fillMaxWidth()) {
                    Text("Assignments To Do", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    pendingAssignments.take(4).forEach { a ->
                        HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp), color = MaterialTheme.colorScheme.outlineVariant)
                        Row(modifier = Modifier.fillMaxWidth().clickable { onNavigate("sassign") }, horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(a.title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            Text("Due ${a.dueDate ?: "—"}", style = MaterialTheme.typography.labelMedium, color = AmberTertiary)
                        }
                    }
                }
            }
        }

        val myQuizzes = data.materials.filter { it.batchId in enrolledBatchIds && it.kind == "quiz" && it.publish }
        if (myQuizzes.isNotEmpty()) {
            item {
                AppCard(modifier = Modifier.fillMaxWidth()) {
                    Text("Available Quizzes", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    myQuizzes.take(3).forEach { q ->
                        HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp), color = MaterialTheme.colorScheme.outlineVariant)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(q.title, style = MaterialTheme.typography.titleSmall)
                                Text("${data.quizQuestions.count { it.materialId == q.id }} questions · ${q.timeLimitMin ?: 0} min", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            FilledTonalButton(onClick = { onTakeQuiz(q.id) }) { Text("Start") }
                        }
                    }
                }
            }
        }

        if (notices.isNotEmpty()) {
            item {
                AppCard(modifier = Modifier.fillMaxWidth()) {
                    Text("Announcements", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    notices.forEach { n ->
                        HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp), color = MaterialTheme.colorScheme.outlineVariant)
                        Text(n.title, style = MaterialTheme.typography.titleSmall)
                        if (!n.description.isNullOrBlank()) Text(n.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
fun StudentClassesScreen(
    user: UserEntity,
    data: AppDataSnapshot,
    onOpenClass: (Int) -> Unit
) {
    val sid = user.studentId ?: 0
    val batchIds = data.enrolments.filter { it.studentId == sid }.map { it.batchId }.toSet()
    val batches = data.batches.filter { it.id in batchIds }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(batches, key = { it.id }) { b ->
            val c = data.courses.firstOrNull { it.id == b.courseId }
            val t = data.users.firstOrNull { it.id == b.teacherId }
            AppCard(modifier = Modifier.fillMaxWidth(), onClick = { onOpenClass(b.id) }) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${c?.name ?: ""} · ${b.name}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    StatusPill(b.status, statusColorFor(b.status))
                }
                Text("Teacher: ${t?.fullName ?: "TBA"} · ${b.startTime}–${b.endTime} (${b.classDays})", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentClassDetailScreen(
    data: AppDataSnapshot,
    batchId: Int,
    onBack: () -> Unit,
    onTakeQuiz: (Int) -> Unit
) {
    BackHandler { onBack() }
    val batch = data.batches.firstOrNull { it.id == batchId } ?: return
    val course = data.courses.firstOrNull { it.id == batch.courseId }
    var kind by remember { mutableStateOf("lecture") }
    val items = data.materials.filter { it.batchId == batchId && it.kind == kind && it.publish }
    val ctx = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("${course?.name ?: ""} · ${batch.name}") },
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
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(DomainRules.LEARN_TYPES) { (k, lbl) ->
                        FilterChip(selected = kind == k, onClick = { kind = k }, label = { Text(lbl) })
                    }
                }
            }
            if (items.isEmpty()) {
                item { EmptyStateCard("Nothing published yet", "Check other tabs for materials, lectures, or quizzes.") }
            } else {
                items(items, key = { it.id }) { m ->
                    AppCard(modifier = Modifier.fillMaxWidth()) {
                        Text(m.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        if (!m.description.isNullOrBlank()) Text(m.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(6.dp))
                        if (!m.url.isNullOrBlank()) {
                            FilledTonalButton(onClick = { runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(m.url))) } }) {
                                Text(if (m.kind == "live") "Join Live Class" else "Open Link")
                            }
                        }
                        if (m.kind == "quiz") {
                            Button(onClick = { onTakeQuiz(m.id) }) { Text("Open Quiz") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StudentAssignmentsScreen(vm: MainViewModel, user: UserEntity, data: AppDataSnapshot) {
    val sid = user.studentId ?: 0
    val batchIds = data.enrolments.filter { it.studentId == sid }.map { it.batchId }.toSet()
    val homeworks = data.materials.filter { it.batchId in batchIds && it.kind == "homework" && it.publish }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (homeworks.isEmpty()) {
            item { EmptyStateCard("No assignments", "You are all caught up.") }
        } else {
            items(homeworks, key = { it.id }) { hw ->
                val sub = data.submissions.firstOrNull { it.materialId == hw.id && it.studentId == sid }
                val batch = data.batches.firstOrNull { it.id == hw.batchId }
                var answer by remember(sub) { mutableStateOf(sub?.answerText ?: "") }
                var link by remember(sub) { mutableStateOf(sub?.linkUrl ?: "") }
                AppCard(modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(hw.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        StatusPill(if (sub != null) "Submitted" else "To Do", if (sub != null) EmeraldSuccess else AmberTertiary)
                    }
                    Text("${batch?.name ?: ""} · Due ${hw.dueDate ?: "No due date"} · Max ${hw.maxMarks?.toInt() ?: 100}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (!hw.description.isNullOrBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(hw.description, style = MaterialTheme.typography.bodyMedium)
                    }
                    if (sub?.marks != null) {
                        Spacer(Modifier.height(6.dp))
                        StatusPill("Score: ${sub.marks} / ${hw.maxMarks ?: 100}${sub.feedback?.let { " · $it" } ?: ""}", EmeraldSuccess)
                    } else {
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(value = answer, onValueChange = { answer = it }, label = { Text("Your answer / response") }, minLines = 2, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = link, onValueChange = { link = it }, label = { Text("Drive / attachment link (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = { vm.submitAssignment(hw.id, answer, link) }, modifier = Modifier.fillMaxWidth()) {
                            Text(if (sub != null) "Update Submission" else "Submit Assignment")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StudentQuizzesScreen(user: UserEntity, data: AppDataSnapshot, onTakeQuiz: (Int) -> Unit) {
    val sid = user.studentId ?: 0
    val batchIds = data.enrolments.filter { it.studentId == sid }.map { it.batchId }.toSet()
    val quizzes = data.materials.filter { it.batchId in batchIds && it.kind == "quiz" && it.publish }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (quizzes.isEmpty()) {
            item { EmptyStateCard("No quizzes", "Quizzes published by your teachers will appear here.") }
        } else {
            items(quizzes, key = { it.id }) { q ->
                val qCount = data.quizQuestions.count { it.materialId == q.id }
                val attempts = data.quizAttempts.filter { it.materialId == q.id && it.studentId == sid && it.submittedAt != null }
                val best = attempts.maxOfOrNull { it.score }
                AppCard(modifier = Modifier.fillMaxWidth()) {
                    Text(q.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("$qCount questions · ${q.timeLimitMin?.let { "$it min" } ?: "No limit"} · ${attempts.size}/${q.maxAttempts} attempts used", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (best != null) Text("Best score: $best / $qCount", style = MaterialTheme.typography.labelLarge, color = EmeraldSuccess)
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { onTakeQuiz(q.id) },
                        enabled = qCount > 0,
                        modifier = Modifier.testTag("start_quiz_${q.id}")
                    ) {
                        Text(if (attempts.size >= q.maxAttempts) "Review Attempts" else "Start Quiz")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InteractiveQuizRunnerScreen(
    vm: MainViewModel,
    user: UserEntity,
    data: AppDataSnapshot,
    materialId: Int,
    onBack: () -> Unit
) {
    BackHandler { onBack() }
    val sid = user.studentId ?: 0
    val quiz = data.materials.firstOrNull { it.id == materialId } ?: return
    val questions = data.quizQuestions.filter { it.materialId == materialId }
    val pastAttempts = data.quizAttempts.filter { it.materialId == materialId && it.studentId == sid && it.submittedAt != null }
    val exhausted = pastAttempts.size >= quiz.maxAttempts

    var activeResult by remember { mutableStateOf<QuizAttemptEntity?>(if (exhausted) pastAttempts.firstOrNull() else null) }
    val selected = remember(materialId) { mutableStateMapOf<Int, String>() }
    val startedMillis = remember(materialId) { System.currentTimeMillis() }
    val totalSec = (quiz.timeLimitMin ?: 0) * 60
    var remainSec by remember(materialId) { mutableStateOf(totalSec) }

    if (totalSec > 0 && activeResult == null) {
        LaunchedEffect(materialId) {
            while (remainSec > 0 && activeResult == null) {
                delay(1000L)
                remainSec--
                if (remainSec == 0) {
                    vm.submitQuizAttempt(materialId, selected.toMap(), startedMillis, quiz.timeLimitMin) { activeResult = it }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(quiz.title) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
                actions = {
                    if (totalSec > 0 && activeResult == null) {
                        val m = remainSec / 60; val s = remainSec % 60
                        StatusPill("%02d:%02d".format(m, s), if (remainSec <= 60) CrimsonAlert else MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
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
            val res = activeResult
            if (res != null) {
                val pct = if (res.total > 0) (res.score * 100 / res.total) else 0
                val ansMap = res.answersJson.split(",").mapNotNull {
                    val p = it.split(":"); if (p.size == 2) p[0].toIntOrNull()?.let { k -> k to p[1] } else null
                }.toMap()

                item {
                    AppCard(modifier = Modifier.fillMaxWidth()) {
                        Text("Quiz Result", style = MaterialTheme.typography.titleMedium)
                        Text("${res.score} / ${res.total} ($pct%)", style = MaterialTheme.typography.displaySmall, color = if (pct >= 50) EmeraldSuccess else CrimsonAlert)
                        if (res.overtime) Text("Time limit exceeded — attempt not scored.", color = CrimsonAlert)
                    }
                }
                items(questions, key = { it.id }) { q ->
                    val mine = ansMap[q.id] ?: ""
                    AppCard(modifier = Modifier.fillMaxWidth()) {
                        Text("${q.position}. ${q.question}", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(6.dp))
                        listOf("A" to q.optA, "B" to q.optB, "C" to q.optC, "D" to q.optD).forEach { (l, txt) ->
                            val isCorrect = l == q.correct
                            val isMineWrong = l == mine && !isCorrect
                            Text(
                                "$l. $txt" + if (isCorrect) " ✓" else if (isMineWrong) " ✗ (your answer)" else "",
                                color = when {
                                    isCorrect -> EmeraldSuccess
                                    isMineWrong -> CrimsonAlert
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                fontWeight = if (isCorrect || isMineWrong) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                        if (!q.explanation.isNullOrBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Text("Explanation: ${q.explanation}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
                item {
                    Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Back to Quizzes") }
                }
            } else {
                items(questions, key = { it.id }) { q ->
                    AppCard(modifier = Modifier.fillMaxWidth()) {
                        Text("${q.position}. ${q.question}", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(6.dp))
                        listOf("A" to q.optA, "B" to q.optB, "C" to q.optC, "D" to q.optD).forEach { (l, txt) ->
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable { selected[q.id] = l }.padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(selected = selected[q.id] == l, onClick = { selected[q.id] = l })
                                Text("$l. $txt", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
                item {
                    Button(
                        onClick = {
                            vm.submitQuizAttempt(materialId, selected.toMap(), startedMillis, quiz.timeLimitMin) { activeResult = it }
                        },
                        modifier = Modifier.fillMaxWidth().height(50.dp).testTag("submit_quiz_button")
                    ) { Text("Submit Answers") }
                }
            }
        }
    }
}

@Composable
fun StudentResultsAttendanceFeesScreen(mode: String, user: UserEntity, data: AppDataSnapshot) {
    val sid = user.studentId ?: 0
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        when (mode) {
            "sresults" -> {
                val marks = data.examMarks.filter { it.studentId == sid }
                if (marks.isEmpty()) item { EmptyStateCard("No exam results", "Published exam grades will appear here.") }
                else items(marks, key = { it.id }) { m ->
                    val ex = data.exams.firstOrNull { it.id == m.examId }
                    val b = data.batches.firstOrNull { it.id == ex?.batchId }
                    val c = data.courses.firstOrNull { it.id == b?.courseId }
                    val ct = data.courseTypes.firstOrNull { it.id == c?.typeId }
                    val pct = if (!m.absent && m.marks != null && ex != null && ex.maxMarks > 0) (m.marks / ex.maxMarks) * 100.0 else null
                    val grade = pct?.let { DomainRules.gradeFor(it, data.settings) }
                    val outcome = if (pct != null && ex != null) DomainRules.examOutcome(ex.examType, ct?.code ?: "", pct, data.settings) else ""
                    AppCard(modifier = Modifier.fillMaxWidth()) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(ex?.title ?: "Exam", style = MaterialTheme.typography.titleMedium)
                                Text("${c?.name ?: ""} · ${ex?.examDate ?: ""}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (outcome.isNotEmpty()) Text(outcome, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            }
                            if (m.absent) StatusPill("Absent", CrimsonAlert)
                            else if (grade != null) StatusPill("${m.marks?.toInt()} / ${ex?.maxMarks?.toInt()} ($grade)", EmeraldSuccess)
                        }
                    }
                }
            }
            "sattendance" -> {
                val recs = data.attendance.filter { it.studentId == sid }
                val pres = recs.count { it.status == "present" }
                val pct = if (recs.isNotEmpty()) pres * 100 / recs.size else 0
                item {
                    AppCard(modifier = Modifier.fillMaxWidth()) {
                        Text("Overall Attendance", style = MaterialTheme.typography.titleMedium)
                        Text("$pct% ($pres of ${recs.size} sessions)", style = MaterialTheme.typography.headlineSmall, color = if (pct >= 75) EmeraldSuccess else AmberTertiary)
                    }
                }
                items(recs, key = { it.id }) { r ->
                    val b = data.batches.firstOrNull { it.id == r.batchId }
                    AppCard(modifier = Modifier.fillMaxWidth()) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text(r.attDate, style = MaterialTheme.typography.titleMedium)
                                Text("${b?.name ?: ""}${r.checkIn?.let { " · in $it" } ?: ""}${r.checkOut?.let { " · out $it" } ?: ""}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            StatusPill(r.status, statusColorFor(r.status))
                        }
                    }
                }
            }
            "sfees" -> {
                val accounts = data.feeAccounts.filter { it.studentId == sid }
                if (data.paymentInfo.isNotBlank()) {
                    item {
                        AppCard(modifier = Modifier.fillMaxWidth()) {
                            Text("How to Pay Fees", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(4.dp))
                            Text(data.paymentInfo, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                items(accounts, key = { it.id }) { acc ->
                    val b = data.batches.firstOrNull { it.id == acc.batchId }
                    val insts = data.installments.filter { it.accountId == acc.id }.sortedBy { it.no }
                    AppCard(modifier = Modifier.fillMaxWidth()) {
                        Text(b?.name ?: "Batch", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        insts.forEach { inst ->
                            val done = inst.paid >= inst.amount - 0.004
                            val late = !done && inst.dueDate < LocalDate.now().toString()
                            val st = if (done) "paid" else if (late) "overdue" else "pending"
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column {
                                    Text("${inst.label} (Due ${inst.dueDate})", style = MaterialTheme.typography.bodyMedium)
                                    Text("${DomainRules.formatMoney(inst.paid, data.currency)} / ${DomainRules.formatMoney(inst.amount, data.currency)}", style = MaterialTheme.typography.labelMedium)
                                }
                                StatusPill(st, statusColorFor(st))
                            }
                        }
                    }
                }
            }
        }
    }
}

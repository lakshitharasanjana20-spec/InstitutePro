package com.example.institutepro.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.institutepro.data.db.UserEntity
import com.example.institutepro.data.repo.DomainRules
import com.example.institutepro.ui.components.*
import com.example.institutepro.ui.theme.*
import com.example.institutepro.ui.viewmodel.AppDataSnapshot
import com.example.institutepro.ui.viewmodel.MainViewModel
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@Composable
fun DashboardScreen(
    vm: MainViewModel,
    user: UserEntity,
    data: AppDataSnapshot,
    allowedMenus: List<String>,
    onNavigate: (String) -> Unit,
    onOpenBatch: (Int) -> Unit,
    onOpenStudent: (Int) -> Unit,
    onOpenExamMarks: (Int) -> Unit
) {
    val today = remember { LocalDate.now() }
    val todayStr = remember { today.toString() }
    val monthStart = remember { today.withDayOfMonth(1).toString() }
    val weekday = remember { today.dayOfWeek.name.take(3).lowercase().replaceFirstChar { it.uppercase() } }
    val nowTime = remember { LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")) }
    val monthPrefix = remember { todayStr.take(7) }
    val branchScope = vm.effectiveBranchId()
    val isTeacher = DomainRules.isTeacher(user.role)
    val canSeeReports = "reports" in allowedMenus || "payments" in allowedMenus

    val visibleBatches = remember(data.batches, branchScope, isTeacher, user.id, data.substitutions) {
        data.batches.filter { b ->
            (branchScope == null || b.branchId == branchScope) &&
                (!isTeacher || b.teacherId == user.id || data.substitutions.any { it.batchId == b.id && it.substituteId == user.id && it.classDate >= today.minusDays(1).toString() })
        }
    }
    val visibleBatchIds = remember(visibleBatches) { visibleBatches.map { it.id }.toSet() }
    val visibleStudents = remember(data.students, data.enrolments, branchScope, isTeacher, visibleBatchIds) {
        data.students.filter { s ->
            (branchScope == null || s.branchId == branchScope) &&
                (!isTeacher || data.enrolments.any { it.studentId == s.id && it.batchId in visibleBatchIds })
        }
    }

    val activeStudents = visibleStudents.count { it.status == "active" }
    val boysCount = visibleStudents.count { it.status == "active" && it.gender.equals("Male", true) }
    val girlsCount = visibleStudents.count { it.status == "active" && it.gender.equals("Female", true) }
    val inactiveStudents = visibleStudents.filter { it.status == "inactive" }
    val newThisMonth = visibleStudents.count { it.registeredAt.startsWith(monthPrefix) }
    val activeBatches = visibleBatches.filter { it.status == "active" }

    val todaysClasses = remember(activeBatches, todayStr, weekday) {
        activeBatches.filter { b ->
            b.startDate <= todayStr && b.endDate >= todayStr &&
                (b.classDays.isBlank() || weekday in b.classDays.split(","))
        }.sortedBy { it.startTime }
    }

    val todayAttendance = remember(data.attendance, visibleBatchIds, todayStr) {
        data.attendance.filter { it.attDate == todayStr && it.batchId in visibleBatchIds }
    }
    val presentToday = todayAttendance.count { it.status == "present" }
    val absentToday = todayAttendance.count { it.status == "absent" }
    val totalTodayAtt = presentToday + absentToday
    val presentPctToday = if (totalTodayAtt > 0) (presentToday * 100) / totalTodayAtt else 0

    // Chart datasets
    val boysGirlsSeries = remember(data.attendance, visibleStudents, visibleBatchIds) {
        ChartBuilders.buildBoysGirlsAttendanceSeries(data.attendance, visibleStudents, visibleBatchIds, 7)
    }
    val studentGrowthSeries = remember(visibleStudents) {
        ChartBuilders.buildStudentGrowthSeries(visibleStudents, 6)
    }
    val incomeExpenseSeries = remember(data.payments, data.expenses, branchScope, today) {
        ChartBuilders.buildIncomeExpenseSeries(
            payments = data.payments,
            expenses = data.expenses,
            branchScope = branchScope,
            fromDate = today.minusDays(20).toString(),
            toDate = todayStr
        )
    }

    // Financial KPI metrics surfaced directly in Dashboard KPI Cards (links to Reports)
    val monthRevenue = remember(data.payments, branchScope, monthStart, todayStr) {
        data.payments.filter { it.voidedAt == null && (branchScope == null || it.branchId == branchScope) && it.paidOn in monthStart..todayStr }
            .sumOf { it.amount }
    }
    val monthExpenses = remember(data.expenses, branchScope, monthStart, todayStr) {
        data.expenses.filter { (branchScope == null || it.branchId == branchScope) && it.spentOn in monthStart..todayStr }
            .sumOf { it.amount }
    }
    val overdueTotal = remember(data.installments, data.feeAccounts, data.students, branchScope, todayStr) {
        data.installments.filter { inst ->
            val acc = data.feeAccounts.firstOrNull { it.id == inst.accountId }
            val st = data.students.firstOrNull { it.id == acc?.studentId }
            (branchScope == null || st?.branchId == branchScope) && inst.dueDate < todayStr && inst.amount - inst.paid > 0.004
        }.sumOf { it.amount - it.paid }
    }

    val upcomingExams = remember(data.exams, visibleBatchIds, todayStr, today) {
        val nextWeek = today.plusDays(7).toString()
        data.exams.filter { it.batchId in visibleBatchIds && it.examDate in todayStr..nextWeek }.sortedBy { it.examDate }
    }
    val pendingMarksExams = remember(data.exams, data.examMarks, data.enrolments, visibleBatchIds, todayStr) {
        data.exams.filter { x ->
            if (x.batchId !in visibleBatchIds || x.examDate > todayStr) return@filter false
            val totalEnr = data.enrolments.count { it.batchId == x.batchId }
            val done = data.examMarks.count { it.examId == x.id && (it.marks != null || it.absent) }
            totalEnr > 0 && done < totalEnr
        }.take(5)
    }
    val staffPresentToday = remember(data.staffAttendance, data.users, branchScope, todayStr) {
        data.staffAttendance.count { sa ->
            sa.attDate == todayStr && (sa.status == "present" || sa.status == "half_day") &&
                (branchScope == null || data.users.firstOrNull { it.id == sa.userId }?.branchId == branchScope)
        }
    }

    val greeting = remember {
        when (LocalTime.now().hour) {
            in 0..11 -> "Good morning"
            in 12..16 -> "Good afternoon"
            else -> "Good evening"
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Hero Blue Greeting Card
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = today.format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy")),
                                style = MaterialTheme.typography.labelMedium,
                                color = CobaltContainer
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = "$greeting, ${user.fullName.substringBefore(' ')}",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "${visibleStudents.size} total students · ${activeBatches.size} active batches",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White.copy(alpha = 0.88f)
                            )
                        }

                        if (DomainRules.isOwner(user.role) && data.branches.isNotEmpty()) {
                            var expanded by remember { mutableStateOf(false) }
                            val selectedBranchName = data.branches.firstOrNull { it.id == branchScope }?.code ?: "All"
                            Box {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color.White.copy(alpha = 0.18f),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable { expanded = true }
                                        .testTag("dashboard_branch_filter")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(Icons.Filled.Apartment, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                        Text(selectedBranchName, color = Color.White, style = MaterialTheme.typography.labelLarge)
                                        Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                    }
                                }
                                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                    DropdownMenuItem(
                                        text = { Text("All branches") },
                                        onClick = { vm.setBranchFilter(null); expanded = false }
                                    )
                                    data.branches.filter { it.status == "active" }.forEach { b ->
                                        DropdownMenuItem(
                                            text = { Text(b.name) },
                                            onClick = { vm.setBranchFilter(b.id); expanded = false }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // One-Tap Quick Actions Bar
        item {
            val quickActions = remember(allowedMenus, user.role) {
                buildList {
                    if ("attendance" in allowedMenus) add(Triple("Mark Attendance", Icons.Filled.FactCheck, "attendance"))
                    if ("payments" in allowedMenus) add(Triple("Collect Fee", Icons.Filled.Payments, "payments"))
                    if ("students" in allowedMenus && DomainRules.can(user.role, "register_student")) add(Triple("Students", Icons.Filled.PersonAdd, "students"))
                    if ("reports" in allowedMenus) add(Triple("Reports", Icons.Filled.Assessment, "reports"))
                    if ("cloud_sync" in allowedMenus) add(Triple("Cloud Sync", Icons.Filled.CloudSync, "cloud_sync"))
                    if ("schedule" in allowedMenus) add(Triple("Timetable", Icons.Filled.CalendarMonth, "schedule"))
                    if ("exams" in allowedMenus) add(Triple("Exams", Icons.AutoMirrored.Filled.Assignment, "exams"))
                    if ("materials" in allowedMenus) add(Triple("Learning Hub", Icons.Filled.PlayLesson, "materials"))
                    if ("leaves" in allowedMenus) add(Triple("Leaves", Icons.Filled.EventAvailable, "leaves"))
                }
            }
            if (quickActions.isNotEmpty()) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(quickActions, key = { it.third }) { (label, icon, dest) ->
                        QuickActionPill(
                            label = label,
                            icon = icon,
                            onClick = { onNavigate(dest) },
                            modifier = Modifier.testTag("quick_action_$dest")
                        )
                    }
                }
            }
        }

        // Core Operations + Reports KPI Grid (Large-Number AutoFit Cards)
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if ("students" in allowedMenus) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        KpiCard(
                            label = "Active Students",
                            value = activeStudents.toString(),
                            subtitle = "$boysCount boys · $girlsCount girls",
                            icon = Icons.Filled.People,
                            accent = CobaltPrimary,
                            modifier = Modifier.weight(1f).testTag("kpi_active_students"),
                            onClick = { onNavigate("students") }
                        )
                        KpiCard(
                            label = "New This Month",
                            value = "+$newThisMonth",
                            subtitle = "${inactiveStudents.size} inactive",
                            icon = Icons.Filled.PersonAdd,
                            accent = OceanBlue,
                            modifier = Modifier.weight(1f).testTag("kpi_new_students"),
                            onClick = { onNavigate("students") }
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if ("attendance" in allowedMenus) {
                        KpiCard(
                            label = "Present Today",
                            value = "$presentToday / $totalTodayAtt",
                            subtitle = "$presentPctToday% present · $absentToday absent",
                            icon = Icons.Filled.CheckCircle,
                            accent = CobaltMid,
                            modifier = Modifier.weight(1f),
                            onClick = { onNavigate("attendance") }
                        )
                    }
                    if ("schedule" in allowedMenus) {
                        KpiCard(
                            label = "Classes Today",
                            value = todaysClasses.size.toString(),
                            subtitle = "${activeBatches.size} active batches",
                            icon = Icons.Filled.CalendarToday,
                            accent = IndigoBlue,
                            modifier = Modifier.weight(1f),
                            onClick = { onNavigate("schedule") }
                        )
                    }
                }

                // Financial & Report KPI Cards directly inside Main Dashboard KPI section
                if (canSeeReports) {
                    val reportDest = if ("reports" in allowedMenus) "reports" else "payments"
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        KpiCard(
                            label = "Monthly Revenue",
                            value = DomainRules.formatMoney(monthRevenue, data.currency),
                            subtitle = "View Financial Report →",
                            icon = Icons.Filled.TrendingUp,
                            accent = CobaltPrimary,
                            modifier = Modifier.weight(1f).testTag("kpi_dashboard_revenue"),
                            onClick = { onNavigate(reportDest) }
                        )
                        KpiCard(
                            label = "Monthly Expenses",
                            value = DomainRules.formatMoney(monthExpenses, data.currency),
                            subtitle = "View Expense Report →",
                            icon = Icons.Filled.ReceiptLong,
                            accent = SkyBlue,
                            modifier = Modifier.weight(1f).testTag("kpi_dashboard_expenses"),
                            onClick = { onNavigate(reportDest) }
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        KpiCard(
                            label = "Net Cash Balance",
                            value = DomainRules.formatMoney(monthRevenue - monthExpenses, data.currency),
                            subtitle = "Open Reports Module →",
                            icon = Icons.Filled.AccountBalanceWallet,
                            accent = CobaltDark,
                            modifier = Modifier.weight(1f).testTag("kpi_dashboard_net"),
                            onClick = { onNavigate(reportDest) }
                        )
                        KpiCard(
                            label = "Overdue Fees",
                            value = DomainRules.formatMoney(overdueTotal, data.currency),
                            subtitle = "Tap for due list →",
                            icon = Icons.Filled.Assessment,
                            accent = if (overdueTotal > 0.0) CrimsonAlert else OceanBlue,
                            modifier = Modifier.weight(1f).testTag("kpi_dashboard_overdue"),
                            onClick = { onNavigate(reportDest) }
                        )
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if ("exams" in allowedMenus) {
                            KpiCard(
                                label = "Upcoming Exams",
                                value = upcomingExams.size.toString(),
                                subtitle = "Next 7 days",
                                icon = Icons.Filled.EditNote,
                                accent = OceanBlue,
                                modifier = Modifier.weight(1f),
                                onClick = { onNavigate("exams") }
                            )
                        }
                        if (DomainRules.can(user.role, "mark_staff_attendance")) {
                            KpiCard(
                                label = "Staff Present",
                                value = staffPresentToday.toString(),
                                subtitle = "Today",
                                icon = Icons.Filled.Badge,
                                accent = CobaltPrimary,
                                modifier = Modifier.weight(1f),
                                onClick = { onNavigate("attendance") }
                            )
                        }
                    }
                }
            }
        }

        // Graphical Chart 1: Daily Attendance Doughnut Chart
        if ("attendance" in allowedMenus) {
            item {
                AppCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Daily Attendance", style = MaterialTheme.typography.titleLarge, color = CobaltDark)
                            Text("Today's student attendance summary", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        TextButton(onClick = { onNavigate("attendance") }) { Text("Mark") }
                    }
                    Spacer(Modifier.height(10.dp))
                    DoughnutChart(
                        slices = ChartBuilders.buildDailyAttendanceSlices(presentToday, absentToday),
                        centerValue = "$presentPctToday%",
                        centerLabel = "Present"
                    )
                }
            }

            // Graphical Chart 2: Daily Students Attendance (Boys & Girls in one Bar Graph)
            item {
                AppCard(modifier = Modifier.fillMaxWidth()) {
                    Text("Daily Attendance: Boys & Girls", style = MaterialTheme.typography.titleLarge, color = CobaltDark)
                    Text("Each day's present count by gender (last 7 days)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(12.dp))
                    BoysGirlsAttendanceBarChart(days = boysGirlsSeries)
                }
            }
        }

        // Graphical Chart 3: Total Students Increasing With Time (Line Graph)
        if ("students" in allowedMenus) {
            item {
                AppCard(modifier = Modifier.fillMaxWidth()) {
                    Text("Total Students Growth", style = MaterialTheme.typography.titleLarge, color = CobaltDark)
                    Text("Cumulative student enrollment over time", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(12.dp))
                    StudentGrowthLineChart(points = studentGrowthSeries)
                }
            }
        }

        // Graphical Chart 4: Income vs Expenses Line Graph (Reports Preview on Dashboard)
        if (canSeeReports) {
            item {
                AppCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Income & Expenses Trend", style = MaterialTheme.typography.titleLarge, color = CobaltDark)
                            Text("Recent 21 days by date", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if ("reports" in allowedMenus) {
                            TextButton(onClick = { onNavigate("reports") }) { Text("Full Report →") }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    IncomeExpenseLineChart(points = incomeExpenseSeries, currency = data.currency)
                }
            }
        }

        // Today's Classes
        if ("schedule" in allowedMenus) {
            item {
                AppCard(modifier = Modifier.fillMaxWidth()) {
                    Text("Today's classes", style = MaterialTheme.typography.titleLarge, color = CobaltDark)
                    Spacer(Modifier.height(8.dp))
                    if (todaysClasses.isEmpty()) {
                        Text("No classes scheduled for today.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        todaysClasses.forEach { b ->
                            val course = data.courses.firstOrNull { it.id == b.courseId }
                            val teacher = data.users.firstOrNull { it.id == b.teacherId }
                            val count = data.enrolments.count { it.batchId == b.id }
                            val marked = data.attendance.any { it.batchId == b.id && it.attDate == todayStr }
                            val statusText = when {
                                nowTime >= b.startTime && nowTime <= b.endTime -> "Now"
                                nowTime < b.startTime -> "Upcoming"
                                else -> "Done"
                            }
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
                            Column(modifier = Modifier.fillMaxWidth().clickable { onOpenBatch(b.id) }) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "${b.startTime} – ${b.endTime}",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    StatusPill(statusText, statusColorFor(statusText))
                                }
                                Spacer(Modifier.height(2.dp))
                                Text("${course?.name ?: "Course"} · ${b.name} (${b.batchYear})", style = MaterialTheme.typography.titleMedium)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "${teacher?.fullName ?: "No teacher"} · $count students",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.weight(1f)
                                    )
                                    if ("attendance" in allowedMenus) {
                                        Text(
                                            text = if (marked) "✓ Attendance marked" else "Mark attendance →",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (marked) CobaltPrimary else OceanBlue,
                                            modifier = if (!marked) Modifier.clickable { onNavigate("attendance") } else Modifier
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Upcoming Exams & Pending Marks
        if ("exams" in allowedMenus) {
            item {
                AppCard(modifier = Modifier.fillMaxWidth()) {
                    Text("Upcoming exams (next 7 days)", style = MaterialTheme.typography.titleLarge, color = CobaltDark)
                    Spacer(Modifier.height(8.dp))
                    if (upcomingExams.isEmpty()) {
                        Text("No exams in the next 7 days.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        upcomingExams.forEach { x ->
                            val b = data.batches.firstOrNull { it.id == x.batchId }
                            val c = data.courses.firstOrNull { it.id == b?.courseId }
                            HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp), color = MaterialTheme.colorScheme.outlineVariant)
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable { onOpenExamMarks(x.id) },
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(x.title, style = MaterialTheme.typography.titleMedium)
                                    Text("${c?.name ?: ""} · ${b?.name ?: ""}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text(x.examDate, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
            if (pendingMarksExams.isNotEmpty()) {
                item {
                    AppCard(modifier = Modifier.fillMaxWidth()) {
                        Text("Marks still to enter", style = MaterialTheme.typography.titleLarge, color = CobaltDark)
                        Spacer(Modifier.height(8.dp))
                        pendingMarksExams.forEach { x ->
                            val b = data.batches.firstOrNull { it.id == x.batchId }
                            val totalEnr = data.enrolments.count { it.batchId == x.batchId }
                            val done = data.examMarks.count { it.examId == x.id && (it.marks != null || it.absent) }
                            HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp), color = MaterialTheme.colorScheme.outlineVariant)
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable { onOpenExamMarks(x.id) },
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(x.title, style = MaterialTheme.typography.titleMedium)
                                    Text("${b?.name ?: ""} · ${x.examDate}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                StatusPill("$done/$totalEnr", OceanBlue)
                            }
                        }
                    }
                }
            }
        }

        // Inactive & Recent Students
        if ("students" in allowedMenus) {
            if (inactiveStudents.isNotEmpty()) {
                item {
                    AppCard(modifier = Modifier.fillMaxWidth()) {
                        Text("Inactive students", style = MaterialTheme.typography.titleLarge, color = CobaltDark)
                        Spacer(Modifier.height(8.dp))
                        inactiveStudents.take(5).forEach { s ->
                            val b = data.batches.firstOrNull { it.id == s.batchId }
                            val c = data.courses.firstOrNull { it.id == b?.courseId }
                            HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp), color = MaterialTheme.colorScheme.outlineVariant)
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable { onOpenStudent(s.id) },
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(s.fullName, style = MaterialTheme.typography.titleMedium)
                                    Text("${s.studentCode} · ${c?.name ?: ""}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                StatusPill("inactive", CrimsonAlert)
                            }
                        }
                    }
                }
            }

            item {
                AppCard(modifier = Modifier.fillMaxWidth()) {
                    Text("Recently registered", style = MaterialTheme.typography.titleLarge, color = CobaltDark)
                    Spacer(Modifier.height(8.dp))
                    if (visibleStudents.isEmpty()) {
                        Text("No students registered yet.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        visibleStudents.sortedByDescending { it.registeredAt }.take(5).forEach { s ->
                            val b = data.batches.firstOrNull { it.id == s.batchId }
                            val c = data.courses.firstOrNull { it.id == b?.courseId }
                            HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp), color = MaterialTheme.colorScheme.outlineVariant)
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable { onOpenStudent(s.id) },
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(s.fullName, style = MaterialTheme.typography.titleMedium)
                                    Text("${s.studentCode} · ${c?.name ?: ""}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text(s.registeredAt, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

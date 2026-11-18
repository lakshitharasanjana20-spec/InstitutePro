package com.example.institutepro.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.example.institutepro.data.db.PaymentEntity
import com.example.institutepro.data.db.StudentEntity
import com.example.institutepro.data.db.UserEntity
import com.example.institutepro.data.repo.DomainRules
import com.example.institutepro.ui.components.*
import com.example.institutepro.ui.theme.*
import com.example.institutepro.ui.viewmodel.AppDataSnapshot
import com.example.institutepro.ui.viewmodel.MainViewModel
import java.time.LocalDate

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PaymentsScreen(
    vm: MainViewModel,
    user: UserEntity,
    data: AppDataSnapshot
) {
    var verifiedStudent by remember { mutableStateOf<StudentEntity?>(null) }
    var pendingVerifyStudent by remember { mutableStateOf<StudentEntity?>(null) }
    var lookupQuery by remember { mutableStateOf("") }
    var receiptToView by remember { mutableStateOf<PaymentEntity?>(null) }
    var voidTarget by remember { mutableStateOf<PaymentEntity?>(null) }

    val latestReceipt by vm.latestReceipt.collectAsState()
    LaunchedEffect(latestReceipt) {
        if (latestReceipt != null) {
            receiptToView = latestReceipt
            vm.clearLatestReceipt()
        }
    }

    val branchScope = vm.effectiveBranchId()
    val canPay = DomainRules.can(user.role, "take_payment")
    val canScholarship = DomainRules.can(user.role, "set_scholarship")
    val canVoid = DomainRules.can(user.role, "void_payment")
    val todayStr = LocalDate.now().toString()

    if (verifiedStudent != null) {
        val stu = verifiedStudent!!
        val accounts = data.feeAccounts.filter { it.studentId == stu.id }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                TextButton(onClick = { verifiedStudent = null }) { Text("← Back to payments") }
                AppCard(modifier = Modifier.fillMaxWidth()) {
                    StatusPill("Verified", CobaltPrimary)
                    Spacer(Modifier.height(6.dp))
                    Text(stu.fullName, style = MaterialTheme.typography.headlineSmall)
                    Text("${stu.studentCode} · ${stu.phone}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            if (accounts.isEmpty()) {
                item { EmptyStateCard("No fee accounts", "Student has no active fee accounts.") }
            } else {
                items(accounts, key = { it.id }) { acc ->
                    val batch = data.batches.firstOrNull { it.id == acc.batchId }
                    val course = data.courses.firstOrNull { it.id == batch?.courseId }
                    val insts = data.installments.filter { it.accountId == acc.id }.sortedBy { it.no }
                    val net = insts.sumOf { it.amount }
                    val paid = insts.sumOf { it.paid }
                    val balance = DomainRules.round2(net - paid)
                    val nextDueAmt = insts.firstOrNull { it.amount - it.paid > 0.004 }?.let { DomainRules.round2(it.amount - it.paid) } ?: 0.0

                    var payAmt by remember(acc.id, nextDueAmt) { mutableStateOf(if (nextDueAmt > 0) nextDueAmt.toString() else "") }
                    var method by remember { mutableStateOf("cash") }
                    var ref by remember { mutableStateOf("") }
                    var note by remember { mutableStateOf("") }
                    var schAmt by remember { mutableStateOf("") }

                    AppCard(modifier = Modifier.fillMaxWidth()) {
                        Text("${course?.name ?: ""} · ${batch?.name ?: ""}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column { Text("Fee", style = MaterialTheme.typography.labelSmall); Text(DomainRules.formatMoney(acc.totalFee, data.currency)) }
                            Column { Text("Scholarship", style = MaterialTheme.typography.labelSmall); Text(DomainRules.formatMoney(acc.scholarship, data.currency)) }
                            Column { Text("Paid", style = MaterialTheme.typography.labelSmall); Text(DomainRules.formatMoney(paid, data.currency)) }
                            Column { Text("Balance", style = MaterialTheme.typography.labelSmall); Text(DomainRules.formatMoney(balance, data.currency), fontWeight = FontWeight.Bold) }
                        }
                        Spacer(Modifier.height(10.dp))
                        insts.forEach { inst ->
                            val done = inst.paid >= inst.amount - 0.004
                            val overdue = !done && inst.dueDate < todayStr
                            val stText = if (done) "paid" else if (overdue) "overdue" else "pending"
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column {
                                    Text(inst.label, style = MaterialTheme.typography.bodyMedium)
                                    Text("Due ${inst.dueDate} · ${DomainRules.formatMoney(inst.paid, data.currency)} / ${DomainRules.formatMoney(inst.amount, data.currency)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                StatusPill(stText, statusColorFor(stText))
                            }
                        }

                        if (balance > 0.004 && canPay) {
                            Spacer(Modifier.height(12.dp))
                            Text("Record Payment", style = MaterialTheme.typography.titleSmall)
                            Spacer(Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (nextDueAmt > 0.004) {
                                    AssistChip(
                                        onClick = { payAmt = nextDueAmt.toString() },
                                        label = { Text("Next due: ${DomainRules.formatMoney(nextDueAmt, data.currency)}") }
                                    )
                                }
                                if (balance > nextDueAmt + 0.004) {
                                    AssistChip(
                                        onClick = { payAmt = balance.toString() },
                                        label = { Text("Full: ${DomainRules.formatMoney(balance, data.currency)}") }
                                    )
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                            OutlinedTextField(value = payAmt, onValueChange = { payAmt = it }, label = { Text("Amount") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("payment_amount_input"))
                            Spacer(Modifier.height(6.dp))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                DomainRules.PAYMENT_METHODS.forEach { (k, lbl) ->
                                    FilterChip(selected = method == k, onClick = { method = k }, label = { Text(lbl) })
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                            OutlinedTextField(value = ref, onValueChange = { ref = it }, label = { Text("Reference (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                            OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("Note (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                            Spacer(Modifier.height(8.dp))
                            Button(
                                onClick = { vm.recordPayment(acc.id, payAmt.toDoubleOrNull() ?: 0.0, method, ref, todayStr, note) },
                                modifier = Modifier.fillMaxWidth().testTag("record_payment_button")
                            ) { Text("Record Payment & Issue Receipt") }

                            if (canScholarship && acc.scholarship == 0.0) {
                                Spacer(Modifier.height(10.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    OutlinedTextField(value = schAmt, onValueChange = { schAmt = it }, label = { Text("One-time scholarship") }, singleLine = true, modifier = Modifier.weight(1f))
                                    FilledTonalButton(onClick = { vm.applyScholarship(acc.id, schAmt.toDoubleOrNull() ?: 0.0); schAmt = "" }) { Text("Apply") }
                                }
                            }
                        } else if (balance <= 0.004) {
                            Spacer(Modifier.height(8.dp))
                            StatusPill("Fully Paid", CobaltPrimary)
                        }
                    }
                }
            }
        }
    } else {
        val matchingStudents = remember(lookupQuery, data.students, branchScope) {
            if (lookupQuery.isBlank()) emptyList()
            else data.students.filter { s ->
                (branchScope == null || s.branchId == branchScope) &&
                    (s.fullName.contains(lookupQuery, true) || s.studentCode.contains(lookupQuery, true) || s.phone.contains(lookupQuery, true))
            }.take(10)
        }
        val history = remember(data.payments, branchScope, lookupQuery) {
            data.payments.filter { p ->
                (branchScope == null || p.branchId == branchScope) &&
                    (lookupQuery.isBlank() || p.receiptNo.contains(lookupQuery, true) ||
                        (data.students.firstOrNull { it.id == p.studentId }?.fullName?.contains(lookupQuery, true) == true))
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (canPay) {
                item {
                    AppCard(modifier = Modifier.fillMaxWidth()) {
                        Text("Step 1 · Find & Verify Student", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        SearchField(
                            value = lookupQuery,
                            onValueChange = { lookupQuery = it },
                            placeholder = "Search student name, ID, phone or receipt",
                            testTag = "payment_student_search"
                        )
                        if (matchingStudents.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            matchingStudents.forEach { st ->
                                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(st.fullName, style = MaterialTheme.typography.titleMedium)
                                        Text("${st.studentCode} · ${st.phone}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    FilledTonalButton(onClick = { pendingVerifyStudent = st }) { Text("Select") }
                                }
                            }
                        } else if (lookupQuery.isBlank()) {
                            val quickDueStudents = remember(data.students, data.feeAccounts, data.installments, branchScope) {
                                data.students.filter { branchScope == null || it.branchId == branchScope }
                                    .mapNotNull { st ->
                                        val accIds = data.feeAccounts.filter { it.studentId == st.id }.map { it.id }.toSet()
                                        val due = DomainRules.round2(data.installments.filter { it.accountId in accIds }.sumOf { it.amount - it.paid })
                                        if (due > 0.004) st to due else null
                                    }
                                    .sortedByDescending { it.second }
                                    .take(5)
                            }
                            if (quickDueStudents.isNotEmpty()) {
                                Spacer(Modifier.height(10.dp))
                                Text(
                                    "Students with Pending Dues",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = CobaltDark,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(Modifier.height(4.dp))
                                quickDueStudents.forEach { (st, due) ->
                                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(st.fullName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                            Text("${st.studentCode} · Due ${DomainRules.formatMoney(due, data.currency)}", style = MaterialTheme.typography.labelSmall, color = CrimsonAlert)
                                        }
                                        FilledTonalButton(onClick = { pendingVerifyStudent = st }) { Text("Collect") }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item { Text("Payment History (${history.size})", style = MaterialTheme.typography.titleMedium) }
            if (history.isEmpty()) {
                item { EmptyStateCard("No payments recorded", "Search a student above to collect a fee payment.") }
            } else {
                items(history, key = { it.id }) { p ->
                    val st = data.students.firstOrNull { it.id == p.studentId }
                    val b = data.batches.firstOrNull { it.id == p.batchId }
                    val isVoid = p.voidedAt != null
                    AppCard(modifier = Modifier.fillMaxWidth(), onClick = { receiptToView = p }) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(p.receiptNo, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                                Text("${st?.fullName ?: ""} (${st?.studentCode ?: ""})", style = MaterialTheme.typography.bodyMedium)
                                Text("${b?.name ?: ""} · ${p.paidOn} · ${p.method}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    DomainRules.formatMoney(p.amount, data.currency),
                                    style = MaterialTheme.typography.titleMedium.copy(textDecoration = if (isVoid) TextDecoration.LineThrough else TextDecoration.None),
                                    color = if (isVoid) MaterialTheme.colorScheme.onSurfaceVariant else CobaltPrimary
                                )
                                if (isVoid) {
                                    StatusPill("VOID", CrimsonAlert)
                                } else if (canVoid) {
                                    TextButton(onClick = { voidTarget = p }) { Text("Void", color = CrimsonAlert) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    pendingVerifyStudent?.let { st ->
        AlertDialog(
            onDismissRequest = { pendingVerifyStudent = null },
            title = { Text("Confirm Student Identity") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(st.fullName, style = MaterialTheme.typography.titleLarge)
                    Text("ID: ${st.studentCode}")
                    Text("Phone: ${st.phone}")
                    st.email?.let { Text("Email: $it") }
                    Spacer(Modifier.height(4.dp))
                    Text("Verify these details with the student or guardian before taking payment.", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = {
                Button(onClick = {
                    verifiedStudent = st
                    pendingVerifyStudent = null
                }) { Text("Identity Verified") }
            },
            dismissButton = { TextButton(onClick = { pendingVerifyStudent = null }) { Text("Cancel") } }
        )
    }

    receiptToView?.let { p ->
        val st = data.students.firstOrNull { it.id == p.studentId }
        val b = data.batches.firstOrNull { it.id == p.batchId }
        val br = data.branches.firstOrNull { it.id == p.branchId }
        AlertDialog(
            onDismissRequest = { receiptToView = null },
            title = { Text(if (p.voidedAt != null) "Payment Receipt (VOID)" else "Official Payment Receipt") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(data.instituteName, fontWeight = FontWeight.Bold)
                    Text("Branch: ${br?.name ?: ""}")
                    HorizontalDivider()
                    Text("Receipt No: ${p.receiptNo}", fontWeight = FontWeight.Bold)
                    Text("Date: ${p.paidOn}")
                    Text("Student: ${st?.fullName ?: ""} (${st?.studentCode ?: ""})")
                    Text("Class: ${b?.name ?: ""}")
                    Text("Method: ${p.method}${p.reference?.let { " · Ref: $it" } ?: ""}")
                    Text("Amount: ${DomainRules.formatMoney(p.amount, data.currency)}", style = MaterialTheme.typography.titleLarge, color = if (p.voidedAt != null) CrimsonAlert else CobaltPrimary)
                    if (p.voidedAt != null) {
                        Text("VOIDED on ${p.voidedAt}: ${p.voidReason ?: ""}", color = CrimsonAlert, fontWeight = FontWeight.Bold)
                    }
                }
            },
            confirmButton = { Button(onClick = { receiptToView = null }) { Text("Close") } }
        )
    }

    voidTarget?.let { p ->
        var reason by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { voidTarget = null },
            title = { Text("Void Receipt ${p.receiptNo}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Cancelling reverses the payment back onto the student's installments and keeps this receipt marked VOID.", style = MaterialTheme.typography.bodyMedium)
                    OutlinedTextField(value = reason, onValueChange = { reason = it }, label = { Text("Reason (min 5 chars)") }, singleLine = true)
                }
            },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonAlert),
                    onClick = { vm.voidPayment(p.id, reason); voidTarget = null }
                ) { Text("Confirm Void") }
            },
            dismissButton = { TextButton(onClick = { voidTarget = null }) { Text("Cancel") } }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReportsScreen(
    vm: MainViewModel,
    user: UserEntity,
    data: AppDataSnapshot,
    onOpenStudent: (Int) -> Unit
) {
    var tab by remember { mutableStateOf(0) } // 0 Overview & Analytics, 1 Expenses
    var rangeMode by remember { mutableStateOf(1) } // 0 = This Month, 1 = Last 30 Days, 2 = All Time
    val branchScope = vm.effectiveBranchId()
    val today = remember { LocalDate.now() }
    val todayStr = remember { today.toString() }
    val fromDate = when (rangeMode) {
        0 -> today.withDayOfMonth(1).toString()
        1 -> today.minusDays(30).toString()
        else -> "2000-01-01"
    }
    val toDate = todayStr

    val filteredPayments = remember(data.payments, branchScope, fromDate, toDate) {
        data.payments.filter { it.voidedAt == null && (branchScope == null || it.branchId == branchScope) && it.paidOn in fromDate..toDate }
    }
    val filteredExpenses = remember(data.expenses, branchScope, fromDate, toDate) {
        data.expenses.filter { (branchScope == null || it.branchId == branchScope) && it.spentOn in fromDate..toDate }
    }
    val revenue = remember(filteredPayments) { filteredPayments.sumOf { it.amount } }
    val expensesTotal = remember(filteredExpenses) { filteredExpenses.sumOf { it.amount } }
    val payrollPaid = remember(data.payrolls, branchScope, fromDate, toDate) {
        data.payrolls.filter { it.status == "paid" && (branchScope == null || it.branchId == branchScope) && (it.paidOn ?: "") in fromDate..toDate }.sumOf { it.net }
    }
    val netProfit = revenue - expensesTotal - payrollPaid

    val overdueAccounts = remember(data.installments, data.feeAccounts, data.students, branchScope, toDate) {
        data.feeAccounts.mapNotNull { acc ->
            val st = data.students.firstOrNull { it.id == acc.studentId } ?: return@mapNotNull null
            if (branchScope != null && st.branchId != branchScope) return@mapNotNull null
            val due = data.installments.filter { it.accountId == acc.id && it.dueDate < toDate && it.amount - it.paid > 0.004 }
                .sumOf { it.amount - it.paid }
            if (due > 0.004) Triple(st, acc.batchId, due) else null
        }
    }
    val overdueTotal = overdueAccounts.sumOf { it.third }
    val outstandingTotal = remember(data.installments, data.feeAccounts, data.students, branchScope) {
        data.installments.filter { inst ->
            val acc = data.feeAccounts.firstOrNull { it.id == inst.accountId }
            val st = data.students.firstOrNull { it.id == acc?.studentId }
            (branchScope == null || st?.branchId == branchScope) && inst.amount - inst.paid > 0.004
        }.sumOf { it.amount - it.paid }
    }
    val collectedAllTime = remember(data.installments, data.feeAccounts, data.students, branchScope) {
        data.installments.filter { inst ->
            val acc = data.feeAccounts.firstOrNull { it.id == inst.accountId }
            val st = data.students.firstOrNull { it.id == acc?.studentId }
            branchScope == null || st?.branchId == branchScope
        }.sumOf { it.paid }
    }

    // Graphical Datasets
    val incomeExpenseSeries = remember(data.payments, data.expenses, branchScope, fromDate, toDate) {
        ChartBuilders.buildIncomeExpenseSeries(data.payments, data.expenses, branchScope, fromDate, toDate)
    }
    val visibleStudents = remember(data.students, branchScope) {
        data.students.filter { branchScope == null || it.branchId == branchScope }
    }
    val studentGrowthSeries = remember(visibleStudents) {
        ChartBuilders.buildStudentGrowthSeries(visibleStudents, 6)
    }
    val visibleBatchIds = remember(data.batches, branchScope) {
        data.batches.filter { branchScope == null || it.branchId == branchScope }.map { it.id }.toSet()
    }
    val boysGirlsSeries = remember(data.attendance, visibleStudents, visibleBatchIds) {
        ChartBuilders.buildBoysGirlsAttendanceSeries(data.attendance, visibleStudents, visibleBatchIds, 7)
    }
    val todayAtt = data.attendance.filter { it.attDate == todayStr && it.batchId in visibleBatchIds }
    val presentToday = todayAtt.count { it.status == "present" }
    val absentToday = todayAtt.count { it.status == "absent" }
    val presentPctToday = if (presentToday + absentToday > 0) (presentToday * 100) / (presentToday + absentToday) else 0

    val revenueByMethodSlices = remember(filteredPayments) {
        filteredPayments.groupBy { it.method }
            .entries
            .sortedByDescending { it.value.sumOf { p -> p.amount } }
            .mapIndexed { idx, (method, list) ->
                ChartSlice(
                    label = method.replace('_', ' ').replaceFirstChar { it.uppercase() },
                    value = list.sumOf { it.amount },
                    color = ChartBluePalette[idx % ChartBluePalette.size]
                )
            }
    }
    val expenseCategorySlices = remember(filteredExpenses) {
        filteredExpenses.groupBy { it.category }
            .entries
            .sortedByDescending { it.value.sumOf { e -> e.amount } }
            .mapIndexed { idx, (cat, list) ->
                ChartSlice(
                    label = cat,
                    value = list.sumOf { it.amount },
                    color = ChartBluePalette[idx % ChartBluePalette.size]
                )
            }
    }
    val branchComparison = remember(data.branches, filteredPayments, filteredExpenses) {
        data.branches.filter { it.status == "active" }.map { br ->
            BranchComparisonPoint(
                label = br.code,
                primaryVal = filteredPayments.filter { it.branchId == br.id }.sumOf { it.amount },
                secondaryVal = filteredExpenses.filter { it.branchId == br.id }.sumOf { it.amount }
            )
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Analytics & Reports") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Expenses") })
        }

        if (tab == 0) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("This Month", "Last 30 Days", "All Time").forEachIndexed { idx, title ->
                            FilterChip(
                                selected = rangeMode == idx,
                                onClick = { rangeMode = idx },
                                label = { Text(title) }
                            )
                        }
                    }
                }

                // KPI Cards (Large-Number Auto-Fit)
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            KpiCard("Revenue", DomainRules.formatMoney(revenue, data.currency), Icons.Filled.ArrowCircleDown, CobaltPrimary, Modifier.weight(1f), subtitle = "${filteredPayments.size} receipts")
                            KpiCard("Expenses", DomainRules.formatMoney(expensesTotal, data.currency), Icons.Filled.ArrowCircleUp, SkyBlue, Modifier.weight(1f), subtitle = "${filteredExpenses.size} entries")
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            KpiCard("Payroll Paid", DomainRules.formatMoney(payrollPaid, data.currency), Icons.Filled.Payments, OceanBlue, Modifier.weight(1f), subtitle = "Disbursed salaries")
                            KpiCard("Net Profit", DomainRules.formatMoney(netProfit, data.currency), Icons.Filled.TrendingUp, if (netProfit >= 0) CobaltDark else CrimsonAlert, Modifier.weight(1f), subtitle = "Rev - Exp - Payroll")
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            KpiCard("Overdue Fees", DomainRules.formatMoney(overdueTotal, data.currency), Icons.Filled.Warning, if (overdueTotal > 0) CrimsonAlert else CobaltPrimary, Modifier.weight(1f), subtitle = "${overdueAccounts.size} student(s)")
                            KpiCard("Outstanding", DomainRules.formatMoney(outstandingTotal, data.currency), Icons.Filled.HourglassBottom, IndigoBlue, Modifier.weight(1f), subtitle = "Unpaid balance")
                        }
                    }
                }

                // 1. Line Graph: Income vs Expenses (Y = Amount, X = Date)
                item {
                    AppCard(modifier = Modifier.fillMaxWidth()) {
                        Text("Income & Expenses Over Time", style = MaterialTheme.typography.titleLarge, color = CobaltDark)
                        Text("Daily fee income vs operating expenses", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(10.dp))
                        IncomeExpenseLineChart(points = incomeExpenseSeries, currency = data.currency)
                    }
                }

                // 2. Doughnut Chart: Fee Collection Breakdown & Daily Attendance
                item {
                    AppCard(modifier = Modifier.fillMaxWidth()) {
                        Text("Fee Collection Status", style = MaterialTheme.typography.titleLarge, color = CobaltDark)
                        Text("Collected vs pending & overdue installments", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(10.dp))
                        val upcomingPending = (outstandingTotal - overdueTotal).coerceAtLeast(0.0)
                        val totalAllFees = (collectedAllTime + outstandingTotal).coerceAtLeast(1.0)
                        val collectedPct = ((collectedAllTime / totalAllFees) * 100).toInt()
                        DoughnutChart(
                            slices = listOf(
                                ChartSlice("Collected", collectedAllTime, CobaltPrimary),
                                ChartSlice("Upcoming Due", upcomingPending, SkyLight),
                                ChartSlice("Overdue", overdueTotal, CobaltDark)
                            ),
                            centerValue = "$collectedPct%",
                            centerLabel = "Collected",
                            formatAsMoney = true,
                            currency = data.currency
                        )
                    }
                }

                // 3. Pie Charts: Revenue by Method & Expenses by Category
                item {
                    AppCard(modifier = Modifier.fillMaxWidth()) {
                        Text("Revenue by Payment Method", style = MaterialTheme.typography.titleLarge, color = CobaltDark)
                        Spacer(Modifier.height(10.dp))
                        PieChart(slices = revenueByMethodSlices, formatAsMoney = true, currency = data.currency)
                    }
                }

                item {
                    AppCard(modifier = Modifier.fillMaxWidth()) {
                        Text("Expenses by Category", style = MaterialTheme.typography.titleLarge, color = CobaltDark)
                        Spacer(Modifier.height(10.dp))
                        PieChart(slices = expenseCategorySlices, formatAsMoney = true, currency = data.currency)
                    }
                }

                // 4. Bar Graph: Daily Student Attendance (Boys & Girls in one chart) + Doughnut
                item {
                    AppCard(modifier = Modifier.fillMaxWidth()) {
                        Text("Daily Attendance Snapshot", style = MaterialTheme.typography.titleLarge, color = CobaltDark)
                        Spacer(Modifier.height(10.dp))
                        DoughnutChart(
                            slices = ChartBuilders.buildDailyAttendanceSlices(presentToday, absentToday),
                            centerValue = "$presentPctToday%",
                            centerLabel = "Present Today"
                        )
                        Spacer(Modifier.height(16.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Spacer(Modifier.height(12.dp))
                        Text("Boys & Girls Attendance by Day", style = MaterialTheme.typography.titleMedium, color = CobaltDark)
                        Spacer(Modifier.height(8.dp))
                        BoysGirlsAttendanceBarChart(days = boysGirlsSeries)
                    }
                }

                // 5. Line Graph: Total Students Increasing With Time
                item {
                    AppCard(modifier = Modifier.fillMaxWidth()) {
                        Text("Total Students Increasing Over Time", style = MaterialTheme.typography.titleLarge, color = CobaltDark)
                        Text("6-month cumulative enrollment growth", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(10.dp))
                        StudentGrowthLineChart(points = studentGrowthSeries)
                    }
                }

                // 6. Bar Graph: Branch Comparison
                if (branchScope == null && branchComparison.size > 1) {
                    item {
                        AppCard(modifier = Modifier.fillMaxWidth()) {
                            Text("Branch Revenue vs Expenses", style = MaterialTheme.typography.titleLarge, color = CobaltDark)
                            Spacer(Modifier.height(10.dp))
                            GroupedComparisonBarChart(
                                items = branchComparison,
                                primaryLabel = "Revenue",
                                secondaryLabel = "Expenses"
                            )
                        }
                    }
                }

                // Overdue Fees List
                item { Text("Overdue Fees (${overdueAccounts.size})", style = MaterialTheme.typography.titleMedium) }
                if (overdueAccounts.isEmpty()) {
                    item { EmptyStateCard("No overdue fees", "All due student installments are up to date.") }
                } else {
                    items(overdueAccounts) { (st, batchId, due) ->
                        val b = data.batches.firstOrNull { it.id == batchId }
                        AppCard(modifier = Modifier.fillMaxWidth(), onClick = { onOpenStudent(st.id) }) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(st.fullName, style = MaterialTheme.typography.titleMedium)
                                    Text("${st.studentCode} · ${b?.name ?: ""} · ${st.phone}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text(DomainRules.formatMoney(due, data.currency), color = CrimsonAlert, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        } else {
            ExpensesTab(vm, user, data, branchScope)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExpensesTab(vm: MainViewModel, user: UserEntity, data: AppDataSnapshot, branchScope: Int?) {
    val activeBranches = data.branches.filter { it.status == "active" }
    var branchId by remember { mutableStateOf(branchScope ?: user.branchId ?: activeBranches.firstOrNull()?.id ?: 1) }
    var category by remember { mutableStateOf(DomainRules.EXPENSE_CATEGORIES.first()) }
    var amount by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var spentOn by remember { mutableStateOf(LocalDate.now().toString()) }

    val list = data.expenses.filter { branchScope == null || it.branchId == branchScope }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            AppCard(modifier = Modifier.fillMaxWidth()) {
                Text("Record Expense", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                if (DomainRules.isOwner(user.role)) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        activeBranches.forEach { b ->
                            FilterChip(selected = branchId == b.id, onClick = { branchId = b.id }, label = { Text(b.code) })
                        }
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DomainRules.EXPENSE_CATEGORIES.forEach { cat ->
                        FilterChip(selected = category == cat, onClick = { category = cat }, label = { Text(cat) })
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = amount, onValueChange = { amount = it }, label = { Text("Amount") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = spentOn, onValueChange = { spentOn = it }, label = { Text("Date") }, singleLine = true, modifier = Modifier.weight(1f))
                }
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(value = desc, onValueChange = { desc = it }, label = { Text("Description") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        vm.addExpense(branchId, category, amount.toDoubleOrNull() ?: 0.0, spentOn, desc)
                        amount = ""; desc = ""
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Add Expense") }
            }
        }

        items(list, key = { it.id }) { exp ->
            val br = data.branches.firstOrNull { it.id == exp.branchId }
            AppCard(modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("${exp.category} · ${exp.spentOn}", style = MaterialTheme.typography.titleMedium)
                        Text("${br?.name ?: ""} ${exp.description?.let { "· $it" } ?: ""}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(DomainRules.formatMoney(exp.amount, data.currency), fontWeight = FontWeight.Bold, color = CobaltDark)
                        if (DomainRules.isOwner(user.role)) {
                            IconButton(onClick = { vm.deleteExpense(exp.id) }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Delete expense", tint = CrimsonAlert)
                            }
                        }
                    }
                }
            }
        }
    }
}

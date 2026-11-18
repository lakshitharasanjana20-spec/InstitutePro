package com.example.institutepro.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.institutepro.data.db.BranchEntity
import com.example.institutepro.data.db.PayrollEntity
import com.example.institutepro.data.db.UserEntity
import com.example.institutepro.data.repo.DomainRules
import com.example.institutepro.ui.components.*
import com.example.institutepro.ui.theme.CrimsonAlert
import com.example.institutepro.ui.theme.EmeraldSuccess
import com.example.institutepro.ui.viewmodel.AppDataSnapshot
import com.example.institutepro.ui.viewmodel.MainViewModel
import java.time.LocalDate
import java.time.YearMonth

@Composable
fun SalariesScreen(vm: MainViewModel, user: UserEntity, data: AppDataSnapshot) {
    var tab by remember { mutableStateOf(0) } // 0 Monthly Payroll, 1 Salary Setup
    var period by remember { mutableStateOf(YearMonth.now().toString()) }
    var editingPayroll by remember { mutableStateOf<PayrollEntity?>(null) }
    var slipToView by remember { mutableStateOf<PayrollEntity?>(null) }
    var setupTarget by remember { mutableStateOf<UserEntity?>(null) }
    val branchScope = vm.effectiveBranchId()

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Monthly Payroll") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Salary Setup") })
        }

        if (tab == 0) {
            val rows = data.payrolls.filter { p -> p.period == period && (branchScope == null || p.branchId == branchScope) }
            val totalNet = rows.sumOf { it.net }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    AppCard(modifier = Modifier.fillMaxWidth()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(value = period, onValueChange = { period = it }, label = { Text("Period (YYYY-MM)") }, singleLine = true, modifier = Modifier.weight(1f))
                            Button(onClick = { vm.generateMonthlyPayroll(period) }) { Text("Create Payroll") }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text("Total Net: ${DomainRules.formatMoney(totalNet, data.currency)}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                }
                if (rows.isEmpty()) {
                    item { EmptyStateCard("No payroll for $period", "Set up staff salaries first, then tap Create Payroll.") }
                } else {
                    items(rows, key = { it.id }) { p ->
                        val u = data.users.firstOrNull { it.id == p.userId }
                        AppCard(modifier = Modifier.fillMaxWidth()) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(u?.fullName ?: "Staff", style = MaterialTheme.typography.titleMedium)
                                    Text("Basic ${DomainRules.formatMoney(p.basic, data.currency)} · EPF -${DomainRules.formatMoney(p.epf, data.currency)}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("Net: ${DomainRules.formatMoney(p.net, data.currency)}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    StatusPill(p.status, statusColorFor(p.status))
                                    if (p.status == "draft") {
                                        TextButton(onClick = { editingPayroll = p }) { Text("Edit / Pay") }
                                    } else {
                                        TextButton(onClick = { slipToView = p }) { Text("Payslip") }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            val staff = data.users.filter { it.status == "active" && it.role != "student" && it.id != user.id && (branchScope == null || it.branchId == branchScope) }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(staff, key = { it.id }) { u ->
                    val ss = data.salaryStructures.firstOrNull { it.userId == u.id }
                    AppCard(modifier = Modifier.fillMaxWidth()) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(u.fullName, style = MaterialTheme.typography.titleMedium)
                                Text(DomainRules.ROLE_LABELS[u.role] ?: u.role, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    if (ss == null) "Not configured"
                                    else "Basic ${DomainRules.formatMoney(ss.basic, data.currency)} + Allow ${DomainRules.formatMoney(ss.allowance, data.currency)}${if (ss.epf) " · EPF 8%" else ""}",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            FilledTonalButton(onClick = { setupTarget = u }) { Text("Set Pay") }
                        }
                    }
                }
            }
        }
    }

    setupTarget?.let { target ->
        val existing = data.salaryStructures.firstOrNull { it.userId == target.id }
        var basic by remember { mutableStateOf((existing?.basic ?: 80000.0).toString()) }
        var allowance by remember { mutableStateOf((existing?.allowance ?: 10000.0).toString()) }
        var epf by remember { mutableStateOf(existing?.epf ?: true) }
        AlertDialog(
            onDismissRequest = { setupTarget = null },
            title = { Text("Salary Setup: ${target.fullName}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = basic, onValueChange = { basic = it }, label = { Text("Basic salary") }, singleLine = true)
                    OutlinedTextField(value = allowance, onValueChange = { allowance = it }, label = { Text("Fixed allowance") }, singleLine = true)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = epf, onCheckedChange = { epf = it })
                        Text("Deduct EPF (8% of basic)")
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    vm.saveSalarySetup(target.id, basic.toDoubleOrNull() ?: 0.0, allowance.toDoubleOrNull() ?: 0.0, epf)
                    setupTarget = null
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { setupTarget = null }) { Text("Cancel") } }
        )
    }

    editingPayroll?.let { p ->
        val u = data.users.firstOrNull { it.id == p.userId }
        var bonus by remember { mutableStateOf(p.bonus.toString()) }
        var ot by remember { mutableStateOf(p.overtime.toString()) }
        var otherDed by remember { mutableStateOf(p.otherDeduction.toString()) }
        var adv by remember { mutableStateOf(p.advance.toString()) }
        var note by remember { mutableStateOf(p.note ?: "") }
        var method by remember { mutableStateOf("bank_transfer") }
        AlertDialog(
            onDismissRequest = { editingPayroll = null },
            title = { Text("Payroll: ${u?.fullName ?: ""}") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Basic ${p.basic} · Allowance ${p.allowance} · EPF -${p.epf}", style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = bonus, onValueChange = { bonus = it }, label = { Text("Bonus") }, singleLine = true, modifier = Modifier.weight(1f))
                        OutlinedTextField(value = ot, onValueChange = { ot = it }, label = { Text("Overtime") }, singleLine = true, modifier = Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = otherDed, onValueChange = { otherDed = it }, label = { Text("Deductions") }, singleLine = true, modifier = Modifier.weight(1f))
                        OutlinedTextField(value = adv, onValueChange = { adv = it }, label = { Text("Advance") }, singleLine = true, modifier = Modifier.weight(1f))
                    }
                    OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("Note") }, singleLine = true)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("cash", "bank_transfer").forEach { m ->
                            FilterChip(selected = method == m, onClick = { method = m }, label = { Text(m.replace('_', ' ')) })
                        }
                    }
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        vm.updateOrPayPayroll(p.id, bonus.toDoubleOrNull() ?: 0.0, ot.toDoubleOrNull() ?: 0.0, otherDed.toDoubleOrNull() ?: 0.0, adv.toDoubleOrNull() ?: 0.0, note, false, LocalDate.now().toString(), method)
                        editingPayroll = null
                    }) { Text("Save Draft") }
                    Button(onClick = {
                        vm.updateOrPayPayroll(p.id, bonus.toDoubleOrNull() ?: 0.0, ot.toDoubleOrNull() ?: 0.0, otherDed.toDoubleOrNull() ?: 0.0, adv.toDoubleOrNull() ?: 0.0, note, true, LocalDate.now().toString(), method)
                        editingPayroll = null
                    }) { Text("Mark Paid") }
                }
            },
            dismissButton = { TextButton(onClick = { editingPayroll = null }) { Text("Cancel") } }
        )
    }

    slipToView?.let { p ->
        val u = data.users.firstOrNull { it.id == p.userId }
        AlertDialog(
            onDismissRequest = { slipToView = null },
            title = { Text("Payslip ${p.slipNo ?: ""}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("${data.instituteName} · ${p.period}", fontWeight = FontWeight.Bold)
                    HorizontalDivider()
                    Text("Employee: ${u?.fullName ?: ""}")
                    Text("Basic: ${DomainRules.formatMoney(p.basic, data.currency)}")
                    Text("Allowance: ${DomainRules.formatMoney(p.allowance, data.currency)}")
                    Text("Bonus + OT: ${DomainRules.formatMoney(p.bonus + p.overtime, data.currency)}")
                    Text("EPF (8%): -${DomainRules.formatMoney(p.epf, data.currency)}")
                    Text("Other Deductions + Adv: -${DomainRules.formatMoney(p.otherDeduction + p.advance, data.currency)}")
                    HorizontalDivider()
                    Text("Net Pay: ${DomainRules.formatMoney(p.net, data.currency)}", style = MaterialTheme.typography.titleLarge, color = EmeraldSuccess)
                    Text("Paid on ${p.paidOn ?: ""} via ${p.method ?: ""}", style = MaterialTheme.typography.labelMedium)
                }
            },
            confirmButton = { Button(onClick = { slipToView = null }) { Text("Close") } }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StaffDirectoryScreen(vm: MainViewModel, user: UserEntity, data: AppDataSnapshot, teachersOnly: Boolean) {
    var query by remember { mutableStateOf("") }
    var showAdd by remember { mutableStateOf(false) }
    var resetTarget by remember { mutableStateOf<UserEntity?>(null) }
    val branchScope = vm.effectiveBranchId()
    val canManage = DomainRules.can(user.role, "manage_users")

    val list = remember(data.users, teachersOnly, query, branchScope) {
        data.users.filter { u ->
            (if (teachersOnly) u.role == "teacher" else u.role != "teacher" && u.role != "student") &&
                (branchScope == null || u.branchId == branchScope) &&
                (query.isBlank() || u.fullName.contains(query, true) || u.email.contains(query, true))
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item { SearchField(value = query, onValueChange = { query = it }, placeholder = "Search name or email") }
            items(list, key = { it.id }) { u ->
                val br = data.branches.firstOrNull { it.id == u.branchId }
                AppCard(modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        InitialAvatar(u.fullName)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(u.fullName, style = MaterialTheme.typography.titleMedium)
                            Text("${DomainRules.ROLE_LABELS[u.role] ?: u.role} · ${br?.name ?: "All branches"}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            Text("${u.email} ${if (u.phone.isNotBlank()) "· ${u.phone}" else ""}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        StatusPill(u.status, statusColorFor(u.status))
                    }
                    if (canManage && u.id != user.id) {
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { vm.toggleUserStatus(u.id) }) {
                                Text(if (u.status == "active") "Deactivate" else "Activate")
                            }
                            OutlinedButton(onClick = { resetTarget = u }) { Text("Reset Password") }
                        }
                    }
                }
            }
        }

        if (canManage) {
            ExtendedFloatingActionButton(
                onClick = { showAdd = true },
                icon = { Icon(Icons.Filled.PersonAdd, contentDescription = null) },
                text = { Text(if (teachersOnly) "Add Teacher" else "Add Staff") },
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
            )
        }
    }

    if (showAdd) {
        val assignable = if (teachersOnly) listOf("teacher") else listOf("branch_manager", "administrator", "receptionist", "finance", "other")
        var role by remember { mutableStateOf(assignable.first()) }
        val activeBranches = data.branches.filter { it.status == "active" }
        var branchId by remember { mutableStateOf(user.branchId ?: activeBranches.firstOrNull()?.id) }
        var name by remember { mutableStateOf("") }
        var email by remember { mutableStateOf("") }
        var phone by remember { mutableStateOf("") }
        var job by remember { mutableStateOf("") }
        var pw by remember { mutableStateOf("Institute@123") }
        var err by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text(if (teachersOnly) "Add Teacher" else "Add Staff Member") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (err != null) Text(err!!, color = CrimsonAlert)
                    if (!teachersOnly) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            assignable.forEach { r ->
                                FilterChip(selected = role == r, onClick = { role = r }, label = { Text(DomainRules.ROLE_LABELS[r] ?: r) })
                            }
                        }
                    }
                    if (DomainRules.isOwner(user.role)) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            activeBranches.forEach { b ->
                                FilterChip(selected = branchId == b.id, onClick = { branchId = b.id }, label = { Text(b.code) })
                            }
                        }
                    }
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Full name *") }, singleLine = true)
                    OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email *") }, singleLine = true)
                    OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Phone") }, singleLine = true)
                    OutlinedTextField(value = job, onValueChange = { job = it }, label = { Text("Job title") }, singleLine = true)
                    OutlinedTextField(value = pw, onValueChange = { pw = it }, label = { Text("Temp password (8+ chars) *") }, singleLine = true)
                }
            },
            confirmButton = {
                Button(onClick = {
                    vm.createStaffUser(role, branchId, name, email, phone, "", job, pw) { res ->
                        res.onSuccess { showAdd = false; vm.showToast("Account created for $name.") }
                            .onFailure { err = it.message }
                    }
                }) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text("Cancel") } }
        )
    }

    resetTarget?.let { t ->
        var newPw by remember { mutableStateOf("Institute@123") }
        AlertDialog(
            onDismissRequest = { resetTarget = null },
            title = { Text("Reset Password: ${t.fullName}") },
            text = { OutlinedTextField(value = newPw, onValueChange = { newPw = it }, label = { Text("New temporary password") }, singleLine = true) },
            confirmButton = { Button(onClick = { vm.resetUserPassword(t.id, newPw); resetTarget = null }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { resetTarget = null }) { Text("Cancel") } }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LeavesScreen(vm: MainViewModel, user: UserEntity, data: AppDataSnapshot) {
    val canApprove = DomainRules.can(user.role, "approve_leave")
    var tab by remember { mutableStateOf(0) }
    val branchScope = vm.effectiveBranchId()

    Column(modifier = Modifier.fillMaxSize()) {
        if (canApprove) {
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("My Leave") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Team Requests") })
            }
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (tab == 0 || !canApprove) {
                item {
                    var type by remember { mutableStateOf("casual") }
                    var from by remember { mutableStateOf(LocalDate.now().plusDays(1).toString()) }
                    var to by remember { mutableStateOf(LocalDate.now().plusDays(1).toString()) }
                    var reason by remember { mutableStateOf("") }
                    var err by remember { mutableStateOf<String?>(null) }

                    AppCard(modifier = Modifier.fillMaxWidth()) {
                        Text("Request Leave", style = MaterialTheme.typography.titleMedium)
                        if (err != null) Text(err!!, color = CrimsonAlert)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            DomainRules.LEAVE_TYPES.forEach { (k, lbl) ->
                                FilterChip(selected = type == k, onClick = { type = k }, label = { Text(lbl) })
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(value = from, onValueChange = { from = it }, label = { Text("From") }, singleLine = true, modifier = Modifier.weight(1f))
                            OutlinedTextField(value = to, onValueChange = { to = it }, label = { Text("To") }, singleLine = true, modifier = Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(6.dp))
                        OutlinedTextField(value = reason, onValueChange = { reason = it }, label = { Text("Reason") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = {
                            vm.requestLeave(type, from, to, reason) { res ->
                                res.onSuccess { reason = ""; err = null; vm.showToast("Leave request submitted.") }
                                    .onFailure { err = it.message }
                            }
                        }, modifier = Modifier.fillMaxWidth()) { Text("Submit Request") }
                    }
                }
                val mine = data.leaves.filter { it.userId == user.id }
                items(mine, key = { it.id }) { lr ->
                    AppCard(modifier = Modifier.fillMaxWidth()) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("${lr.fromDate} → ${lr.toDate} (${lr.leaveType})", style = MaterialTheme.typography.titleMedium)
                                Text(lr.reason, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                StatusPill(lr.status, statusColorFor(lr.status))
                                if (lr.status == "pending") {
                                    TextButton(onClick = { vm.cancelLeave(lr.id) }) { Text("Cancel") }
                                }
                            }
                        }
                    }
                }
            } else {
                val team = data.leaves.filter { lr ->
                    lr.userId != user.id && (branchScope == null || lr.branchId == branchScope)
                }
                items(team, key = { it.id }) { lr ->
                    val requester = data.users.firstOrNull { it.id == lr.userId }
                    AppCard(modifier = Modifier.fillMaxWidth()) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(requester?.fullName ?: "Staff", style = MaterialTheme.typography.titleMedium)
                                Text("${lr.fromDate} → ${lr.toDate} · ${lr.leaveType}", style = MaterialTheme.typography.labelMedium)
                                Text(lr.reason, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            StatusPill(lr.status, statusColorFor(lr.status))
                        }
                        if (lr.status == "pending") {
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { vm.decideLeave(lr.id, false, "") }) { Text("Reject") }
                                Button(onClick = { vm.decideLeave(lr.id, true, "") }) { Text("Approve") }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BranchesScreen(vm: MainViewModel, data: AppDataSnapshot) {
    var showAdd by remember { mutableStateOf(false) }
    var editBranch by remember { mutableStateOf<BranchEntity?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(data.branches, key = { it.id }) { b ->
                val stuCount = data.students.count { it.branchId == b.id }
                val staffCount = data.users.count { it.branchId == b.id && it.role != "student" }
                val batchCount = data.batches.count { it.branchId == b.id && it.status == "active" }
                AppCard(modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(b.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text("Code: ${b.code} · ${b.phone}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            if (b.address.isNotBlank()) Text(b.address, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("$stuCount students · $staffCount staff · $batchCount active batches", style = MaterialTheme.typography.labelMedium)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            StatusPill(b.status, statusColorFor(b.status))
                            TextButton(onClick = { editBranch = b }) { Text("Edit") }
                        }
                    }
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = { showAdd = true },
            icon = { Icon(Icons.Filled.AddBusiness, contentDescription = null) },
            text = { Text("Add Branch") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
        )
    }

    if (showAdd) {
        var code by remember { mutableStateOf("") }
        var name by remember { mutableStateOf("") }
        var addr by remember { mutableStateOf("") }
        var phone by remember { mutableStateOf("") }
        var err by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text("Add Branch") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (err != null) Text(err!!, color = CrimsonAlert)
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Branch name *") }, singleLine = true)
                    OutlinedTextField(value = code, onValueChange = { code = it }, label = { Text("Code (2–6 chars) *") }, singleLine = true)
                    OutlinedTextField(value = addr, onValueChange = { addr = it }, label = { Text("Address") }, singleLine = true)
                    OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Phone") }, singleLine = true)
                }
            },
            confirmButton = {
                Button(onClick = {
                    vm.addBranch(code, name, addr, phone) { res ->
                        res.onSuccess { showAdd = false; vm.showToast("Branch added.") }
                            .onFailure { err = it.message }
                    }
                }) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text("Cancel") } }
        )
    }

    editBranch?.let { b ->
        var name by remember { mutableStateOf(b.name) }
        var addr by remember { mutableStateOf(b.address) }
        var phone by remember { mutableStateOf(b.phone) }
        var active by remember { mutableStateOf(b.status == "active") }
        AlertDialog(
            onDismissRequest = { editBranch = null },
            title = { Text("Edit Branch (${b.code})") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true)
                    OutlinedTextField(value = addr, onValueChange = { addr = it }, label = { Text("Address") }, singleLine = true)
                    OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Phone") }, singleLine = true)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = active, onCheckedChange = { active = it })
                        Text("Active branch")
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    vm.updateBranch(b.copy(name = name.trim(), address = addr.trim(), phone = phone.trim(), status = if (active) "active" else "inactive"))
                    editBranch = null
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { editBranch = null }) { Text("Cancel") } }
        )
    }
}

@Composable
fun SettingsScreen(vm: MainViewModel, user: UserEntity, data: AppDataSnapshot) {
    var tab by remember { mutableStateOf(0) }
    val isOwner = DomainRules.isOwner(user.role)
    val isSuper = user.role == "super_admin"

    Column(modifier = Modifier.fillMaxSize()) {
        if (isOwner) {
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("General") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Grading") })
                if (isSuper) Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text("Role Access") })
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (!isOwner) {
                val br = data.branches.firstOrNull { it.id == user.branchId }
                item {
                    if (br == null) EmptyStateCard("No branch assigned", "Your account has no branch assigned.")
                    else {
                        var addr by remember { mutableStateOf(br.address) }
                        var phone by remember { mutableStateOf(br.phone) }
                        AppCard(modifier = Modifier.fillMaxWidth()) {
                            Text("My Branch: ${br.name} (${br.code})", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Phone") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(value = addr, onValueChange = { addr = it }, label = { Text("Address") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                            if (user.role == "branch_manager") {
                                Spacer(Modifier.height(10.dp))
                                Button(onClick = { vm.updateBranch(br.copy(phone = phone.trim(), address = addr.trim())) }) { Text("Save Branch Details") }
                            }
                        }
                    }
                }
            } else if (tab == 0) {
                item {
                    var instName by remember { mutableStateOf(data.instituteName) }
                    var currency by remember { mutableStateOf(data.currency) }
                    var payInfo by remember { mutableStateOf(data.paymentInfo) }
                    AppCard(modifier = Modifier.fillMaxWidth()) {
                        Text("General Settings", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(value = instName, onValueChange = { instName = it }, label = { Text("Institute name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(value = currency, onValueChange = { currency = it }, label = { Text("Currency (e.g. LKR, USD)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(value = payInfo, onValueChange = { payInfo = it }, label = { Text("Payment instructions for students") }, minLines = 3, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(10.dp))
                        Button(onClick = {
                            vm.saveSettings(mapOf("institute_name" to instName.trim(), "currency" to currency.trim().uppercase(), "payment_info" to payInfo.trim()))
                        }) { Text("Save General Settings") }
                    }
                }
            } else if (tab == 1) {
                item {
                    val map = data.settings.associate { it.k to it.v }
                    var ga by remember { mutableStateOf(map["grade_a"] ?: "75") }
                    var gb by remember { mutableStateOf(map["grade_b"] ?: "65") }
                    var gc by remember { mutableStateOf(map["grade_c"] ?: "50") }
                    var gs by remember { mutableStateOf(map["grade_s"] ?: "35") }
                    AppCard(modifier = Modifier.fillMaxWidth()) {
                        Text("Grade Thresholds (%)", style = MaterialTheme.typography.titleMedium)
                        Text("Below S is W. C is the pass mark for annual-intake final exams.", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(value = ga, onValueChange = { ga = it }, label = { Text("A") }, singleLine = true, modifier = Modifier.weight(1f))
                            OutlinedTextField(value = gb, onValueChange = { gb = it }, label = { Text("B") }, singleLine = true, modifier = Modifier.weight(1f))
                            OutlinedTextField(value = gc, onValueChange = { gc = it }, label = { Text("C (Pass)") }, singleLine = true, modifier = Modifier.weight(1f))
                            OutlinedTextField(value = gs, onValueChange = { gs = it }, label = { Text("S") }, singleLine = true, modifier = Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(10.dp))
                        Button(onClick = {
                            vm.saveSettings(mapOf("grade_a" to ga, "grade_b" to gb, "grade_c" to gc, "grade_s" to gs))
                        }) { Text("Save Grading") }
                    }
                }
            } else if (tab == 2 && isSuper) {
                val editableRoles = listOf("admin", "branch_manager", "administrator", "receptionist", "teacher", "finance", "other")
                val allKeys = listOf("dashboard", "students", "courses", "branches", "schedule", "attendance", "exams", "materials", "staff", "teachers", "leaves", "payments", "salaries", "reports", "settings", "activity", "profile")
                items(editableRoles) { role ->
                    val current = data.roleMenus.filter { it.role == role }.map { it.menuKey }.toSet()
                    AppCard(modifier = Modifier.fillMaxWidth()) {
                        Text(DomainRules.ROLE_LABELS[role] ?: role, style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(6.dp))
                        allKeys.chunked(3).forEach { rowKeys ->
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                rowKeys.forEach { k ->
                                    val locked = k == "dashboard" || k == "profile"
                                    val checked = k in current || locked
                                    FilterChip(
                                        selected = checked,
                                        onClick = {
                                            if (!locked) {
                                                val next = if (k in current) current - k else current + k
                                                vm.saveRoleMenu(role, (next + setOf("dashboard", "profile")).toList())
                                            }
                                        },
                                        label = { Text(k) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ActivityLogScreen(data: AppDataSnapshot) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(data.activityLogs, key = { it.id }) { log ->
            AppCard(modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(log.action, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Text(log.createdAt, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("${log.userName ?: "System"} · ${log.details}", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
fun ProfileScreen(vm: MainViewModel, user: UserEntity, data: AppDataSnapshot) {
    val branch = data.branches.firstOrNull { it.id == user.branchId }
    var currentPw by remember { mutableStateOf("") }
    var newPw by remember { mutableStateOf("") }
    var confirmPw by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            AppCard(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    InitialAvatar(user.fullName, sizeDp = 56)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(user.fullName, style = MaterialTheme.typography.headlineSmall)
                        Text(DomainRules.ROLE_LABELS[user.role] ?: user.role, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        Text("${user.email} · ${branch?.name ?: "All branches"}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = { vm.signOut() }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Logout, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Sign out")
                }
            }
        }
        item {
            AppCard(modifier = Modifier.fillMaxWidth()) {
                Text("Change Password", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                if (err != null) Text(err!!, color = CrimsonAlert)
                OutlinedTextField(value = currentPw, onValueChange = { currentPw = it }, label = { Text("Current password") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(value = newPw, onValueChange = { newPw = it }, label = { Text("New password (8+ chars)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(value = confirmPw, onValueChange = { confirmPw = it }, label = { Text("Confirm new password") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(10.dp))
                Button(onClick = {
                    if (newPw != confirmPw) err = "Passwords do not match."
                    else vm.changePassword(currentPw, newPw) { res ->
                        res.onSuccess { currentPw = ""; newPw = ""; confirmPw = ""; err = null; vm.showToast("Password updated.") }
                            .onFailure { err = it.message }
                    }
                }, modifier = Modifier.fillMaxWidth()) { Text("Update Password") }
            }
        }
    }
}

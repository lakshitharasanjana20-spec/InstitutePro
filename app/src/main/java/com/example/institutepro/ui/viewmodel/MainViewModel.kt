package com.example.institutepro.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.institutepro.InstituteProApp
import com.example.institutepro.data.db.*
import com.example.institutepro.data.repo.AttendanceAlerts
import com.example.institutepro.data.repo.DomainRules
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class AppDataSnapshot(
    val initialized: Boolean = false,
    val branches: List<BranchEntity> = emptyList(),
    val users: List<UserEntity> = emptyList(),
    val roleMenus: List<RoleMenuEntity> = emptyList(),
    val courseTypes: List<CourseTypeEntity> = emptyList(),
    val courses: List<CourseEntity> = emptyList(),
    val batches: List<BatchEntity> = emptyList(),
    val students: List<StudentEntity> = emptyList(),
    val enrolments: List<EnrolmentEntity> = emptyList(),
    val attendance: List<AttendanceEntity> = emptyList(),
    val staffAttendance: List<StaffAttendanceEntity> = emptyList(),
    val leaves: List<LeaveRequestEntity> = emptyList(),
    val substitutions: List<SubstitutionEntity> = emptyList(),
    val exams: List<ExamEntity> = emptyList(),
    val examMarks: List<ExamMarkEntity> = emptyList(),
    val feeAccounts: List<FeeAccountEntity> = emptyList(),
    val installments: List<InstallmentEntity> = emptyList(),
    val payments: List<PaymentEntity> = emptyList(),
    val salaryStructures: List<SalaryStructureEntity> = emptyList(),
    val payrolls: List<PayrollEntity> = emptyList(),
    val expenses: List<ExpenseEntity> = emptyList(),
    val materials: List<MaterialEntity> = emptyList(),
    val quizQuestions: List<QuizQuestionEntity> = emptyList(),
    val quizAttempts: List<QuizAttemptEntity> = emptyList(),
    val submissions: List<AssignmentSubmissionEntity> = emptyList(),
    val activityLogs: List<ActivityLogEntity> = emptyList(),
    val settings: List<SettingEntity> = emptyList()
) {
    val currency: String get() = settings.firstOrNull { it.k == "currency" }?.v ?: "LKR"
    val instituteName: String get() = settings.firstOrNull { it.k == "institute_name" }?.v ?: "InstitutePro"
    val paymentInfo: String get() = settings.firstOrNull { it.k == "payment_info" }?.v ?: ""
}

class MainViewModel(application: Application) : AndroidViewModel(application) {
    val repo = (application as InstituteProApp).repository

    private val _currentUser = MutableStateFlow<UserEntity?>(null)
    val currentUser: StateFlow<UserEntity?> = _currentUser.asStateFlow()

    private val _branchFilter = MutableStateFlow<Int?>(null)
    val branchFilter: StateFlow<Int?> = _branchFilter.asStateFlow()

    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

    private val _attendanceAlerts = MutableStateFlow<AttendanceAlerts?>(null)
    val attendanceAlerts: StateFlow<AttendanceAlerts?> = _attendanceAlerts.asStateFlow()

    private val _latestReceipt = MutableStateFlow<PaymentEntity?>(null)
    val latestReceipt: StateFlow<PaymentEntity?> = _latestReceipt.asStateFlow()

    private val _initialized = MutableStateFlow(false)

    private val baseFlowA = combine(
        repo.branches, repo.users, repo.roleMenus, repo.courseTypes, repo.courses
    ) { br, u, rm, ct, c -> Tuple5(br, u, rm, ct, c) }

    private val baseFlowB = combine(
        repo.batches, repo.students, repo.enrolments, repo.attendance, repo.staffAttendance
    ) { b, s, e, a, sa -> Tuple5(b, s, e, a, sa) }

    private val baseFlowC = combine(
        repo.leaves, repo.substitutions, repo.exams, repo.examMarks, repo.feeAccounts
    ) { l, sub, ex, em, fa -> Tuple5(l, sub, ex, em, fa) }

    private val baseFlowD = combine(
        repo.installments, repo.payments, repo.salaryStructures, repo.payrolls, repo.expenses
    ) { inst, pay, ss, pr, exp -> Tuple5(inst, pay, ss, pr, exp) }

    private val baseFlowE = combine(
        repo.materials, repo.quizQuestions, repo.quizAttempts, repo.submissions, repo.activityLogs
    ) { mat, qq, qa, sub, logs -> Tuple5(mat, qq, qa, sub, logs) }

    val dataState: StateFlow<AppDataSnapshot> = combine(
        baseFlowA, baseFlowB, baseFlowC, baseFlowD, combine(baseFlowE, repo.settings, _initialized) { e, set, init -> Triple(e, set, init) }
    ) { a, b, c, d, ePair ->
        val (e, settings, init) = ePair
        AppDataSnapshot(
            initialized = init,
            branches = a.v1, users = a.v2, roleMenus = a.v3, courseTypes = a.v4, courses = a.v5,
            batches = b.v1, students = b.v2, enrolments = b.v3, attendance = b.v4, staffAttendance = b.v5,
            leaves = c.v1, substitutions = c.v2, exams = c.v3, examMarks = c.v4, feeAccounts = c.v5,
            installments = d.v1, payments = d.v2, salaryStructures = d.v3, payrolls = d.v4, expenses = d.v5,
            materials = e.v1, quizQuestions = e.v2, quizAttempts = e.v3, submissions = e.v4, activityLogs = e.v5,
            settings = settings
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppDataSnapshot())

    init {
        viewModelScope.launch {
            repo.ensureInitialized()
            _initialized.value = true
        }
    }

    fun clearToast() { _toastMessage.value = null }
    fun showToast(msg: String) { _toastMessage.value = msg }
    fun clearAttendanceAlerts() { _attendanceAlerts.value = null }
    fun clearLatestReceipt() { _latestReceipt.value = null }
    fun setBranchFilter(branchId: Int?) { _branchFilter.value = branchId }

    fun effectiveBranchId(): Int? {
        val u = _currentUser.value ?: return null
        return if (DomainRules.isOwner(u.role)) _branchFilter.value else u.branchId
    }

    fun allowedMenusForCurrentUser(): List<String> {
        val u = _currentUser.value ?: return emptyList()
        return dataState.value.roleMenus.filter { it.role == u.role }.map { it.menuKey }
    }

    fun signIn(email: String, password: String, onError: (String) -> Unit) {
        viewModelScope.launch {
            val res = repo.authenticate(email, password)
            res.onSuccess { user ->
                _branchFilter.value = null
                _currentUser.value = user
            }.onFailure { err ->
                onError(err.message ?: "Sign in failed.")
            }
        }
    }

    fun quickDemoSignIn(role: String) {
        viewModelScope.launch {
            repo.quickDemoLogin(role)?.let {
                _branchFilter.value = null
                _currentUser.value = it
            }
        }
    }

    fun signOut() {
        val u = _currentUser.value
        viewModelScope.launch {
            if (u != null) repo.log(u, "logout", "Signed out")
            _currentUser.value = null
            _branchFilter.value = null
        }
    }

    fun changePassword(currentPw: String, newPw: String, onDone: (Result<Unit>) -> Unit) {
        val u = _currentUser.value ?: return
        viewModelScope.launch {
            val res = repo.changePassword(u.id, currentPw, newPw)
            res.onSuccess { updated ->
                _currentUser.value = updated
                onDone(Result.success(Unit))
            }.onFailure { onDone(Result.failure(it)) }
        }
    }

    fun registerStudent(
        branchId: Int, batchId: Int, firstName: String, lastName: String, gender: String, dob: String,
        phone: String, email: String?, address: String, nic: String?,
        guardianName: String?, guardianRelation: String?, guardianPhone: String?,
        fingerprintId: String?, createPortalLogin: Boolean,
        onResult: (Result<Pair<StudentEntity, String?>>) -> Unit
    ) {
        val actor = _currentUser.value ?: return
        viewModelScope.launch {
            val res = repo.registerStudent(
                actor, branchId, batchId, firstName, lastName, gender, dob, phone, email, address,
                nic, guardianName, guardianRelation, guardianPhone, fingerprintId, createPortalLogin
            )
            onResult(res)
        }
    }

    fun toggleStudentStatus(studentId: Int) {
        val actor = _currentUser.value ?: return
        viewModelScope.launch {
            repo.toggleStudentStatus(actor, studentId)
            _toastMessage.value = "Student status updated."
        }
    }

    fun updateStudent(student: StudentEntity, onDone: () -> Unit) {
        val actor = _currentUser.value ?: return
        viewModelScope.launch {
            repo.updateStudentDetails(actor, student)
            _toastMessage.value = "Student profile updated."
            onDone()
        }
    }

    fun createCourse(typeId: Int, code: String, name: String, onDone: (Result<Unit>) -> Unit) {
        val actor = _currentUser.value ?: return
        viewModelScope.launch {
            val res = repo.createCourse(actor, typeId, code, name)
            onDone(res)
        }
    }

    fun createBatch(
        branchId: Int, courseId: Int, year: Int, name: String, startDate: String,
        durationMonths: Int, startTime: String, endTime: String, classDays: List<String>,
        fee: Double, feePlan: String, maxStudents: Int?, teacherId: Int?, room: String,
        onDone: (Result<String>) -> Unit
    ) {
        val actor = _currentUser.value ?: return
        viewModelScope.launch {
            val res = repo.createBatch(
                actor, branchId, courseId, year, name, startDate, durationMonths,
                startTime, endTime, classDays, fee, feePlan, maxStudents, teacherId, room
            )
            onDone(res)
        }
    }

    fun markBatchAttendance(batchId: Int, date: String, statuses: Map<Int, String>) {
        val actor = _currentUser.value ?: return
        viewModelScope.launch {
            val res = repo.markBatchAttendance(actor, batchId, date, statuses)
            res.onSuccess { alerts ->
                _toastMessage.value = "Attendance saved for ${statuses.size} student(s)."
                if (alerts.reactivated.isNotEmpty() || alerts.markedInactive.isNotEmpty()) {
                    _attendanceAlerts.value = alerts
                }
            }.onFailure { _toastMessage.value = it.message ?: "Error saving attendance." }
        }
    }

    fun importFingerprintPunches(branchId: Int, rawCsv: String) {
        val actor = _currentUser.value ?: return
        viewModelScope.launch {
            val res = repo.importFingerprintPunches(actor, branchId, rawCsv)
            res.onSuccess { (msg, alerts) ->
                _toastMessage.value = msg
                if (alerts.reactivated.isNotEmpty() || alerts.markedInactive.isNotEmpty()) {
                    _attendanceAlerts.value = alerts
                }
            }.onFailure { _toastMessage.value = it.message ?: "Import failed." }
        }
    }

    fun markStaffAttendance(date: String, statuses: Map<Int, String>) {
        val actor = _currentUser.value ?: return
        viewModelScope.launch {
            repo.markStaffAttendance(actor, date, statuses)
            _toastMessage.value = "Staff attendance saved."
        }
    }

    fun scheduleExam(batchId: Int, title: String, type: String, date: String, time: String?, maxMarks: Double, onDone: (Result<Unit>) -> Unit) {
        val actor = _currentUser.value ?: return
        viewModelScope.launch {
            val res = repo.scheduleExam(actor, batchId, title, type, date, time, maxMarks)
            onDone(res)
        }
    }

    fun saveExamMarks(examId: Int, entries: Map<Int, Triple<Double?, Boolean, String?>>) {
        val actor = _currentUser.value ?: return
        viewModelScope.launch {
            val res = repo.saveExamMarks(actor, examId, entries)
            res.onSuccess { _toastMessage.value = "Saved marks for $it student(s)." }
                .onFailure { _toastMessage.value = it.message ?: "Could not save marks." }
        }
    }

    fun recordPayment(accountId: Int, amount: Double, method: String, ref: String, paidOn: String, note: String) {
        val actor = _currentUser.value ?: return
        viewModelScope.launch {
            val res = repo.financeLearning.recordPayment(actor, accountId, amount, method, ref, paidOn, note)
            res.onSuccess { payment ->
                _latestReceipt.value = payment
                _toastMessage.value = "Receipt ${payment.receiptNo} issued."
            }.onFailure { _toastMessage.value = it.message ?: "Payment failed." }
        }
    }

    fun applyScholarship(accountId: Int, amount: Double) {
        val actor = _currentUser.value ?: return
        viewModelScope.launch {
            val res = repo.financeLearning.applyScholarship(actor, accountId, amount)
            res.onSuccess { _toastMessage.value = "Scholarship applied." }
                .onFailure { _toastMessage.value = it.message ?: "Could not apply scholarship." }
        }
    }

    fun voidPayment(paymentId: Int, reason: String) {
        val actor = _currentUser.value ?: return
        viewModelScope.launch {
            val res = repo.financeLearning.voidPayment(actor, paymentId, reason)
            res.onSuccess { _toastMessage.value = "Payment cancelled and marked VOID." }
                .onFailure { _toastMessage.value = it.message ?: "Could not void payment." }
        }
    }

    fun saveSalarySetup(userId: Int, basic: Double, allowance: Double, epf: Boolean) {
        val actor = _currentUser.value ?: return
        viewModelScope.launch {
            val res = repo.financeLearning.saveSalarySetup(actor, userId, basic, allowance, epf)
            res.onSuccess { _toastMessage.value = "Salary structure saved." }
                .onFailure { _toastMessage.value = it.message ?: "Error saving salary setup." }
        }
    }

    fun generateMonthlyPayroll(period: String) {
        val actor = _currentUser.value ?: return
        viewModelScope.launch {
            val n = repo.financeLearning.generateMonthlyPayroll(actor, period, effectiveBranchId())
            _toastMessage.value = if (n > 0) "Generated payroll for $n staff." else "No new payroll rows to generate for $period."
        }
    }

    fun updateOrPayPayroll(payrollId: Int, bonus: Double, ot: Double, otherDed: Double, adv: Double, note: String, markPaid: Boolean, paidOn: String, method: String) {
        val actor = _currentUser.value ?: return
        viewModelScope.launch {
            val res = repo.financeLearning.updateOrPayPayroll(actor, payrollId, bonus, ot, otherDed, adv, note, markPaid, paidOn, method)
            res.onSuccess { _toastMessage.value = if (markPaid) "Payroll marked paid & payslip ready." else "Payroll draft saved." }
                .onFailure { _toastMessage.value = it.message ?: "Could not update payroll." }
        }
    }

    fun addExpense(branchId: Int, category: String, amount: Double, spentOn: String, description: String) {
        val actor = _currentUser.value ?: return
        viewModelScope.launch {
            val res = repo.financeLearning.addExpense(actor, branchId, category, amount, spentOn, description)
            res.onSuccess { _toastMessage.value = "Expense recorded." }
                .onFailure { _toastMessage.value = it.message ?: "Error recording expense." }
        }
    }

    fun deleteExpense(id: Int) {
        val actor = _currentUser.value ?: return
        viewModelScope.launch {
            repo.financeLearning.deleteExpense(actor, id)
            _toastMessage.value = "Expense deleted."
        }
    }

    fun requestLeave(type: String, from: String, to: String, reason: String, onDone: (Result<Unit>) -> Unit) {
        val actor = _currentUser.value ?: return
        viewModelScope.launch {
            val res = repo.financeLearning.requestLeave(actor, type, from, to, reason)
            onDone(res)
        }
    }

    fun decideLeave(leaveId: Int, approve: Boolean, note: String) {
        val actor = _currentUser.value ?: return
        viewModelScope.launch {
            repo.financeLearning.decideLeave(actor, leaveId, approve, note)
            _toastMessage.value = if (approve) "Leave approved." else "Leave rejected."
        }
    }

    fun cancelLeave(leaveId: Int) {
        val actor = _currentUser.value ?: return
        viewModelScope.launch {
            repo.financeLearning.cancelLeave(actor, leaveId)
            _toastMessage.value = "Leave request cancelled."
        }
    }

    fun assignSubstitute(batchId: Int, date: String, subId: Int, leaveId: Int?, note: String) {
        val actor = _currentUser.value ?: return
        viewModelScope.launch {
            val res = repo.financeLearning.assignSubstitute(actor, batchId, date, subId, leaveId, note)
            res.onSuccess { _toastMessage.value = "Substitute teacher assigned." }
                .onFailure { _toastMessage.value = it.message ?: "Could not assign substitute." }
        }
    }

    fun removeSubstitute(subId: Int) {
        val actor = _currentUser.value ?: return
        viewModelScope.launch {
            repo.financeLearning.removeSubstitute(actor, subId)
            _toastMessage.value = "Substitute removed."
        }
    }

    fun addBranch(code: String, name: String, address: String, phone: String, onDone: (Result<Unit>) -> Unit) {
        val actor = _currentUser.value ?: return
        viewModelScope.launch {
            onDone(repo.financeLearning.addBranch(actor, code, name, address, phone))
        }
    }

    fun updateBranch(branch: BranchEntity) {
        val actor = _currentUser.value ?: return
        viewModelScope.launch {
            repo.financeLearning.updateBranch(actor, branch)
            _toastMessage.value = "Branch updated."
        }
    }

    fun createStaffUser(role: String, branchId: Int?, name: String, email: String, phone: String, nic: String, job: String, pw: String, onDone: (Result<Unit>) -> Unit) {
        val actor = _currentUser.value ?: return
        viewModelScope.launch {
            onDone(repo.financeLearning.createStaffUser(actor, role, branchId, name, email, phone, nic, job, pw))
        }
    }

    fun toggleUserStatus(userId: Int) {
        val actor = _currentUser.value ?: return
        viewModelScope.launch {
            repo.financeLearning.toggleUserStatus(actor, userId)
            _toastMessage.value = "User status updated."
        }
    }

    fun resetUserPassword(userId: Int, newPw: String) {
        val actor = _currentUser.value ?: return
        viewModelScope.launch {
            val res = repo.financeLearning.resetUserPassword(actor, userId, newPw)
            res.onSuccess { _toastMessage.value = "Password reset successfully." }
                .onFailure { _toastMessage.value = it.message ?: "Error resetting password." }
        }
    }

    fun addMaterial(material: MaterialEntity, onCreated: (Long) -> Unit = {}) {
        val actor = _currentUser.value ?: return
        viewModelScope.launch {
            val id = repo.financeLearning.addMaterial(actor, material)
            _toastMessage.value = "Item added to Learning Hub."
            onCreated(id)
        }
    }

    fun toggleMaterialPublish(materialId: Int) {
        val actor = _currentUser.value ?: return
        viewModelScope.launch {
            repo.financeLearning.toggleMaterialPublish(actor, materialId)
        }
    }

    fun deleteMaterial(materialId: Int) {
        val actor = _currentUser.value ?: return
        viewModelScope.launch {
            repo.financeLearning.deleteMaterial(actor, materialId)
            _toastMessage.value = "Deleted from Learning Hub."
        }
    }

    fun toggleBatchPortal(batchId: Int, regen: Boolean = false) {
        val actor = _currentUser.value ?: return
        viewModelScope.launch {
            repo.financeLearning.toggleBatchPortal(actor, batchId, regen)
            _toastMessage.value = if (regen) "New student link generated." else "Student portal visibility updated."
        }
    }

    fun addQuizQuestion(materialId: Int, qText: String, a: String, b: String, c: String, d: String, correct: String, exp: String?) {
        val actor = _currentUser.value ?: return
        viewModelScope.launch {
            val res = repo.financeLearning.addQuizQuestion(actor, materialId, qText, a, b, c, d, correct, exp)
            res.onSuccess { _toastMessage.value = "Question added." }
                .onFailure { _toastMessage.value = it.message ?: "Invalid question." }
        }
    }

    fun deleteQuizQuestion(qId: Int) {
        viewModelScope.launch { repo.financeLearning.deleteQuizQuestion(qId) }
    }

    fun submitQuizAttempt(materialId: Int, answers: Map<Int, String>, startedMillis: Long, timeLimitMin: Int?, onResult: (QuizAttemptEntity) -> Unit) {
        val sid = _currentUser.value?.studentId ?: return
        viewModelScope.launch {
            val att = repo.financeLearning.submitQuizAttempt(sid, materialId, answers, startedMillis, timeLimitMin)
            onResult(att)
        }
    }

    fun submitAssignment(materialId: Int, text: String, link: String?) {
        val sid = _currentUser.value?.studentId ?: return
        viewModelScope.launch {
            repo.financeLearning.submitAssignment(sid, materialId, text, link)
            _toastMessage.value = "Assignment submitted!"
        }
    }

    fun gradeSubmission(submission: AssignmentSubmissionEntity, marks: Double?, feedback: String?) {
        val actor = _currentUser.value ?: return
        viewModelScope.launch {
            repo.financeLearning.gradeSubmission(actor, submission, marks, feedback)
            _toastMessage.value = "Submission graded."
        }
    }

    fun saveSettings(entries: Map<String, String>) {
        val actor = _currentUser.value ?: return
        viewModelScope.launch {
            repo.financeLearning.saveSettings(actor, entries)
            _toastMessage.value = "Settings saved."
        }
    }

    fun saveRoleMenu(role: String, keys: List<String>) {
        val actor = _currentUser.value ?: return
        viewModelScope.launch {
            repo.financeLearning.saveRoleMenu(actor, role, keys)
            _toastMessage.value = "Access updated for ${DomainRules.ROLE_LABELS[role] ?: role}."
        }
    }
}

private data class Tuple5<A, B, C, D, E>(val v1: A, val v2: B, val v3: C, val v4: D, val v5: E)

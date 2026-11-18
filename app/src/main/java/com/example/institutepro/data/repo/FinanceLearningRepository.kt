package com.example.institutepro.data.repo

import com.example.institutepro.data.db.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.UUID

class FinanceLearningRepository(
    private val db: InstituteDatabase,
    private val core: InstituteRepository
) {
    suspend fun ensureAccountForEnrolment(enrolmentId: Int): FeeAccountEntity? {
        db.financeDao().getAccountByEnrolment(enrolmentId)?.let { return it }
        val allBatches = db.courseDao().getActiveBatches()
        // Lookup enrolment from any student's enrolments via batch search or direct check
        val enrol = findEnrolment(enrolmentId) ?: return null
        val batch = db.courseDao().getBatchById(enrol.batchId) ?: return null
        val dur = batch.durationMonths.coerceAtLeast(1)
        val fee = DomainRules.round2(batch.fee)
        val total = if (batch.feePlan == "monthly") DomainRules.round2(fee * dur) else fee
        val accId = db.financeDao().insertFeeAccount(
            FeeAccountEntity(enrolmentId = enrolmentId, studentId = enrol.studentId, batchId = batch.id, totalFee = total, plan = batch.feePlan)
        ).toInt()
        val start = runCatching { LocalDate.parse(batch.startDate) }.getOrElse { LocalDate.now() }
        when (batch.feePlan) {
            "installments_3" -> {
                val step = (dur / 3).coerceAtLeast(1)
                val each = DomainRules.round2(total / 3.0)
                for (k in 0 until 3) {
                    val amt = if (k < 2) each else DomainRules.round2(total - 2 * each)
                    db.financeDao().insertInstallment(
                        InstallmentEntity(accountId = accId, no = k + 1, label = "Installment ${k + 1}", dueDate = start.plusMonths((k * step).toLong()).toString(), amount = amt)
                    )
                }
            }
            "monthly" -> {
                for (k in 0 until dur) {
                    db.financeDao().insertInstallment(
                        InstallmentEntity(accountId = accId, no = k + 1, label = "Month ${k + 1}", dueDate = start.plusMonths(k.toLong()).toString(), amount = fee)
                    )
                }
            }
            else -> {
                db.financeDao().insertInstallment(
                    InstallmentEntity(accountId = accId, no = 1, label = "Full fee", dueDate = batch.startDate, amount = total)
                )
            }
        }
        return db.financeDao().getAccountById(accId)
    }

    private suspend fun findEnrolment(enrolmentId: Int): EnrolmentEntity? {
        for (b in db.courseDao().getActiveBatches()) {
            db.studentDao().getEnrolmentsByBatch(b.id).firstOrNull { it.id == enrolmentId }?.let { return it }
        }
        return null
    }

    suspend fun recordPayment(
        actor: UserEntity, accountId: Int, rawAmount: Double, method: String, reference: String, paidOn: String, note: String
    ): Result<PaymentEntity> {
        val amt = DomainRules.round2(rawAmount)
        if (amt <= 0) return Result.failure(IllegalArgumentException("Enter an amount greater than zero."))
        val acc = db.financeDao().getAccountById(accountId) ?: return Result.failure(IllegalArgumentException("Fee account not found."))
        val st = db.studentDao().getById(acc.studentId) ?: return Result.failure(IllegalArgumentException("Student not found."))
        val branch = db.branchDao().getById(st.branchId) ?: return Result.failure(IllegalArgumentException("Branch not found."))
        val installments = db.financeDao().getInstallmentsByAccount(accountId)
        val outstanding = DomainRules.round2(installments.sumOf { it.amount - it.paid })
        if (outstanding <= 0) return Result.failure(IllegalArgumentException("This account is already fully paid."))
        if (amt > outstanding + 0.01) return Result.failure(IllegalArgumentException("Amount exceeds outstanding balance ($outstanding)."))

        val prefix = "RC-${branch.code}-${LocalDate.now().format(DateTimeFormatter.ofPattern("yyMM"))}"
        val seq = db.financeDao().getMaxReceiptSeq(prefix) + 1
        val receiptNo = "$prefix-${seq.toString().padStart(4, '0')}"

        val payment = PaymentEntity(
            receiptPrefix = prefix, seqNo = seq, receiptNo = receiptNo,
            studentId = st.id, batchId = acc.batchId, accountId = acc.id, branchId = st.branchId,
            amount = amt, method = method, reference = reference.trim().takeIf { it.isNotEmpty() },
            paidOn = paidOn, note = note.trim().takeIf { it.isNotEmpty() }, receivedBy = actor.id
        )
        val pid = db.financeDao().insertPayment(payment).toInt()
        var left = amt
        for (inst in installments) {
            if (left <= 0.004) break
            val room = DomainRules.round2(inst.amount - inst.paid)
            if (room <= 0) continue
            val take = minOf(room, left)
            db.financeDao().updateInstallment(inst.copy(paid = DomainRules.round2(inst.paid + take)))
            db.financeDao().insertAllocation(PaymentAllocationEntity(paymentId = pid, installmentId = inst.id, amount = take))
            left = DomainRules.round2(left - take)
        }
        core.log(actor, "payment", "${st.studentCode} $receiptNo ($amt)")
        return Result.success(payment.copy(id = pid))
    }

    suspend fun applyScholarship(actor: UserEntity, accountId: Int, rawAmt: Double): Result<Unit> {
        val amt = DomainRules.round2(rawAmt)
        if (amt <= 0) return Result.failure(IllegalArgumentException("Enter a positive scholarship amount."))
        val acc = db.financeDao().getAccountById(accountId) ?: return Result.failure(IllegalArgumentException("Account not found."))
        if (acc.scholarship > 0) return Result.failure(IllegalArgumentException("A scholarship is already applied to this account."))
        val instDesc = db.financeDao().getInstallmentsByAccountDesc(accountId)
        val unpaid = DomainRules.round2(instDesc.sumOf { it.amount - it.paid })
        if (amt > unpaid + 0.01) return Result.failure(IllegalArgumentException("Scholarship exceeds unpaid balance."))
        var left = amt
        for (inst in instDesc) {
            if (left <= 0.004) break
            val room = DomainRules.round2(inst.amount - inst.paid)
            val cut = minOf(room, left)
            if (cut > 0) {
                db.financeDao().updateInstallment(inst.copy(amount = DomainRules.round2(inst.amount - cut)))
                left = DomainRules.round2(left - cut)
            }
        }
        db.financeDao().updateFeeAccount(acc.copy(scholarship = amt))
        core.log(actor, "scholarship", "account #$accountId ($amt)")
        return Result.success(Unit)
    }

    suspend fun voidPayment(actor: UserEntity, paymentId: Int, reason: String): Result<Unit> {
        val cleanReason = reason.trim()
        if (cleanReason.length < 5) return Result.failure(IllegalArgumentException("Provide a reason (at least 5 characters)."))
        val p = db.financeDao().getPaymentById(paymentId) ?: return Result.failure(IllegalArgumentException("Payment not found."))
        if (p.voidedAt != null) return Result.failure(IllegalArgumentException("Payment is already voided."))
        val allocs = db.financeDao().getAllocationsByPayment(paymentId)
        val instMap = db.financeDao().getInstallmentsByAccount(p.accountId).associateBy { it.id }
        for (a in allocs) {
            instMap[a.installmentId]?.let { inst ->
                db.financeDao().updateInstallment(inst.copy(paid = maxOf(0.0, DomainRules.round2(inst.paid - a.amount))))
            }
        }
        db.financeDao().updatePayment(p.copy(voidedAt = LocalDate.now().toString(), voidedBy = actor.id, voidReason = cleanReason))
        core.log(actor, "payment_void", "${p.receiptNo}: $cleanReason")
        return Result.success(Unit)
    }

    suspend fun saveSalarySetup(actor: UserEntity, userId: Int, basic: Double, allowance: Double, epf: Boolean): Result<Unit> {
        if (basic < 0 || allowance < 0) return Result.failure(IllegalArgumentException("Amounts cannot be negative."))
        db.financeDao().upsertSalaryStructure(SalaryStructureEntity(userId = userId, basic = DomainRules.round2(basic), allowance = DomainRules.round2(allowance), epf = epf))
        core.log(actor, "salary_setup", "user #$userId")
        return Result.success(Unit)
    }

    suspend fun generateMonthlyPayroll(actor: UserEntity, period: String, branchScope: Int?): Int {
        val structures = db.financeDao().getAllSalaryStructures()
        var created = 0
        for (ss in structures) {
            if (ss.basic <= 0) continue
            val u = db.userDao().getById(ss.userId) ?: continue
            if (u.status != "active" || u.id == actor.id) continue
            if (branchScope != null && u.branchId != branchScope) continue
            if (db.financeDao().getPayrollForUserPeriod(u.id, period) != null) continue
            val epf = if (ss.epf) DomainRules.round2(ss.basic * 0.08) else 0.0
            val net = DomainRules.round2(ss.basic + ss.allowance - epf)
            db.financeDao().insertPayroll(
                PayrollEntity(userId = u.id, branchId = u.branchId, period = period, basic = ss.basic, allowance = ss.allowance, epf = epf, net = net)
            )
            created++
        }
        core.log(actor, "payroll_generate", "$period ($created rows)")
        return created
    }

    suspend fun updateOrPayPayroll(
        actor: UserEntity, payrollId: Int, bonus: Double, overtime: Double, otherDed: Double, advance: Double, note: String, markPaid: Boolean, paidOn: String, method: String
    ): Result<Unit> {
        val p = db.financeDao().getPayrollById(payrollId) ?: return Result.failure(IllegalArgumentException("Payroll not found."))
        if (p.status == "paid") return Result.failure(IllegalArgumentException("Paid payroll is locked."))
        val net = DomainRules.round2(p.basic + p.allowance + bonus + overtime - p.epf - otherDed - advance)
        if (net < 0) return Result.failure(IllegalArgumentException("Deductions exceed earnings."))
        var updated = p.copy(
            bonus = DomainRules.round2(bonus), overtime = DomainRules.round2(overtime),
            otherDeduction = DomainRules.round2(otherDed), advance = DomainRules.round2(advance),
            net = net, note = note.trim().takeIf { it.isNotEmpty() }
        )
        if (markPaid) {
            val prefix = "PS-${p.period.replace("-", "")}"
            val seq = db.financeDao().getMaxSlipSeq(prefix) + 1
            updated = updated.copy(
                status = "paid", paidOn = paidOn, method = method,
                slipPrefix = prefix, seqNo = seq, slipNo = "$prefix-${seq.toString().padStart(4, '0')}"
            )
        }
        db.financeDao().updatePayroll(updated)
        core.log(actor, if (markPaid) "payroll_paid" else "payroll_edit", "${p.period} #${p.id}")
        return Result.success(Unit)
    }

    suspend fun addExpense(actor: UserEntity, branchId: Int, category: String, amount: Double, spentOn: String, description: String): Result<Unit> {
        if (amount <= 0) return Result.failure(IllegalArgumentException("Enter a positive amount."))
        db.financeDao().insertExpense(
            ExpenseEntity(branchId = branchId, category = category, amount = DomainRules.round2(amount), spentOn = spentOn, description = description.trim().takeIf { it.isNotEmpty() }, createdBy = actor.id)
        )
        core.log(actor, "expense_add", "$category $amount")
        return Result.success(Unit)
    }

    suspend fun deleteExpense(actor: UserEntity, id: Int) {
        db.financeDao().deleteExpenseById(id)
        core.log(actor, "expense_delete", "#$id")
    }

    suspend fun requestLeave(actor: UserEntity, type: String, from: String, to: String, reason: String): Result<Unit> {
        val fDate = runCatching { LocalDate.parse(from) }.getOrNull() ?: return Result.failure(IllegalArgumentException("Valid start date required."))
        val tDate = runCatching { LocalDate.parse(to) }.getOrNull() ?: return Result.failure(IllegalArgumentException("Valid end date required."))
        if (fDate.isAfter(tDate)) return Result.failure(IllegalArgumentException("End date must be on or after start date."))
        if (ChronoUnit.DAYS.between(fDate, tDate) > 59) return Result.failure(IllegalArgumentException("Leave cannot exceed 60 days."))
        if (reason.trim().length < 3) return Result.failure(IllegalArgumentException("Please provide a reason."))
        if (db.leaveAndSubDao().getOverlappingLeaves(actor.id, from, to).isNotEmpty()) {
            return Result.failure(IllegalArgumentException("You already have an active leave overlapping these dates."))
        }
        db.leaveAndSubDao().insertLeave(
            LeaveRequestEntity(userId = actor.id, branchId = actor.branchId, leaveType = type, fromDate = from, toDate = to, reason = reason.trim())
        )
        core.log(actor, "leave_request", "$from to $to")
        return Result.success(Unit)
    }

    suspend fun decideLeave(actor: UserEntity, leaveId: Int, approve: Boolean, note: String) {
        val l = db.leaveAndSubDao().getLeaveById(leaveId) ?: return
        val status = if (approve) "approved" else "rejected"
        db.leaveAndSubDao().updateLeave(
            l.copy(status = status, decidedBy = actor.id, decidedAt = LocalDate.now().toString(), decisionNote = note.trim().takeIf { it.isNotEmpty() })
        )
        core.log(actor, "leave_$status", "leave #$leaveId")
    }

    suspend fun cancelLeave(actor: UserEntity, leaveId: Int) {
        val l = db.leaveAndSubDao().getLeaveById(leaveId) ?: return
        if (l.userId == actor.id && (l.status == "pending" || l.status == "approved")) {
            db.leaveAndSubDao().updateLeave(l.copy(status = "cancelled"))
            db.leaveAndSubDao().deleteSubstitutionsByLeave(leaveId)
            core.log(actor, "leave_cancel", "#$leaveId")
        }
    }

    suspend fun assignSubstitute(actor: UserEntity, batchId: Int, date: String, substituteId: Int, leaveId: Int?, note: String): Result<Unit> {
        val batch = db.courseDao().getBatchById(batchId) ?: return Result.failure(IllegalArgumentException("Batch not found."))
        if (batch.teacherId == substituteId) return Result.failure(IllegalArgumentException("That is already the class teacher."))
        if (db.leaveAndSubDao().getApprovedLeavesOnDate(substituteId, date).isNotEmpty()) {
            return Result.failure(IllegalArgumentException("Selected teacher is on approved leave on $date."))
        }
        val wd = LocalDate.parse(date).dayOfWeek.name.take(3).lowercase().replaceFirstChar { it.uppercase() }
        val otherBatches = db.courseDao().getActiveBatches().filter {
            it.id != batchId && it.teacherId == substituteId && it.startDate <= date && it.endDate >= date &&
                it.startTime < batch.endTime && it.endTime > batch.startTime &&
                (it.classDays.isBlank() || wd in it.classDays.split(","))
        }
        if (otherBatches.isNotEmpty()) {
            return Result.failure(IllegalArgumentException("Selected teacher already has ${otherBatches.first().name} at that time."))
        }
        db.leaveAndSubDao().insertSubstitution(
            SubstitutionEntity(batchId = batchId, classDate = date, originalTeacherId = batch.teacherId, substituteId = substituteId, leaveId = leaveId, note = note.trim().takeIf { it.isNotEmpty() }, createdBy = actor.id)
        )
        core.log(actor, "substitute_assign", "batch #$batchId $date -> teacher #$substituteId")
        return Result.success(Unit)
    }

    suspend fun removeSubstitute(actor: UserEntity, subId: Int) {
        db.leaveAndSubDao().deleteSubstitutionById(subId)
        core.log(actor, "substitute_remove", "#$subId")
    }

    suspend fun addBranch(actor: UserEntity, code: String, name: String, address: String, phone: String): Result<Unit> {
        val cleanCode = code.uppercase().replace(Regex("[^A-Z0-9]"), "")
        if (cleanCode.length !in 2..6 || name.isBlank()) return Result.failure(IllegalArgumentException("Name and 2–6 char code required."))
        if (db.branchDao().getAll().any { it.code == cleanCode }) return Result.failure(IllegalArgumentException("Branch code already exists."))
        db.branchDao().insert(BranchEntity(code = cleanCode, name = name.trim(), address = address.trim(), phone = phone.trim()))
        core.log(actor, "branch_create", "$cleanCode $name")
        return Result.success(Unit)
    }

    suspend fun updateBranch(actor: UserEntity, branch: BranchEntity) {
        db.branchDao().update(branch)
        core.log(actor, "branch_update", "${branch.name} (${branch.status})")
    }

    suspend fun createStaffUser(
        actor: UserEntity, role: String, branchId: Int?, fullName: String, email: String, phone: String, nic: String, jobTitle: String, password: String
    ): Result<Unit> {
        if (fullName.isBlank() || !email.contains("@") || password.length < 8) {
            return Result.failure(IllegalArgumentException("Valid name, email, and 8+ character password required."))
        }
        if (db.userDao().getByEmail(email.trim()) != null) return Result.failure(IllegalArgumentException("That email is already in use."))
        db.userDao().insert(
            UserEntity(
                branchId = if (DomainRules.isOwner(role)) null else branchId,
                role = role, fullName = fullName.trim(), email = email.trim(),
                passwordHash = SeedData.hashPassword(password), phone = phone.trim(),
                nic = nic.trim().uppercase(), jobTitle = jobTitle.trim(),
                joinedOn = LocalDate.now().toString(), mustChangePw = true
            )
        )
        core.log(actor, "user_create", "$fullName ($role)")
        return Result.success(Unit)
    }

    suspend fun toggleUserStatus(actor: UserEntity, userId: Int) {
        val u = db.userDao().getById(userId) ?: return
        val next = if (u.status == "active") "inactive" else "active"
        db.userDao().update(u.copy(status = next))
        core.log(actor, "user_status", "${u.email} -> $next")
    }

    suspend fun resetUserPassword(actor: UserEntity, userId: Int, newPw: String): Result<Unit> {
        if (newPw.length < 8) return Result.failure(IllegalArgumentException("Password must be 8+ characters."))
        val u = db.userDao().getById(userId) ?: return Result.failure(IllegalArgumentException("User not found."))
        db.userDao().update(u.copy(passwordHash = SeedData.hashPassword(newPw), mustChangePw = true, failedLogins = 0, lockedUntilMillis = null))
        core.log(actor, "user_pw_reset", u.email)
        return Result.success(Unit)
    }

    suspend fun addMaterial(actor: UserEntity, material: MaterialEntity): Long {
        val id = db.learningDao().insertMaterial(material)
        core.log(actor, "learning_add", "${material.kind}: ${material.title}")
        return id
    }

    suspend fun toggleMaterialPublish(actor: UserEntity, materialId: Int) {
        val m = db.learningDao().getMaterialById(materialId) ?: return
        db.learningDao().updateMaterial(m.copy(publish = !m.publish))
    }

    suspend fun deleteMaterial(actor: UserEntity, materialId: Int) {
        db.learningDao().deleteMaterialById(materialId)
        core.log(actor, "learning_delete", "#$materialId")
    }

    suspend fun toggleBatchPortal(actor: UserEntity, batchId: Int, regenToken: Boolean = false) {
        val b = db.courseDao().getBatchById(batchId) ?: return
        val newToken = if (regenToken || b.accessToken.isBlank()) UUID.randomUUID().toString().replace("-", "").take(16) else b.accessToken
        val newOn = if (regenToken) true else !b.portalOn
        db.courseDao().updateBatch(b.copy(portalOn = newOn, accessToken = newToken))
        core.log(actor, "portal_update", "batch #$batchId on=$newOn")
    }

    suspend fun addQuizQuestion(actor: UserEntity, materialId: Int, qText: String, a: String, b: String, c: String, d: String, correct: String, exp: String?): Result<Unit> {
        if (qText.isBlank() || a.isBlank() || b.isBlank() || c.isBlank() || d.isBlank() || correct !in listOf("A", "B", "C", "D")) {
            return Result.failure(IllegalArgumentException("Enter question, all 4 options, and correct letter."))
        }
        val pos = db.learningDao().getQuestionsForQuiz(materialId).size + 1
        db.learningDao().insertQuestion(
            QuizQuestionEntity(materialId = materialId, position = pos, question = qText.trim(), optA = a.trim(), optB = b.trim(), optC = c.trim(), optD = d.trim(), correct = correct, explanation = exp?.trim()?.takeIf { it.isNotEmpty() })
        )
        return Result.success(Unit)
    }

    suspend fun deleteQuizQuestion(questionId: Int) {
        db.learningDao().deleteQuestionById(questionId)
    }

    suspend fun submitQuizAttempt(studentId: Int, materialId: Int, answers: Map<Int, String>, startedMillis: Long, timeLimitMin: Int?): QuizAttemptEntity {
        val questions = db.learningDao().getQuestionsForQuiz(materialId)
        var score = 0
        for (q in questions) {
            if (answers[q.id]?.uppercase() == q.correct) score++
        }
        val overtime = timeLimitMin != null && timeLimitMin > 0 && System.currentTimeMillis() > startedMillis + (timeLimitMin * 60_000L) + 15_000L
        val serialized = answers.entries.joinToString(",") { "${it.key}:${it.value}" }
        val attempt = QuizAttemptEntity(
            materialId = materialId, studentId = studentId, startedAtMillis = startedMillis,
            submittedAt = LocalDate.now().toString() + " " + java.time.LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")),
            score = if (overtime) 0 else score, total = questions.size,
            answersJson = serialized, overtime = overtime
        )
        val id = db.learningDao().insertAttempt(attempt).toInt()
        return attempt.copy(id = id)
    }

    suspend fun submitAssignment(studentId: Int, materialId: Int, answerText: String, linkUrl: String?) {
        db.learningDao().upsertSubmission(
            AssignmentSubmissionEntity(
                materialId = materialId, studentId = studentId,
                answerText = answerText.trim(), linkUrl = linkUrl?.trim()?.takeIf { it.isNotEmpty() },
                submittedAt = LocalDate.now().toString()
            )
        )
    }

    suspend fun gradeSubmission(actor: UserEntity, submission: AssignmentSubmissionEntity, marks: Double?, feedback: String?) {
        db.learningDao().upsertSubmission(submission.copy(marks = marks, feedback = feedback?.trim()?.takeIf { it.isNotEmpty() }, gradedBy = actor.id))
        core.log(actor, "assignment_grade", "submission #${submission.id}")
    }

    suspend fun saveSettings(actor: UserEntity, entries: Map<String, String>) {
        entries.forEach { (k, v) -> db.systemDao().upsertSetting(SettingEntity(k, v)) }
        core.log(actor, "settings_update", entries.keys.joinToString(","))
    }

    suspend fun saveRoleMenu(actor: UserEntity, role: String, keys: List<String>) {
        db.roleMenuDao().deleteForRole(role)
        db.roleMenuDao().insertAll(keys.distinct().map { RoleMenuEntity(role, it) })
        core.log(actor, "role_menu_update", role)
    }
}

package com.example.institutepro.data.repo

import com.example.institutepro.data.db.*
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

class InstituteRepository(private val db: InstituteDatabase) {
    val branches = db.branchDao().observeAll()
    val users = db.userDao().observeAll()
    val roleMenus = db.roleMenuDao().observeAll()
    val courseTypes = db.courseDao().observeTypes()
    val courses = db.courseDao().observeCourses()
    val batches = db.courseDao().observeBatches()
    val students = db.studentDao().observeStudents()
    val enrolments = db.studentDao().observeEnrolments()
    val attendance = db.attendanceDao().observeAttendance()
    val staffAttendance = db.attendanceDao().observeStaffAttendance()
    val leaves = db.leaveAndSubDao().observeLeaves()
    val substitutions = db.leaveAndSubDao().observeSubstitutions()
    val exams = db.examDao().observeExams()
    val examMarks = db.examDao().observeExamMarks()
    val feeAccounts = db.financeDao().observeFeeAccounts()
    val installments = db.financeDao().observeInstallments()
    val payments = db.financeDao().observePayments()
    val salaryStructures = db.financeDao().observeSalaryStructures()
    val payrolls = db.financeDao().observePayroll()
    val expenses = db.financeDao().observeExpenses()
    val materials = db.learningDao().observeMaterials()
    val quizQuestions = db.learningDao().observeQuizQuestions()
    val quizAttempts = db.learningDao().observeQuizAttempts()
    val submissions = db.learningDao().observeSubmissions()
    val activityLogs = db.systemDao().observeLogs()
    val settings = db.systemDao().observeSettings()

    val financeLearning = FinanceLearningRepository(db, this)

    suspend fun ensureInitialized() {
        SeedData.populateIfNeeded(db)
        db.courseDao().completeExpiredBatches(LocalDate.now().toString())
    }

    suspend fun log(user: UserEntity?, action: String, details: String) {
        db.systemDao().insertLog(
            ActivityLogEntity(
                userId = user?.id,
                userName = user?.fullName ?: "System",
                branchId = user?.branchId,
                action = action,
                details = details.take(500),
                createdAt = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
            )
        )
    }

    suspend fun authenticate(email: String, password: String): Result<UserEntity> {
        val u = db.userDao().getByEmail(email.trim())
            ?: return Result.failure(IllegalArgumentException("Invalid email or password."))
        val nowMillis = System.currentTimeMillis()
        if (u.lockedUntilMillis != null && u.lockedUntilMillis > nowMillis) {
            return Result.failure(IllegalStateException("Account temporarily locked. Try again in a few minutes."))
        }
        if (u.status != "active") {
            return Result.failure(IllegalStateException("This account is inactive."))
        }
        if (u.passwordHash == SeedData.hashPassword(password)) {
            val updated = u.copy(
                failedLogins = 0,
                lockedUntilMillis = null,
                lastLoginAt = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
            )
            db.userDao().update(updated)
            log(updated, "login", "Signed in")
            return Result.success(updated)
        } else {
            val attempts = u.failedLogins + 1
            val lockUntil = if (attempts >= 5) nowMillis + 600_000L else null
            db.userDao().update(u.copy(failedLogins = if (lockUntil != null) 0 else attempts, lockedUntilMillis = lockUntil))
            return Result.failure(IllegalArgumentException("Invalid email or password."))
        }
    }

    suspend fun quickDemoLogin(role: String): UserEntity? {
        val targetEmail = when (role) {
            "super_admin" -> "admin@institutepro.lk"
            "branch_manager" -> "manager@institutepro.lk"
            "teacher" -> "dilani@institutepro.lk"
            "finance" -> "finance@institutepro.lk"
            "student" -> "student@institutepro.lk"
            else -> "admin@institutepro.lk"
        }
        val u = db.userDao().getByEmail(targetEmail) ?: return null
        log(u, "login", "Demo sign-in (${DomainRules.ROLE_LABELS[u.role] ?: u.role})")
        return u
    }

    suspend fun changePassword(userId: Int, currentPw: String, newPw: String): Result<UserEntity> {
        if (newPw.length < 8) return Result.failure(IllegalArgumentException("Password must be at least 8 characters."))
        val u = db.userDao().getById(userId) ?: return Result.failure(IllegalArgumentException("User not found."))
        if (!u.mustChangePw && u.passwordHash != SeedData.hashPassword(currentPw)) {
            return Result.failure(IllegalArgumentException("Current password is incorrect."))
        }
        val updated = u.copy(passwordHash = SeedData.hashPassword(newPw), mustChangePw = false)
        db.userDao().update(updated)
        log(updated, "password_change", "Updated own password")
        return Result.success(updated)
    }

    suspend fun registerStudent(
        actor: UserEntity,
        branchId: Int,
        batchId: Int,
        firstName: String,
        lastName: String,
        gender: String,
        dob: String,
        phone: String,
        email: String?,
        address: String,
        nic: String?,
        guardianName: String?,
        guardianRelation: String?,
        guardianPhone: String?,
        fingerprintId: String?,
        createPortalLogin: Boolean
    ): Result<Pair<StudentEntity, String?>> {
        val fn = firstName.trim()
        val ln = lastName.trim()
        if (fn.isEmpty() || ln.isEmpty()) return Result.failure(IllegalArgumentException("First name and last name are required."))
        val age = DomainRules.ageFromDob(dob) ?: return Result.failure(IllegalArgumentException("Enter a valid date of birth (YYYY-MM-DD, not in future)."))
        if (phone.trim().length < 7) return Result.failure(IllegalArgumentException("Valid contact number is required."))
        val cleanEmail = email?.trim()?.takeIf { it.isNotEmpty() }
        if (cleanEmail != null && !cleanEmail.contains("@")) return Result.failure(IllegalArgumentException("Email address is invalid."))
        val cleanNic = nic?.trim()?.uppercase()
        if (age >= 18) {
            if (cleanNic == null || !DomainRules.NIC_REGEX.matches(cleanNic)) {
                return Result.failure(IllegalArgumentException("NIC required for students 18+ (9 digits + V/X, or 12 digits)."))
            }
        } else {
            if (guardianName.isNullOrBlank() || guardianPhone.isNullOrBlank() || guardianRelation !in listOf("Mother", "Father", "Guardian")) {
                return Result.failure(IllegalArgumentException("Guardian name, relationship and phone are required for students under 18."))
            }
        }

        val branch = db.branchDao().getById(branchId) ?: return Result.failure(IllegalArgumentException("Choose a valid branch."))
        val batch = db.courseDao().getBatchById(batchId) ?: return Result.failure(IllegalArgumentException("Choose an active batch."))
        if (batch.branchId != branchId || batch.status != "active") {
            return Result.failure(IllegalArgumentException("Choose an active batch in this branch."))
        }
        val currentCount = db.studentDao().countEnrolmentsInBatch(batchId)
        if (batch.maxStudents != null && currentCount >= batch.maxStudents) {
            return Result.failure(IllegalArgumentException("This class is full (max ${batch.maxStudents} students)."))
        }
        val course = db.courseDao().getCourses().firstOrNull { it.id == batch.courseId }
            ?: return Result.failure(IllegalArgumentException("Course not found."))
        val cType = db.courseDao().getTypes().firstOrNull { it.id == course.typeId }
            ?: return Result.failure(IllegalArgumentException("Course type not found."))

        val prefix = "${branch.code}-${cType.code}-${course.code}-${batch.batchYear}"
        val seq = db.studentDao().getMaxSeqForPrefix(prefix) + 1
        val code = "$prefix-${seq.toString().padStart(3, '0')}"
        val full = "$fn $ln"
        val today = LocalDate.now().toString()

        val studentId = db.studentDao().insertStudent(
            StudentEntity(
                studentCode = code, idPrefix = prefix, seqNo = seq,
                branchId = branchId, batchId = batchId,
                firstName = fn, lastName = ln, fullName = full,
                gender = if (gender == "Female") "Female" else "Male",
                dateOfBirth = dob, address = address.trim(), phone = phone.trim(),
                email = cleanEmail,
                nic = if (age >= 18) cleanNic else null,
                guardianName = if (age < 18) guardianName?.trim() else null,
                guardianRelation = if (age < 18) guardianRelation else null,
                guardianPhone = if (age < 18) guardianPhone?.trim() else null,
                fingerprintId = fingerprintId?.trim()?.takeIf { it.isNotEmpty() },
                status = "active", registeredAt = today
            )
        ).toInt()

        val enrolmentId = db.studentDao().insertEnrolment(EnrolmentEntity(studentId = studentId, batchId = batchId, enrolledOn = today)).toInt()
        financeLearning.ensureAccountForEnrolment(enrolmentId)

        var tempPw: String? = null
        if (createPortalLogin && cleanEmail != null) {
            if (db.userDao().getByEmail(cleanEmail) == null) {
                tempPw = "STU-" + UUID.randomUUID().toString().take(6).uppercase()
                db.userDao().insert(
                    UserEntity(
                        branchId = branchId, role = "student", fullName = full,
                        email = cleanEmail, passwordHash = SeedData.hashPassword(tempPw),
                        phone = phone.trim(), studentId = studentId, mustChangePw = true
                    )
                )
            }
        }
        log(actor, "student_register", "$code $full")
        val saved = db.studentDao().getById(studentId)!!
        return Result.success(saved to tempPw)
    }

    suspend fun toggleStudentStatus(actor: UserEntity, studentId: Int) {
        val st = db.studentDao().getById(studentId) ?: return
        val next = if (st.status == "active") "inactive" else "active"
        db.studentDao().updateStudent(st.copy(status = next))
        log(actor, "student_status", "${st.studentCode} -> $next")
    }

    suspend fun updateStudentDetails(actor: UserEntity, student: StudentEntity) {
        db.studentDao().updateStudent(student)
        log(actor, "student_update", "${student.studentCode} ${student.fullName}")
    }

    suspend fun createCourse(actor: UserEntity, typeId: Int, rawCode: String, name: String): Result<Unit> {
        val code = rawCode.uppercase().replace(Regex("[^A-Z0-9]"), "")
        val cleanName = name.trim()
        if (code.isEmpty() || code.length > 10 || cleanName.isEmpty()) {
            return Result.failure(IllegalArgumentException("Short code (max 10 chars) and course name are required."))
        }
        val exists = db.courseDao().getCourses().any { it.typeId == typeId && it.code == code }
        if (exists) return Result.failure(IllegalArgumentException("Course code $code already exists for this type."))
        db.courseDao().insertCourse(CourseEntity(typeId = typeId, code = code, name = cleanName))
        log(actor, "course_create", "$code $cleanName")
        return Result.success(Unit)
    }

    suspend fun createBatch(
        actor: UserEntity,
        branchId: Int,
        courseId: Int,
        year: Int,
        name: String,
        startDate: String,
        durationMonths: Int,
        startTime: String,
        endTime: String,
        classDays: List<String>,
        fee: Double,
        feePlan: String,
        maxStudents: Int?,
        teacherId: Int?,
        room: String
    ): Result<String> {
        val cleanName = name.trim()
        if (cleanName.isEmpty()) return Result.failure(IllegalArgumentException("Batch name is required."))
        if (durationMonths !in 1..60) return Result.failure(IllegalArgumentException("Duration must be 1–60 months."))
        if (endTime <= startTime) return Result.failure(IllegalArgumentException("End time must be after start time."))
        if (fee < 0) return Result.failure(IllegalArgumentException("Fee cannot be negative."))
        val parsedStart = runCatching { LocalDate.parse(startDate) }.getOrNull()
            ?: return Result.failure(IllegalArgumentException("Valid start date (YYYY-MM-DD) is required."))
        val endDate = parsedStart.plusMonths(durationMonths.toLong()).toString()
        val daysStr = classDays.joinToString(",")

        if (teacherId != null) {
            val clash = findTeacherClash(teacherId, startDate, endDate, startTime, endTime, classDays)
            if (clash != null) return Result.failure(IllegalArgumentException("Teacher already teaches $clash at that time."))
        }

        val token = UUID.randomUUID().toString().replace("-", "").take(16)
        db.courseDao().insertBatch(
            BatchEntity(
                branchId = branchId, courseId = courseId, batchYear = year, name = cleanName,
                startDate = startDate, durationMonths = durationMonths, endDate = endDate,
                startTime = startTime, endTime = endTime, classDays = daysStr,
                fee = fee, feePlan = feePlan, maxStudents = maxStudents,
                teacherId = teacherId, room = room.trim(), status = "active",
                portalOn = true, accessToken = token
            )
        )
        log(actor, "batch_create", "$cleanName ($year)")
        return Result.success("Batch created (ends $endDate).")
    }

    private suspend fun findTeacherClash(
        teacherId: Int, start: String, end: String, st: String, et: String, days: List<String>
    ): String? {
        val active = db.courseDao().getActiveBatches().filter { it.teacherId == teacherId }
        for (o in active) {
            if (o.endDate < start || o.startDate > end) continue
            if (o.startTime < et && o.endTime > st) {
                val theirs = o.classDays.split(",").filter { it.isNotBlank() }
                if (days.isEmpty() || theirs.isEmpty() || days.any { it in theirs }) {
                    return "${o.name} (${o.batchYear})"
                }
            }
        }
        return null
    }

    suspend fun markBatchAttendance(
        actor: UserEntity,
        batchId: Int,
        date: String,
        statuses: Map<Int, String>
    ): Result<AttendanceAlerts> {
        val parsed = runCatching { LocalDate.parse(date) }.getOrNull()
            ?: return Result.failure(IllegalArgumentException("Invalid date."))
        if (parsed.isAfter(LocalDate.now())) {
            return Result.failure(IllegalArgumentException("Cannot mark attendance for a future date."))
        }
        val reactivated = mutableListOf<Pair<String, String>>()
        val markedInactive = mutableListOf<Pair<String, String>>()
        for ((sid, status) in statuses) {
            if (status !in listOf("present", "absent")) continue
            applyAttendanceRule(sid, batchId, date, status, null, null, "manual", actor.id, reactivated, markedInactive)
        }
        log(actor, "attendance_mark", "batch #$batchId $date (${statuses.size} students)")
        return Result.success(AttendanceAlerts(reactivated.distinct(), markedInactive.distinct()))
    }

    suspend fun importFingerprintPunches(
        actor: UserEntity,
        branchId: Int,
        rawText: String
    ): Result<Pair<String, AttendanceAlerts>> {
        val punches = DomainRules.parsePunches(rawText)
        if (punches.isEmpty()) {
            return Result.failure(IllegalArgumentException("No usable punch lines found. Format: ID,YYYY-MM-DD HH:MM:SS"))
        }
        val allStudents = db.studentDao().getEnrolmentsByBatch(0) // unused placeholder
        val branchStudents = mutableListOf<StudentEntity>()
        // Fetch branch students with fingerprint IDs
        val batchesInBranch = db.courseDao().getActiveBatches().filter { it.branchId == branchId }
        val fpMap = mutableMapOf<String, Int>()
        val seenIds = mutableSetOf<Int>()
        for (b in batchesInBranch) {
            for (enr in db.studentDao().getEnrolmentsByBatch(b.id)) {
                if (seenIds.add(enr.studentId)) {
                    db.studentDao().getById(enr.studentId)?.let { st ->
                        if (st.branchId == branchId && !st.fingerprintId.isNullOrBlank()) {
                            fpMap[st.fingerprintId.trimStart('0').ifEmpty { "0" }] = st.id
                            branchStudents.add(st)
                        }
                    }
                }
            }
        }
        val byDate = sortedMapOf<String, MutableMap<Int, Pair<String, String?>>>()
        var unknownCount = 0
        for ((fp, days) in punches) {
            val sid = fpMap[fp]
            if (sid == null) { unknownCount++; continue }
            for ((d, times) in days) {
                byDate.getOrPut(d) { mutableMapOf() }[sid] = times.first() to if (times.size > 1) times.last() else null
            }
        }
        val reactivated = mutableListOf<Pair<String, String>>()
        val markedInactive = mutableListOf<Pair<String, String>>()
        var pres = 0; var abs = 0
        for ((d, people) in byDate) {
            val wd = LocalDate.parse(d).dayOfWeek.name.take(3).lowercase().replaceFirstChar { it.uppercase() }
            for (batch in batchesInBranch) {
                if (d < batch.startDate || d > batch.endDate) continue
                if (batch.classDays.isNotBlank() && wd !in batch.classDays.split(",")) continue
                for (enr in db.studentDao().getEnrolmentsByBatch(batch.id)) {
                    val punch = people[enr.studentId]
                    if (punch != null) {
                        applyAttendanceRule(enr.studentId, batch.id, d, "present", punch.first, punch.second, "fingerprint", actor.id, reactivated, markedInactive)
                        pres++
                    } else {
                        val existing = db.attendanceDao().getRecord(enr.studentId, batch.id, d)
                        if (existing?.status != "present") {
                            applyAttendanceRule(enr.studentId, batch.id, d, "absent", null, null, "fingerprint", actor.id, reactivated, markedInactive)
                            abs++
                        }
                    }
                }
            }
        }
        val msg = "Imported ${byDate.size} day(s): $pres present, $abs absent." +
            if (unknownCount > 0) " $unknownCount unknown fingerprint ID(s) skipped." else ""
        log(actor, "attendance_import", msg)
        return Result.success(msg to AttendanceAlerts(reactivated.distinct(), markedInactive.distinct()))
    }

    private suspend fun applyAttendanceRule(
        studentId: Int, batchId: Int, date: String, status: String,
        checkIn: String?, checkOut: String?, source: String, markedBy: Int,
        reactivated: MutableList<Pair<String, String>>, markedInactive: MutableList<Pair<String, String>>
    ) {
        val existing = db.attendanceDao().getRecord(studentId, batchId, date)
        db.attendanceDao().upsert(
            AttendanceEntity(
                id = existing?.id ?: 0,
                studentId = studentId, batchId = batchId, attDate = date,
                status = status, checkIn = checkIn, checkOut = checkOut, source = source, markedBy = markedBy
            )
        )
        val st = db.studentDao().getById(studentId) ?: return
        if (status == "present") {
            if (st.status == "inactive") {
                db.studentDao().updateStudent(st.copy(status = "active"))
                reactivated.add(st.studentCode to st.fullName)
            }
        } else {
            val lastTwo = db.attendanceDao().getLastTwoRecords(studentId, batchId, date)
            if (lastTwo.size == 2 && lastTwo[0].status == "absent" && lastTwo[1].status == "absent" && st.status == "active") {
                db.studentDao().updateStudent(st.copy(status = "inactive"))
                markedInactive.add(st.studentCode to st.fullName)
            }
        }
    }

    suspend fun markStaffAttendance(actor: UserEntity, date: String, statuses: Map<Int, String>) {
        for ((uid, st) in statuses) {
            if (st in listOf("present", "half_day", "absent")) {
                db.attendanceDao().upsertStaff(StaffAttendanceEntity(userId = uid, attDate = date, status = st, markedBy = actor.id))
            }
        }
        log(actor, "staff_attendance", "$date (${statuses.size} staff)")
    }

    suspend fun scheduleExam(
        actor: UserEntity, batchId: Int, title: String, examType: String, date: String, startTime: String?, maxMarks: Double
    ): Result<Unit> {
        if (title.isBlank()) return Result.failure(IllegalArgumentException("Exam title is required."))
        if (maxMarks <= 0 || maxMarks > 1000) return Result.failure(IllegalArgumentException("Max marks must be 1–1000."))
        val batch = db.courseDao().getBatchById(batchId) ?: return Result.failure(IllegalArgumentException("Batch not found."))
        db.examDao().insertExam(
            ExamEntity(
                branchId = batch.branchId, batchId = batchId, title = title.trim(),
                examType = examType, examDate = date, startTime = startTime?.takeIf { it.isNotBlank() },
                maxMarks = maxMarks, createdBy = actor.id
            )
        )
        log(actor, "exam_schedule", "$title $date")
        return Result.success(Unit)
    }

    suspend fun saveExamMarks(
        actor: UserEntity, examId: Int, entries: Map<Int, Triple<Double?, Boolean, String?>>
    ): Result<Int> {
        val exam = db.examDao().getExamById(examId) ?: return Result.failure(IllegalArgumentException("Exam not found."))
        if (exam.examDate > LocalDate.now().toString()) {
            return Result.failure(IllegalArgumentException("Marks can only be entered on or after ${exam.examDate}."))
        }
        var count = 0
        for ((sid, data) in entries) {
            val (marks, absent, remarks) = data
            if (marks == null && !absent && remarks.isNullOrBlank()) continue
            if (!absent && marks != null && (marks < 0 || marks > exam.maxMarks)) continue
            db.examDao().upsertMark(
                ExamMarkEntity(
                    examId = examId, studentId = sid,
                    marks = if (absent) null else marks?.let { DomainRules.round2(it) },
                    absent = absent, remarks = remarks?.trim()?.takeIf { it.isNotEmpty() },
                    enteredBy = actor.id
                )
            )
            count++
        }
        log(actor, "exam_marks", "exam #$examId ($count students)")
        return Result.success(count)
    }
}

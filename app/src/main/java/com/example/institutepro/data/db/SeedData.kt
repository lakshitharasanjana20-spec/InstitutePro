package com.example.institutepro.data.db

import java.security.MessageDigest
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

private data class SeedStaffIds(
    val adminId: Int,
    val mgrId: Int,
    val teacher1Id: Int,
    val teacher2Id: Int,
    val teacher3Id: Int,
    val financeId: Int
)

object SeedData {
    private val fmt = DateTimeFormatter.ISO_LOCAL_DATE

    fun hashPassword(raw: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(("instpro_salt_$raw").toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    suspend fun populateIfNeeded(db: InstituteDatabase) {
        if (db.branchDao().getAll().isNotEmpty()) return

        val today = LocalDate.now()
        seedSettingsAndRoles(db)

        val mnId = db.branchDao().insert(BranchEntity(code = "MN", name = "Colombo Main Campus", address = "42 Galle Road, Colombo 03", phone = "011-234-5678", status = "active")).toInt()
        val kdId = db.branchDao().insert(BranchEntity(code = "KD", name = "Kandy City Branch", address = "18 Peradeniya Rd, Kandy", phone = "081-223-4455", status = "active")).toInt()

        val (cIelts, cDip, cGr10, cSpk) = seedCourses(db)
        val staff = seedStaff(db, mnId, kdId)
        val (b1, b2, b3) = seedBatches(db, today, mnId, kdId, cIelts, cDip, cGr10, cSpk, staff.teacher1Id, staff.teacher2Id, staff.teacher3Id)
        val (students, enrolmentMap) = seedStudentsAndEnrolments(db, today, mnId, kdId, b1, b2, b3)
        seedFeesAndPayments(db, today, mnId, kdId, b1, b2, b3, staff.financeId, students, enrolmentMap)
        seedAttendanceAndExams(db, today, mnId, b1, b2, b3, staff.mgrId, staff.teacher1Id, staff.teacher2Id, staff.financeId, students)
        seedLearningHub(db, today, mnId, b1, staff.teacher1Id)
        seedFinancePayrollAndLogs(db, today, mnId, kdId, staff.adminId, staff.teacher1Id, staff.teacher2Id, staff.financeId)
    }

    private suspend fun seedSettingsAndRoles(db: InstituteDatabase) {
        val settings = listOf(
            SettingEntity("institute_name", "InstitutePro English Institute"),
            SettingEntity("currency", "LKR"),
            SettingEntity("session_minutes", "30"),
            SettingEntity("payment_info", "Pay via branch cash/card desk or Bank Transfer to InstitutePro Holdings (Acc: 1004-5582-9012, Commercial Bank). Include Student ID as reference."),
            SettingEntity("grade_a", "75"),
            SettingEntity("grade_b", "65"),
            SettingEntity("grade_c", "50"),
            SettingEntity("grade_s", "35")
        )
        settings.forEach { db.systemDao().upsertSetting(it) }

        val roleMenuMap = mapOf(
            "super_admin" to listOf("dashboard","students","courses","branches","schedule","attendance","exams","materials","staff","teachers","leaves","payments","salaries","reports","settings","activity","profile"),
            "admin" to listOf("dashboard","students","courses","branches","schedule","attendance","exams","materials","staff","teachers","leaves","payments","salaries","reports","settings","activity","profile"),
            "branch_manager" to listOf("dashboard","students","courses","schedule","attendance","exams","materials","staff","teachers","leaves","payments","salaries","reports","settings","activity","profile"),
            "administrator" to listOf("dashboard","students","courses","schedule","attendance","exams","materials","staff","teachers","leaves","payments","settings","profile"),
            "receptionist" to listOf("dashboard","students","courses","schedule","attendance","exams","staff","teachers","leaves","payments","profile"),
            "teacher" to listOf("dashboard","students","courses","schedule","attendance","exams","materials","leaves","teachers","staff","profile"),
            "finance" to listOf("dashboard","payments","salaries","reports","leaves","profile"),
            "other" to listOf("dashboard","leaves","profile"),
            "student" to listOf("sdash","sclasses","sschedule","sassign","squiz","sresults","sattendance","sfees","sprofile")
        )
        db.roleMenuDao().insertAll(roleMenuMap.flatMap { (role, keys) -> keys.map { RoleMenuEntity(role, it) } })
    }

    private suspend fun seedCourses(db: InstituteDatabase): List<Int> {
        val aiType = db.courseDao().insertType(CourseTypeEntity(code = "AI", name = "Annual Intake")).toInt()
        val grType = db.courseDao().insertType(CourseTypeEntity(code = "GR", name = "Grade Classes")).toInt()
        val scType = db.courseDao().insertType(CourseTypeEntity(code = "SC", name = "Short Courses")).toInt()
        return listOf(
            db.courseDao().insertCourse(CourseEntity(typeId = aiType, code = "IELTS", name = "IELTS Academic Mastery")).toInt(),
            db.courseDao().insertCourse(CourseEntity(typeId = aiType, code = "DIP", name = "Diploma in English")).toInt(),
            db.courseDao().insertCourse(CourseEntity(typeId = grType, code = "G10", name = "Grade 10 General English")).toInt(),
            db.courseDao().insertCourse(CourseEntity(typeId = scType, code = "SPK", name = "Spoken English Intensive")).toInt()
        )
    }

    private suspend fun seedStaff(db: InstituteDatabase, mnId: Int, kdId: Int): SeedStaffIds {
        val demoPw = hashPassword("Institute@123")
        val adminId = db.userDao().insert(UserEntity(
            branchId = null, role = "super_admin", fullName = "Nadeesha Perera",
            email = "admin@institutepro.lk", passwordHash = demoPw,
            phone = "077-100-0001", nic = "199012345678", jobTitle = "Director", joinedOn = "2022-01-10"
        )).toInt()
        val mgrId = db.userDao().insert(UserEntity(
            branchId = mnId, role = "branch_manager", fullName = "Kasun Wijesinghe",
            email = "manager@institutepro.lk", passwordHash = demoPw,
            phone = "077-200-0002", nic = "199254321098", jobTitle = "Branch Manager", joinedOn = "2023-03-01", fingerprintId = "101"
        )).toInt()
        val teacher1Id = db.userDao().insert(UserEntity(
            branchId = mnId, role = "teacher", fullName = "Dilani Fernando",
            email = "dilani@institutepro.lk", passwordHash = demoPw,
            phone = "071-334-1122", nic = "199488776655", jobTitle = "Senior IELTS Lecturer", joinedOn = "2023-06-15", fingerprintId = "102"
        )).toInt()
        val teacher2Id = db.userDao().insert(UserEntity(
            branchId = mnId, role = "teacher", fullName = "Ruwan Jayawardena",
            email = "ruwan@institutepro.lk", passwordHash = demoPw,
            phone = "076-445-9988", nic = "199122334455", jobTitle = "English Instructor", joinedOn = "2024-01-15", fingerprintId = "103"
        )).toInt()
        val teacher3Id = db.userDao().insert(UserEntity(
            branchId = kdId, role = "teacher", fullName = "Sithumi Bandara",
            email = "sithumi@institutepro.lk", passwordHash = demoPw,
            phone = "077-554-7766", nic = "199566778899", jobTitle = "Kandy Lead Lecturer", joinedOn = "2024-02-01", fingerprintId = "201"
        )).toInt()
        val financeId = db.userDao().insert(UserEntity(
            branchId = mnId, role = "finance", fullName = "Tharindu Silva",
            email = "finance@institutepro.lk", passwordHash = demoPw,
            phone = "077-889-1234", nic = "199311223344", jobTitle = "Accounts Officer", joinedOn = "2023-09-01", fingerprintId = "104"
        )).toInt()
        return SeedStaffIds(adminId, mgrId, teacher1Id, teacher2Id, teacher3Id, financeId)
    }

    private suspend fun seedBatches(
        db: InstituteDatabase, today: LocalDate, mnId: Int, kdId: Int,
        cIelts: Int, cDip: Int, cGr10: Int, cSpk: Int,
        teacher1Id: Int, teacher2Id: Int, teacher3Id: Int
    ): List<Int> {
        val todayDay = today.dayOfWeek.name.take(3).lowercase().replaceFirstChar { it.uppercase() }
        val b1 = db.courseDao().insertBatch(BatchEntity(
            branchId = mnId, courseId = cIelts, batchYear = today.year, name = "Morning Alpha",
            startDate = today.minusMonths(2).format(fmt), durationMonths = 6, endDate = today.plusMonths(4).format(fmt),
            startTime = "08:30", endTime = "10:30", classDays = listOf("Mon", "Wed", "Fri", todayDay).distinct().joinToString(","),
            fee = 45000.0, feePlan = "installments_3", maxStudents = 25, teacherId = teacher1Id,
            room = "Hall A1", status = "active", portalOn = true, accessToken = "demo-ielts-alpha"
        )).toInt()
        val b2 = db.courseDao().insertBatch(BatchEntity(
            branchId = mnId, courseId = cGr10, batchYear = today.year, name = "Evening Batch B",
            startDate = today.minusMonths(1).format(fmt), durationMonths = 12, endDate = today.plusMonths(11).format(fmt),
            startTime = "16:00", endTime = "17:30", classDays = listOf("Tue", "Thu", "Sat", todayDay).distinct().joinToString(","),
            fee = 4500.0, feePlan = "monthly", maxStudents = 30, teacherId = teacher2Id,
            room = "Room B2", status = "active", portalOn = true, accessToken = "demo-g10-batchb"
        )).toInt()
        val b3 = db.courseDao().insertBatch(BatchEntity(
            branchId = kdId, courseId = cSpk, batchYear = today.year, name = "Weekend Fluency",
            startDate = today.minusDays(14).format(fmt), durationMonths = 3, endDate = today.plusDays(18).format(fmt),
            startTime = "09:00", endTime = "12:00", classDays = "Sat,Sun",
            fee = 24000.0, feePlan = "custom", maxStudents = 20, teacherId = teacher3Id,
            room = "Kandy Studio", status = "active", portalOn = true, accessToken = "demo-kdy-spk"
        )).toInt()
        db.courseDao().insertBatch(BatchEntity(
            branchId = mnId, courseId = cDip, batchYear = today.year - 1, name = "Cohort 2025",
            startDate = today.minusMonths(14).format(fmt), durationMonths = 12, endDate = today.minusMonths(2).format(fmt),
            startTime = "10:00", endTime = "12:00", classDays = "Mon,Wed",
            fee = 60000.0, feePlan = "installments_3", maxStudents = 25, teacherId = teacher1Id,
            room = "Hall A2", status = "completed", portalOn = false, accessToken = ""
        ))
        return listOf(b1, b2, b3)
    }

    private suspend fun seedStudentsAndEnrolments(
        db: InstituteDatabase, today: LocalDate, mnId: Int, kdId: Int, b1: Int, b2: Int, b3: Int
    ): Pair<List<Int>, Map<Pair<Int, Int>, Int>> {
        val demoPw = hashPassword("Institute@123")
        val y = today.year
        val rows = listOf(
            StudentEntity(studentCode = "MN-AI-IELTS-$y-001", idPrefix = "MN-AI-IELTS-$y", seqNo = 1, branchId = mnId, batchId = b1, firstName = "Amaya", lastName = "Senanayake", fullName = "Amaya Senanayake", gender = "Female", dateOfBirth = "2003-05-14", address = "15 Havelock Town, Colombo 05", phone = "077-901-1122", email = "student@institutepro.lk", nic = "200356701234", fingerprintId = "1001", status = "active", registeredAt = today.minusMonths(5).withDayOfMonth(10).format(fmt)),
            StudentEntity(studentCode = "MN-AI-IELTS-$y-002", idPrefix = "MN-AI-IELTS-$y", seqNo = 2, branchId = mnId, batchId = b1, firstName = "Kaveen", lastName = "Dissanayake", fullName = "Kaveen Dissanayake", gender = "Male", dateOfBirth = "2002-11-22", address = "88 Nawala Rd, Rajagiriya", phone = "071-882-3344", email = "kaveen@example.lk", nic = "200211204567", fingerprintId = "1002", status = "active", registeredAt = today.minusMonths(4).withDayOfMonth(14).format(fmt)),
            StudentEntity(studentCode = "MN-AI-IELTS-$y-003", idPrefix = "MN-AI-IELTS-$y", seqNo = 3, branchId = mnId, batchId = b1, firstName = "Nethmi", lastName = "Rajapaksa", fullName = "Nethmi Rajapaksa", gender = "Female", dateOfBirth = "2004-02-09", address = "21 Ward Place, Colombo 07", phone = "076-556-7788", email = "nethmi@example.lk", nic = "200454308899", fingerprintId = "1003", status = "inactive", registeredAt = today.minusMonths(4).withDayOfMonth(22).format(fmt)),
            StudentEntity(studentCode = "MN-GR-G10-$y-001", idPrefix = "MN-GR-G10-$y", seqNo = 1, branchId = mnId, batchId = b2, firstName = "Dineth", lastName = "Karunaratne", fullName = "Dineth Karunaratne", gender = "Male", dateOfBirth = "2011-08-19", address = "7 Park Street, Colombo 02", phone = "077-312-9090", guardianName = "Chaminda Karunaratne", guardianRelation = "Father", guardianPhone = "077-312-9090", fingerprintId = "1004", status = "active", registeredAt = today.minusMonths(3).withDayOfMonth(8).format(fmt)),
            StudentEntity(studentCode = "KD-SC-SPK-$y-001", idPrefix = "KD-SC-SPK-$y", seqNo = 1, branchId = kdId, batchId = b3, firstName = "Hiruni", lastName = "Ekanayake", fullName = "Hiruni Ekanayake", gender = "Female", dateOfBirth = "2001-03-30", address = "14 Temple St, Kandy", phone = "077-654-3210", email = "hiruni@example.lk", nic = "200109003210", fingerprintId = "2001", status = "active", registeredAt = today.minusMonths(3).withDayOfMonth(19).format(fmt)),
            StudentEntity(studentCode = "MN-AI-IELTS-$y-004", idPrefix = "MN-AI-IELTS-$y", seqNo = 4, branchId = mnId, batchId = b1, firstName = "Ravindu", lastName = "Gunasekara", fullName = "Ravindu Gunasekara", gender = "Male", dateOfBirth = "2003-07-12", address = "34 Duplication Rd, Colombo 04", phone = "077-402-1188", email = "ravindu@example.lk", nic = "200319302211", fingerprintId = "1005", status = "active", registeredAt = today.minusMonths(2).withDayOfMonth(5).format(fmt)),
            StudentEntity(studentCode = "MN-AI-IELTS-$y-005", idPrefix = "MN-AI-IELTS-$y", seqNo = 5, branchId = mnId, batchId = b1, firstName = "Tharushi", lastName = "Perera", fullName = "Tharushi Perera", gender = "Female", dateOfBirth = "2002-09-25", address = "19 Reid Ave, Colombo 07", phone = "071-609-4433", email = "tharushi@example.lk", nic = "200276501122", fingerprintId = "1006", status = "active", registeredAt = today.minusMonths(2).withDayOfMonth(16).format(fmt)),
            StudentEntity(studentCode = "MN-GR-G10-$y-002", idPrefix = "MN-GR-G10-$y", seqNo = 2, branchId = mnId, batchId = b2, firstName = "Sithum", lastName = "Wickramasinghe", fullName = "Sithum Wickramasinghe", gender = "Male", dateOfBirth = "2010-12-03", address = "52 Kirulapone Ave, Colombo 06", phone = "077-718-9900", guardianName = "Nimal Wickramasinghe", guardianRelation = "Father", guardianPhone = "077-718-9900", fingerprintId = "1007", status = "active", registeredAt = today.minusMonths(1).withDayOfMonth(6).format(fmt)),
            StudentEntity(studentCode = "MN-GR-G10-$y-003", idPrefix = "MN-GR-G10-$y", seqNo = 3, branchId = mnId, batchId = b2, firstName = "Hasini", lastName = "Madushani", fullName = "Hasini Madushani", gender = "Female", dateOfBirth = "2011-04-18", address = "9 Kotte Rd, Ethul Kotte", phone = "076-220-7711", guardianName = "Mala Madushani", guardianRelation = "Mother", guardianPhone = "076-220-7711", fingerprintId = "1008", status = "active", registeredAt = today.minusMonths(1).withDayOfMonth(15).format(fmt)),
            StudentEntity(studentCode = "KD-SC-SPK-$y-002", idPrefix = "KD-SC-SPK-$y", seqNo = 2, branchId = kdId, batchId = b3, firstName = "Pasindu", lastName = "Herath", fullName = "Pasindu Herath", gender = "Male", dateOfBirth = "2000-01-21", address = "102 Katugastota Rd, Kandy", phone = "077-990-1212", email = "pasindu@example.lk", nic = "200002104433", fingerprintId = "2002", status = "active", registeredAt = today.minusMonths(1).withDayOfMonth(21).format(fmt)),
            StudentEntity(studentCode = "MN-AI-IELTS-$y-006", idPrefix = "MN-AI-IELTS-$y", seqNo = 6, branchId = mnId, batchId = b1, firstName = "Isuri", lastName = "Jayasinghe", fullName = "Isuri Jayasinghe", gender = "Female", dateOfBirth = "2003-10-08", address = "61 Jawatta Rd, Colombo 05", phone = "071-411-5522", email = "isuri@example.lk", nic = "200378205544", fingerprintId = "1009", status = "active", registeredAt = today.minusDays(8).format(fmt)),
            StudentEntity(studentCode = "KD-SC-SPK-$y-003", idPrefix = "KD-SC-SPK-$y", seqNo = 3, branchId = kdId, batchId = b3, firstName = "Chamara", lastName = "Rathnayake", fullName = "Chamara Rathnayake", gender = "Male", dateOfBirth = "2001-06-15", address = "28 William Gopallawa Mw, Kandy", phone = "077-833-2244", email = "chamara@example.lk", nic = "200116708811", fingerprintId = "2003", status = "active", registeredAt = today.minusDays(3).format(fmt))
        )

        val ids = rows.map { db.studentDao().insertStudent(it).toInt() }
        db.userDao().insert(UserEntity(
            branchId = mnId, role = "student", fullName = "Amaya Senanayake",
            email = "student@institutepro.lk", passwordHash = demoPw,
            phone = "077-901-1122", studentId = ids[0], status = "active", mustChangePw = false
        ))

        val enrMap = mutableMapOf<Pair<Int, Int>, Int>()
        rows.forEachIndexed { idx, st ->
            val enrId = db.studentDao().insertEnrolment(EnrolmentEntity(studentId = ids[idx], batchId = st.batchId, enrolledOn = st.registeredAt)).toInt()
            enrMap[ids[idx] to st.batchId] = enrId
        }
        val extraEnrId = db.studentDao().insertEnrolment(EnrolmentEntity(studentId = ids[0], batchId = b2, enrolledOn = today.minusDays(20).format(fmt))).toInt()
        enrMap[ids[0] to b2] = extraEnrId
        return ids to enrMap
    }

    private suspend fun seedFeesAndPayments(
        db: InstituteDatabase, today: LocalDate, mnId: Int, kdId: Int,
        b1: Int, b2: Int, b3: Int, financeId: Int, students: List<Int>,
        enrByStudentBatch: Map<Pair<Int, Int>, Int>
    ) {
        val rcMn = "RC-MN-${today.format(DateTimeFormatter.ofPattern("yyMM"))}"
        val rcKd = "RC-KD-${today.format(DateTimeFormatter.ofPattern("yyMM"))}"

        suspend fun addInstallmentPayAccount(
            sid: Int, batchId: Int, branchId: Int, totalFee: Double, inst1Paid: Double, inst2DueDaysOffset: Long, inst2Paid: Double,
            paidAmt: Double, method: String, ref: String?, paidDaysAgo: Long, rcPrefix: String, seqNo: Int
        ) {
            val enrId = enrByStudentBatch[sid to batchId] ?: return
            val accId = db.financeDao().insertFeeAccount(FeeAccountEntity(enrolmentId = enrId, studentId = sid, batchId = batchId, totalFee = totalFee, plan = "installments_3")).toInt()
            val part = totalFee / 3.0
            val i1 = db.financeDao().insertInstallment(InstallmentEntity(accountId = accId, no = 1, label = "Installment 1", dueDate = today.minusMonths(2).format(fmt), amount = part, paid = inst1Paid)).toInt()
            val i2 = db.financeDao().insertInstallment(InstallmentEntity(accountId = accId, no = 2, label = "Installment 2", dueDate = today.plusDays(inst2DueDaysOffset).format(fmt), amount = part, paid = inst2Paid)).toInt()
            db.financeDao().insertInstallment(InstallmentEntity(accountId = accId, no = 3, label = "Installment 3", dueDate = today.plusMonths(2).format(fmt), amount = part, paid = 0.0))

            if (paidAmt > 0.0) {
                val pid = db.financeDao().insertPayment(PaymentEntity(
                    receiptPrefix = rcPrefix, seqNo = seqNo, receiptNo = "$rcPrefix-${seqNo.toString().padStart(4, '0')}",
                    studentId = sid, batchId = batchId, accountId = accId, branchId = branchId,
                    amount = paidAmt, method = method, reference = ref,
                    paidOn = today.minusDays(paidDaysAgo).format(fmt), receivedBy = financeId
                )).toInt()
                if (inst1Paid > 0) db.financeDao().insertAllocation(PaymentAllocationEntity(paymentId = pid, installmentId = i1, amount = inst1Paid))
                if (inst2Paid > 0) db.financeDao().insertAllocation(PaymentAllocationEntity(paymentId = pid, installmentId = i2, amount = inst2Paid))
            }
        }

        addInstallmentPayAccount(students[0], b1, mnId, 45000.0, 15000.0, -5L, 5000.0, 20000.0, "bank_transfer", "TXN-88412", 14L, rcMn, 1)
        addInstallmentPayAccount(students[1], b1, mnId, 45000.0, 15000.0, 30L, 0.0, 15000.0, "cash", null, 10L, rcMn, 2)
        addInstallmentPayAccount(students[2], b1, mnId, 45000.0, 15000.0, -14L, 0.0, 0.0, "cash", null, 0L, rcMn, 99)
        addInstallmentPayAccount(students[5], b1, mnId, 45000.0, 15000.0, -2L, 15000.0, 30000.0, "card", "POS-9041", 7L, rcMn, 3)
        addInstallmentPayAccount(students[6], b1, mnId, 45000.0, 15000.0, 25L, 0.0, 15000.0, "bank_transfer", "TXN-99210", 4L, rcMn, 4)
        addInstallmentPayAccount(students[10], b1, mnId, 45000.0, 15000.0, 28L, 0.0, 15000.0, "cash", null, 1L, rcMn, 5)

        // Monthly fee account for Amaya's 2nd batch
        enrByStudentBatch[students[0] to b2]?.let { enrId1b ->
            val acc1b = db.financeDao().insertFeeAccount(FeeAccountEntity(enrolmentId = enrId1b, studentId = students[0], batchId = b2, totalFee = 54000.0, plan = "monthly")).toInt()
            for (m in 0 until 3) {
                db.financeDao().insertInstallment(InstallmentEntity(accountId = acc1b, no = m + 1, label = "Month ${m + 1}", dueDate = today.minusDays(20).plusMonths(m.toLong()).format(fmt), amount = 4500.0, paid = if (m == 0) 4500.0 else 0.0))
            }
        }

        // Kandy student payment today
        enrByStudentBatch[students[11] to b3]?.let { enrId12 ->
            val acc12 = db.financeDao().insertFeeAccount(FeeAccountEntity(enrolmentId = enrId12, studentId = students[11], batchId = b3, totalFee = 24000.0, plan = "custom")).toInt()
            val i12 = db.financeDao().insertInstallment(InstallmentEntity(accountId = acc12, no = 1, label = "Full Fee", dueDate = today.format(fmt), amount = 24000.0, paid = 24000.0)).toInt()
            val p12 = db.financeDao().insertPayment(PaymentEntity(
                receiptPrefix = rcKd, seqNo = 1, receiptNo = "$rcKd-0001",
                studentId = students[11], batchId = b3, accountId = acc12, branchId = kdId,
                amount = 24000.0, method = "card", reference = "POS-7712",
                paidOn = today.format(fmt), receivedBy = financeId
            )).toInt()
            db.financeDao().insertAllocation(PaymentAllocationEntity(paymentId = p12, installmentId = i12, amount = 24000.0))
        }
    }

    private suspend fun seedAttendanceAndExams(
        db: InstituteDatabase, today: LocalDate, mnId: Int, b1: Int, b2: Int, b3: Int,
        mgrId: Int, teacher1Id: Int, teacher2Id: Int, financeId: Int, students: List<Int>
    ) {
        val batchPattern = listOf(
            students[0] to b1, students[1] to b1, students[2] to b1,
            students[3] to b2, students[4] to b3, students[5] to b1,
            students[6] to b1, students[7] to b2, students[8] to b2,
            students[9] to b3, students[10] to b1, students[11] to b3
        )
        for (dOffset in (6L downTo 0L)) {
            val d = today.minusDays(dOffset).format(fmt)
            batchPattern.forEachIndexed { idx, (sid, bid) ->
                val isAbs = (idx == 2 && dOffset <= 2L) || (idx == 1 && dOffset == 4L) ||
                    (idx == 6 && dOffset == 3L) || (idx == 10 && dOffset == 5L) ||
                    (idx == 4 && dOffset == 1L) || (idx == 5 && dOffset == 2L) ||
                    (idx == 7 && dOffset == 0L)
                db.attendanceDao().upsert(AttendanceEntity(
                    studentId = sid, batchId = bid, attDate = d,
                    status = if (isAbs) "absent" else "present",
                    checkIn = if (isAbs) null else "08:26",
                    source = if (idx % 2 == 0) "fingerprint" else "manual",
                    markedBy = mgrId
                ))
            }
        }
        listOf(teacher1Id, teacher2Id, financeId).forEach { uid ->
            db.attendanceDao().upsertStaff(StaffAttendanceEntity(userId = uid, attDate = today.format(fmt), status = "present", markedBy = mgrId))
        }

        val pastExamId = db.examDao().insertExam(ExamEntity(
            branchId = mnId, batchId = b1, title = "Mid-Term Academic Writing", examType = "term",
            examDate = today.minusDays(10).format(fmt), startTime = "09:00", maxMarks = 100.0, createdBy = teacher1Id
        )).toInt()
        db.examDao().upsertMark(ExamMarkEntity(examId = pastExamId, studentId = students[0], marks = 84.0, absent = false, remarks = "Strong essay structure", enteredBy = teacher1Id))
        db.examDao().upsertMark(ExamMarkEntity(examId = pastExamId, studentId = students[1], marks = 68.0, absent = false, remarks = "Good coherence", enteredBy = teacher1Id))
        db.examDao().insertExam(ExamEntity(branchId = mnId, batchId = b1, title = "Mock Listening & Reading Test", examType = "final", examDate = today.plusDays(4).format(fmt), startTime = "08:30", maxMarks = 100.0, createdBy = teacher1Id))
        db.examDao().insertExam(ExamEntity(branchId = mnId, batchId = b2, title = "Grammar Unit 3 Assessment", examType = "term", examDate = today.plusDays(6).format(fmt), startTime = "16:00", maxMarks = 50.0, createdBy = teacher2Id))
    }

    private suspend fun seedLearningHub(db: InstituteDatabase, today: LocalDate, mnId: Int, b1: Int, teacher1Id: Int) {
        val items = listOf(
            MaterialEntity(batchId = b1, branchId = mnId, kind = "notice", title = "Speaking Mock Interviews This Friday", description = "Please arrive 15 minutes early in Hall A1 with your candidate card.", publish = true, uploadedBy = teacher1Id, createdAt = today.minusDays(1).format(fmt)),
            MaterialEntity(batchId = b1, branchId = mnId, kind = "live", title = "IELTS Task 2 Essay Workshop", description = "Live interactive feedback on thesis statements and band 8 vocabulary.", url = "https://meet.google.com/abc-defg-hij", passcode = "IELTS26", startsAt = "${today.format(fmt)} 18:00", durationMin = 60, publish = true, uploadedBy = teacher1Id, createdAt = today.format(fmt)),
            MaterialEntity(batchId = b1, branchId = mnId, kind = "lecture", title = "Academic Reading: True/False/Not Given", description = "Recorded walkthrough of 3 Cambridge passages with time-management strategies.", url = "https://www.youtube.com/watch?v=dQw4w9WgXcQ", publish = true, uploadedBy = teacher1Id, createdAt = today.minusDays(5).format(fmt)),
            MaterialEntity(batchId = b1, branchId = mnId, kind = "material", title = "Band 8+ Collocations Handbook", description = "Core academic collocations organized by essay topic.", url = "https://docs.google.com/document/d/example-handbook", attachmentName = "IELTS_Collocations_2026.pdf", publish = true, uploadedBy = teacher1Id, createdAt = today.minusDays(7).format(fmt)),
            MaterialEntity(batchId = b1, branchId = mnId, kind = "homework", title = "Task 2 Essay: Technology in Education", description = "Write a 280-word essay discussing whether digital tablets should replace printed textbooks in schools.", dueDate = today.plusDays(3).format(fmt), maxMarks = 20.0, publish = true, uploadedBy = teacher1Id, createdAt = today.minusDays(2).format(fmt))
        )
        items.forEach { db.learningDao().insertMaterial(it) }

        val quizId = db.learningDao().insertMaterial(MaterialEntity(
            batchId = b1, branchId = mnId, kind = "quiz", title = "Academic Vocabulary & Grammar Check",
            description = "Test your command of formal connectors, passive structures, and academic register.",
            timeLimitMin = 10, maxAttempts = 3, publish = true, uploadedBy = teacher1Id, createdAt = today.minusDays(3).format(fmt)
        )).toInt()

        listOf(
            QuizQuestionEntity(materialId = quizId, position = 1, question = "Which connector best introduces a contrasting academic argument?", optA = "Moreover", optB = "Nevertheless", optC = "Consequently", optD = "Similarly", correct = "B", explanation = "'Nevertheless' signals contrast, whereas 'Moreover' and 'Similarly' add supporting points."),
            QuizQuestionEntity(materialId = quizId, position = 2, question = "Choose the grammatically accurate sentence for IELTS Writing Task 1:", optA = "The number of graduates rose dramatic between 2015 and 2020.", optB = "The number of graduates rose dramatically between 2015 and 2020.", optC = "The number of graduates was rise dramatically in 2015 to 2020.", optD = "The number of graduates risen dramatically during 2015 and 2020.", correct = "B", explanation = "An adverb ('dramatically') is needed to modify the past-tense verb 'rose'."),
            QuizQuestionEntity(materialId = quizId, position = 3, question = "What does the academic verb 'mitigate' mean?", optA = "To make a problem less severe", optB = "To completely ignore evidence", optC = "To double the cost of a policy", optD = "To postpone a decision indefinitely", correct = "A", explanation = "'Mitigate' means to reduce the severity, seriousness, or painfulness of something.")
        ).forEach { db.learningDao().insertQuestion(it) }
    }

    private suspend fun seedFinancePayrollAndLogs(
        db: InstituteDatabase, today: LocalDate, mnId: Int, kdId: Int,
        adminId: Int, teacher1Id: Int, teacher2Id: Int, financeId: Int
    ) {
        val thisMonth = YearMonth.now().toString()
        val lastMonth = YearMonth.now().minusMonths(1).toString()

        db.financeDao().upsertSalaryStructure(SalaryStructureEntity(userId = teacher1Id, basic = 120000.0, allowance = 15000.0, epf = true))
        db.financeDao().upsertSalaryStructure(SalaryStructureEntity(userId = teacher2Id, basic = 95000.0, allowance = 10000.0, epf = true))
        db.financeDao().upsertSalaryStructure(SalaryStructureEntity(userId = financeId, basic = 85000.0, allowance = 8000.0, epf = true))

        val slipPrefix = "PS-${lastMonth.replace("-", "")}"
        db.financeDao().insertPayroll(PayrollEntity(
            userId = teacher1Id, branchId = mnId, period = lastMonth, basic = 120000.0, allowance = 15000.0,
            epf = 9600.0, bonus = 5000.0, net = 130400.0, status = "paid",
            paidOn = today.minusDays(15).format(fmt), method = "bank_transfer",
            slipPrefix = slipPrefix, seqNo = 1, slipNo = "$slipPrefix-0001"
        ))
        db.financeDao().insertPayroll(PayrollEntity(
            userId = teacher2Id, branchId = mnId, period = thisMonth, basic = 95000.0, allowance = 10000.0,
            epf = 7600.0, net = 97400.0, status = "draft"
        ))

        listOf(
            ExpenseEntity(branchId = mnId, category = "Rent", description = "Colombo Main Campus monthly lease", amount = 42000.0, spentOn = today.minusDays(14).format(fmt), createdBy = financeId),
            ExpenseEntity(branchId = mnId, category = "Marketing", description = "Social media intake campaign", amount = 14000.0, spentOn = today.minusDays(10).format(fmt), createdBy = financeId),
            ExpenseEntity(branchId = mnId, category = "Utilities", description = "Electricity & fiber internet", amount = 12500.0, spentOn = today.minusDays(7).format(fmt), createdBy = financeId),
            ExpenseEntity(branchId = mnId, category = "Supplies", description = "Printed workbooks & markers", amount = 8500.0, spentOn = today.minusDays(4).format(fmt), createdBy = financeId),
            ExpenseEntity(branchId = kdId, category = "Rent", description = "Kandy branch studio rent", amount = 22000.0, spentOn = today.minusDays(1).format(fmt), createdBy = financeId),
            ExpenseEntity(branchId = mnId, category = "Maintenance", description = "Projector servicing Hall A1", amount = 6000.0, spentOn = today.format(fmt), createdBy = financeId)
        ).forEach { db.financeDao().insertExpense(it) }

        db.leaveAndSubDao().insertLeave(LeaveRequestEntity(
            userId = teacher2Id, branchId = mnId, leaveType = "casual",
            fromDate = today.plusDays(2).format(fmt), toDate = today.plusDays(2).format(fmt),
            reason = "Family medical appointment", status = "pending"
        ))

        db.systemDao().insertLog(ActivityLogEntity(
            userId = adminId, userName = "Nadeesha Perera", branchId = mnId,
            action = "system_init", details = "InstitutePro initialized with sample branches, courses, students & LMS data",
            createdAt = today.format(fmt) + " 08:00"
        ))
    }
}

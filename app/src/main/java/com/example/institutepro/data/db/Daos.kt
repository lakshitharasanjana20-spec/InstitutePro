package com.example.institutepro.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface BranchDao {
    @Query("SELECT * FROM branches ORDER BY name")
    fun observeAll(): Flow<List<BranchEntity>>

    @Query("SELECT * FROM branches ORDER BY name")
    suspend fun getAll(): List<BranchEntity>

    @Query("SELECT * FROM branches WHERE id = :id")
    suspend fun getById(id: Int): BranchEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(branch: BranchEntity): Long

    @Update
    suspend fun update(branch: BranchEntity)
}

@Dao
interface UserDao {
    @Query("SELECT * FROM users ORDER BY fullName")
    fun observeAll(): Flow<List<UserEntity>>

    @Query("SELECT * FROM users WHERE email = :email LIMIT 1")
    suspend fun getByEmail(email: String): UserEntity?

    @Query("SELECT * FROM users WHERE id = :id LIMIT 1")
    suspend fun getById(id: Int): UserEntity?

    @Query("SELECT * FROM users WHERE studentId = :studentId LIMIT 1")
    suspend fun getByStudentId(studentId: Int): UserEntity?

    @Query("SELECT * FROM users WHERE role = 'teacher' AND status = 'active' ORDER BY fullName")
    suspend fun getActiveTeachers(): List<UserEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(user: UserEntity): Long

    @Update
    suspend fun update(user: UserEntity)
}

@Dao
interface RoleMenuDao {
    @Query("SELECT * FROM role_menu")
    fun observeAll(): Flow<List<RoleMenuEntity>>

    @Query("SELECT menuKey FROM role_menu WHERE role = :role")
    suspend fun getKeysForRole(role: String): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<RoleMenuEntity>)

    @Query("DELETE FROM role_menu WHERE role = :role")
    suspend fun deleteForRole(role: String)
}

@Dao
interface CourseDao {
    @Query("SELECT * FROM course_types ORDER BY id")
    fun observeTypes(): Flow<List<CourseTypeEntity>>

    @Query("SELECT * FROM courses ORDER BY typeId, id")
    fun observeCourses(): Flow<List<CourseEntity>>

    @Query("SELECT * FROM batches ORDER BY startDate DESC, id DESC")
    fun observeBatches(): Flow<List<BatchEntity>>

    @Query("SELECT * FROM course_types ORDER BY id")
    suspend fun getTypes(): List<CourseTypeEntity>

    @Query("SELECT * FROM courses ORDER BY typeId, id")
    suspend fun getCourses(): List<CourseEntity>

    @Query("SELECT * FROM batches WHERE id = :id")
    suspend fun getBatchById(id: Int): BatchEntity?

    @Query("SELECT * FROM batches WHERE status = 'active'")
    suspend fun getActiveBatches(): List<BatchEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertType(type: CourseTypeEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCourse(course: CourseEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertBatch(batch: BatchEntity): Long

    @Update
    suspend fun updateBatch(batch: BatchEntity)

    @Query("UPDATE batches SET status = 'completed' WHERE status = 'active' AND endDate < :today")
    suspend fun completeExpiredBatches(today: String)
}

@Dao
interface StudentDao {
    @Query("SELECT * FROM students ORDER BY id DESC")
    fun observeStudents(): Flow<List<StudentEntity>>

    @Query("SELECT * FROM enrolments ORDER BY id DESC")
    fun observeEnrolments(): Flow<List<EnrolmentEntity>>

    @Query("SELECT * FROM students WHERE id = :id")
    suspend fun getById(id: Int): StudentEntity?

    @Query("SELECT COALESCE(MAX(seqNo), 0) FROM students WHERE idPrefix = :prefix")
    suspend fun getMaxSeqForPrefix(prefix: String): Int

    @Query("SELECT COUNT(*) FROM enrolments WHERE batchId = :batchId")
    suspend fun countEnrolmentsInBatch(batchId: Int): Int

    @Query("SELECT * FROM enrolments WHERE batchId = :batchId")
    suspend fun getEnrolmentsByBatch(batchId: Int): List<EnrolmentEntity>

    @Query("SELECT * FROM enrolments WHERE studentId = :studentId")
    suspend fun getEnrolmentsByStudent(studentId: Int): List<EnrolmentEntity>

    @Query("SELECT * FROM enrolments WHERE studentId = :studentId AND batchId = :batchId LIMIT 1")
    suspend fun getEnrolment(studentId: Int, batchId: Int): EnrolmentEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertStudent(student: StudentEntity): Long

    @Update
    suspend fun updateStudent(student: StudentEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertEnrolment(enrolment: EnrolmentEntity): Long
}

@Dao
interface AttendanceDao {
    @Query("SELECT * FROM attendance ORDER BY attDate DESC, id DESC")
    fun observeAttendance(): Flow<List<AttendanceEntity>>

    @Query("SELECT * FROM staff_attendance ORDER BY attDate DESC, id DESC")
    fun observeStaffAttendance(): Flow<List<StaffAttendanceEntity>>

    @Query("SELECT * FROM attendance WHERE studentId = :studentId AND batchId = :batchId AND attDate = :date LIMIT 1")
    suspend fun getRecord(studentId: Int, batchId: Int, date: String): AttendanceEntity?

    @Query("SELECT * FROM attendance WHERE studentId = :studentId AND batchId = :batchId AND attDate <= :date ORDER BY attDate DESC LIMIT 2")
    suspend fun getLastTwoRecords(studentId: Int, batchId: Int, date: String): List<AttendanceEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(record: AttendanceEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertStaff(record: StaffAttendanceEntity)
}

@Dao
interface LeaveAndSubDao {
    @Query("SELECT * FROM leave_requests ORDER BY id DESC")
    fun observeLeaves(): Flow<List<LeaveRequestEntity>>

    @Query("SELECT * FROM substitutions ORDER BY classDate ASC, id DESC")
    fun observeSubstitutions(): Flow<List<SubstitutionEntity>>

    @Query("SELECT * FROM leave_requests WHERE id = :id")
    suspend fun getLeaveById(id: Int): LeaveRequestEntity?

    @Query("SELECT * FROM leave_requests WHERE userId = :userId AND status IN ('pending', 'approved') AND NOT (toDate < :from OR fromDate > :to)")
    suspend fun getOverlappingLeaves(userId: Int, from: String, to: String): List<LeaveRequestEntity>

    @Query("SELECT * FROM leave_requests WHERE userId = :userId AND status = 'approved' AND fromDate <= :date AND toDate >= :date")
    suspend fun getApprovedLeavesOnDate(userId: Int, date: String): List<LeaveRequestEntity>

    @Query("SELECT * FROM substitutions WHERE substituteId = :teacherId AND classDate = :date")
    suspend fun getSubsForTeacherOnDate(teacherId: Int, date: String): List<SubstitutionEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertLeave(leave: LeaveRequestEntity): Long

    @Update
    suspend fun updateLeave(leave: LeaveRequestEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSubstitution(sub: SubstitutionEntity): Long

    @Query("DELETE FROM substitutions WHERE id = :id")
    suspend fun deleteSubstitutionById(id: Int)

    @Query("DELETE FROM substitutions WHERE leaveId = :leaveId")
    suspend fun deleteSubstitutionsByLeave(leaveId: Int)
}

@Dao
interface ExamDao {
    @Query("SELECT * FROM exams ORDER BY examDate DESC, id DESC")
    fun observeExams(): Flow<List<ExamEntity>>

    @Query("SELECT * FROM exam_marks")
    fun observeExamMarks(): Flow<List<ExamMarkEntity>>

    @Query("SELECT * FROM exams WHERE id = :id")
    suspend fun getExamById(id: Int): ExamEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertExam(exam: ExamEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMark(mark: ExamMarkEntity)
}

@Dao
interface FinanceDao {
    @Query("SELECT * FROM fee_accounts ORDER BY id DESC")
    fun observeFeeAccounts(): Flow<List<FeeAccountEntity>>

    @Query("SELECT * FROM installments ORDER BY dueDate ASC, no ASC")
    fun observeInstallments(): Flow<List<InstallmentEntity>>

    @Query("SELECT * FROM payments ORDER BY id DESC")
    fun observePayments(): Flow<List<PaymentEntity>>

    @Query("SELECT * FROM salary_structures")
    fun observeSalaryStructures(): Flow<List<SalaryStructureEntity>>

    @Query("SELECT * FROM payroll ORDER BY period DESC, id DESC")
    fun observePayroll(): Flow<List<PayrollEntity>>

    @Query("SELECT * FROM expenses ORDER BY spentOn DESC, id DESC")
    fun observeExpenses(): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM fee_accounts WHERE enrolmentId = :enrolmentId LIMIT 1")
    suspend fun getAccountByEnrolment(enrolmentId: Int): FeeAccountEntity?

    @Query("SELECT * FROM fee_accounts WHERE id = :id LIMIT 1")
    suspend fun getAccountById(id: Int): FeeAccountEntity?

    @Query("SELECT * FROM installments WHERE accountId = :accountId ORDER BY dueDate ASC, no ASC")
    suspend fun getInstallmentsByAccount(accountId: Int): List<InstallmentEntity>

    @Query("SELECT * FROM installments WHERE accountId = :accountId ORDER BY dueDate DESC, no DESC")
    suspend fun getInstallmentsByAccountDesc(accountId: Int): List<InstallmentEntity>

    @Query("SELECT COALESCE(MAX(seqNo), 0) FROM payments WHERE receiptPrefix = :prefix")
    suspend fun getMaxReceiptSeq(prefix: String): Int

    @Query("SELECT * FROM payments WHERE id = :id LIMIT 1")
    suspend fun getPaymentById(id: Int): PaymentEntity?

    @Query("SELECT * FROM payment_allocations WHERE paymentId = :paymentId")
    suspend fun getAllocationsByPayment(paymentId: Int): List<PaymentAllocationEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertFeeAccount(account: FeeAccountEntity): Long

    @Update
    suspend fun updateFeeAccount(account: FeeAccountEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertInstallment(installment: InstallmentEntity): Long

    @Update
    suspend fun updateInstallment(installment: InstallmentEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPayment(payment: PaymentEntity): Long

    @Update
    suspend fun updatePayment(payment: PaymentEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAllocation(allocation: PaymentAllocationEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSalaryStructure(structure: SalaryStructureEntity)

    @Query("SELECT * FROM salary_structures")
    suspend fun getAllSalaryStructures(): List<SalaryStructureEntity>

    @Query("SELECT * FROM payroll WHERE userId = :userId AND period = :period LIMIT 1")
    suspend fun getPayrollForUserPeriod(userId: Int, period: String): PayrollEntity?

    @Query("SELECT * FROM payroll WHERE id = :id LIMIT 1")
    suspend fun getPayrollById(id: Int): PayrollEntity?

    @Query("SELECT COALESCE(MAX(seqNo), 0) FROM payroll WHERE slipPrefix = :prefix")
    suspend fun getMaxSlipSeq(prefix: String): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPayroll(payroll: PayrollEntity): Long

    @Update
    suspend fun updatePayroll(payroll: PayrollEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertExpense(expense: ExpenseEntity): Long

    @Query("DELETE FROM expenses WHERE id = :id")
    suspend fun deleteExpenseById(id: Int)
}

@Dao
interface LearningDao {
    @Query("SELECT * FROM materials ORDER BY id DESC")
    fun observeMaterials(): Flow<List<MaterialEntity>>

    @Query("SELECT * FROM quiz_questions ORDER BY position ASC, id ASC")
    fun observeQuizQuestions(): Flow<List<QuizQuestionEntity>>

    @Query("SELECT * FROM quiz_attempts ORDER BY id DESC")
    fun observeQuizAttempts(): Flow<List<QuizAttemptEntity>>

    @Query("SELECT * FROM assignment_submissions ORDER BY id DESC")
    fun observeSubmissions(): Flow<List<AssignmentSubmissionEntity>>

    @Query("SELECT * FROM materials WHERE id = :id LIMIT 1")
    suspend fun getMaterialById(id: Int): MaterialEntity?

    @Query("SELECT * FROM quiz_questions WHERE materialId = :materialId ORDER BY position ASC, id ASC")
    suspend fun getQuestionsForQuiz(materialId: Int): List<QuizQuestionEntity>

    @Query("SELECT * FROM quiz_attempts WHERE id = :id LIMIT 1")
    suspend fun getAttemptById(id: Int): QuizAttemptEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertMaterial(material: MaterialEntity): Long

    @Update
    suspend fun updateMaterial(material: MaterialEntity)

    @Query("DELETE FROM materials WHERE id = :id")
    suspend fun deleteMaterialById(id: Int)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertQuestion(question: QuizQuestionEntity): Long

    @Query("DELETE FROM quiz_questions WHERE id = :id")
    suspend fun deleteQuestionById(id: Int)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAttempt(attempt: QuizAttemptEntity): Long

    @Update
    suspend fun updateAttempt(attempt: QuizAttemptEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSubmission(submission: AssignmentSubmissionEntity)
}

@Dao
interface SystemDao {
    @Query("SELECT * FROM activity_logs ORDER BY id DESC LIMIT 300")
    fun observeLogs(): Flow<List<ActivityLogEntity>>

    @Query("SELECT * FROM settings")
    fun observeSettings(): Flow<List<SettingEntity>>

    @Query("SELECT v FROM settings WHERE k = :key LIMIT 1")
    suspend fun getSetting(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSetting(setting: SettingEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertLog(log: ActivityLogEntity): Long
}

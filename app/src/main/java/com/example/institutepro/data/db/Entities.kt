package com.example.institutepro.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "branches", indices = [Index(value = ["code"], unique = true)])
data class BranchEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val code: String,
    val name: String,
    val address: String = "",
    val phone: String = "",
    val status: String = "active"
)

@Entity(tableName = "users", indices = [Index(value = ["email"], unique = true)])
data class UserEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val branchId: Int? = null,
    val role: String, // super_admin, admin, branch_manager, administrator, receptionist, teacher, finance, other, student
    val fullName: String,
    val email: String,
    val passwordHash: String,
    val phone: String = "",
    val nic: String = "",
    val address: String = "",
    val jobTitle: String = "",
    val joinedOn: String = "",
    val fingerprintId: String? = null,
    val studentId: Int? = null,
    val status: String = "active",
    val mustChangePw: Boolean = false,
    val failedLogins: Int = 0,
    val lockedUntilMillis: Long? = null,
    val lastLoginAt: String? = null
)

@Entity(tableName = "role_menu", primaryKeys = ["role", "menuKey"])
data class RoleMenuEntity(
    val role: String,
    val menuKey: String
)

@Entity(tableName = "course_types", indices = [Index(value = ["code"], unique = true)])
data class CourseTypeEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val code: String, // AI, GR, SC
    val name: String
)

@Entity(tableName = "courses")
data class CourseEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val typeId: Int,
    val code: String,
    val name: String
)

@Entity(tableName = "batches")
data class BatchEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val branchId: Int,
    val courseId: Int,
    val batchYear: Int,
    val name: String,
    val startDate: String, // yyyy-MM-dd
    val durationMonths: Int,
    val endDate: String,
    val startTime: String, // HH:mm
    val endTime: String,
    val classDays: String, // e.g., "Mon,Wed,Fri"
    val fee: Double,
    val feePlan: String, // installments_3, monthly, custom
    val maxStudents: Int? = null,
    val teacherId: Int? = null,
    val room: String = "",
    val status: String = "active", // active, completed
    val portalOn: Boolean = true,
    val accessToken: String = ""
)

@Entity(tableName = "students", indices = [Index(value = ["studentCode"], unique = true)])
data class StudentEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val studentCode: String,
    val idPrefix: String,
    val seqNo: Int,
    val branchId: Int,
    val batchId: Int,
    val firstName: String,
    val lastName: String,
    val fullName: String,
    val gender: String = "Male",
    val dateOfBirth: String, // yyyy-MM-dd
    val address: String = "",
    val phone: String,
    val email: String? = null,
    val nic: String? = null,
    val guardianName: String? = null,
    val guardianRelation: String? = null,
    val guardianPhone: String? = null,
    val fingerprintId: String? = null,
    val status: String = "active",
    val registeredAt: String
)

@Entity(tableName = "enrolments", indices = [Index(value = ["studentId", "batchId"], unique = true)])
data class EnrolmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val studentId: Int,
    val batchId: Int,
    val enrolledOn: String
)

@Entity(tableName = "attendance", indices = [Index(value = ["studentId", "batchId", "attDate"], unique = true)])
data class AttendanceEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val studentId: Int,
    val batchId: Int,
    val attDate: String,
    val status: String, // present, absent
    val checkIn: String? = null,
    val checkOut: String? = null,
    val source: String = "manual", // manual, fingerprint
    val markedBy: Int? = null
)

@Entity(tableName = "staff_attendance", indices = [Index(value = ["userId", "attDate"], unique = true)])
data class StaffAttendanceEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val userId: Int,
    val attDate: String,
    val status: String, // present, half_day, absent
    val markedBy: Int? = null
)

@Entity(tableName = "leave_requests")
data class LeaveRequestEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val userId: Int,
    val branchId: Int? = null,
    val leaveType: String, // casual, medical, annual, other
    val fromDate: String,
    val toDate: String,
    val reason: String,
    val status: String = "pending", // pending, approved, rejected, cancelled
    val decidedBy: Int? = null,
    val decidedAt: String? = null,
    val decisionNote: String? = null
)

@Entity(tableName = "substitutions", indices = [Index(value = ["batchId", "classDate"], unique = true)])
data class SubstitutionEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val batchId: Int,
    val classDate: String,
    val originalTeacherId: Int? = null,
    val substituteId: Int,
    val leaveId: Int? = null,
    val note: String? = null,
    val createdBy: Int? = null
)

@Entity(tableName = "exams")
data class ExamEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val branchId: Int,
    val batchId: Int,
    val title: String,
    val examType: String, // term, project, final, other
    val examDate: String,
    val startTime: String? = null,
    val maxMarks: Double = 100.0,
    val createdBy: Int? = null
)

@Entity(tableName = "exam_marks", indices = [Index(value = ["examId", "studentId"], unique = true)])
data class ExamMarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val examId: Int,
    val studentId: Int,
    val marks: Double? = null,
    val absent: Boolean = false,
    val remarks: String? = null,
    val enteredBy: Int? = null
)

@Entity(tableName = "fee_accounts", indices = [Index(value = ["enrolmentId"], unique = true)])
data class FeeAccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val enrolmentId: Int,
    val studentId: Int,
    val batchId: Int,
    val totalFee: Double,
    val scholarship: Double = 0.0,
    val plan: String
)

@Entity(tableName = "installments")
data class InstallmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val accountId: Int,
    val no: Int,
    val label: String,
    val dueDate: String,
    val amount: Double,
    val paid: Double = 0.0
)

@Entity(tableName = "payments", indices = [Index(value = ["receiptNo"], unique = true)])
data class PaymentEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val receiptPrefix: String,
    val seqNo: Int,
    val receiptNo: String,
    val studentId: Int,
    val batchId: Int,
    val accountId: Int,
    val branchId: Int,
    val amount: Double,
    val method: String, // cash, card, bank_transfer, other
    val reference: String? = null,
    val paidOn: String,
    val note: String? = null,
    val receivedBy: Int? = null,
    val voidedAt: String? = null,
    val voidedBy: Int? = null,
    val voidReason: String? = null
)

@Entity(tableName = "payment_allocations")
data class PaymentAllocationEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val paymentId: Int,
    val installmentId: Int,
    val amount: Double
)

@Entity(tableName = "salary_structures", indices = [Index(value = ["userId"], unique = true)])
data class SalaryStructureEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val userId: Int,
    val basic: Double,
    val allowance: Double,
    val epf: Boolean
)

@Entity(tableName = "payroll", indices = [Index(value = ["userId", "period"], unique = true)])
data class PayrollEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val userId: Int,
    val branchId: Int? = null,
    val period: String, // yyyy-MM
    val basic: Double,
    val allowance: Double,
    val epf: Double,
    val bonus: Double = 0.0,
    val overtime: Double = 0.0,
    val otherDeduction: Double = 0.0,
    val advance: Double = 0.0,
    val net: Double,
    val status: String = "draft", // draft, paid
    val paidOn: String? = null,
    val method: String? = null,
    val slipPrefix: String? = null,
    val seqNo: Int? = null,
    val slipNo: String? = null,
    val note: String? = null
)

@Entity(tableName = "expenses")
data class ExpenseEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val branchId: Int,
    val category: String,
    val description: String? = null,
    val amount: Double,
    val spentOn: String,
    val createdBy: Int? = null
)

@Entity(tableName = "materials")
data class MaterialEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val batchId: Int,
    val branchId: Int,
    val kind: String, // lecture, live, material, homework, quiz, reading, notice
    val title: String,
    val description: String? = null,
    val url: String? = null,
    val passcode: String? = null,
    val startsAt: String? = null,
    val durationMin: Int? = null,
    val dueDate: String? = null,
    val maxMarks: Double? = null,
    val timeLimitMin: Int? = null,
    val opensAt: String? = null,
    val closesAt: String? = null,
    val maxAttempts: Int = 1,
    val publish: Boolean = true,
    val attachmentName: String? = null,
    val uploadedBy: Int? = null,
    val createdAt: String
)

@Entity(tableName = "quiz_questions")
data class QuizQuestionEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val materialId: Int,
    val position: Int,
    val question: String,
    val optA: String,
    val optB: String,
    val optC: String,
    val optD: String,
    val correct: String, // A, B, C, D
    val explanation: String? = null
)

@Entity(tableName = "quiz_attempts")
data class QuizAttemptEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val materialId: Int,
    val studentId: Int,
    val startedAtMillis: Long,
    val submittedAt: String? = null,
    val score: Int = 0,
    val total: Int = 0,
    val answersJson: String = "", // format: "qid:A,qid:B"
    val overtime: Boolean = false
)

@Entity(tableName = "assignment_submissions", indices = [Index(value = ["materialId", "studentId"], unique = true)])
data class AssignmentSubmissionEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val materialId: Int,
    val studentId: Int,
    val answerText: String = "",
    val linkUrl: String? = null,
    val submittedAt: String,
    val marks: Double? = null,
    val feedback: String? = null,
    val gradedBy: Int? = null
)

@Entity(tableName = "activity_logs")
data class ActivityLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val userId: Int? = null,
    val userName: String? = null,
    val branchId: Int? = null,
    val action: String,
    val details: String = "",
    val createdAt: String
)

@Entity(tableName = "settings")
data class SettingEntity(
    @PrimaryKey val k: String,
    val v: String
)

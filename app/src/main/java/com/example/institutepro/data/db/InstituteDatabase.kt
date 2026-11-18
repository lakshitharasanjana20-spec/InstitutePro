package com.example.institutepro.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        BranchEntity::class,
        UserEntity::class,
        RoleMenuEntity::class,
        CourseTypeEntity::class,
        CourseEntity::class,
        BatchEntity::class,
        StudentEntity::class,
        EnrolmentEntity::class,
        AttendanceEntity::class,
        StaffAttendanceEntity::class,
        LeaveRequestEntity::class,
        SubstitutionEntity::class,
        ExamEntity::class,
        ExamMarkEntity::class,
        FeeAccountEntity::class,
        InstallmentEntity::class,
        PaymentEntity::class,
        PaymentAllocationEntity::class,
        SalaryStructureEntity::class,
        PayrollEntity::class,
        ExpenseEntity::class,
        MaterialEntity::class,
        QuizQuestionEntity::class,
        QuizAttemptEntity::class,
        AssignmentSubmissionEntity::class,
        ActivityLogEntity::class,
        SettingEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class InstituteDatabase : RoomDatabase() {
    abstract fun branchDao(): BranchDao
    abstract fun userDao(): UserDao
    abstract fun roleMenuDao(): RoleMenuDao
    abstract fun courseDao(): CourseDao
    abstract fun studentDao(): StudentDao
    abstract fun attendanceDao(): AttendanceDao
    abstract fun leaveAndSubDao(): LeaveAndSubDao
    abstract fun examDao(): ExamDao
    abstract fun financeDao(): FinanceDao
    abstract fun learningDao(): LearningDao
    abstract fun systemDao(): SystemDao

    companion object {
        @Volatile
        private var INSTANCE: InstituteDatabase? = null

        fun getInstance(context: Context): InstituteDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    InstituteDatabase::class.java,
                    "institute_pro.db"
                ).fallbackToDestructiveMigration(true).build().also { INSTANCE = it }
            }
        }
    }
}

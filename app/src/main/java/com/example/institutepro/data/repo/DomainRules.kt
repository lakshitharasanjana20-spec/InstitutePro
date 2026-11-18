package com.example.institutepro.data.repo

import com.example.institutepro.data.db.SettingEntity
import java.text.NumberFormat
import java.time.LocalDate
import java.time.Period
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.round

object DomainRules {
    val NIC_REGEX = Regex("""^(\d{9}[VvXx]|\d{12})$""")

    val ROLE_LABELS = mapOf(
        "super_admin" to "Super Admin",
        "admin" to "Admin",
        "branch_manager" to "Branch Manager",
        "administrator" to "Administrator",
        "receptionist" to "Receptionist",
        "teacher" to "Teacher",
        "finance" to "Finance",
        "other" to "Other",
        "student" to "Student"
    )

    val EXPENSE_CATEGORIES = listOf("Rent", "Utilities", "Marketing", "Supplies", "Maintenance", "Other")
    val LEAVE_TYPES = listOf("casual" to "Casual", "medical" to "Medical", "annual" to "Annual", "other" to "Other")
    val PAYMENT_METHODS = listOf("cash" to "Cash", "card" to "Card", "bank_transfer" to "Bank transfer", "other" to "Other")
    val EXAM_TYPES = listOf("term" to "Term / unit test", "project" to "Project", "final" to "Final exam", "other" to "Other")

    val LEARN_TYPES = listOf(
        "lecture" to "Recorded Lectures",
        "live" to "Live Classes",
        "material" to "Course Materials",
        "homework" to "Assignments",
        "quiz" to "Quizzes & MCQ",
        "reading" to "Extra Readings",
        "notice" to "Announcements"
    )

    private val ACTION_ROLES = mapOf(
        "register_student" to setOf("super_admin", "admin", "branch_manager", "administrator", "receptionist"),
        "manage_batches" to setOf("super_admin", "admin", "branch_manager", "administrator"),
        "manage_users" to setOf("super_admin", "admin", "branch_manager"),
        "mark_attendance" to setOf("super_admin", "admin", "branch_manager", "administrator", "receptionist"),
        "take_payment" to setOf("super_admin", "admin", "branch_manager", "administrator", "receptionist", "finance"),
        "set_scholarship" to setOf("super_admin", "admin", "branch_manager"),
        "schedule_exam" to setOf("super_admin", "admin", "branch_manager", "administrator"),
        "enter_marks" to setOf("super_admin", "admin", "branch_manager", "administrator", "teacher"),
        "manage_payroll" to setOf("super_admin", "admin", "branch_manager", "finance"),
        "manage_expenses" to setOf("super_admin", "admin", "branch_manager", "finance"),
        "void_payment" to setOf("super_admin", "admin", "branch_manager"),
        "mark_staff_attendance" to setOf("super_admin", "admin", "branch_manager", "administrator"),
        "approve_leave" to setOf("super_admin", "admin", "branch_manager"),
        "manage_substitutes" to setOf("super_admin", "admin", "branch_manager", "administrator"),
        "upload_material" to setOf("super_admin", "admin", "branch_manager", "administrator", "teacher"),
        "manage_student_accounts" to setOf("super_admin", "admin", "branch_manager", "administrator", "receptionist"),
        "edit_student" to setOf("super_admin", "admin", "branch_manager", "administrator", "receptionist"),
        "delete_student" to setOf("super_admin", "admin")
    )

    fun can(role: String, action: String): Boolean = ACTION_ROLES[action]?.contains(role) == true
    fun isOwner(role: String): Boolean = role == "super_admin" || role == "admin"
    fun isTeacher(role: String): Boolean = role == "teacher"
    fun isStudent(role: String): Boolean = role == "student"

    fun ageFromDob(dob: String): Int? = runCatching {
        val parsed = LocalDate.parse(dob, DateTimeFormatter.ISO_LOCAL_DATE)
        if (parsed.isAfter(LocalDate.now())) return null
        Period.between(parsed, LocalDate.now()).years
    }.getOrNull()

    fun gradeFor(pct: Double, settings: List<SettingEntity>): String {
        val map = settings.associate { it.k to (it.v.toDoubleOrNull() ?: 0.0) }
        val a = map["grade_a"] ?: 75.0
        val b = map["grade_b"] ?: 65.0
        val c = map["grade_c"] ?: 50.0
        val s = map["grade_s"] ?: 35.0
        return when {
            pct >= a -> "A"
            pct >= b -> "B"
            pct >= c -> "C"
            pct >= s -> "S"
            else -> "W"
        }
    }

    fun isPass(pct: Double, settings: List<SettingEntity>): Boolean {
        val c = settings.firstOrNull { it.k == "grade_c" }?.v?.toDoubleOrNull() ?: 50.0
        return pct >= c
    }

    fun examOutcome(examType: String, courseTypeCode: String, pct: Double, settings: List<SettingEntity>): String {
        if (examType != "final") return ""
        return when (courseTypeCode) {
            "AI" -> if (isPass(pct, settings)) "Course completed" else "Repeat required"
            "GR" -> "Eligible for next grade"
            else -> ""
        }
    }

    fun formatMoney(amount: Double, currency: String = "LKR"): String {
        val nf = NumberFormat.getNumberInstance(Locale.US).apply {
            minimumFractionDigits = 2
            maximumFractionDigits = 2
        }
        return "$currency ${nf.format(round(amount * 100) / 100.0)}"
    }

    fun round2(v: Double): Double = round(v * 100.0) / 100.0

    /**
     * Parses fingerprint machine CSV/TXT lines: "id,yyyy-mm-dd hh:mm:ss" or "id,dd/mm/yyyy hh:mm"
     * Returns map of normalized fingerprintId -> date -> sorted list of HH:mm times.
     */
    fun parsePunches(raw: String): Map<String, Map<String, List<String>>> {
        val out = mutableMapOf<String, MutableMap<String, MutableList<String>>>()
        val lineRegex = Regex("""(\d{4}-\d{2}-\d{2}|\d{2}/\d{2}/\d{4})[ T,;\t"]*(\d{1,2}):(\d{2})(?::\d{2})?""")
        raw.lines().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty()) return@forEach
            val parts = trimmed.split(Regex("""[,;\t]+"""))
            val idRaw = parts.firstOrNull()?.trim(' ', '"', '\'', '\uFEFF') ?: return@forEach
            if (idRaw.isEmpty() || idRaw.contains('-') || idRaw.contains('/') || idRaw.contains(':')) return@forEach
            val m = lineRegex.find(trimmed) ?: return@forEach
            var d = m.groupValues[1]
            if (d.contains('/')) {
                val dp = d.split('/')
                if (dp.size == 3) d = "${dp[2]}-${dp[1]}-${dp[0]}"
            }
            val validDate = runCatching { LocalDate.parse(d) }.isSuccess
            if (!validDate) return@forEach
            val hh = m.groupValues[2].padStart(2, '0')
            val mm = m.groupValues[3]
            val fpKey = idRaw.trimStart('0').ifEmpty { "0" }
            val dateMap = out.getOrPut(fpKey) { mutableMapOf() }
            dateMap.getOrPut(d) { mutableListOf() }.add("$hh:$mm")
        }
        return out.mapValues { (_, dm) -> dm.mapValues { (_, times) -> times.sorted() } }
    }
}

data class AttendanceAlerts(
    val reactivated: List<Pair<String, String>> = emptyList(),
    val markedInactive: List<Pair<String, String>> = emptyList()
)

package com.example.institutepro.ui.components

import com.example.institutepro.data.db.AttendanceEntity
import com.example.institutepro.data.db.ExpenseEntity
import com.example.institutepro.data.db.PaymentEntity
import com.example.institutepro.data.db.StudentEntity
import com.example.institutepro.ui.theme.*
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

object ChartBuilders {
    private val shortDateFmt = DateTimeFormatter.ofPattern("MMM d", Locale.US)
    private val monthLabelFmt = DateTimeFormatter.ofPattern("MMM yy", Locale.US)

    fun buildIncomeExpenseSeries(
        payments: List<PaymentEntity>,
        expenses: List<ExpenseEntity>,
        branchScope: Int?,
        fromDate: String,
        toDate: String
    ): List<DateSeriesPoint> {
        val payByDate = payments
            .filter { it.voidedAt == null && (branchScope == null || it.branchId == branchScope) && it.paidOn in fromDate..toDate }
            .groupBy { it.paidOn }
            .mapValues { (_, list) -> list.sumOf { it.amount } }

        val expByDate = expenses
            .filter { (branchScope == null || it.branchId == branchScope) && it.spentOn in fromDate..toDate }
            .groupBy { it.spentOn }
            .mapValues { (_, list) -> list.sumOf { it.amount } }

        val allDates = (payByDate.keys + expByDate.keys).toSortedSet()
        return allDates.map { d ->
            val lbl = runCatching { LocalDate.parse(d).format(shortDateFmt) }.getOrElse { d.takeLast(5) }
            DateSeriesPoint(
                dateLabel = lbl,
                fullDate = d,
                income = payByDate[d] ?: 0.0,
                expense = expByDate[d] ?: 0.0
            )
        }
    }

    fun buildStudentGrowthSeries(
        students: List<StudentEntity>,
        monthsCount: Int = 6
    ): List<StudentGrowthPoint> {
        val currentYm = YearMonth.now()
        val months = ((monthsCount - 1) downTo 0).map { offset -> currentYm.minusMonths(offset.toLong()) }
        return months.map { ym ->
            val cutoff = ym.atEndOfMonth().toString()
            val cumulative = students.count { it.registeredAt <= cutoff }
            StudentGrowthPoint(
                label = ym.format(monthLabelFmt),
                totalStudents = cumulative
            )
        }
    }

    fun buildBoysGirlsAttendanceSeries(
        attendance: List<AttendanceEntity>,
        students: List<StudentEntity>,
        visibleBatchIds: Set<Int>,
        daysCount: Int = 7
    ): List<GenderAttendanceDay> {
        val today = LocalDate.now()
        val genderById = students.associate { it.id to it.gender }
        return ((daysCount - 1L) downTo 0L).map { offset ->
            val d = today.minusDays(offset)
            val ds = d.toString()
            val label = if (offset == 0L) "Today" else d.dayOfWeek.name.take(3).lowercase().replaceFirstChar { it.uppercase() }
            val presentRecs = attendance.filter {
                it.attDate == ds && it.batchId in visibleBatchIds && it.status == "present"
            }
            val boys = presentRecs.count { genderById[it.studentId].equals("Male", ignoreCase = true) }
            val girls = presentRecs.count { genderById[it.studentId].equals("Female", ignoreCase = true) }
            GenderAttendanceDay(dayLabel = label, boysPresent = boys, girlsPresent = girls)
        }
    }

    fun buildDailyAttendanceSlices(present: Int, absent: Int): List<ChartSlice> = listOf(
        ChartSlice("Present", present.toDouble(), CobaltPrimary),
        ChartSlice("Absent", absent.toDouble(), SkyLight)
    )
}

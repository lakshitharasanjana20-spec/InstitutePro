package com.example.institutepro.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.institutepro.data.repo.DomainRules
import com.example.institutepro.ui.theme.*
import kotlin.math.max
import kotlin.math.roundToInt

data class ChartSlice(
    val label: String,
    val value: Double,
    val color: Color
)

data class DateSeriesPoint(
    val dateLabel: String,
    val fullDate: String,
    val income: Double,
    val expense: Double
)

data class GenderAttendanceDay(
    val dayLabel: String,
    val boysPresent: Int,
    val girlsPresent: Int
)

data class StudentGrowthPoint(
    val label: String,
    val totalStudents: Int
)

data class BranchComparisonPoint(
    val label: String,
    val primaryVal: Double,
    val secondaryVal: Double
)

private fun compactNumber(v: Double): String {
    return when {
        v >= 1_000_000 -> String.format("%.1fM", v / 1_000_000.0)
        v >= 1_000 -> String.format("%.0fk", v / 1_000.0)
        else -> v.roundToInt().toString()
    }
}

@Composable
fun DoughnutChart(
    slices: List<ChartSlice>,
    centerValue: String,
    centerLabel: String,
    modifier: Modifier = Modifier,
    formatAsMoney: Boolean = false,
    currency: String = "LKR"
) {
    val total = slices.sumOf { it.value }.coerceAtLeast(0.0)
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(
            modifier = Modifier.size(136.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize().padding(6.dp)) {
                val strokePx = 22.dp.toPx()
                val arcSize = Size(size.width - strokePx, size.height - strokePx)
                val topLeft = Offset(strokePx / 2f, strokePx / 2f)

                drawArc(
                    color = CobaltContainer,
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokePx, cap = StrokeCap.Butt)
                )

                if (total > 0.0) {
                    var startAngle = -90f
                    slices.filter { it.value > 0.0 }.forEach { slice ->
                        val sweep = ((slice.value / total) * 360.0).toFloat()
                        val gap = if (slices.count { it.value > 0.0 } > 1) 2.5f else 0f
                        drawArc(
                            color = slice.color,
                            startAngle = startAngle + gap / 2f,
                            sweepAngle = (sweep - gap).coerceAtLeast(1f),
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(width = strokePx, cap = StrokeCap.Butt)
                        )
                        startAngle += sweep
                    }
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = centerValue,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = CobaltDark
                )
                Text(
                    text = centerLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            slices.forEach { slice ->
                val pct = if (total > 0.0) ((slice.value / total) * 100.0).roundToInt() else 0
                val valText = if (formatAsMoney) DomainRules.formatMoney(slice.value, currency) else slice.value.roundToInt().toString()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Box(Modifier.size(10.dp).background(slice.color, CircleShape))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = slice.label,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = "$valText ($pct%)",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = CobaltDark
                    )
                }
            }
        }
    }
}

@Composable
fun PieChart(
    slices: List<ChartSlice>,
    modifier: Modifier = Modifier,
    formatAsMoney: Boolean = true,
    currency: String = "LKR"
) {
    val total = slices.sumOf { it.value }.coerceAtLeast(0.0)
    if (total <= 0.0) {
        Text(
            "No chart data available for this selection.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Canvas(modifier = Modifier.size(126.dp)) {
            var start = -90f
            val nonZero = slices.filter { it.value > 0.0 }
            nonZero.forEach { slice ->
                val sweep = ((slice.value / total) * 360.0).toFloat()
                drawArc(
                    color = slice.color,
                    startAngle = start,
                    sweepAngle = sweep,
                    useCenter = true
                )
                if (nonZero.size > 1) {
                    drawArc(
                        color = Color.White,
                        startAngle = start,
                        sweepAngle = sweep,
                        useCenter = true,
                        style = Stroke(width = 2.dp.toPx())
                    )
                }
                start += sweep
            }
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            slices.forEach { slice ->
                val pct = ((slice.value / total) * 100.0).roundToInt()
                val formatted = if (formatAsMoney) DomainRules.formatMoney(slice.value, currency) else slice.value.roundToInt().toString()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Box(Modifier.size(10.dp).background(slice.color, RoundedCornerShape(3.dp)))
                        Spacer(Modifier.width(7.dp))
                        Text(
                            text = "${slice.label} · $pct%",
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = formatted,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = CobaltDark
                    )
                }
            }
        }
    }
}

@Composable
fun IncomeExpenseLineChart(
    points: List<DateSeriesPoint>,
    currency: String,
    modifier: Modifier = Modifier
) {
    if (points.isEmpty()) {
        Text(
            "No income or expense records for this period.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }

    var selectedIdx by remember(points) { mutableStateOf(points.lastIndex) }
    val textMeasurer = rememberTextMeasurer()
    val axisTextStyle = TextStyle(fontSize = 10.sp, color = Color(0xFF475569))
    val maxVal = max(
        points.maxOfOrNull { max(it.income, it.expense) } ?: 1.0,
        1000.0
    ) * 1.15

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Legend
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.size(12.dp, 4.dp).background(CobaltPrimary, RoundedCornerShape(2.dp)))
                    Text("Income", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = CobaltDark)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.size(12.dp, 4.dp).background(SkyBlue, RoundedCornerShape(2.dp)))
                    Text("Expenses", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = OceanBlue)
                }
            }
            Text("Y: $currency  |  X: Date", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        // Line Chart Canvas
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(190.dp)
        ) {
            val leftPad = 42.dp.toPx()
            val rightPad = 14.dp.toPx()
            val topPad = 14.dp.toPx()
            val bottomPad = 28.dp.toPx()
            val chartW = (size.width - leftPad - rightPad).coerceAtLeast(1f)
            val chartH = (size.height - topPad - bottomPad).coerceAtLeast(1f)

            // Y-axis gridlines & labels (4 steps)
            val ySteps = 4
            for (i in 0..ySteps) {
                val frac = i.toFloat() / ySteps
                val y = topPad + chartH - (frac * chartH)
                val valAtStep = maxVal * frac
                drawLine(
                    color = CobaltContainer,
                    start = Offset(leftPad, y),
                    end = Offset(leftPad + chartW, y),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = if (i > 0) PathEffect.dashPathEffect(floatArrayOf(8f, 6f)) else null
                )
                val lbl = compactNumber(valAtStep)
                val measured = textMeasurer.measure(lbl, axisTextStyle)
                drawText(
                    measured,
                    topLeft = Offset((leftPad - measured.size.width - 6.dp.toPx()).coerceAtLeast(0f), y - measured.size.height / 2f)
                )
            }

            // Y-axis vertical line
            drawLine(
                color = PowderBlue,
                start = Offset(leftPad, topPad),
                end = Offset(leftPad, topPad + chartH),
                strokeWidth = 1.2.dp.toPx()
            )

            val n = points.size
            fun xFor(idx: Int): Float = if (n == 1) leftPad + chartW / 2f else leftPad + (idx.toFloat() / (n - 1)) * chartW
            fun yFor(v: Double): Float = topPad + chartH - ((v / maxVal).toFloat().coerceIn(0f, 1f) * chartH)

            val incOffsets = points.mapIndexed { idx, p -> Offset(xFor(idx), yFor(p.income)) }
            val expOffsets = points.mapIndexed { idx, p -> Offset(xFor(idx), yFor(p.expense)) }

            // Shaded Income Area
            if (incOffsets.size >= 2) {
                val areaPath = Path().apply {
                    moveTo(incOffsets.first().x, topPad + chartH)
                    incOffsets.forEach { lineTo(it.x, it.y) }
                    lineTo(incOffsets.last().x, topPad + chartH)
                    close()
                }
                drawPath(
                    path = areaPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(CobaltPrimary.copy(alpha = 0.20f), Color.Transparent),
                        startY = topPad,
                        endY = topPad + chartH
                    )
                )
            }

            // Income Line
            if (incOffsets.size >= 2) {
                val incPath = Path().apply {
                    moveTo(incOffsets.first().x, incOffsets.first().y)
                    incOffsets.drop(1).forEach { lineTo(it.x, it.y) }
                }
                drawPath(
                    path = incPath,
                    color = CobaltPrimary,
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
            }

            // Expenses Line
            if (expOffsets.size >= 2) {
                val expPath = Path().apply {
                    moveTo(expOffsets.first().x, expOffsets.first().y)
                    expOffsets.drop(1).forEach { lineTo(it.x, it.y) }
                }
                drawPath(
                    path = expPath,
                    color = SkyBlue,
                    style = Stroke(
                        width = 2.8.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 6f))
                    )
                )
            }

            // Selected vertical guide + nodes
            val selX = xFor(selectedIdx.coerceIn(0, n - 1))
            drawLine(
                color = CobaltContainer,
                start = Offset(selX, topPad),
                end = Offset(selX, topPad + chartH),
                strokeWidth = 1.5.dp.toPx()
            )

            incOffsets.forEachIndexed { idx, pt ->
                drawCircle(color = Color.White, radius = if (idx == selectedIdx) 6.dp.toPx() else 4.5.dp.toPx(), center = pt)
                drawCircle(color = CobaltPrimary, radius = if (idx == selectedIdx) 4.5.dp.toPx() else 3.2.dp.toPx(), center = pt)
            }
            expOffsets.forEachIndexed { idx, pt ->
                drawCircle(color = Color.White, radius = if (idx == selectedIdx) 6.dp.toPx() else 4.5.dp.toPx(), center = pt)
                drawCircle(color = SkyBlue, radius = if (idx == selectedIdx) 4.5.dp.toPx() else 3.2.dp.toPx(), center = pt)
            }

            // X-axis date labels (evenly sampled if many)
            val labelIndices = when {
                n <= 5 -> points.indices.toList()
                else -> listOf(0, n / 3, (2 * n) / 3, n - 1).distinct()
            }
            labelIndices.forEach { idx ->
                val measured = textMeasurer.measure(points[idx].dateLabel, axisTextStyle)
                val cx = xFor(idx) - measured.size.width / 2f
                drawText(
                    measured,
                    topLeft = Offset(cx.coerceIn(leftPad, leftPad + chartW - measured.size.width), topPad + chartH + 6.dp.toPx())
                )
            }
        }

        // Interactive Date Selector Chips + Detail Banner
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val displayIndices = if (points.size <= 5) points.indices.toList() else listOf(0, points.size / 3, (2 * points.size) / 3, points.lastIndex).distinct()
            displayIndices.forEach { idx ->
                val isSel = idx == selectedIdx
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isSel) CobaltPrimary else CobaltContainer.copy(alpha = 0.55f),
                    modifier = Modifier.weight(1f).clickable { selectedIdx = idx }
                ) {
                    Text(
                        text = points[idx].dateLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isSel) Color.White else CobaltDark,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        modifier = Modifier.padding(vertical = 6.dp, horizontal = 4.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        }

        val focus = points.getOrNull(selectedIdx.coerceIn(0, points.lastIndex))
        if (focus != null) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(focus.fullDate, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = CobaltDark)
                    Text("In: ${DomainRules.formatMoney(focus.income, currency)}", style = MaterialTheme.typography.labelMedium, color = CobaltPrimary, fontWeight = FontWeight.SemiBold)
                    Text("Out: ${DomainRules.formatMoney(focus.expense, currency)}", style = MaterialTheme.typography.labelMedium, color = OceanBlue, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
fun StudentGrowthLineChart(
    points: List<StudentGrowthPoint>,
    modifier: Modifier = Modifier
) {
    if (points.isEmpty()) return
    val textMeasurer = rememberTextMeasurer()
    val axisTextStyle = TextStyle(fontSize = 10.sp, color = Color(0xFF475569))
    val valueTextStyle = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Bold, color = CobaltDark)
    val maxCount = (points.maxOfOrNull { it.totalStudents } ?: 1).coerceAtLeast(4)
    val yCap = ((maxCount + 3) / 4) * 4

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(10.dp).background(CobaltPrimary, CircleShape))
                Text("Cumulative Enrolled Students", style = MaterialTheme.typography.labelMedium, color = CobaltDark, fontWeight = FontWeight.SemiBold)
            }
            Text("Y: Total Students  |  X: Month", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(176.dp)
        ) {
            val leftPad = 30.dp.toPx()
            val rightPad = 16.dp.toPx()
            val topPad = 20.dp.toPx()
            val bottomPad = 26.dp.toPx()
            val chartW = (size.width - leftPad - rightPad).coerceAtLeast(1f)
            val chartH = (size.height - topPad - bottomPad).coerceAtLeast(1f)

            for (step in 0..4) {
                val frac = step / 4f
                val y = topPad + chartH - frac * chartH
                val stepVal = (yCap * frac).roundToInt()
                drawLine(
                    color = CobaltContainer,
                    start = Offset(leftPad, y),
                    end = Offset(leftPad + chartW, y),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = if (step > 0) PathEffect.dashPathEffect(floatArrayOf(8f, 6f)) else null
                )
                val lbl = textMeasurer.measure(stepVal.toString(), axisTextStyle)
                drawText(lbl, topLeft = Offset((leftPad - lbl.size.width - 6.dp.toPx()).coerceAtLeast(0f), y - lbl.size.height / 2f))
            }

            val n = points.size
            fun xFor(idx: Int): Float = if (n == 1) leftPad + chartW / 2f else leftPad + (idx.toFloat() / (n - 1)) * chartW
            fun yFor(v: Int): Float = topPad + chartH - ((v.toFloat() / yCap).coerceIn(0f, 1f) * chartH)

            val offsets = points.mapIndexed { idx, pt -> Offset(xFor(idx), yFor(pt.totalStudents)) }
            if (offsets.size >= 2) {
                val fillPath = Path().apply {
                    moveTo(offsets.first().x, topPad + chartH)
                    offsets.forEach { lineTo(it.x, it.y) }
                    lineTo(offsets.last().x, topPad + chartH)
                    close()
                }
                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(SkyLight.copy(alpha = 0.28f), Color.Transparent),
                        startY = topPad,
                        endY = topPad + chartH
                    )
                )
                val linePath = Path().apply {
                    moveTo(offsets.first().x, offsets.first().y)
                    offsets.drop(1).forEach { lineTo(it.x, it.y) }
                }
                drawPath(
                    path = linePath,
                    color = CobaltMid,
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
            }

            offsets.forEachIndexed { idx, pt ->
                drawCircle(color = CobaltPrimary, radius = 5.dp.toPx(), center = pt)
                drawCircle(color = Color.White, radius = 2.4.dp.toPx(), center = pt)
                val valMeasured = textMeasurer.measure(points[idx].totalStudents.toString(), valueTextStyle)
                drawText(
                    valMeasured,
                    topLeft = Offset(
                        (pt.x - valMeasured.size.width / 2f).coerceIn(leftPad, leftPad + chartW - valMeasured.size.width),
                        (pt.y - valMeasured.size.height - 6.dp.toPx()).coerceAtLeast(0f)
                    )
                )
                val xMeasured = textMeasurer.measure(points[idx].label, axisTextStyle)
                drawText(
                    xMeasured,
                    topLeft = Offset(
                        (pt.x - xMeasured.size.width / 2f).coerceIn(leftPad, leftPad + chartW - xMeasured.size.width),
                        topPad + chartH + 6.dp.toPx()
                    )
                )
            }
        }
    }
}

@Composable
fun BoysGirlsAttendanceBarChart(
    days: List<GenderAttendanceDay>,
    modifier: Modifier = Modifier
) {
    if (days.isEmpty()) return
    val textMeasurer = rememberTextMeasurer()
    val axisStyle = TextStyle(fontSize = 10.sp, color = Color(0xFF475569))
    val barValStyle = TextStyle(fontSize = 9.sp, fontWeight = FontWeight.Bold, color = CobaltDark)
    val maxVal = (days.maxOfOrNull { max(it.boysPresent, it.girlsPresent) } ?: 1).coerceAtLeast(4)
    val yCap = ((maxVal + 3) / 4) * 4

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.size(10.dp).background(CobaltPrimary, RoundedCornerShape(3.dp)))
                    Text("Boys (${days.sumOf { it.boysPresent }})", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = CobaltDark)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.size(10.dp).background(SkyLight, RoundedCornerShape(3.dp)))
                    Text("Girls (${days.sumOf { it.girlsPresent }})", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = OceanBlue)
                }
            }
            Text("Daily Present", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(175.dp)
        ) {
            val leftPad = 26.dp.toPx()
            val rightPad = 8.dp.toPx()
            val topPad = 18.dp.toPx()
            val bottomPad = 26.dp.toPx()
            val chartW = (size.width - leftPad - rightPad).coerceAtLeast(1f)
            val chartH = (size.height - topPad - bottomPad).coerceAtLeast(1f)

            for (step in 0..4) {
                val frac = step / 4f
                val y = topPad + chartH - frac * chartH
                val labelVal = (yCap * frac).roundToInt()
                drawLine(
                    color = CobaltContainer,
                    start = Offset(leftPad, y),
                    end = Offset(leftPad + chartW, y),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = if (step > 0) PathEffect.dashPathEffect(floatArrayOf(8f, 6f)) else null
                )
                val measured = textMeasurer.measure(labelVal.toString(), axisStyle)
                drawText(measured, topLeft = Offset((leftPad - measured.size.width - 4.dp.toPx()).coerceAtLeast(0f), y - measured.size.height / 2f))
            }

            val count = days.size.coerceAtLeast(1)
            val slotW = chartW / count
            val barW = (slotW * 0.28f).coerceIn(6.dp.toPx(), 18.dp.toPx())
            val gap = 3.dp.toPx()

            days.forEachIndexed { idx, day ->
                val center = leftPad + idx * slotW + slotW / 2f
                val boysX = center - barW - gap / 2f
                val girlsX = center + gap / 2f

                val boysH = ((day.boysPresent.toFloat() / yCap).coerceIn(0f, 1f)) * chartH
                val girlsH = ((day.girlsPresent.toFloat() / yCap).coerceIn(0f, 1f)) * chartH

                if (boysH > 0f) {
                    drawRoundRect(
                        color = CobaltPrimary,
                        topLeft = Offset(boysX, topPad + chartH - boysH),
                        size = Size(barW, boysH),
                        cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                    )
                    val bTxt = textMeasurer.measure(day.boysPresent.toString(), barValStyle)
                    drawText(bTxt, topLeft = Offset(boysX + (barW - bTxt.size.width) / 2f, (topPad + chartH - boysH - bTxt.size.height - 2.dp.toPx()).coerceAtLeast(0f)))
                }
                if (girlsH > 0f) {
                    drawRoundRect(
                        color = SkyLight,
                        topLeft = Offset(girlsX, topPad + chartH - girlsH),
                        size = Size(barW, girlsH),
                        cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                    )
                    val gTxt = textMeasurer.measure(day.girlsPresent.toString(), barValStyle)
                    drawText(gTxt, topLeft = Offset(girlsX + (barW - gTxt.size.width) / 2f, (topPad + chartH - girlsH - gTxt.size.height - 2.dp.toPx()).coerceAtLeast(0f)))
                }

                val dayTxt = textMeasurer.measure(day.dayLabel, axisStyle)
                drawText(dayTxt, topLeft = Offset(center - dayTxt.size.width / 2f, topPad + chartH + 6.dp.toPx()))
            }
        }
    }
}

@Composable
fun GroupedComparisonBarChart(
    items: List<BranchComparisonPoint>,
    primaryLabel: String,
    secondaryLabel: String,
    modifier: Modifier = Modifier
) {
    if (items.isEmpty()) return
    val textMeasurer = rememberTextMeasurer()
    val axisStyle = TextStyle(fontSize = 10.sp, color = Color(0xFF475569))
    val maxVal = max(items.maxOfOrNull { max(it.primaryVal, it.secondaryVal) } ?: 1.0, 1000.0) * 1.15

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(10.dp).background(CobaltPrimary, RoundedCornerShape(3.dp)))
                Text(primaryLabel, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = CobaltDark)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(10.dp).background(PeriwinkleBlue, RoundedCornerShape(3.dp)))
                Text(secondaryLabel, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = OceanBlue)
            }
        }

        Canvas(modifier = Modifier.fillMaxWidth().height(155.dp)) {
            val leftPad = 40.dp.toPx()
            val rightPad = 10.dp.toPx()
            val topPad = 12.dp.toPx()
            val bottomPad = 26.dp.toPx()
            val chartW = (size.width - leftPad - rightPad).coerceAtLeast(1f)
            val chartH = (size.height - topPad - bottomPad).coerceAtLeast(1f)

            for (step in 0..3) {
                val frac = step / 3f
                val y = topPad + chartH - frac * chartH
                drawLine(
                    color = CobaltContainer,
                    start = Offset(leftPad, y),
                    end = Offset(leftPad + chartW, y),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = if (step > 0) PathEffect.dashPathEffect(floatArrayOf(8f, 6f)) else null
                )
                val lbl = textMeasurer.measure(compactNumber(maxVal * frac), axisStyle)
                drawText(lbl, topLeft = Offset((leftPad - lbl.size.width - 5.dp.toPx()).coerceAtLeast(0f), y - lbl.size.height / 2f))
            }

            val count = items.size.coerceAtLeast(1)
            val slotW = chartW / count
            val barW = (slotW * 0.24f).coerceIn(10.dp.toPx(), 26.dp.toPx())
            val gap = 4.dp.toPx()

            items.forEachIndexed { idx, item ->
                val center = leftPad + idx * slotW + slotW / 2f
                val h1 = ((item.primaryVal / maxVal).toFloat().coerceIn(0f, 1f)) * chartH
                val h2 = ((item.secondaryVal / maxVal).toFloat().coerceIn(0f, 1f)) * chartH
                if (h1 > 0f) {
                    drawRoundRect(
                        color = CobaltPrimary,
                        topLeft = Offset(center - barW - gap / 2f, topPad + chartH - h1),
                        size = Size(barW, h1),
                        cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                    )
                }
                if (h2 > 0f) {
                    drawRoundRect(
                        color = PeriwinkleBlue,
                        topLeft = Offset(center + gap / 2f, topPad + chartH - h2),
                        size = Size(barW, h2),
                        cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                    )
                }
                val lbl = textMeasurer.measure(item.label, axisStyle)
                drawText(lbl, topLeft = Offset(center - lbl.size.width / 2f, topPad + chartH + 6.dp.toPx()))
            }
        }
    }
}

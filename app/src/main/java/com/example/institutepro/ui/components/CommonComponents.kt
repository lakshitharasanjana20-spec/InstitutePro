package com.example.institutepro.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.institutepro.R
import com.example.institutepro.ui.theme.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val cardMod = if (onClick != null) modifier.clickable(onClick = onClick) else modifier
    Surface(
        modifier = cardMod,
        shape = MaterialTheme.shapes.large,
        color = containerColor,
        tonalElevation = 1.dp,
        shadowElevation = 1.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp), content = content)
    }
}

@Composable
fun KpiCard(
    label: String,
    value: String,
    icon: ImageVector,
    accent: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null
) {
    val mod = if (onClick != null) modifier.clickable(onClick = onClick) else modifier
    Surface(
        modifier = mod,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.22f))
    ) {
        Column(
            modifier = Modifier
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            accent.copy(alpha = 0.06f),
                            Color.White
                        )
                    )
                )
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(end = 6.dp)
                )
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(accent.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = label, tint = accent, modifier = Modifier.size(18.dp))
                }
            }

            AutoFitKpiValue(
                text = value,
                color = CobaltDark
            )

            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = accent,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun AutoFitKpiValue(
    text: String,
    color: Color = MaterialTheme.colorScheme.onSurface,
    modifier: Modifier = Modifier
) {
    val initialSp = when {
        text.length >= 16 -> 16f
        text.length >= 12 -> 18f
        else -> 22f
    }
    var fontSizeSp by remember(text) { mutableStateOf(initialSp) }
    Text(
        text = text,
        color = color,
        style = MaterialTheme.typography.headlineSmall.copy(
            fontSize = fontSizeSp.sp,
            lineHeight = (fontSizeSp + 4f).sp,
            fontWeight = FontWeight.Bold
        ),
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Visible,
        onTextLayout = { result ->
            if (result.didOverflowWidth && fontSizeSp > 11.5f) {
                fontSizeSp = (fontSizeSp - 1.5f).coerceAtLeast(11.5f)
            }
        },
        modifier = modifier.fillMaxWidth()
    )
}

@Composable
fun StatusPill(text: String, color: Color) {
    Surface(
        shape = CircleShape,
        color = color.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.28f))
    ) {
        Text(
            text = text,
            color = color,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun statusColorFor(status: String): Color = when (status.lowercase()) {
    "active", "present", "approved", "paid", "visible", "live now", "done" -> CobaltPrimary
    "inactive", "absent", "rejected", "void", "overdue", "ended" -> CrimsonAlert
    "pending", "half_day", "upcoming", "draft" -> OceanBlue
    else -> MaterialTheme.colorScheme.primary
}

@Composable
fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    testTag: String = "search_input"
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = "Search", tint = CobaltPrimary) },
        trailingIcon = {
            if (value.isNotEmpty()) {
                IconButton(onClick = { onValueChange("") }) {
                    Icon(Icons.Filled.Clear, contentDescription = "Clear search", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = CobaltPrimary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline
        ),
        modifier = modifier.fillMaxWidth().testTag(testTag)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerField(
    value: String,
    onDateSelected: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    showStepButtons: Boolean = false
) {
    var showPicker by remember { mutableStateOf(false) }
    val parsed = remember(value) { runCatching { LocalDate.parse(value) }.getOrNull() }
    val todayStr = remember { LocalDate.now().toString() }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (showStepButtons && parsed != null) {
                FilledTonalIconButton(
                    onClick = { onDateSelected(parsed.minusDays(1).toString()) },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous day", tint = CobaltPrimary)
                }
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clickable { showPicker = true }
            ) {
                OutlinedTextField(
                    value = value,
                    onValueChange = {},
                    readOnly = true,
                    enabled = false,
                    label = { Text(label) },
                    trailingIcon = {
                        Icon(Icons.Filled.CalendarMonth, contentDescription = "Select date", tint = CobaltPrimary)
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        disabledTextColor = MaterialTheme.colorScheme.onSurface,
                        disabledBorderColor = MaterialTheme.colorScheme.outline,
                        disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        disabledTrailingIconColor = CobaltPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (showStepButtons && parsed != null) {
                FilledTonalIconButton(
                    onClick = { onDateSelected(parsed.plusDays(1).toString()) },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next day", tint = CobaltPrimary)
                }
            }
        }
        if (showStepButtons && value != todayStr) {
            TextButton(
                onClick = { onDateSelected(todayStr) },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                modifier = Modifier.height(30.dp)
            ) {
                Text("Jump to Today ($todayStr)", style = MaterialTheme.typography.labelMedium)
            }
        }
    }

    if (showPicker) {
        val initialMillis = remember(parsed) {
            (parsed ?: LocalDate.now()).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        }
        val state = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                Button(onClick = {
                    state.selectedDateMillis?.let { millis ->
                        val picked = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().toString()
                        onDateSelected(picked)
                    }
                    showPicker = false
                }) { Text("Select") }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = state)
        }
    }
}

@Composable
fun QuickActionPill(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    accent: Color = CobaltPrimary,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = accent.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.24f)),
        modifier = modifier.heightIn(min = 48.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(accent.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(16.dp))
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = CobaltDark,
                maxLines = 1
            )
        }
    }
}

@Composable
fun EmptyStateCard(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    AppCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_empty_state),
                contentDescription = title,
                modifier = Modifier
                    .height(110.dp)
                    .clip(RoundedCornerShape(14.dp)),
                contentScale = ContentScale.Crop
            )
            Spacer(Modifier.height(12.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun SevenDayAttendanceChart(
    days: List<Triple<String, Int, Int>>, // label, present, absent
    modifier: Modifier = Modifier
) {
    val maxTotal = (days.maxOfOrNull { it.second + it.third } ?: 1).coerceAtLeast(1)
    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(110.dp)
        ) {
            val count = days.size.coerceAtLeast(1)
            val slotW = size.width / count
            val barW = (slotW * 0.48f).coerceAtMost(36.dp.toPx())
            days.forEachIndexed { idx, (_, present, absent) ->
                val x = idx * slotW + (slotW - barW) / 2f
                val totalH = ((present + absent).toFloat() / maxTotal) * (size.height - 8.dp.toPx())
                val presH = if (present + absent > 0) totalH * (present.toFloat() / (present + absent)) else 0f
                val absH = totalH - presH
                if (present + absent == 0) {
                    drawRoundRect(
                        color = CobaltContainer,
                        topLeft = Offset(x, size.height - 6.dp.toPx()),
                        size = Size(barW, 6.dp.toPx()),
                        cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                    )
                } else {
                    if (absH > 0f) {
                        drawRoundRect(
                            color = PowderBlue,
                            topLeft = Offset(x, size.height - totalH),
                            size = Size(barW, absH),
                            cornerRadius = CornerRadius(5.dp.toPx(), 5.dp.toPx())
                        )
                    }
                    if (presH > 0f) {
                        drawRoundRect(
                            color = CobaltPrimary,
                            topLeft = Offset(x, size.height - presH),
                            size = Size(barW, presH),
                            cornerRadius = CornerRadius(5.dp.toPx(), 5.dp.toPx())
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
            days.forEach { (label, _, _) ->
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(10.dp).background(CobaltPrimary, CircleShape))
                Text("Present", style = MaterialTheme.typography.labelSmall)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(10.dp).background(PowderBlue, CircleShape))
                Text("Absent", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
fun InitialAvatar(name: String, sizeDp: Int = 44, bg: Color = MaterialTheme.colorScheme.primaryContainer) {
    val initial = name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
    Box(
        modifier = Modifier
            .size(sizeDp.dp)
            .clip(CircleShape)
            .background(bg),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initial,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

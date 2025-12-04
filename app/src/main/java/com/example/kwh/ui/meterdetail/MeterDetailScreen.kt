package com.example.kwh.ui.meterdetail

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.kwh.R
import com.example.kwh.ui.components.SectionCard
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeterDetailScreen(
    viewModel: MeterDetailViewModel,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onAddReading: () -> Unit,
    onViewGraph: () -> Unit,
    onViewLog: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is MeterDetailEvent.Error -> snackbarHostState.showSnackbar(event.message)
                is MeterDetailEvent.ShowMessage -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = uiState.meterName) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Filled.ArrowBack,
                            contentDescription = stringResource(id = R.string.back)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = stringResource(id = R.string.settings_action)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            // Circular reading display
            ReadingGauge(
                reading = uiState.lastReading,
                usedUnits = uiState.usedUnits,
                projectedUnits = uiState.projectedUnits,
                hasProjection = uiState.hasProjection,
                modifier = Modifier.size(260.dp)
            )

            // Last recorded date
            uiState.lastRecordedDate?.let { date ->
                val formatter = remember { DateTimeFormatter.ofPattern("MMM d, yyyy HH:mm") }
                val formatted = remember(date) {
                    formatter.format(date.atZone(ZoneId.systemDefault()))
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(id = R.string.meter_detail_recorded_on, formatted),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Stats row
            if (uiState.hasProjection) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    StatCard(
                        title = stringResource(id = R.string.meter_detail_usage_today),
                        value = formatUnits(uiState.ratePerDay),
                        unit = "kWh/day",
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        title = stringResource(id = R.string.meter_detail_projected),
                        value = formatUnits(uiState.projectedUnits),
                        unit = "kWh",
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))
            }

            // Action buttons
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ActionButton(
                    text = stringResource(id = R.string.meter_detail_add_reading),
                    icon = Icons.Filled.Add,
                    onClick = onAddReading,
                    isPrimary = true
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ActionButton(
                        text = stringResource(id = R.string.meter_detail_see_graph),
                        icon = Icons.Filled.ShowChart,
                        onClick = onViewGraph,
                        modifier = Modifier.weight(1f)
                    )
                    ActionButton(
                        text = stringResource(id = R.string.meter_detail_see_log),
                        icon = Icons.Filled.History,
                        onClick = onViewLog,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Cycle info
            uiState.cycleInfo?.let { cycle ->
                SectionCard(
                    modifier = Modifier.padding(horizontal = 24.dp)
                ) {
                    val cycleFormatter = remember { DateTimeFormatter.ofPattern("dd MMM") }
                    val startText = remember(cycle.start) {
                        cycleFormatter.format(cycle.start.atZone(ZoneId.systemDefault()))
                    }
                    val endText = remember(cycle.end) {
                        cycleFormatter.format(cycle.end.atZone(ZoneId.systemDefault()))
                    }

                    Text(
                        text = stringResource(id = R.string.cycle_range, startText, endText),
                        style = MaterialTheme.typography.titleMedium
                    )

                    Text(
                        text = stringResource(
                            id = R.string.cycle_used_chip,
                            formatUnits(cycle.usedUnits)
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    cycle.nextThresholdValue?.let { threshold ->
                        cycle.nextThresholdDate?.let { date ->
                            val thresholdFormatter = remember { DateTimeFormatter.ofPattern("dd MMM") }
                            Text(
                                text = stringResource(
                                    id = R.string.next_threshold_eta,
                                    threshold,
                                    thresholdFormatter.format(date)
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }
                    }

                    // Next reminder
                    uiState.nextReminder?.let { reminder ->
                        val reminderFormatter = remember { DateTimeFormatter.ofPattern("MMM d, yyyy HH:mm") }
                        val reminderText = remember(reminder) {
                            reminderFormatter.format(reminder.atZone(ZoneId.systemDefault()))
                        }
                        Text(
                            text = stringResource(id = R.string.next_reminder, reminderText),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun ReadingGauge(
    reading: Double?,
    usedUnits: Double,
    projectedUnits: Double,
    hasProjection: Boolean,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val primaryColor = colorScheme.primary
    val tertiaryColor = colorScheme.tertiary
    val surfaceColor = colorScheme.surfaceVariant

    // Animated progress for the arc
    var targetProgress by remember { mutableFloatStateOf(0f) }
    val animatedProgress by animateFloatAsState(
        targetValue = targetProgress,
        animationSpec = tween(durationMillis = 1000),
        label = "progress"
    )

    LaunchedEffect(usedUnits, projectedUnits) {
        targetProgress = if (projectedUnits > 0 && hasProjection) {
            (usedUnits / projectedUnits).coerceIn(0.0, 1.0).toFloat()
        } else {
            0f
        }
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        // Background gradient circle
        Box(
            modifier = Modifier
                .size(260.dp)
                .clip(CircleShape)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            colorScheme.primaryContainer.copy(alpha = 0.3f),
                            colorScheme.surface
                        )
                    )
                )
        )

        // Progress arc
        Canvas(modifier = Modifier.size(220.dp)) {
            val strokeWidth = 16.dp.toPx()
            val startAngle = 135f
            val sweepAngle = 270f

            // Background arc
            drawArc(
                color = surfaceColor,
                startAngle = startAngle,
                sweepAngle = sweepAngle,
                useCenter = false,
                topLeft = Offset(strokeWidth / 2, strokeWidth / 2),
                size = Size(size.width - strokeWidth, size.height - strokeWidth),
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            // Progress arc
            if (hasProjection && animatedProgress > 0) {
                drawArc(
                    brush = Brush.sweepGradient(
                        colors = listOf(primaryColor, tertiaryColor, primaryColor)
                    ),
                    startAngle = startAngle,
                    sweepAngle = sweepAngle * animatedProgress,
                    useCenter = false,
                    topLeft = Offset(strokeWidth / 2, strokeWidth / 2),
                    size = Size(size.width - strokeWidth, size.height - strokeWidth),
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }
        }

        // Reading value
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = reading?.let {
                    String.format(Locale.getDefault(), "%.1f", it)
                } ?: stringResource(id = R.string.meter_detail_no_reading),
                style = MaterialTheme.typography.displayMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 48.sp
                ),
                color = colorScheme.onSurface
            )
            Text(
                text = stringResource(id = R.string.meter_detail_kwh_unit),
                style = MaterialTheme.typography.titleMedium,
                color = colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun StatCard(
    title: String,
    value: String,
    unit: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.secondaryContainer,
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Text(
                text = unit,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun ActionButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isPrimary: Boolean = false
) {
    if (isPrimary) {
        FilledTonalButton(
            onClick = onClick,
            modifier = modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = MaterialTheme.shapes.large
        ) {
            Icon(imageVector = icon, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge
            )
        }
    } else {
        Surface(
            onClick = onClick,
            modifier = modifier.height(56.dp),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceVariant,
            tonalElevation = 1.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun formatUnits(value: Double): String {
    return String.format(Locale.getDefault(), "%.1f", value)
}

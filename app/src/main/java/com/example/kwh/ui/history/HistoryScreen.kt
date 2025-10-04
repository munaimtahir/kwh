package com.example.kwh.ui.history

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.kwh.R
import com.example.kwh.ui.components.SectionCard
import java.time.Duration
import java.io.BufferedReader
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            scope.launch {
                val csv = context.contentResolver.openInputStream(uri)?.use { stream ->
                    BufferedReader(stream.reader()).readText()
                }
                if (csv.isNullOrBlank()) {
                    snackbarHostState.showSnackbar(context.getString(R.string.import_failed))
                } else {
                    viewModel.importFromCsv(csv)
                }
            }
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is HistoryEvent.ShowUndo -> {
                    val result = snackbarHostState.showSnackbar(
                        message = context.getString(R.string.reading_deleted),
                        actionLabel = context.getString(R.string.undo),
                        duration = SnackbarDuration.Short
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        viewModel.undoDelete(event.reading)
                    }
                }
                is HistoryEvent.MeterDeleted -> {
                    snackbarHostState.showSnackbar(context.getString(R.string.meter_deleted))
                    onBack()
                }
                is HistoryEvent.ExportCsv -> {
                    shareCsv(context, uiState.meterName, event.csv)
                    snackbarHostState.showSnackbar(context.getString(R.string.csv_export_started))
                }
                is HistoryEvent.Imported -> {
                    val message = context.resources.getQuantityString(
                        R.plurals.imported_count,
                        event.count,
                        event.count
                    )
                    snackbarHostState.showSnackbar(message)
                }
                is HistoryEvent.Error -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = uiState.meterName.ifBlank { stringResource(id = R.string.history_title) }) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.Filled.ArrowBack, contentDescription = stringResource(id = R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.requestCsvExport() }) {
                        Icon(imageVector = Icons.Filled.CloudUpload, contentDescription = stringResource(id = R.string.export_csv))
                    }
                    IconButton(onClick = { importLauncher.launch("text/*") }) {
                        Icon(imageVector = Icons.Filled.CloudDownload, contentDescription = stringResource(id = R.string.import_csv))
                    }
                    IconButton(onClick = { viewModel.toggleDeleteMeterDialog(true) }) {
                        Icon(imageVector = Icons.Filled.Delete, contentDescription = stringResource(id = R.string.delete_meter))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors()
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            FilterChips(
                selected = uiState.filter,
                onSelected = viewModel::onFilterSelected
            )
            uiState.cycle?.let { summary ->
                CycleSummaryCard(summary = summary)
            }
            TrendChart(uiState.trend)
            if (uiState.isEmpty) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .weight(1f, fill = true),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(text = stringResource(id = R.string.history_empty), style = MaterialTheme.typography.bodyLarge)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f, fill = true),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.readings, key = { it.id }) { reading ->
                        HistoryListItem(reading = reading, onDelete = { viewModel.deleteReading(reading.id) })
                    }
                }
            }
        }
    }

    // ✅ This is now safe because it's inside a composable context.
    if (uiState.showDeleteMeterDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.toggleDeleteMeterDialog(false) },
            title = { Text(text = stringResource(id = R.string.delete_meter)) },
            text = { Text(text = stringResource(id = R.string.delete_meter_confirm, uiState.meterName)) },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteMeter() }) {
                    Text(text = stringResource(id = R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.toggleDeleteMeterDialog(false) }) {
                    Text(text = stringResource(id = R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun FilterChips(selected: HistoryFilter, onSelected: (HistoryFilter) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        HistoryFilter.values().forEach { filter ->
            FilterChip(
                selected = filter == selected,
                onClick = { onSelected(filter) },
                label = { Text(text = filter.label) },
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primaryContainer)
            )
        }
    }
}

@Composable
private fun TrendChart(data: TrendChartData) {
    val start = data.windowStart
    val end = data.windowEnd
    if (start == null || end == null || data.points.size < 2) return
    val durationMillis = Duration.between(start, end).toMillis().coerceAtLeast(1L)
    val values = buildList {
        addAll(data.points.map { it.value })
        data.projection?.let { add(it.endValue) }
    }
    if (values.isEmpty()) return
    val maxValue = values.maxOrNull() ?: return
    val minValue = values.minOrNull() ?: return
    val span = (maxValue - minValue).takeIf { it != 0.0 } ?: 1.0
    val primaryColor = MaterialTheme.colorScheme.primary

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp)
            .padding(horizontal = 16.dp)
    ) {
        val path = Path()
        val width = size.width
        val height = size.height
        data.points.forEachIndexed { index, point ->
            val elapsed = Duration.between(start, point.instant).toMillis().coerceIn(0, durationMillis)
            val fraction = elapsed.toFloat() / durationMillis.toFloat()
            val x = fraction * width
            val normalized = (point.value - minValue) / span
            val y = height - (normalized.toFloat() * height)
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(
            path = path,
            color = primaryColor,
            style = Stroke(width = 6f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )

        data.projection?.let { projection ->
            val lastPoint = data.points.last()
            val lastElapsed = Duration.between(start, lastPoint.instant).toMillis().coerceIn(0, durationMillis)
            val startFraction = lastElapsed.toFloat() / durationMillis.toFloat()
            val startX = startFraction * width
            val startNormalized = (lastPoint.value - minValue) / span
            val startY = height - (startNormalized.toFloat() * height)
            val endNormalized = (projection.endValue - minValue) / span
            val endY = height - (endNormalized.toFloat() * height)
            drawLine(
                color = primaryColor,
                start = Offset(startX, startY),
                end = Offset(width, endY),
                strokeWidth = 4f,
                cap = StrokeCap.Round,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 12f))
            )
        }
    }
}

@Composable
private fun CycleSummaryCard(summary: CycleSummary) {
    val rangeFormatter = remember { DateTimeFormatter.ofPattern("dd MMM") }
    val dateTimeFormatter = remember { DateTimeFormatter.ofPattern("MMM d, yyyy HH:mm") }
    val thresholdFormatter = remember { DateTimeFormatter.ofPattern("dd MMM") }
    val startText = remember(summary.start) { rangeFormatter.format(summary.start.atZone(ZoneId.systemDefault())) }
    val endText = remember(summary.end) { rangeFormatter.format(summary.end.atZone(ZoneId.systemDefault())) }
    SectionCard(
        modifier = Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = stringResource(id = R.string.history_cycle_range, startText, endText),
            style = MaterialTheme.typography.titleMedium
        )
        summary.baseline?.let {
            val baselineDate = remember(it.recordedAt) {
                dateTimeFormatter.format(it.recordedAt.atZone(ZoneId.systemDefault()))
            }
            Text(
                text = stringResource(
                    id = R.string.history_baseline,
                    it.value,
                    baselineDate
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        summary.latest?.let {
            val latestDate = remember(it.recordedAt) {
                dateTimeFormatter.format(it.recordedAt.atZone(ZoneId.systemDefault()))
            }
            Text(
                text = stringResource(
                    id = R.string.history_latest,
                    it.value,
                    latestDate
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = stringResource(id = R.string.history_used, summary.usedUnits),
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = stringResource(id = R.string.history_projected, summary.projectedUnits),
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = stringResource(id = R.string.history_rate, summary.ratePerDay),
            style = MaterialTheme.typography.bodyMedium
        )
        if (summary.nextThresholdValue != null && summary.nextThresholdDate != null) {
            val etaText = remember(summary.nextThresholdDate) {
                thresholdFormatter.format(summary.nextThresholdDate)
            }
            Text(
                text = stringResource(id = R.string.history_next_threshold, summary.nextThresholdValue, etaText),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun HistoryListItem(reading: HistoryReading, onDelete: () -> Unit) {
    val formatter = remember { DateTimeFormatter.ofPattern("MMM d, yyyy HH:mm") }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = stringResource(id = R.string.history_reading_value, reading.value), style = MaterialTheme.typography.titleMedium)
                Text(
                    text = formatter.format(reading.recordedAt.atZone(ZoneId.systemDefault())),
                    style = MaterialTheme.typography.bodySmall
                )
                reading.notes?.let {
                    Text(text = it, style = MaterialTheme.typography.bodySmall)
                }
            }
            IconButton(onClick = onDelete) {
                Icon(imageVector = Icons.Filled.Delete, contentDescription = stringResource(id = R.string.delete_reading))
            }
        }
    }
}

private fun shareCsv(context: android.content.Context, meterName: String, csv: String) {
    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/csv"
        putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.csv_subject, meterName))
        putExtra(Intent.EXTRA_TEXT, csv)
    }
    val chooser = Intent.createChooser(sendIntent, context.getString(R.string.export_csv))
    if (sendIntent.resolveActivity(context.packageManager) != null) {
        context.startActivity(chooser)
    }
}

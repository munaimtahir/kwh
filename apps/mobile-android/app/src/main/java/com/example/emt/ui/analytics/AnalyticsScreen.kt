package com.example.emt.ui.analytics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun AnalyticsScreen(viewModel: AnalyticsViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val selectedPeriod by viewModel.selectedTimePeriod.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("Analytics", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))

        TimePeriodSelector(selectedPeriod) { newPeriod ->
            viewModel.setTimePeriod(newPeriod)
        }

        Spacer(modifier = Modifier.height(16.dp))
        KpiCard("Total kWh", String.format("%.2f", uiState.totalKwhThisMonth))
        Spacer(modifier = Modifier.height(8.dp))
        KpiCard("Average Daily kWh", String.format("%.2f", uiState.averageDailyKwh))

        Spacer(modifier = Modifier.height(16.dp))
        if (uiState.chartData.isNotEmpty()) {
            SimpleBarChart(uiState.chartData)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePeriodSelector(selectedPeriod: String, onPeriodSelected: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val items = listOf("1 Month", "7 Days", "30 Days")

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        TextField(
            value = selectedPeriod,
            onValueChange = {},
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            items.forEach { selectionOption ->
                DropdownMenuItem(
                    text = { Text(selectionOption) },
                    onClick = {
                        onPeriodSelected(selectionOption)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
fun KpiCard(title: String, value: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(value, style = MaterialTheme.typography.headlineLarge)
        }
    }
}

@Composable
fun SimpleBarChart(data: List<Pair<String, Double>>) {
    val maxVal = data.maxOfOrNull { it.second } ?: 1.0

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        data.forEach {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .height((200 * (it.second / maxVal)).dp)
                        .width(20.dp)
                        .background(MaterialTheme.colorScheme.primary)
                )
                Text(it.first.take(5), fontSize = 10.sp) // Simple label
            }
        }
    }
                Text(formatChartDateLabel(it.first), fontSize = 10.sp)
            }
        }
    }
}

// Helper function to format date strings for chart labels
fun formatChartDateLabel(dateString: String): String {
    return try {
        val inputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val outputFormat = SimpleDateFormat("MMM dd", Locale.getDefault())
        val date = inputFormat.parse(dateString)
        if (date != null) outputFormat.format(date) else dateString
    } catch (e: Exception) {
        dateString
    }
}

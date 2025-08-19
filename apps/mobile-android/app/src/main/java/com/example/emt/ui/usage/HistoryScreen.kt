package com.example.emt.ui.usage

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.emt.data.Usage
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * History screen that shows a list of readings and supports edit/delete.
 * Provide the list and the callbacks from your ViewModel.
 */
@Composable
fun HistoryScreen(
    items: List<Usage>,
    onEdit: (Usage) -> Unit,
    onDelete: (Usage) -> Unit,
    modifier: Modifier = Modifier
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            items(items, key = { it.id }) { usage ->
                HistoryRow(
                    item = usage,
                    onEdit = { onEdit(usage) },
                    onDelete = {
                        onDelete(usage)
                        // Optional: simple feedback snackbar
                        scope.launch {
                            snackbarHostState.showSnackbar(
                                "Deleted ${formatDate(usage.date)} (${formatKwh(usage.kWh)})"
                            )
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun HistoryRow(
    item: Usage,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = formatDate(item.date),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = formatKwh(item.kWh),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            IconButton(onClick = onEdit) {
                Icon(imageVector = Icons.Outlined.Edit, contentDescription = "Edit")
            }
            IconButton(onClick = onDelete) {
                Icon(imageVector = Icons.Outlined.Delete, contentDescription = "Delete")
            }
        }
        Divider()
    }
}

private fun formatDate(millis: Long): String {
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    return sdf.format(Date(millis))
}

private fun formatKwh(v: Double): String =
    String.format(Locale.getDefault(), "%.2f kWh", v)

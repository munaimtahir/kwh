package com.example.emt.ui.usage

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.emt.data.Usage
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun HistoryScreen(viewModel: UsageViewModel) {
    val usages by viewModel.allUsages.collectAsState()
    var showEditDialog by remember { mutableStateOf(false) }
    var selectedUsage by remember { mutableStateOf<Usage?>(null) }

    if (usages.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text("No usage history yet.")
        }
    } else {
        LazyColumn(modifier = Modifier.padding(16.dp)) {
            items(usages) { usage ->
                UsageListItem(
                    usage = usage,
                    onEdit = {
                        selectedUsage = it
                        showEditDialog = true
                    },
                    onDelete = {
                        viewModel.deleteUsage(it)
                    }
                )
            }
        }
    }

    if (showEditDialog && selectedUsage != null) {
        EditUsageDialog(
            usage = selectedUsage!!,
            onDismiss = { showEditDialog = false },
            onSave = { updatedUsage ->
                viewModel.updateUsage(updatedUsage)
                showEditDialog = false
            }
        )
    }
}

@Composable
fun UsageListItem(usage: Usage, onEdit: (Usage) -> Unit, onDelete: (Usage) -> Unit) {
    val formattedDate = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(usage.date)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$formattedDate: ${usage.kwh} kWh",
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = { onEdit(usage) }) {
            Icon(Icons.Default.Edit, contentDescription = "Edit")
        }
        IconButton(onClick = { onDelete(usage) }) {
            Icon(Icons.Default.Delete, contentDescription = "Delete")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditUsageDialog(usage: Usage, onDismiss: () -> Unit, onSave: (Usage) -> Unit) {
    var kwh by remember { mutableStateOf(usage.kwh.toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Usage") },
        text = {
            Column {
                OutlinedTextField(
                    value = kwh,
                    onValueChange = { kwh = it },
                    label = { Text("kWh") }
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                val updatedKwh = kwh.toDoubleOrNull()
                if (updatedKwh != null) {
                    onSave(usage.copy(kwh = updatedKwh))
                }
            }) {
                Text("Save")
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

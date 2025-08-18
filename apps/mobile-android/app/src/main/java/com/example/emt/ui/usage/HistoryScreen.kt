package com.example.emt.ui.usage

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.emt.data.Usage
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun HistoryScreen(viewModel: UsageViewModel) {
    val usages by viewModel.allUsages.collectAsState()

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
                UsageListItem(usage)
            }
        }
    }
}

@Composable
fun UsageListItem(usage: Usage) {
    val formattedDate = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(usage.date)
    Text(text = "$formattedDate: ${usage.kwh} kWh")
}

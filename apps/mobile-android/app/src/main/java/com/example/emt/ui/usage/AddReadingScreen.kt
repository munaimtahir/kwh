package com.example.emt.ui.usage

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddReadingScreen(viewModel: UsageViewModel) {
    var kwh by remember { mutableStateOf("") }
    val datePickerState = rememberDatePickerState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("Add New Reading", style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(16.dp))

            DatePicker(state = datePickerState, modifier = Modifier.padding(bottom = 16.dp))

            OutlinedTextField(
                value = kwh,
                onValueChange = { kwh = it },
                label = { Text("kWh") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(onClick = {
                val kwhValue = kwh.toDoubleOrNull()
                if (kwhValue == null || kwhValue <= 0) {
                    scope.launch {
                        snackbarHostState.showSnackbar("Invalid kWh value. Please enter a positive number.")
                    }
                } else {
                    val selectedDate = datePickerState.selectedDateMillis?.let { Date(it) } ?: Date()
                    viewModel.addUsage(kwhValue, selectedDate)
                    kwh = "" // Clear input field
                    scope.launch {
                        snackbarHostState.showSnackbar("Usage added successfully.")
                    }
                }
            }) {
                Text("Save")
            }
        }
    }
}

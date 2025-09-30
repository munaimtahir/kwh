package com.example.kwh.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType

/**
 * Generic labeled text field used across the app.
 */
@Composable
fun LabeledTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = true
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = singleLine,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions
    )
}

/**
 * Numeric input wrapper. Keeps only digits and a single dot.
 * If you need integers only, strip the dot logic.
 */
@Composable
fun NumberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    imeAction: ImeAction = ImeAction.Done,
    maxLength: Int? = null,
    allowDecimal: Boolean = true
) {
    LabeledTextField(
        value = value,
        onValueChange = { new ->
            val filtered = buildString {
                var dotSeen = false
                for (ch in new) {
                    when {
                        ch.isDigit() -> append(ch)
                        ch == '.' && allowDecimal && !dotSeen -> { append(ch); dotSeen = true }
                    }
                }
            }
            val result = if (maxLength != null && filtered.length > maxLength) {
                filtered.take(maxLength)
            } else {
                filtered
            }
            onValueChange(result)
        },
        label = label,
        modifier = modifier,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Number,
            imeAction = imeAction
        )
    )
}

package com.example.kwh.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions as FoundationKeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType

/**
 * Basic text input used around the app.
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
 * Numeric-only text field (still text-backed; validate/parse on submit).
 */
@Composable
fun NumberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    imeAction: ImeAction = ImeAction.Done
) {
    LabeledTextField(
        value = value,
        onValueChange = { new ->
            // Allow digits and optional decimal point; tweak if you need integers only.
            val filtered = new.filter { it.isDigit() || it == '.' }
            onValueChange(filtered)
        },
        label = label,
        modifier = modifier,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Number,
            imeAction = imeAction
        )
    )
}

/**
 * Sometimes callers import Foundation KeyboardOptions by mistake.
 * This helper maps it to the UI-text KeyboardOptions to avoid confusion.
 */
fun foundationToUiKeyboardOptions(
    foundation: FoundationKeyboardOptions
): KeyboardOptions = KeyboardOptions(
    keyboardType = when (foundation.keyboardType) {
        KeyboardType.Number -> KeyboardType.Number
        KeyboardType.Phone -> KeyboardType.Phone
        KeyboardType.Email -> KeyboardType.Email
        else -> KeyboardType.Text
    },
    imeAction = foundation.imeAction
)

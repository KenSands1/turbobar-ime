package com.turbobar.ime.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.turbobar.ime.data.InsertMode
import com.turbobar.ime.data.PrefixEntry
import com.turbobar.ime.data.resolvedText

data class MacroDialogResult(
    val prefix: String,
    val label: String,
    val text: String,
    val insertMode: InsertMode
)

/**
 * [editing] is the entry being edited (if the long-pressed slot already had
 * a macro in it) — null means "create new", matching the two-mode dialog
 * from the web prototype. [defaultPrefix] / [defaultText] prefill a new
 * macro from the current typed prefix / draft text, same as before.
 */
@Composable
fun MacroDialog(
    editing: PrefixEntry?,
    defaultPrefix: String,
    defaultText: String,
    onSave: (MacroDialogResult) -> Unit,
    onReset: (() -> Unit)?, // non-null only when editing an existing macro
    onCancel: () -> Unit
) {
    var prefix by remember { mutableStateOf(editing?.prefix ?: defaultPrefix) }
    var label by remember { mutableStateOf(editing?.let { if (it.label == it.resolvedText.trim()) "" else it.label } ?: "") }
    var text by remember { mutableStateOf(editing?.resolvedText ?: defaultText) }
    var insertMode by remember { mutableStateOf(editing?.insertMode ?: InsertMode.TEXT) }

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(if (editing != null) "Edit macro" else "Save as macro") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = prefix,
                    onValueChange = { prefix = it.lowercase() },
                    label = { Text("Trigger prefix") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Short label (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = insertMode == InsertMode.TEXT,
                        onClick = { insertMode = InsertMode.TEXT },
                        label = { Text("Text") }
                    )
                    FilterChip(
                        selected = insertMode == InsertMode.QR,
                        onClick = { insertMode = InsertMode.QR },
                        label = { Text("QR code") }
                    )
                }
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(if (insertMode == InsertMode.QR) "Text to encode as QR" else "Text to save") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )
                if (insertMode == InsertMode.QR) {
                    Text(
                        "Only works in fields that accept images — greyed out elsewhere.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (prefix.isNotBlank() && text.isNotBlank()) {
                    onSave(MacroDialogResult(prefix, label.ifBlank { text }, text, insertMode))
                }
            }) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (onReset != null) {
                    TextButton(onClick = onReset) { Text("Reset") }
                }
                TextButton(onClick = onCancel) { Text("Cancel") }
            }
        }
    )
}

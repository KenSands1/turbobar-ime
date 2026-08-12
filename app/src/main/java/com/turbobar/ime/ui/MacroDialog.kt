package com.turbobar.ime.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
 * IMPORTANT: this is deliberately NOT a system AlertDialog. The first
 * on-device test showed a real bug — a standard AlertDialog popped from
 * inside an IME's window doesn't behave like a normal dialog; it took the
 * whole keyboard down instead of showing anything. IME windows are a
 * special, constrained window type that system dialogs don't reliably
 * layer on top of.
 *
 * The fix: draw this as a plain in-line Composable overlay, inside the
 * SAME ComposeView/window that's already successfully rendering the
 * keyboard, rather than asking Android to open a separate dialog window.
 * A semi-transparent scrim behind a card, both just regular Compose UI —
 * no Window/Dialog APIs involved at all.
 *
 * [editing] is the entry being edited (if the long-pressed slot already had
 * a macro in it) — null means "create new". [defaultPrefix] / [defaultText]
 * prefill a new macro from the current typed prefix / draft text.
 */
@Composable
fun MacroOverlay(
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

    // Scrim — fills the whole keyboard area, tapping it cancels (same as
    // tapping "outside" a normal dialog would).
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(onClick = onCancel),
        contentAlignment = Alignment.BottomCenter
    ) {
        // The card itself — clickable with an empty lambda so taps inside
        // it don't fall through to the scrim's cancel-on-click behavior.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
                .background(Color.White)
                .clickable(onClick = {})
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                if (editing != null) "Edit macro" else "Save as macro",
                style = MaterialTheme.typography.titleMedium
            )

            OutlinedTextField(
                value = prefix,
                onValueChange = { if (it.length <= 2) prefix = it.lowercase() },
                label = { Text("Trigger prefix (max 2 letters)") },
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

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
            ) {
                TextButton(onClick = onCancel) { Text("Cancel") }
                if (onReset != null) {
                    TextButton(onClick = onReset) { Text("Reset") }
                }
                Spacer(Modifier.weight(1f))
                Button(onClick = {
                    if (prefix.isNotBlank() && text.isNotBlank()) {
                        onSave(MacroDialogResult(prefix, label.ifBlank { text }, text, insertMode))
                    }
                }) { Text("Save") }
            }
        }
    }
}

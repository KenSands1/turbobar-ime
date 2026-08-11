@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.turbobar.ime.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.turbobar.ime.data.EntryKind
import com.turbobar.ime.data.InsertMode
import com.turbobar.ime.data.PrefixEntry
import com.turbobar.ime.data.resolvedText

private val AccentColor = Color(0xFF0E9D86)
private val AccentSoft = Color(0xFFE4F6F2)
private val MacroColor = Color(0xFFA6621F)
private val MacroSoft = Color(0xFFFBEEE0)
private val KeyBg = Color(0xFFFFFFFF)
private val KeyBorder = Color(0xFFDDE3E1)

data class KeyboardCallbacks(
    val onLetter: (Char) -> Unit,
    val onSpace: () -> Unit,
    val onBackspace: () -> Unit,
    val onShift: () -> Unit,
    val onSlotTap: (PrefixEntry) -> Unit,
    val onSlotLongPress: (Int, PrefixEntry?) -> Unit, // slot index + current entry (or null if empty)
)

@Composable
fun KeyboardScreen(
    state: KeyboardState,
    callbacks: KeyboardCallbacks,
    imageInsertSupported: Boolean
) {
    val slots by state.slots.collectAsState()
    val shiftMode by state.shiftMode.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFEEF1F0))
            .padding(8.dp)
    ) {
        MacroRow(
            slots = slots,
            shiftMode = shiftMode,
            imageInsertSupported = imageInsertSupported,
            onTap = callbacks.onSlotTap,
            onLongPress = callbacks.onSlotLongPress
        )
        Spacer(Modifier.height(6.dp))
        QwertyRows(shiftMode = shiftMode, onLetter = callbacks.onLetter)
        Spacer(Modifier.height(4.dp))
        BottomRow(
            shiftMode = shiftMode,
            onShift = callbacks.onShift,
            onSpace = callbacks.onSpace,
            onBackspace = callbacks.onBackspace
        )
    }
}

@Composable
private fun MacroRow(
    slots: List<PrefixEntry?>,
    shiftMode: ShiftMode,
    imageInsertSupported: Boolean,
    onTap: (PrefixEntry) -> Unit,
    onLongPress: (Int, PrefixEntry?) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        for (i in 0 until 6) {
            val entry = slots.getOrNull(i)
            val isQr = entry?.insertMode == InsertMode.QR
            val disabled = isQr && !imageInsertSupported
            val bg = when {
                entry == null -> Color(0xFFF2F4F3)
                entry.kind == EntryKind.MACRO -> MacroSoft
                else -> AccentSoft
            }
            val fg = when {
                entry == null -> Color(0xFF9AA5A1)
                entry.kind == EntryKind.MACRO -> MacroColor
                else -> AccentColor
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (disabled) bg.copy(alpha = 0.35f) else bg)
                    .combinedClickable(
                        enabled = !disabled,
                        onClick = { entry?.let(onTap) },
                        onLongClick = { onLongPress(i, entry) }
                    )
                    .padding(2.dp),
                contentAlignment = Alignment.Center
            ) {
                val label = if (entry == null) "+" else {
                    val prefixIcon = if (isQr) "▦ " else ""
                    val raw = entry.label
                    val cased = when (shiftMode) {
                        ShiftMode.CAPS -> raw.uppercase()
                        ShiftMode.TITLE -> raw.replaceFirstChar { it.uppercase() }
                        ShiftMode.NONE -> raw
                    }
                    prefixIcon + (if (cased.length > 12) cased.take(12) + "…" else cased)
                }
                Text(
                    text = label,
                    color = fg,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    maxLines = 2
                )
            }
        }
    }
}

@Composable
private fun QwertyRows(shiftMode: ShiftMode, onLetter: (Char) -> Unit) {
    val row1 = "qwertyuiop"
    val row2 = "asdfghjkl"
    val row3 = "zxcvbnm"

    @Composable
    fun KeyRow(letters: String, horizontalPadding: Dp = 0.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = horizontalPadding),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            for (c in letters) {
                val display = when (shiftMode) {
                    ShiftMode.NONE -> c.toString()
                    ShiftMode.TITLE, ShiftMode.CAPS -> c.uppercaseChar().toString()
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(KeyBg)
                        .combinedClickable(onClick = { onLetter(c) }, onLongClick = {}),
                    contentAlignment = Alignment.Center
                ) {
                    Text(display, fontSize = 15.sp)
                }
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        KeyRow(row1)
        KeyRow(row2, horizontalPadding = 16.dp)
        KeyRow(row3, horizontalPadding = 32.dp)
    }
}

@Composable
private fun BottomRow(
    shiftMode: ShiftMode,
    onShift: () -> Unit,
    onSpace: () -> Unit,
    onBackspace: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(42.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        BottomKey(
            label = if (shiftMode == ShiftMode.CAPS) "⇪" else "⇧",
            weight = 1.3f,
            highlighted = shiftMode != ShiftMode.NONE,
            onClick = onShift
        )
        BottomKey(label = "SPACE", weight = 5f, onClick = onSpace)
        BottomKey(label = "⌫", weight = 1.3f, danger = true, onClick = onBackspace)
    }
}

@Composable
private fun RowScope.BottomKey(
    label: String,
    weight: Float,
    highlighted: Boolean = false,
    danger: Boolean = false,
    onClick: () -> Unit
) {
    val fg = when {
        danger -> Color(0xFFE0524A)
        highlighted -> AccentColor
        else -> Color(0xFF67736F)
    }
    val bg = if (highlighted) AccentSoft else KeyBg
    Box(
        modifier = Modifier
            .weight(weight)
            .fillMaxHeight()
            .clip(RoundedCornerShape(7.dp))
            .background(bg)
            .combinedClickable(onClick = onClick, onLongClick = {}),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = fg, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}


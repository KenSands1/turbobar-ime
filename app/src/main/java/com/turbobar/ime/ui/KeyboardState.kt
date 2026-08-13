package com.turbobar.ime.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.turbobar.ime.data.PrefixDao
import com.turbobar.ime.data.PrefixEntry
import com.turbobar.ime.data.resolvedText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class ShiftMode { NONE, TITLE, CAPS }

/**
 * NOTE: same caveat as everywhere else in this file set — this is a first
 * draft, not verified against a compiler. The overall shape (prefix as a
 * StateFlow, slots derived via flatMapLatest so they automatically re-query
 * whenever the prefix OR the underlying Room data changes) is a standard,
 * idiomatic pattern, but specific API usage should be checked in Android
 * Studio before trusting it.
 *
 * IMPORTANT LIMITATION CARRIED OVER FROM THE WEB PROTOTYPE: `prefix` is
 * still a local mirror of "what we think the user has typed", not a read of
 * the real focused text field — a native IME COULD do better here via
 * InputConnection.getTextBeforeCursor(), which the web/WebView version
 * structurally could not. That upgrade is a reasonable next step but isn't
 * implemented in this pass — flagging it explicitly rather than silently
 * carrying the same limitation forward unremarked.
 */
class KeyboardState(private val dao: PrefixDao) : ViewModel() {

    private val _prefix = MutableStateFlow("")
    // Deliberately NOT capped at 2 characters — commitSlot() needs the true
    // full typed length to correctly delete everything typed before
    // inserting the tapped word, even once you're past the point where
    // anything new could match (see `slots` below for where the cap
    // actually happens).
    val prefix: StateFlow<String> = _prefix

    private val _shiftMode = MutableStateFlow(ShiftMode.NONE)
    val shiftMode: StateFlow<ShiftMode> = _shiftMode

    // Query key is capped at the first 2 characters — once macros were also
    // capped at 2-letter prefixes, nothing can ever match past that point
    // anyway, so instead of the bar going blank once you keep typing, it
    // keeps showing (and keeps letting you tap) whatever matched at your
    // first 2 letters. distinctUntilChanged avoids needlessly re-running
    // the same query again on every extra keystroke once the capped key
    // stops changing (e.g. "wh" -> "whe" -> "when" all map to "wh").
    val slots: StateFlow<List<PrefixEntry?>> = _prefix
        .map { it.take(2) }
        .distinctUntilChanged()
        .flatMapLatest { key -> dao.observeSlotsForPrefix(key) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun onLetterTyped(letter: Char) {
        _prefix.value = _prefix.value + letter.lowercaseChar()
        if (_shiftMode.value == ShiftMode.TITLE) _shiftMode.value = ShiftMode.NONE
    }

    fun onWordBoundary() {
        _prefix.value = ""
    }

    fun onBackspace() {
        if (_prefix.value.isNotEmpty()) {
            _prefix.value = _prefix.value.dropLast(1)
        }
        // if backspacing past a word boundary (deleted a space), we can't
        // reconstruct the previous word's prefix from here — same
        // approximation the web prototype used, resets to empty
    }

    fun cycleShift() {
        _shiftMode.value = when (_shiftMode.value) {
            ShiftMode.NONE -> ShiftMode.TITLE
            ShiftMode.TITLE -> ShiftMode.CAPS
            ShiftMode.CAPS -> ShiftMode.NONE
        }
    }

    /** Call after a slot commit — replaces the typed prefix with the full
     *  word/macro text, same "ccan bug" fix as the web prototype: the
     *  prefix is consumed, not left dangling in front of the inserted text. */
    fun consumePrefixOnCommit() {
        _prefix.value = ""
    }

    fun currentPrefixLength(): Int = _prefix.value.length

    suspend fun saveMacro(
        prefix: String,
        label: String,
        text: String,
        insertMode: com.turbobar.ime.data.InsertMode,
        editingId: Long? = null
    ) {
        val cappedPrefix = prefix.take(2) // hard limit — see MacroDialog.kt for why
        viewModelScope.launch {
            if (editingId != null) {
                // editing: remove the old row, then insert fresh — same
                // "remove then re-add" approach as the web prototype, which
                // correctly handles a changed trigger prefix too
                dao.delete(editingId)
            }
            val nextSlot = (dao.maxSlotOrderForPrefix(cappedPrefix) ?: -1) + 1
            dao.insert(
                PrefixEntry(
                    prefix = cappedPrefix,
                    slotOrder = nextSlot.coerceAtMost(5),
                    kind = com.turbobar.ime.data.EntryKind.MACRO,
                    label = label,
                    originalText = "+", // from-scratch macro — no shipped baseline, "+" is the reset target
                    currentText = text,
                    insertMode = insertMode
                )
            )
        }
    }

    suspend fun resetEntry(id: Long) {
        dao.resetToOriginal(id)
    }
}

fun PrefixEntry?.displayLabel(shiftMode: ShiftMode): String {
    if (this == null) return "+"
    val text = this.label
    return when (shiftMode) {
        ShiftMode.CAPS -> text.uppercase()
        ShiftMode.TITLE -> text.replaceFirstChar { it.uppercase() }
        ShiftMode.NONE -> text
    }
}

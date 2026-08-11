package com.turbobar.ime.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Unified schema — the "scrap the lookup table, consolidate into the database"
 * change. Every slot, whether a shipped dictionary word or a user macro, is a
 * row in this one table. No more separate static PREFIX_TABLE + in-memory
 * overrides Map — this table IS both, distinguished by [kind].
 *
 * originalText / currentText follow the pattern discussed earlier:
 *  - originalText is the shipped baseline. An app update can safely overwrite
 *    this column for WORD rows without ever touching what a user customized.
 *  - currentText is null until the user edits or creates something. Display
 *    logic always resolves to (currentText ?: originalText).
 *  - A user-created macro (no shipped baseline at all) has originalText = "+"
 *    per your call — literal text, not a null sentinel, since that's one
 *    fewer code path for whoever builds this for real.
 *  - "Reset" is always the same operation regardless of row type: clear
 *    currentText. For a WORD row that reverts to the shipped word. For a
 *    from-scratch macro that reverts to "+", i.e. empty.
 */
@Entity(tableName = "prefix_entries")
data class PrefixEntry(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** The trigger — "" for Stage 0, a single letter for Stage 1, two letters
     *  for Stage 2, or anything (e.g. "zh", "sy") for a macro. */
    val prefix: String,

    /** Where this row sits among however many share this prefix (0-5).
     *  Determines slot position before any user drag-reorder is applied. */
    val slotOrder: Int,

    /** WORD = shipped dictionary completion. MACRO = user-facing shortcut,
     *  shown with the amber/label styling, editable via long-press either way. */
    val kind: EntryKind,

    /** Short label shown on the slot. For WORD rows this is just the word
     *  itself. For MACRO rows it can differ from the text it inserts (e.g.
     *  label "later" for text "see you later"). */
    val label: String,

    /** The shipped baseline. "+" for slots with no default content at all
     *  (an empty Stage-2 slot, or a from-scratch macro before it's ever
     *  been filled in). */
    val originalText: String,

    /** User's override, if any. Null means "use originalText as-is" —
     *  this is what makes reset a single, universal operation. */
    val currentText: String? = null,

    /** For MACRO rows only: whether tapping this slot inserts text or
     *  generates+inserts a QR code from the text as its payload. */
    val insertMode: InsertMode = InsertMode.TEXT
)

enum class EntryKind { WORD, MACRO }
enum class InsertMode { TEXT, QR }

/** Convenience accessor — every read path should go through this rather than
 *  touching originalText/currentText directly, so "which one wins" is never
 *  duplicated logic. */
val PrefixEntry.resolvedText: String
    get() = currentText ?: originalText

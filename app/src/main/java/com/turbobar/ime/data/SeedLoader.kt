package com.turbobar.ime.data

import android.content.Context
import org.json.JSONArray

/**
 * Loads the bundled, human-reviewed data (the CSV table you've been
 * reviewing by hand, plus the 22 preloaded macros) into Room on first
 * launch only. This deliberately does NOT use Room's createFromAsset()
 * prepackaged-database approach — that requires the asset .db file's
 * schema hash to exactly match what Room generates from the @Entity
 * annotations at compile time, which is a real, easy-to-get-wrong step
 * I have no way to verify without a compiler in my environment. Parsing
 * plain CSV/JSON at runtime is slower on first launch but has no such
 * hidden failure mode.
 */
object SeedLoader {

    suspend fun seedIfEmpty(context: Context, dao: PrefixDao) {
        if (dao.countAll() > 0) return // already seeded — never re-run automatically

        val entries = mutableListOf<PrefixEntry>()
        entries += loadWordTable(context)
        entries += loadPreloadedMacros(context)
        dao.insertAll(entries)
    }

    private fun loadWordTable(context: Context): List<PrefixEntry> {
        val entries = mutableListOf<PrefixEntry>()
        context.assets.open("Turbo_Bar_Prefix_Tables.csv").bufferedReader().use { reader ->
            val header = reader.readLine() // "Stage,Prefix,Slot1,Slot2,Slot3,Slot4,Slot5,Slot6"
            reader.forEachLine { line ->
                val cols = parseCsvLine(line)
                if (cols.size < 8) return@forEachLine
                val prefix = cols[1]
                for (slot in 0..5) {
                    val word = cols[2 + slot].trim()
                    if (word.isEmpty()) continue
                    entries += PrefixEntry(
                        prefix = prefix,
                        slotOrder = slot,
                        kind = EntryKind.WORD,
                        label = word,
                        originalText = "$word ", // trailing space, same as the web prototype's convention
                        currentText = null,
                        insertMode = InsertMode.TEXT
                    )
                }
            }
        }
        return entries
    }

    private fun loadPreloadedMacros(context: Context): List<PrefixEntry> {
        val entries = mutableListOf<PrefixEntry>()
        val json = context.assets.open("preloaded_macros.json").bufferedReader().use { it.readText() }
        val arr = JSONArray(json)
        // group by prefix to assign slotOrder correctly when several macros
        // share one prefix (e.g. "zh" holding 4 messages)
        val slotCounters = HashMap<String, Int>()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            val prefix = obj.getString("Prefix")
            val label = obj.getString("Label")
            val text = obj.getString("Text")
            val slot = slotCounters.getOrDefault(prefix, 0)
            slotCounters[prefix] = slot + 1
            entries += PrefixEntry(
                prefix = prefix,
                slotOrder = slot,
                kind = EntryKind.MACRO,
                label = label,
                originalText = text, // these ARE the shipped baseline for preloaded macros —
                                      // resettable back to this exact text, per the schema design
                currentText = null,
                insertMode = InsertMode.TEXT
            )
        }
        // one demo QR macro, same as the web prototype had
        entries += PrefixEntry(
            prefix = "zq",
            slotOrder = 0,
            kind = EntryKind.MACRO,
            label = "demo qr",
            originalText = "https://example.com",
            currentText = null,
            insertMode = InsertMode.QR
        )
        return entries
    }

    /** Minimal CSV parser — the source file has no quoted/escaped commas in
     *  any field (all words, no free text), so a plain split is safe here. */
    private fun parseCsvLine(line: String): List<String> = line.split(",")
}

package com.turbobar.ime.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PrefixDao {

    /** Live query — the keyboard's top row observes this directly, so
     *  editing a macro (or, on a future sync, updating the shipped word
     *  table) is reflected immediately without any manual re-render call. */
    @Query("SELECT * FROM prefix_entries WHERE prefix = :prefix ORDER BY slotOrder ASC LIMIT 6")
    fun observeSlotsForPrefix(prefix: String): Flow<List<PrefixEntry>>

    @Query("SELECT * FROM prefix_entries WHERE prefix = :prefix ORDER BY slotOrder ASC LIMIT 6")
    suspend fun getSlotsForPrefix(prefix: String): List<PrefixEntry>

    @Query("SELECT COUNT(*) FROM prefix_entries WHERE kind = 'MACRO'")
    fun observeMacroCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: PrefixEntry): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<PrefixEntry>)

    @Update
    suspend fun update(entry: PrefixEntry)

    /** Reset = clear the override. Same operation for every row, shipped
     *  word or from-scratch macro alike — see the resolvedText comment in
     *  PrefixEntry.kt for why that unification is the point. */
    @Query("UPDATE prefix_entries SET currentText = NULL WHERE id = :id")
    suspend fun resetToOriginal(id: Long)

    @Query("DELETE FROM prefix_entries WHERE id = :id")
    suspend fun delete(id: Long)

    /** Renumbers slotOrder for a prefix to match a new order — backs the
     *  drag-to-reorder feature from the web prototype. */
    @Update
    suspend fun updateAll(entries: List<PrefixEntry>)

    @Query("SELECT COUNT(*) FROM prefix_entries")
    suspend fun countAll(): Int

    @Query("SELECT MAX(slotOrder) FROM prefix_entries WHERE prefix = :prefix")
    suspend fun maxSlotOrderForPrefix(prefix: String): Int?
}

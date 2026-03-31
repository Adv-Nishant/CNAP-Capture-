package com.cnapcapture.app.data

import androidx.lifecycle.LiveData
import androidx.room.*

/**
 * Data-access object for [CnapEntry].
 *
 * All write operations are suspending so they must be called from a coroutine.
 * Read operations that observe changes are exposed as [LiveData] so the UI
 * reacts automatically when the underlying data changes.
 */
@Dao
interface CnapDao {

    // -----------------------------------------------------------------------
    // Queries
    // -----------------------------------------------------------------------

    /** All entries, newest first. */
    @Query("SELECT * FROM cnap_entries ORDER BY timestampMs DESC")
    fun getAllEntries(): LiveData<List<CnapEntry>>

    /**
     * Entries whose [CnapEntry.phoneNumber] or [CnapEntry.cnapName] contains [query],
     * case-insensitive, newest first.
     */
    @Query(
        """
        SELECT * FROM cnap_entries
        WHERE phoneNumber LIKE '%' || :query || '%'
           OR cnapName    LIKE '%' || :query || '%'
        ORDER BY timestampMs DESC
        """
    )
    fun search(query: String): LiveData<List<CnapEntry>>

    /**
     * Returns the most recently captured [CnapEntry] for [phoneNumber], or null if none exists.
     * Used for repeat-caller detection and pre-filling the name on a new call.
     */
    @Query(
        "SELECT * FROM cnap_entries WHERE phoneNumber = :phoneNumber ORDER BY timestampMs DESC LIMIT 1"
    )
    suspend fun getLatestForNumber(phoneNumber: String): CnapEntry?

    /** Returns the count of existing entries for [phoneNumber]. */
    @Query("SELECT COUNT(*) FROM cnap_entries WHERE phoneNumber = :phoneNumber")
    suspend fun countForNumber(phoneNumber: String): Int

    // -----------------------------------------------------------------------
    // Writes
    // -----------------------------------------------------------------------

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: CnapEntry): Long

    @Delete
    suspend fun delete(entry: CnapEntry)

    @Query("DELETE FROM cnap_entries")
    suspend fun deleteAll()
}

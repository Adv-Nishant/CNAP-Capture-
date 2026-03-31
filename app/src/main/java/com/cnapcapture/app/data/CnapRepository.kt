package com.cnapcapture.app.data

import android.content.Context
import androidx.lifecycle.LiveData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Single source of truth for CNAP entry data.
 *
 * All database access goes through this repository so that the UI layer
 * (ViewModel / Activity) never touches Room directly.
 */
class CnapRepository(context: Context) {

    private val dao: CnapDao = AppDatabase.getInstance(context).cnapDao()

    // -----------------------------------------------------------------------
    // Observables (for the UI)
    // -----------------------------------------------------------------------

    val allEntries: LiveData<List<CnapEntry>> = dao.getAllEntries()

    fun search(query: String): LiveData<List<CnapEntry>> = dao.search(query)

    // -----------------------------------------------------------------------
    // Suspend helpers (call from a coroutine / ViewModel)
    // -----------------------------------------------------------------------

    /**
     * Inserts a new entry. Automatically marks it as a repeat-caller entry if
     * a previous capture for the same phone number already exists.
     *
     * @return the row ID of the newly inserted entry.
     */
    suspend fun insertEntry(
        phoneNumber: String,
        cnapName: String,
        timestampMs: Long,
        source: String
    ): Long = withContext(Dispatchers.IO) {
        val previousCount = dao.countForNumber(phoneNumber)
        val entry = CnapEntry(
            phoneNumber = phoneNumber,
            cnapName = cnapName,
            timestampMs = timestampMs,
            source = source,
            isRepeatCaller = previousCount > 0
        )
        dao.insert(entry)
    }

    suspend fun deleteEntry(entry: CnapEntry) = withContext(Dispatchers.IO) {
        dao.delete(entry)
    }

    suspend fun deleteAll() = withContext(Dispatchers.IO) {
        dao.deleteAll()
    }

    /**
     * Returns the most recently captured CNAP name for [phoneNumber], or null if the number
     * has never been seen before.  Used for repeat-caller recognition: surface the saved name
     * before the network sends the CNAP signal.
     */
    suspend fun getPreviousNameForNumber(phoneNumber: String): CnapEntry? =
        withContext(Dispatchers.IO) {
            dao.getLatestForNumber(phoneNumber)
        }
}

package com.cnapcapture.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.switchMap
import androidx.lifecycle.viewModelScope
import com.cnapcapture.app.data.CnapEntry
import com.cnapcapture.app.data.CnapRepository
import kotlinx.coroutines.launch

/**
 * ViewModel for [MainActivity]. Survives configuration changes and owns the search state.
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = CnapRepository(application)

    /** Emits the current search query (empty string means "show all"). */
    private val _searchQuery = MutableLiveData("")

    /**
     * The list of [CnapEntry] objects to display.  Switches between the full list and a
     * filtered list based on the current [_searchQuery].
     */
    val entries: LiveData<List<CnapEntry>> = _searchQuery.switchMap { query ->
        if (query.isNullOrBlank()) {
            repository.allEntries
        } else {
            repository.search(query.trim())
        }
    }

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun deleteEntry(entry: CnapEntry) {
        viewModelScope.launch {
            repository.deleteEntry(entry)
        }
    }

    fun deleteAll() {
        viewModelScope.launch {
            repository.deleteAll()
        }
    }

    fun restoreEntry(entry: CnapEntry) {
        viewModelScope.launch {
            repository.insertEntry(
                phoneNumber = entry.phoneNumber,
                cnapName = entry.cnapName,
                timestampMs = entry.timestampMs,
                source = entry.source
            )
        }
    }
}

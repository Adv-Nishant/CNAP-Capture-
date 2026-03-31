package com.cnapcapture.app.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.cnapcapture.app.data.CnapEntry
import com.cnapcapture.app.databinding.ItemCnapEntryBinding
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * [ListAdapter] for the CNAP call-log list.
 *
 * Uses [DiffUtil] for efficient, animated updates when the underlying data changes.
 */
class CnapEntryAdapter(
    private val onAddToContacts: (CnapEntry) -> Unit,
    private val onDelete: (CnapEntry) -> Unit
) : ListAdapter<CnapEntry, CnapEntryAdapter.ViewHolder>(DIFF_CALLBACK) {

    private val timestampFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a", Locale.getDefault())
            .withZone(ZoneId.systemDefault())

    // -----------------------------------------------------------------------
    // RecyclerView.Adapter
    // -----------------------------------------------------------------------

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemCnapEntryBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    // -----------------------------------------------------------------------
    // ViewHolder
    // -----------------------------------------------------------------------

    inner class ViewHolder(
        private val binding: ItemCnapEntryBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(entry: CnapEntry) {
            binding.tvCnapName.text = entry.cnapName
            binding.tvPhoneNumber.text = entry.phoneNumber
            binding.tvTimestamp.text = timestampFormatter.format(Instant.ofEpochMilli(entry.timestampMs))
            binding.tvSource.text = entry.source

            // Repeat-caller badge
            binding.chipRepeatCaller.visibility =
                if (entry.isRepeatCaller) View.VISIBLE else View.GONE

            // "Add to Contacts" – only shown when the number is not yet in contacts
            // (the Activity will set the visibility after checking; default to VISIBLE
            // so newly captured entries invite the user to save the contact).
            binding.btnAddToContacts.visibility = View.VISIBLE
            binding.btnAddToContacts.setOnClickListener { onAddToContacts(entry) }

            // Long-press to delete
            binding.root.setOnLongClickListener {
                onDelete(entry)
                true
            }
        }
    }

    // -----------------------------------------------------------------------
    // DiffUtil
    // -----------------------------------------------------------------------

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<CnapEntry>() {
            override fun areItemsTheSame(oldItem: CnapEntry, newItem: CnapEntry): Boolean =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: CnapEntry, newItem: CnapEntry): Boolean =
                oldItem == newItem
        }
    }
}

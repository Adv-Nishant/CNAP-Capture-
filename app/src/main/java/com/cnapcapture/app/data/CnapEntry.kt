package com.cnapcapture.app.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A single logged CNAP event: the government-verified caller name seen during one incoming call,
 * together with the phone number and the moment it was captured.
 *
 * [source] describes how the name was obtained:
 *   "TELECOM_API" – retrieved from the Android Telecom / InCallService layer (structured)
 *   "ACCESSIBILITY" – read from the on-screen text via the Accessibility Service (visual fallback)
 */
@Entity(
    tableName = "cnap_entries",
    indices = [Index(value = ["phoneNumber"])]
)
data class CnapEntry(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** E.164-formatted or raw phone number as returned by TelephonyManager. */
    val phoneNumber: String,

    /** The CNAP-verified caller name captured from the network or screen. */
    val cnapName: String,

    /** Unix epoch milliseconds when the name was captured. */
    val timestampMs: Long,

    /** How the name was obtained – "TELECOM_API" or "ACCESSIBILITY". */
    val source: String,

    /**
     * True if a previous entry for the same [phoneNumber] already existed in the database at
     * the time this entry was inserted, meaning the user has received a call from this number
     * before and a name was captured then.
     */
    val isRepeatCaller: Boolean = false
)

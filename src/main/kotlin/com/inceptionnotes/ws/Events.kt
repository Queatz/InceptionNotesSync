package com.inceptionnotes.ws

import com.inceptionnotes.db.Invitation
import com.inceptionnotes.db.Note

typealias IdAndRev = List<String>
val IdAndRev.id get() = get(0)
val IdAndRev.rev get() = get(1)
fun idAndRev(id: String, rev: String): IdAndRev = listOf(id, rev)
fun Note.toIdAndRev() = idAndRev(id!!, rev!!)

// This must be the first event sent after a connection is opened
data class IdentifyEvent(
    val device: String
) {
    companion object {
        const val ACTION = "identify"
    }
}

data class StateEvent(
    val notes: List<IdAndRev>
) {
    companion object {
        const val ACTION = "state"
    }
}

data class SyncEvent(
    // Must contain rev otherwise it will not sync
    val notes: List<Note>
) {
    companion object {
        const val ACTION = "sync"
    }
}

data class IdentifyOutgoingEvent(
    val invitation: Invitation?,
    val notes: List<Note>
) : OutgoingEvent("identify")

data class SyncOutgoingEvent(
    val notes: List<Note>
) : OutgoingEvent("sync")

data class StateOutgoingEvent(
    val notes: List<IdAndRev>
) : OutgoingEvent("state")

open class OutgoingEvent(@Suppress("unused") val action: String)

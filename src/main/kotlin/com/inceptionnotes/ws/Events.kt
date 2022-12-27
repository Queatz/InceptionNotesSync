package com.inceptionnotes.ws

import com.inceptionnotes.db.Invitation
import com.inceptionnotes.db.Note
import kotlinx.serialization.Serializable

typealias IdAndRev = List<String>
val IdAndRev.id get() = get(0)
val IdAndRev.rev get() = get(1)
fun idAndRev(id: String, rev: String): IdAndRev = listOf(id, rev)
fun Note.toIdAndRev() = idAndRev(id!!, rev!!)

// This must be the first event sent after a connection is opened
@Serializable
data class IdentifyEvent(
    val device: String
) : IncomingEvent() {
    companion object {
        const val ACTION = "identify"
    }
}

@Serializable
data class StateEvent(
    val notes: List<IdAndRev>
) : IncomingEvent() {
    companion object {
        const val ACTION = "state"
    }
}

@Serializable
data class SyncEvent(
    val notes: List<Note>
) : IncomingEvent() {
    companion object {
        const val ACTION = "sync"
    }
}

@Serializable
data class IdentifyOutgoingEvent(
    val invitation: Invitation
) : OutgoingEvent() {
    companion object {
        const val ACTION = "identify"
    }
}

@Serializable
data class SyncOutgoingEvent(
    val notes: List<Note>
) : OutgoingEvent() {
    companion object {
        const val ACTION = "sync"
    }
}

@Serializable
data class StateOutgoingEvent(
    val notes: List<IdAndRev>
) : OutgoingEvent() {
    companion object {
        const val ACTION = "state"
    }
}

@Serializable sealed class OutgoingEvent
@Serializable sealed class IncomingEvent

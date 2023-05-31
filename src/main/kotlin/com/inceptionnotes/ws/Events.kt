package com.inceptionnotes.ws

import com.inceptionnotes.db.Invitation
import com.inceptionnotes.db.Note
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

typealias IdAndRev = List<String?>
val IdAndRev.id get() = get(0)!!
val IdAndRev.rev get() = get(1)!!
val IdAndRev.oldRev get() = get(2)
fun Note.toIdAndRev(oldRev: String? = null) = listOf(id!!, rev!!, oldRev)

/**
 * Set the device token for this session, expecting invitation details in return.
 *
 * Must be the first event after a session is opened.
 */
@Serializable
data class IdentifyEvent(
    val device: String
) : IncomingEvent() {
    companion object {
        const val ACTION = "identify"
    }
}

/**
 * Tell the server the client's current state, expecting sync events in return.
 */
@Serializable
data class StateEvent(
    val notes: List<IdAndRev>
) : IncomingEvent() {
    companion object {
        const val ACTION = "state"
    }
}

/**
 * Tell the server about note changes.
 */
@Serializable
data class SyncEvent(
    val notes: List<JsonObject>
) : IncomingEvent() {
    companion object {
        const val ACTION = "sync"
    }
}

/**
 * Tell the server to send complete note(s).
 */
@Serializable
data class GetEvent(
    val notes: List<String>
) : IncomingEvent() {
    companion object {
        const val ACTION = "get"
    }
}

/**
 * Tell the client their invitation details.
 */
@Serializable
data class IdentifyOutgoingEvent(
    val invitation: Invitation
) : OutgoingEvent() {
    companion object {
        const val ACTION = "identify"
    }
}

/**
 * Tell the client about note changes.
 *
 * @notes The list of notes containing changed props.
 * @gone A list of notes the client should forget.
 * @view A list of notes the client should not edit.
 * @full Whether the list of notes is all changed notes
 */
@Serializable
data class SyncOutgoingEvent(
    val notes: List<JsonObject>,
    val gone: List<String>? = null,
    val view: List<String>? = null,
    val full: Boolean? = null
) : OutgoingEvent() {
    companion object {
        const val ACTION = "sync"
    }
}

/**
 * Send the client a full note.
 */
@Serializable
data class GetOutgoingEvent(
    val notes: List<Note>
) : OutgoingEvent() {
    companion object {
        const val ACTION = "get"
    }
}

/**
 * Tell the client invitation(s) changed.
 */
@Serializable
data class InvitationOutgoingEvent(
    val reload: Boolean = true
) : OutgoingEvent() {
    companion object {
        const val ACTION = "invitation"
    }
}

/**
 * Tell the client the current server state of notes.
 */
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

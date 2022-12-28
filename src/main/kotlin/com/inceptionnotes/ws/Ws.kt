package com.inceptionnotes.ws

import com.inceptionnotes.*
import com.inceptionnotes.db.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*

val actions = mapOf(
    IdentifyOutgoingEvent::class to IdentifyOutgoingEvent.ACTION,
    SyncOutgoingEvent::class to SyncOutgoingEvent.ACTION,
    StateOutgoingEvent::class to StateOutgoingEvent.ACTION,
)

inline fun <reified T : OutgoingEvent> T.toJsonArrayEvent() = buildJsonArray {
    add(
        json.encodeToJsonElement(
            actions[this@toJsonArrayEvent::class]
                ?: throw RuntimeException("${this@toJsonArrayEvent::class} has no registered action")
        )
    )
    add(json.encodeToJsonElement(this@toJsonArrayEvent))
}

class WsSession(val session: DefaultWebSocketServerSession, val noteChanged: suspend (Invitation, Note) -> Unit) {

    var invitation: Invitation? = null
        private set
    private var deviceToken: String? = null
    private val me get() = invitation!!.id!!

    suspend fun sendNote(note: Note) {
        send(listOf(SyncOutgoingEvent(listOf(note))))
    }

    private suspend fun identify(event: IdentifyEvent): List<OutgoingEvent> {
        deviceToken = event.device
        invitation = db.invitationFromDeviceToken(event.device)

        return if (invitation == null) {
            session.close(CloseReason(CloseReason.Codes.NORMAL, "no invitation"))
            emptyList()
        } else {
            listOf(IdentifyOutgoingEvent(invitation!!))
        }
    }

    private suspend fun state(event: StateEvent): List<OutgoingEvent> {
        val clientState = event.notes.associateBy { it.id }
        val stateDiff = db.allNoteRevsByInvitation(me)
            .filter { clientState[it.id]?.rev != it.rev }
            .mapNotNull { db.document(Note::class, it.id!!) }
        return listOf(SyncOutgoingEvent(stateDiff))
    }

    private suspend fun sync(event: SyncEvent): List<OutgoingEvent> {
        val state = event.notes.mapNotNull { clientNote ->
            val note = db.document(Note::class, clientNote.id!!)

            if (note == null) {
                clientNote.rev = null // ensure this gets set by server
                clientNote.created = null
                clientNote.updated = null
                clientNote.steward = me
                val newNote = db.insert(clientNote)
                noteChanged(invitation!!, newNote)
                newNote.toIdAndRev()
            } else if (clientNote.rev == note.rev) {
                // todo check if they actually can edit this note
                note.updateFrom(clientNote)
                val updatedNote = db.update(note)
                clientNote.rev = updatedNote.rev
                noteChanged(invitation!!, clientNote)
                updatedNote.toIdAndRev()
            } else {
                null
            }
        }

        return if (state.isEmpty()) emptyList() else listOf(StateOutgoingEvent(state))
    }

    suspend fun send(events: List<OutgoingEvent>) =
        session.outgoing.send(
            Frame.Text(
                json.encodeToString(
                    events.map { event -> event.toJsonArrayEvent() }
                )
            )
        )

    suspend fun receive(text: String): List<OutgoingEvent> {
        return json.parseToJsonElement(text).jsonArray
            .flatMap {
                val action = it.jsonArray[0].jsonPrimitive.contentOrNull
                val element = it.jsonArray[1]
                when (action) {
                    IdentifyEvent.ACTION -> identify(json.decodeFromJsonElement(element))
                    StateEvent.ACTION -> state(json.decodeFromJsonElement(element))
                    SyncEvent.ACTION -> sync(json.decodeFromJsonElement(element))
                    else -> {
                        logger.warn("$text does not contain a known action")
                        emptyList()
                    }
                }
            }
    }
}

private fun Note.updateFrom(referenceNote: Note) {
    updateAllFrom(
        referenceNote,
        Note::invitations,
        Note::name,
        Note::description, // todo how to nullify description
        Note::checked,
        Note::color,
        Note::items,
        Note::ref,
        Note::options,
        Note::backgroundUrl,
        Note::collapsed,
        Note::estimate
    )
}

class Ws {

    private val sessions = mutableSetOf<WsSession>()

    fun connect(session: DefaultWebSocketServerSession) = sessions.add(WsSession(session, this::noteChanged))
    fun disconnect(session: DefaultWebSocketServerSession) = sessions.removeIf { it.session == session }

    fun getSession(invitation: Invitation) = sessions.find { it.invitation?.id == invitation.id!! }

    suspend fun frame(serverSession: DefaultWebSocketServerSession, text: String) {
        sessions.first { it.session == serverSession }.let { session ->
            val events = session.receive(text)

            if (events.isNotEmpty()) {
                session.send(events)
            }
        }
    }

    private suspend fun noteChanged(invitation: Invitation, note: Note) {
        db.invitationIdsForNote(note.id!!).let { invitations ->
            sessions.forEach {
                if (it.invitation == null
                    || it.invitation!!.id == invitation.id
                    || !invitations.contains(it.invitation!!.id)
                ) return@forEach

                scope.launch { it.sendNote(note) }
            }
        }
    }
}

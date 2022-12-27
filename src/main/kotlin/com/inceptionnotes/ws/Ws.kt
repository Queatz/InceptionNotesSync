package com.inceptionnotes.ws

import com.inceptionnotes.db
import com.inceptionnotes.db.Invitation
import com.inceptionnotes.db.Note
import com.inceptionnotes.db.allNoteRevsByInvitation
import com.inceptionnotes.db.invitationFromDeviceToken
import com.inceptionnotes.logger
import com.inceptionnotes.json
import com.inceptionnotes.updateAllFrom
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*

val actions = mapOf(
    IdentifyOutgoingEvent::class to IdentifyOutgoingEvent.ACTION,
    SyncOutgoingEvent::class to SyncOutgoingEvent.ACTION,
    StateOutgoingEvent::class to StateOutgoingEvent.ACTION,
)

inline fun <reified T : OutgoingEvent> T.toArrayEvent() = listOf(
    actions[this@toArrayEvent::class] ?: throw RuntimeException("${this@toArrayEvent::class} has no registered action"),
    this
)

inline fun <reified T : OutgoingEvent> T.toJsonArrayEvent() = buildJsonArray {
    add(json.encodeToJsonElement(actions[this@toJsonArrayEvent::class] ?: throw RuntimeException("${this@toJsonArrayEvent::class} has no registered action")))
    add(json.encodeToJsonElement(this@toJsonArrayEvent))
}

class WsSession(val session: DefaultWebSocketServerSession) {

    var invitation: Invitation? = null
        private set
    private var deviceToken: String? = null
    private val me get() = invitation!!.id!!

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
                db.insert(clientNote).toIdAndRev()
            } else if (clientNote.rev == note.rev) {
                // todo check if they actually can edit this note
                note.updateFrom(clientNote)
                db.update(note).toIdAndRev()
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
                    events.map { event -> event.toJsonArrayEvent()}
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

    fun connect(session: DefaultWebSocketServerSession) = sessions.add(WsSession(session))
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
}

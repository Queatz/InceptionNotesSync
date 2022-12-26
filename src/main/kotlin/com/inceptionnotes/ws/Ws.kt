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
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.reflect.KMutableProperty1

class WsSession(val session: DefaultWebSocketServerSession) {

    private var invitation: Invitation? = null
    private var deviceToken: String? = null
    private val me get() = invitation!!.id!!

    suspend fun identify(event: IdentifyEvent) {
        deviceToken = event.device
        invitation = db.invitationFromDeviceToken(event.device)

        if (invitation == null) {
            session.close(CloseReason(CloseReason.Codes.NORMAL, "no invitation"))
        } else {
            send(IdentifyOutgoingEvent(invitation, db.allNoteRevsByInvitation(invitation!!.id!!)))
        }
    }

    suspend fun state(event: StateEvent) {
        val clientState = event.notes.associateBy { it.id }
        val stateDiff = db.allNoteRevsByInvitation(me)
            .filter { clientState[it.id]?.rev == it.rev }
            .mapNotNull { db.document(Note::class, it.id!!) }
        send(SyncOutgoingEvent(stateDiff))
    }

    suspend fun sync(event: SyncEvent) {
        val state = event.notes.mapNotNull { clientNote ->
            val note = db.document(Note::class, clientNote.id!!)

            if (note == null) {
                clientNote.rev = null // ensure set by server
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
        send(StateOutgoingEvent(state))
    }

    private suspend fun send(event: OutgoingEvent) =
        session.outgoing.send(Frame.Text(json.encodeToString(event)))
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

    suspend fun frame(session: DefaultWebSocketServerSession, text: String) {
        val wsSession = sessions.first { it.session == session }
        val element = json.parseToJsonElement(text).jsonObject
        when (element[ACTION]?.jsonPrimitive?.contentOrNull) {
            IdentifyEvent.ACTION -> wsSession.identify(json.decodeFromJsonElement(element))
            StateEvent.ACTION -> wsSession.state(json.decodeFromJsonElement(element))
            SyncEvent.ACTION -> wsSession.sync(json.decodeFromJsonElement(element))
            else -> logger.warn("$text does not contain a known action")
        }
    }

    companion object {
        private const val ACTION = "action"
    }
}

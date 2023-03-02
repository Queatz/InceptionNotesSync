package com.inceptionnotes.ws

import com.inceptionnotes.*
import com.inceptionnotes.db.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import kotlin.reflect.KMutableProperty1

val actions = mapOf(
    IdentifyOutgoingEvent::class to IdentifyOutgoingEvent.ACTION,
    SyncOutgoingEvent::class to SyncOutgoingEvent.ACTION,
    StateOutgoingEvent::class to StateOutgoingEvent.ACTION,
    GetOutgoingEvent::class to GetOutgoingEvent.ACTION,
    InvitationOutgoingEvent::class to InvitationOutgoingEvent.ACTION,
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

class WsSession(val session: DefaultWebSocketServerSession, val noteChanged: suspend (Invitation?, JsonObject) -> Unit) {

    var invitation: Invitation? = null
        private set
    var deviceToken: String? = null
        private set
    private val me get() = invitation!!.id!!

    suspend fun sendNote(jsonObject: JsonObject) {
        send(listOf(SyncOutgoingEvent(listOf(jsonObject))))
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
        val all = db.allNoteRevsByInvitation(me)
        val allIds = all.map { it.id }.toSet()
        val stateDiff = all
            .filter { clientState[it.id]?.rev != it.rev }
            .mapNotNull { db.document(Note::class, it.id) }
            .map { json.encodeToJsonElement(it).jsonObject }
        val gone = clientState.keys.filter { !allIds.contains(it) }
        val view = all.filter { it.access == ItemLink.Ref }.map { it.id }
        return listOf(SyncOutgoingEvent(stateDiff, gone = gone, view = view, full = true))
    }

    private suspend fun get(event: GetEvent): List<OutgoingEvent> {
        return listOf(GetOutgoingEvent(event.notes.mapNotNull {
            if (notes.canView(me, it)) db.document(Note::class, it) else null
        }))
    }

    private suspend fun sync(event: SyncEvent): List<OutgoingEvent> {
        val syncEvents = mutableListOf<OutgoingEvent>()
        val state = event.notes.mapNotNull { jsonObject ->
            val clientNote = json.decodeFromJsonElement<Note>(jsonObject)
            val note = db.document(Note::class, clientNote.id!!)
            val oldRev = clientNote.rev

            if (changesAccess(me, note, clientNote)) {
                logger.warn("Client sent a note that would change access. Client should have prevented this from happening.")
                null
            } else if (note == null) {
                if (clientNote.steward == null) {
                    clientNote.steward = me
                }
                val newNote = notes.insert(clientNote)
                syncEvents.add(SyncOutgoingEvent(listOf(newNote.syncJsonObject(Note::steward))))
                noteChanged(invitation!!, json.encodeToJsonElement(newNote).jsonObject)
                newNote.toIdAndRev(oldRev)
            } else if (oldRev == note.rev) {
                if (notes.canEdit(me, note)) {
                    val updatedNote = notes.update(note, clientNote, jsonObject)
                    noteChanged(invitation!!, json.encodeToJsonElement(
                        jsonObject.toMutableMap().also {
                            it["rev"] = json.parseToJsonElement(updatedNote.rev!!)
                        }
                    ).jsonObject)
                    updatedNote.toIdAndRev(oldRev)
                } else {
                    // todo tell the client to stop sending this note
                    logger.warn("Client sent a note they are unable to edit.")
                    syncEvents.add(SyncOutgoingEvent(listOf(note.jsonObject())))
                    null
                }
            } else {
                logger.warn("Client sent a note with a mismatched revision.")
                syncEvents.add(SyncOutgoingEvent(listOf(note.jsonObject())))
                null
            }
        }

        notes.ensureBidirectionalNoteRefs(state.map { it.id }).forEach { updatedNote ->
            noteChanged(null, updatedNote.syncJsonObject(Note::ref))
        }

        return (
            if (state.isEmpty()) emptyList() else listOf(StateOutgoingEvent(state))
        ) + syncEvents
    }

    private fun changesAccess(invitation: String, currentNote: Note?, updatedNote: Note): Boolean {
        // Find all added items
        val newItems = (updatedNote.items ?: emptyList()).let { items ->
            if (currentNote == null)
                // All items are newly added
                items
            else {
                // Find newly added notes
                items.filter { !currentNote.items!!.contains(it) }
            }
        }
        // Find all added refs
        val newRefs = (updatedNote.items ?: emptyList()).let { refs ->
            if (currentNote == null)
                // All refs are newly added
                refs
            else {
                // Find newly added refs
                refs.filter { !(currentNote.ref ?: emptyList()).contains(it) }
            }
        }

        return newItems.any {
            // If note contains no invitations then it doesn't exist yet, so assume access doesn't change
            db.invitationIdsForNote(it, false).let { it.isNotEmpty() && !it.contains(invitation) }
        } || newRefs.any {
            // If note contains no invitations then it doesn't exist yet, so assume access doesn't change
            db.invitationIdsForNote(it, true).let { it.isNotEmpty() && !it.contains(invitation) }
        }
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
                    GetEvent.ACTION -> get(json.decodeFromJsonElement(element))
                    else -> {
                        logger.warn("$text does not contain a known action")
                        emptyList()
                    }
                }
            }
    }
}

class Ws {

    private val mutex = Mutex()
    private val sessions = mutableSetOf<WsSession>()

    suspend fun connect(session: DefaultWebSocketServerSession) {
        mutex.withLock {
            sessions.add(WsSession(session, this::noteChanged))
        }
    }

    suspend fun disconnect(session: DefaultWebSocketServerSession) {
        mutex.withLock {
            sessions.removeIf { it.session == session }
        }
    }

    fun getSession(deviceToken: String) = sessions.find { it.deviceToken == deviceToken }

    suspend fun frame(serverSession: DefaultWebSocketServerSession, text: String) {
        sessions.find { it.session == serverSession }?.let { session ->
            val events = session.receive(text)

            if (events.isNotEmpty()) {
                session.send(events)
            }
        }
    }

    fun noteChanged(invitation: Invitation?, jsonObject: JsonObject) {
        val invitations = db.invitationIdsForNote(jsonObject["id"]!!.jsonPrimitive.content, true)
        sessions.forEach {
            if (it.invitation == null || it.invitation!!.id == invitation?.id || !invitations.contains(it.invitation!!.id)
            ) return@forEach

            scope.launch { it.sendNote(jsonObject) }
        }
    }

    fun invitationsChanged(changedBy: Invitation?) {
        val event = listOf(InvitationOutgoingEvent())
        sessions.forEach {
            if (it.invitation == null || changedBy == it.invitation) return@forEach
            scope.launch { it.send(event) }
        }
    }
}

fun Note.jsonObject() = json.encodeToJsonElement(this).jsonObject

fun Note.syncJsonObject(vararg fields: KMutableProperty1<Note, *>) = buildJsonObject {
    put(f(Note::id), id)
    put(f(Note::rev), rev)
    fields.forEach {
        when (it) {
            Note::invitations -> json.encodeToJsonElement(invitations)
            Note::steward -> json.encodeToJsonElement(steward)
            Note::name -> json.encodeToJsonElement(name)
            Note::description -> json.encodeToJsonElement(description)
            Note::checked -> json.encodeToJsonElement(checked)
            Note::color -> json.encodeToJsonElement(color)
            Note::items -> json.encodeToJsonElement(items)
            Note::ref -> json.encodeToJsonElement(ref)
            Note::options -> json.encodeToJsonElement(options)
            Note::backgroundUrl -> json.encodeToJsonElement(backgroundUrl)
            Note::collapsed -> json.encodeToJsonElement(collapsed)
            Note::estimate -> json.encodeToJsonElement(estimate)
            else -> null
        }?.let { value ->
            // todo instead of it.name it should be the json name of the field
            put(it.name, value)
        }
    }
}

package com.inceptionnotes.app

import com.inceptionnotes.db
import com.inceptionnotes.db.*
import com.inceptionnotes.updateAllFrom
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject

/**
 * Common note operations.
 */
class Notes {
    fun insert(device: String, note: Note): Note {
        note.rev = null // ensure this gets set by server
        note.revSrc = device
        note.created = null
        note.updated = null
        if (note.name === null) {
            note.name = ""
        }
        if (note.items == null) {
            note.items = emptyList()
        }
        if (note.ref == null) {
            note.ref = emptyList()
        }
        if (note.invitations == null) {
            note.invitations = emptyList()
        }
        val newNote = db.insert(note)
        updateNoteGraph(newNote)
        return newNote
    }

    fun update(device: String, note: Note, referenceNote: Note, jsonObject: JsonObject): Note {
        note.updateFrom(referenceNote)
        note.revSrc = device

        //Removable fields
        if (jsonObject["description"] is JsonNull) note.description = null
        if (jsonObject["date"] is JsonNull) note.date = null

        val updatedNote = db.update(note)
        if (referenceNote.items != null || referenceNote.ref != null) {
            updateNoteGraph(updatedNote)
        }
        return updatedNote
    }

    fun ensureBidirectionalNoteRefs(notes: List<String>): List<Note> {
        return db.ensureBidirectionalNoteRefs(notes) + db.updateNoteRefs(notes)
    }

    private fun updateNoteGraph(note: Note) {
        db.removeObsoleteNoteItems(note.id!!, note.items!!, note.ref!!)
        db.ensureNoteItems(note.id!!, note.items!!, note.ref!!)
    }

    fun canEdit(invitation: String, note: Note) = if (note.invitations?.contains(invitation) == true) {
        true
    } else canEdit(invitation, note.id!!)

    fun canEdit(invitation: String, note: String) = db.invitationsForNote(note).any { it.id == invitation }

    fun canView(invitation: String, note: String) = db.invitationsForNote(note, true).any { it.id == invitation }
}

private fun Note.updateFrom(referenceNote: Note) {
    updateAllFrom(
        referenceNote,
        Note::invitations,
        Note::name,
        Note::date,
        Note::description,
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

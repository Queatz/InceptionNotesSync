package com.inceptionnotes.app

import com.inceptionnotes.db
import com.inceptionnotes.db.*
import com.inceptionnotes.updateAllFrom
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject

class Notes {
    fun insert(note: Note): Note {
        note.rev = null // ensure this gets set by server
        note.created = null
        note.updated = null
        if (note.name == null) {
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

    fun update(note: Note, referenceNote: Note, jsonObject: JsonObject): Note {
        note.updateFrom(referenceNote)
        if (jsonObject["description"] is JsonNull) note.description = null
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

    fun canEdit(invitation: String, note: Note): Boolean {
        return if (note.steward == invitation || note.invitations?.contains(invitation) == true) {
            true
        } else db.invitationsForNote(note.id!!).any { it.id == invitation }
    }

    fun canEdit(invitation: String, note: String): Boolean {
        return if (db.invitationsForNote(note).any { it.id == invitation })
            true
        else steward(note) == invitation
    }

    fun canView(invitation: String, note: String): Boolean {
        return if (db.invitationsForNote(note, true).any { it.id == invitation })
            true
        else steward(note) == invitation
    }

    fun steward(note: String) = db.document(Note::class, note)?.steward
}

private fun Note.updateFrom(referenceNote: Note) {
    updateAllFrom(
        referenceNote,
        Note::invitations,
        Note::name,
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

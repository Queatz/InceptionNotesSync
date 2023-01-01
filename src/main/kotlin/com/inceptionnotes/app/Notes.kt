package com.inceptionnotes.app

import com.inceptionnotes.db
import com.inceptionnotes.db.Note
import com.inceptionnotes.db.ensureNoteItems
import com.inceptionnotes.db.removeObsoleteNoteItems
import com.inceptionnotes.updateAllFrom
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonNull

class Notes {
    fun insert(note: Note): Note {
        note.rev = null // ensure this gets set by server
        note.created = null
        note.updated = null
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
        if (referenceNote.items != null) {
            updateNoteGraph(updatedNote)
        }
        return updatedNote
    }

    private fun updateNoteGraph(note: Note) {
        db.removeObsoleteNoteItems(note.id!!, note.items!!)
        db.ensureNoteItems(note.id!!, note.items!!)
    }
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

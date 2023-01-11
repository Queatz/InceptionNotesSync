package com.inceptionnotes.routes

import com.inceptionnotes.*
import com.inceptionnotes.db.invitationsForNote
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.routing.*

fun Route.noteRoutes() {
    authenticate {
        /**
         * Returns all invitations of the note
         */
        get("/note/{id}/invitations") {
            respond {
                val note = parameter("id")
                val me = me()
                if (me == null || !notes.canEdit(me.id!!, note)) {
                    HttpStatusCode.NotFound
                } else {
                    db.invitationsForNote(note)
                }
            }
        }
    }
}

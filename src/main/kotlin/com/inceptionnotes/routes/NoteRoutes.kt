package com.inceptionnotes.routes

import com.inceptionnotes.db
import com.inceptionnotes.db.invitationsForNote
import com.inceptionnotes.parameter
import com.inceptionnotes.respond
import io.ktor.server.auth.*
import io.ktor.server.routing.*

fun Route.noteRoutes() {
    authenticate {
        /**
         * Returns all invitations of the note
         */
        get("/note/{id}/invitations") {
            respond {
                // todo check that they can see this note
                db.invitationsForNote(parameter("id"))
            }
        }
    }
}

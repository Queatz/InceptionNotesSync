package com.inceptionnotes.routes

import com.inceptionnotes.*
import com.inceptionnotes.db.Invitation
import com.inceptionnotes.db.invitations
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.routing.*

fun Route.invitationRoutes() {
    authenticate {
        get("/invitations") {
            respond { db.invitations() }
        }
        post("/invitations") {
            steward { db.insert(Invitation(token = (0..36).token())) }
        }
        get("/invitations/{id}") {
            respond { db.document(Invitation::class, parameter("id")) ?: HttpStatusCode.NotFound }
        }
        post("/invitations/{id}") {
            steward {
                val invitation = db.document(Invitation::class, parameter("id")) ?: return@steward HttpStatusCode.NotFound
                val update = call.receive<Invitation>()

                if (update.isSteward != null) {
                    invitation.isSteward = update.isSteward
                }

                if (update.name != null) {
                    invitation.name = update.name
                }

                db.update(invitation)
            }
        }
    }
}

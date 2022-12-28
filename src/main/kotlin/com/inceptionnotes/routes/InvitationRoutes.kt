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
            respond {
                db.invitations().let {
                    if (me()?.isSteward != true) {
                        it.onEach { it.token = null }
                    } else it
                }
            }
        }
        post("/invitations") {
            steward { db.insert(Invitation(token = (0..36).token(), name = genHumanName())) }
        }
        get("/invitations/{id}") {
            respond { db.document(Invitation::class, parameter("id")) ?: HttpStatusCode.NotFound }
        }
        post("/invitations/{id}") {
            steward {
                val invitation = db.document(Invitation::class, parameter("id")) ?: return@steward HttpStatusCode.NotFound
                val update = call.receive<Invitation>()

                if (update.isSteward != null) {
                    if (update.isSteward == true || invitation.id == me()?.id) {
                        invitation.isSteward = update.isSteward
                    }
                }

                if (update.name != null) {
                    invitation.name = update.name
                }

                db.update(invitation)
            }
        }
        post("/invitations/{id}/delete") {
            steward {
                val invitation = db.document(Invitation::class, parameter("id")) ?: return@steward HttpStatusCode.NotFound

                if (invitation.isSteward == true) {
                    HttpStatusCode.BadRequest.description("Stewards cannot be removed")
                } else {
                    db.delete(invitation)
                    HttpStatusCode.OK
                }
            }
        }
    }
}

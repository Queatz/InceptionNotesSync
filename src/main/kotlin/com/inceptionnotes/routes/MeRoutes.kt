package com.inceptionnotes.routes

import com.inceptionnotes.InvitationPrincipal
import com.inceptionnotes.db
import com.inceptionnotes.db.Invitation
import com.inceptionnotes.db.deviceFromToken
import com.inceptionnotes.db.invitationFromToken
import com.inceptionnotes.me
import com.inceptionnotes.respond
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.routing.*

data class MeInvitationEvent(
    val token: String
)

fun Route.meRoutes() {
    authenticate {
        get("/me") {
            respond { me() ?: HttpStatusCode.NotFound }
        }
        post("/me") {
            respond {
                val invitation = me() ?: return@respond HttpStatusCode.NotFound
                val event = call.receive<Invitation>()

                if (event.name != null) {
                    invitation.name = event.name!!.take(64)
                    return@respond db.update(invitation)
                }
                return@respond HttpStatusCode.BadRequest
            }
        }
        post("/me/invitation") {
            respond {
                if (me() != null) {
                    // Already connected, pls disconnect first
                    HttpStatusCode.BadRequest
                } else {
                    val event = call.receive<MeInvitationEvent>()
                    val device = db.deviceFromToken(call.principal<InvitationPrincipal>()!!.deviceToken)
                    val invitation = db.invitationFromToken(event.token)

                    if (invitation == null) {
                        HttpStatusCode.NotFound
                    } else {
                        device.invitation = invitation.id
                        db.update(device)
                        HttpStatusCode.OK
                    }
                }
            }
        }
    }
}

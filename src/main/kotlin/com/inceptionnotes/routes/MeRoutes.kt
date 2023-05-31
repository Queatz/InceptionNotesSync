package com.inceptionnotes.routes

import com.inceptionnotes.*
import com.inceptionnotes.db.Invitation
import com.inceptionnotes.db.deviceFromToken
import com.inceptionnotes.db.invitationFromToken
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

@Serializable
data class MeInvitationEvent(
    val token: String,
)

fun Route.meRoutes() {
    authenticate {
        /**
         * Returns your invitation
         */
        get("/me") {
            respond { me() ?: HttpStatusCode.NotFound }
        }

        /**
         * Update the name of your invitation
         */
        post("/me") {
            respond {
                val invitation = me() ?: return@respond HttpStatusCode.NotFound
                val event = call.receive<Invitation>()

                if (event.name != null) {
                    invitation.name = event.name!!.take(64)
                    return@respond db.update(invitation).also {
                        ws.invitationsChanged(me())
                    }
                }

                return@respond HttpStatusCode.BadRequest
            }
        }

        /**
         * Connect your device to an invitation
         */
        post("/me/invitation") {
            respond {
                if (me() != null) {
                    // Already connected, pls disconnect first
                    HttpStatusCode.BadRequest.description("Already connected to an invitation")
                } else {
                    val event = call.receive<MeInvitationEvent>()
                    val device = db.deviceFromToken(call.principal<InvitationPrincipal>()!!.deviceToken)
                    val invitation = db.invitationFromToken(event.token)

                    if (invitation == null) {
                        HttpStatusCode.NotFound
                    } else {
                        device.invitation = invitation.id
                        db.update(device)
                        invitation
                    }
                }
            }
        }
    }
}

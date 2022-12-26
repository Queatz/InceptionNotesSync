package com.inceptionnotes

import com.inceptionnotes.db.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.compression.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.defaultheaders.*
import io.ktor.server.websocket.*
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

fun main() {
    embeddedServer(Netty, port = 8080, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

class InvitationPrincipal(
    val deviceToken: String,
    val invitation: Invitation?
) : Principal

fun Application.module() {
    install(Compression) {
        gzip {
            priority = 1.0
        }
        deflate {
            priority = 10.0
            minimumSize(1024)
        }
    }
    install(CORS) {
        allowNonSimpleContentTypes = true
        allowMethod(HttpMethod.Options)
        allowHeader(HttpHeaders.Authorization)
        allowHost("inceptionnotes.com")
        allowHost("localhost:4200")
        // todo also allow Android app
    }
    install(DefaultHeaders)
    install(ContentNegotiation) {
        json()
    }
    install(Authentication) {
        bearer {
            authenticate { bearer ->
                InvitationPrincipal(bearer.token, db.invitationFromDeviceToken(bearer.token) ?: initialize(bearer.token))
            }
        }
    }
    install(WebSockets) {
        pingPeriod = 15.seconds.toJavaDuration()
        timeout = 30.seconds.toJavaDuration()
    }
    routes()
}

fun initialize(token: String): Invitation? = if (db.countInvitations == 0) {
    val invitation = db.insert(Invitation(isSteward = true))
    db.insert(Device(token = token, invitation = invitation.id!!))
    invitation
} else null

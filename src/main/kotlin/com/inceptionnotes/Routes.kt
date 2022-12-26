package com.inceptionnotes

import com.inceptionnotes.db.Invitation
import com.inceptionnotes.routes.meRoutes
import com.inceptionnotes.routes.invitationRoutes
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.http.content.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.util.pipeline.*
import io.ktor.websocket.*

fun Application.routes() {
    routing {
        get("/hi") {
            call.respond("hi" to true)
        }
        webSocket("/ws") {
            ws.connect(this)
            try {
                for (frame in incoming) {
                    if (frame is Frame.Text) {
                        ws.frame(this, frame.readText())
                    }
                }
            } finally {
                ws.disconnect(this)
            }
        }
        static("/static") {
            resources("static")
        }
        meRoutes()
        invitationRoutes()
    }
}

fun PipelineContext<*, ApplicationCall>.me(): Invitation? {
    return call.principal<InvitationPrincipal>()?.invitation
}
suspend inline fun <reified T : Any> PipelineContext<*, ApplicationCall>.steward(returnIfSteward: () -> T) {
    if (me()?.isSteward == true) respond { returnIfSteward() } else respond { HttpStatusCode.NotFound }
}
suspend inline fun <reified T : Any> PipelineContext<*, ApplicationCall>.respond(block: () -> T) {
    call.respond(block())
}
fun PipelineContext<*, ApplicationCall>.parameter(parameter: String): String = call.parameters[parameter]!!

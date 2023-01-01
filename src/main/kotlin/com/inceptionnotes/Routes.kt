package com.inceptionnotes

import com.inceptionnotes.db.Invitation
import com.inceptionnotes.routes.meRoutes
import com.inceptionnotes.routes.invitationRoutes
import com.inceptionnotes.routes.syncRoutes
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
            call.respond(mapOf("hi" to true))
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
        syncRoutes()
    }
}

fun PipelineContext<*, ApplicationCall>.me(): Invitation? {
    return call.principal<InvitationPrincipal>()?.invitation
}
val PipelineContext<*, ApplicationCall>.deviceToken: String get() =
    call.principal<InvitationPrincipal>()!!.deviceToken

suspend inline fun <reified T : Any> PipelineContext<*, ApplicationCall>.steward(returnIfSteward: () -> T) {
    if (me()?.isSteward == true) respond { returnIfSteward() } else respond { HttpStatusCode.NotFound }
}
suspend inline fun <reified T : Any> PipelineContext<*, ApplicationCall>.respond(block: () -> T) {
    call.respond(block())
}
suspend inline fun <reified T : Any> PipelineContext<*, ApplicationCall>.respondJson(block: () -> T) {
    val result = block()
    when (result) {
        is String -> call.respondText(result, ContentType.Application.Json.withCharset(Charsets.UTF_8))
        else -> call.respond(result)
    }
}

fun PipelineContext<*, ApplicationCall>.parameter(parameter: String): String = call.parameters[parameter]!!

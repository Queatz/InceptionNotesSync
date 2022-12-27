package com.inceptionnotes.routes

import com.inceptionnotes.*
import com.inceptionnotes.ws.toJsonArrayEvent
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import kotlinx.serialization.encodeToString

fun Route.syncRoutes() {
    authenticate {
        post("http") {
            respondJson {
                json.encodeToString(
                    ws.getSession(me() ?: return@respondJson HttpStatusCode.NotFound)?.receive(call.receiveText())
                        ?.map { it.toJsonArrayEvent() }
                        ?: return@respondJson HttpStatusCode.BadRequest.description("Websocket connection must also be open")
                )
            }
        }
    }
}

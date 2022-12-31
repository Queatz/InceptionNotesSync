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
        /**
         * Send websocket events over http.  Useful for large amounts of data.
         *
         * Returns websocket events for client consumption.
         */
        post("http") {
            respondJson {
                if (me() == null) {
                    HttpStatusCode.NotFound
                } else {
                    json.encodeToString(
                        ws.getSession(deviceToken)?.receive(call.receiveText())
                            ?.map { it.toJsonArrayEvent() }
                            ?: return@respondJson HttpStatusCode.BadRequest.description("Websocket connection must also be open")
                    )
                }
            }
        }
    }
}

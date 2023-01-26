package com.inceptionnotes

import com.inceptionnotes.app.Notes
import com.inceptionnotes.db.Db
import com.inceptionnotes.ws.Ws
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.CoroutineScope

lateinit var scope: CoroutineScope

val json = DefaultJson
val db = Db("inception", "inception", "inception")
val ws = Ws()
val notes = Notes()

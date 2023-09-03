package com.inceptionnotes

import com.inceptionnotes.app.Notes
import com.inceptionnotes.db.Db
import com.inceptionnotes.ws.Ws
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.json.Json

lateinit var scope: CoroutineScope

val json = Json {
    encodeDefaults = true
    isLenient = true
    allowSpecialFloatingPointValues = true
    ignoreUnknownKeys = true
    explicitNulls = false
}
val db = Db("inception", "inception", "inception")
val ws = Ws()
val notes = Notes()

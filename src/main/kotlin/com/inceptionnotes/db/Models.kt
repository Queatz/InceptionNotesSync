@file:OptIn(ExperimentalSerializationApi::class)

package com.inceptionnotes.db

import com.arangodb.entity.From
import com.arangodb.entity.Key
import com.arangodb.entity.Rev
import com.arangodb.entity.To
import com.arangodb.internal.DocumentFields
import kotlinx.datetime.Instant
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.JsonNames

data class Device(
    var token: String? = null,
    var invitation: String? = null
) : Model()

data class Invitation(
    var token: String? = null,
    var name: String? = null,
    var isSteward: Boolean? = null
) : Model()

data class Note(
    var steward: String? = null,
    var invitations: List<String>? = null,
    var name: String? = null,
    var description: String? = null,
    var checked: Boolean? = null,
    var color: String? = null,
    var items: List<String>? = null,
    var ref: List<String>? = null,
    var options: NoteOptions? = null,
    var backgroundUrl: String? = null,
    var collapsed: Boolean? = null,
    var estimate: Double? = null
) : Model()

class Item : Edge()

data class NoteOptions(
    var enumerate: Boolean? = null,
    var invertText: Boolean? = null
)

open class Model(
    @Key
    @SerialName("id")
    @JsonNames(DocumentFields.KEY)
    var id: String? = null,
    @Rev
    @SerialName("rev")
    @JsonNames(DocumentFields.REV)
    var rev: String? = null,
    var created: Instant? = null,
    var updated: Instant? = null
)

open class Edge(
    @From
    @SerialName("from")
    @JsonNames(DocumentFields.FROM)
    var from: String? = null,
    @To
    @SerialName("to")
    @JsonNames(DocumentFields.TO)
    var to: String? = null
) : Model()

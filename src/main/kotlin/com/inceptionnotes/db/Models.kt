@file:OptIn(ExperimentalSerializationApi::class)

package com.inceptionnotes.db

import com.arangodb.entity.From
import com.arangodb.entity.Key
import com.arangodb.entity.Rev
import com.arangodb.entity.To
import com.arangodb.internal.DocumentFields
import com.arangodb.velocypack.annotations.SerializedName
import com.fasterxml.jackson.annotation.JsonProperty
import kotlinx.datetime.Instant
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames

@Serializable
data class Device(
    var token: String? = null,
    var invitation: String? = null
) : Model()

@Serializable
data class Invitation(
    var token: String? = null,
    var name: String? = null,
    var isSteward: Boolean? = null
) : Model()

@Serializable
data class Note(
    var invitations: List<String>? = null,
    var name: String? = null,
    var date: Instant? = null,
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

@Serializable
enum class ItemLink {
    @SerialName("item") @JsonProperty("item") @SerializedName("item") Item,
    @SerialName("ref") @JsonProperty("ref") @SerializedName("ref") Ref
}

@Serializable
class Item(
    var link: ItemLink? = null
) : Edge()

@Serializable
data class NoteOptions(
    var enumerate: Boolean? = null,
    var invertText: Boolean? = null
)

@Serializable
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

@Serializable
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

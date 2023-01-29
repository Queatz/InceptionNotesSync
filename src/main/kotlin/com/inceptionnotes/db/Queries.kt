package com.inceptionnotes.db

import com.inceptionnotes.json
import com.inceptionnotes.ws.IdAndRev
import kotlinx.serialization.decodeFromString

/**
 * Returns the total number of invitations.
 */
val Db.countInvitations get() = query(Int::class, """
    return count(`${Invitation::class.collection}`)
""".trimIndent()).first()!!

/**
 * Returns all invitations.
 */
fun Db.invitations() = list(Invitation::class, "for invitation in @@collection return invitation")

/**
 * Returns the invitation with the given token.
 */
fun Db.invitationFromToken(token: String) = one(
    Invitation::class, """
    for invitation in @@collection
        filter invitation.${f(Invitation::token)} == @token
            return invitation
""".trimIndent(),
    mapOf("token" to token)
)

/**
 * Returns the device associated with a device token. Non-null.
 */
fun Db.deviceFromToken(token: String) = one(Device::class, """
    upsert { ${f(Device::token)}: @token }
        insert { ${f(Device::token)}: @token, ${f(Device::created)}: DATE_ISO8601(DATE_NOW()) }
        update { ${f(Device::updated)}: DATE_ISO8601(DATE_NOW()) }
        in @@collection
        return NEW || OLD
""".trimIndent(),
    mapOf("token" to token)
)!!

/**
 * Returns the invitation that has been connected to a device token, or null.
 */
fun Db.invitationFromDeviceToken(token: String) = one(
    Invitation::class, """
    for device in ${Device::class.collection}
        for invitation in @@collection
            filter device.${f(Device::token)} == @token and device.${f(Device::invitation)} == invitation._key
                return invitation
""".trimIndent(),
    mapOf("token" to token)
)

/**
 * Returns the ids of all invitations to this note, including the invitation that created the note, and all invitations to parent notes, up to 99 deep.
 */
fun Db.invitationIdsForNote(note: String, includeRefs: Boolean = false) = query(
    String::class, """
        for invitation in flatten(
            for v, e in 0..99 inbound @note graph `${Item::class.graph}`
                prune found = ${if (includeRefs) "e._to != @note and" else ""} e.${f(Item::link)} == ${v(ItemLink.Ref)} // stop at refs
                options { order: 'weighted', uniqueVertices: 'path' }
                ${if (includeRefs) "" else "filter not found"}
                return append(v.${f(Note::invitations)}, [v.${f(Note::steward)}])
        )
            return distinct invitation
    """.trimIndent(),
    mapOf("note" to note.asId(Note::class))
)

/**
 * Returns all invitations to this note, including the invitation that created the note, and all invitations to parent notes, up to 99 deep.
 */
fun Db.invitationsForNote(note: String, includeRefs: Boolean = false) = list(
    Invitation::class, """
        for invitation in flatten(
            for v, e in 0..99 inbound @note graph `${Item::class.graph}`
                prune found = ${if (includeRefs) "e._to != @note and" else ""} e.${f(Item::link)} == ${v(ItemLink.Ref)} // stop at refs
                options { order: 'weighted', uniqueVertices: 'path' }
                ${if (includeRefs) "" else "filter not found"}
                return append(
                    (
                        for x in v.${f(Note::invitations)}
                            return document(`${Invitation::class.collection}`, x)
                    ),
                    [document(`${Invitation::class.collection}`, v.${f(Note::steward)})]
                )
        )
            return distinct invitation
    """.trimIndent(),
    mapOf("note" to note.asId(Note::class))
)

/**
 * Returns the id and revision of all notes created with the given invitation, or shared with the invitation, including all child notes, up to 99 deep.
 *
 * Includes refs
 */
fun Db.allNoteRevsByInvitation(invitation: String): List<IdAndRevAndAccess> = query(
    List::class, """
        // find all notes with an invitation
        let ids = (
            for note in ${Note::class.collection}
                filter note.${f(Note::steward)} == @invitation
                        or @invitation in note.${f(Note::invitations)}
                    return note._id
        )
        // find all notes under any of those notes
        for note in ${Note::class.collection}
            let access = (note._id in ids) ? ${v(ItemLink.Item)} : first(
                for v, e, p in 1..99 inbound note graph `${Item::class.graph}`
                    prune stop = v._id in ids or (e._to != note._id and e.${f(Item::link)} == ${v(ItemLink.Ref)}) // stop at refs except initial link (note might be a ref)
                    options { order: 'weighted', uniqueVertices: 'path' }
                    filter stop and v._id in ids
                    sort p.edges[0].${f(Item::link)} == ${v(ItemLink.Ref)}
                    limit 1
                    return p.edges[0].${f(Item::link)}
            )
            filter access != null
            return [note._key, note._rev, access]
    """.trimIndent(),
    mapOf("invitation" to invitation)
) as List<IdAndRevAndAccess>

typealias IdAndRevAndAccess = List<String>
val IdAndRev.id get() = get(0)!!
val IdAndRev.rev get() = get(1)!!
val IdAndRev.access get() = json.decodeFromString<ItemLink>(get(2)!!)

/**
 * Inserts items matching the given list into the graph.
 */
fun Db.removeObsoleteNoteItems(note: String, items: List<String>, ref: List<String>) = list(
    Item::class,
    """
    for item in @@collection
        filter item._from == @note
            and (
                (item.${f(Item::link)} == ${v(ItemLink.Item)} and item._to not in @items)
                or (item.${f(Item::link)} == ${v(ItemLink.Ref)} and item._to not in @ref)
            )
            remove item in @@collection
    """.trimIndent(),
    mapOf(
        "note" to note.asId(Note::class),
        "items" to items.map { it.asId(Note::class) },
        "ref" to ref.map { it.asId(Note::class) }
    )
)

/**
 * Removes items not matching the given list from the graph.
 */
fun Db.ensureNoteItems(note: String, items: List<String>, ref: List<String>) = list(
    Item::class,
    """
    for link in [[${v(ItemLink.Item)}, @items], [${v(ItemLink.Ref)}, @ref]]
        for item in link[1]
            upsert { _from: @note, _to: item, ${f(Item::link)}: link[0] }
                insert { _from: @note, _to: item, ${f(Item::link)}: link[0], ${f(Item::created)}: DATE_ISO8601(DATE_NOW()) }
                update { ${f(Item::updated)}: DATE_ISO8601(DATE_NOW()) }
                in @@collection
                return NEW
    """.trimIndent(),
    mapOf(
        "note" to note.asId(Note::class),
        "items" to items.map { it.asId(Note::class) },
        "ref" to ref.map { it.asId(Note::class) }
    )
)

fun Db.removeDevicesByInvitation(invitation: String) = query(Unit::class, """
    for device in ${Device::class.collection}
        filter device.${f(Device::invitation)} == @invitation
        remove device in ${Device::class.collection}
""".trimIndent(), mapOf("invitation" to invitation)
)

fun Db.removeInvitationFromAllNotes(invitation: String) = list(Note::class, """
    for note in @@collection
        filter @invitation in note.${f(Note::invitations)}
        update { _key: note._key, ${f(Note::invitations)}: remove_value(note.${f(Note::invitations)}, @invitation) } in @@collection
        return NEW
""".trimIndent(), mapOf("invitation" to invitation)
)

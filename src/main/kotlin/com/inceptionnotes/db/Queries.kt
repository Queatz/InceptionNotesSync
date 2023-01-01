package com.inceptionnotes.db

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
fun Db.invitationIdsForNote(note: String) = query(
    String::class, """
        for invitation in flatten(
            for v in 0..99 inbound @note graph `${Item::class.graph}`
                options { order: 'weighted', uniqueVertices: 'global' }
                return append(v.${f(Note::invitations)}, [v.${f(Note::steward)}])
        )
            return distinct invitation
    """.trimIndent(),
    mapOf("note" to note.asId(Note::class))
)

/**
 * Returns all invitations to this note, including the invitation that created the note, and all invitations to parent notes, up to 99 deep.
 */
fun Db.invitationsForNote(note: String) = list(
    Invitation::class, """
        for invitation in flatten(
            for v in 0..99 inbound @note graph `${Item::class.graph}`
                options { order: 'weighted', uniqueVertices: 'global' }
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
 */
fun Db.allNoteRevsByInvitation(invitation: String) = list(
    Note::class, """
        let ids = (
            for note in @@collection
                filter note.${f(Note::steward)} == @invitation
                        or @invitation in note.${f(Note::invitations)}
                    return note._id
        )
        for note in @@collection
            filter first(
                for v in 0..99 inbound note graph `${Item::class.graph}`
                    prune found = v._id in ids
                    options { order: 'weighted', uniqueVertices: 'global' }
                    filter found
                    return v
            ) != null
            return keep(note, '_rev', '_key')
    """.trimIndent(),
    mapOf("invitation" to invitation)
)

/**
 * Inserts items matching the given list into the graph.
 */
fun Db.removeObsoleteNoteItems(note: String, items: List<String>) = list(
    Item::class,
    """
    for item in @@collection
        filter item._from == @note and item._to not in @items
            remove item in @@collection
    """.trimIndent(),
    mapOf(
        "note" to note.asId(Note::class),
        "items" to items.map { it.asId(Note::class) }
    )
)

/**
 * Removes items not matching the given list from the graph.
 */
fun Db.ensureNoteItems(note: String, items: List<String>) = list(
    Item::class,
    """
    for item in @items
        upsert { _from: @note, _to: item }
        insert { _from: @note, _to: item, ${f(Item::created)}: DATE_ISO8601(DATE_NOW()) }
        update { ${f(Item::updated)}: DATE_ISO8601(DATE_NOW()) }
        in @@collection
        return NEW
    """.trimIndent(),
    mapOf(
        "note" to note.asId(Note::class),
        "items" to items.map { it.asId(Note::class) }
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

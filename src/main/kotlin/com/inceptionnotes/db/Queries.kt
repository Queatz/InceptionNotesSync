package com.inceptionnotes.db

val Db.countInvitations get() = query(Int::class, """
    return count(`${Invitation::class.collection}`)
""".trimIndent()).first()!!

fun Db.invitations() = list(
    Invitation::class, """
    for invitation in @@collection return invitation
""".trimIndent()
)

fun Db.invitationFromToken(token: String) = one(
    Invitation::class, """
    for invitation in @@collection
        filter invitation.${f(Invitation::token)} == @token
            return invitation
""".trimIndent(),
    mapOf("token" to token)
)

fun Db.deviceFromToken(token: String) = one(Device::class, """
    upsert { ${f(Device::token)}: @token }
        insert { ${f(Device::token)}: @token, ${f(Device::created)}: DATE_ISO8601(DATE_NOW()) }
        update { }
        in @@collection
        return NEW || OLD
""".trimIndent(),
    mapOf("token" to token)
)!!

fun Db.invitationFromDeviceToken(token: String) = one(
    Invitation::class, """
    for device in ${Device::class.collection}
        for invitation in @@collection
            filter device.${f(Device::token)} == @token and device.${f(Device::invitation)} == invitation._key
                return invitation
""".trimIndent(),
    mapOf("token" to token)
)

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

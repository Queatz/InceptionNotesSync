package com.inceptionnotes.db

import com.arangodb.entity.CollectionType

fun collections() = listOf(
    collection<Note> {
        index(Note::items)
        index(Note::ref)
        index(Note::invitations)
    },
    collection<Device> {
        index(Device::token)
    },
    collection<Invitation> {
        index(Invitation::token)
    },
    collection<Item>(collectionType = CollectionType.EDGES, nodes = listOf(Note::class)) { }
)

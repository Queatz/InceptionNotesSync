package com.inceptionnotes.db

import com.arangodb.entity.CollectionType

fun collections() = listOf(
    collection<Note> {
        index(Note::steward)
    },
    collection<Device> {
        index(Device::token)
    },
    collection<Invitation> {
        index(Invitation::token)
    },
    collection<Item>(collectionType = CollectionType.EDGES, nodes = listOf(Note::class)) {  }
)

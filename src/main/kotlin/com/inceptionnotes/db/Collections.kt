package com.inceptionnotes.db

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
    collection<Item> {  }
)

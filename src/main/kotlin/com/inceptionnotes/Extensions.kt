package com.inceptionnotes

import org.slf4j.LoggerFactory
import kotlin.random.Random
import kotlin.reflect.KMutableProperty1

fun IntRange.token() =
    joinToString("") { Random.nextInt(35).toString(36).let {
        if (Random.nextBoolean()) it.uppercase() else it }
    }

val Any.logger get() = LoggerFactory.getLogger(this::class.java)!!

inline fun <reified T : Any> T.updateAllFrom(reference: T, vararg fields: KMutableProperty1<T, *>) {
    for (field in fields) {
        updateFrom(reference, field)
    }
}

fun <T : Any, F : Any?> T.updateFrom(reference: T, field: KMutableProperty1<T, F>) {
    val value = field.get(reference)
    if (value != null) field.set(this, value)
}

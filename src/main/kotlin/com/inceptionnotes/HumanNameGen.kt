package com.inceptionnotes

import kotlin.random.Random.Default.nextBoolean
import kotlin.random.Random.Default.nextInt

internal const val vowels = "aeiou"
internal const val consonantsCommon = "bcdfghjklmnprstvy"
internal const val consonantsRare = "qwxz"

fun nextVowel() = vowels.random().toString()
fun nextConsonant() = (if (nextInt(3) == 0) consonantsRare else consonantsCommon).random().toString()
fun nextLetter() = if (nextBoolean()) nextVowel() else nextConsonant()

fun genHumanName(): String {
    return buildString {
        repeat(nextInt(2, 4)) {
            if (length >= 2 && takeLast(2).all { it !in vowels }) {
                append(nextVowel())
            } else if (length >= 2 && takeLast(2).all { it in vowels }) {
                append(nextConsonant())
            } else {
                append(nextLetter())
            }
        }
    }.replaceFirstChar {
        if (it.isLowerCase()) it.titlecase() else it.toString()
    }
}

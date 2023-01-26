package com.inceptionnotes

import kotlin.random.Random

internal const val vowels = "aeiou"
internal const val consonantsCommon = "bcdfghjlmnprstv"
internal const val consonantsRare = "kqwxyz"
internal const val allConsonants = "$consonantsCommon$consonantsRare"

fun nextVowel() = vowels.random().toString()
fun nextConsonant() = (if (Random.nextInt(3) == 0) consonantsRare else consonantsCommon).random().toString()
fun nextLetter() = if (Random.nextBoolean()) nextVowel() else nextConsonant()

fun genHumanName(): String {
    return buildString {
        repeat((1..Random.nextInt(4, 8)).count()) {
            if (length >= 2 && takeLast(2).all { it in allConsonants }) {
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

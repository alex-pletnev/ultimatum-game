package edu.itmo.ultimatum_game.util

import edu.itmo.ultimatum_game.exceptions.InvalidUuidFormatException
import java.util.*


fun String?.toUuidOrThrow(fieldName: String = "id"): UUID {
    if (this.isNullOrBlank()) {
        throw InvalidUuidFormatException("Поле '$fieldName' не может быть пустым или null")
    }
    return try {
        UUID.fromString(this)
    } catch (ex: IllegalArgumentException) {
        throw InvalidUuidFormatException("Поле '$fieldName' содержит некорректный UUID: '$this'")
    }
}

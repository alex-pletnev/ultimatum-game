package edu.itmo.ultimatumgame.util

import edu.itmo.ultimatumgame.exceptions.InvalidUuidFormatException
import java.util.UUID

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

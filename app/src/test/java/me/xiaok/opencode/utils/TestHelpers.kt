package me.xiaok.opencode.utils

import kotlinx.serialization.json.Json

object TestHelpers {
    val testJson = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
        classDiscriminator = "type"
    }
}

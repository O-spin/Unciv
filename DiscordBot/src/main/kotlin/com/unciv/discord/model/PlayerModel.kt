package com.unciv.discord.model

import kotlinx.serialization.*

@Serializable
data class PlayerEntry(
    val userDiscordId: Long,
    var uncivId: String? = null,
    var elo: Int = 1000,
    var token: Int = 0,
    var discordName: String? = null
)

enum class RegisterResult {
    SUCCESS,
    ALREADY_REGISTERED,
    UNCIV_ID_TAKEN
}

enum class AddIdResult {
    SUCCESS,
    NOT_REGISTERED,
    ALREADY_HAS_ID,
    UNCIV_ID_TAKEN
}

@Serializable
data class BotData(
    val players: MutableList<PlayerEntry> = mutableListOf(),
    val presets: MutableList<GamePreset> = mutableListOf(),
    val presetUsage: MutableMap<String, Int> = mutableMapOf()
)

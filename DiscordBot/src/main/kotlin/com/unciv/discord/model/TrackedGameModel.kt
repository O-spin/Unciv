package com.unciv.discord.model

import kotlinx.serialization.*

@Serializable
data class TrackedGame(
    val gameId: String,
    val creatorDiscordId: Long,
    val players: MutableList<String> = mutableListOf(),
    var status: String = "ACTIVE",
    val skipTurnHours: Int = 12,
    val averageTurnTimeForAutoresignHours: Int = 4
)

@Serializable
data class TrackedGamesData(
    val games: MutableList<TrackedGame> = mutableListOf()
)

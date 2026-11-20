package com.unciv.discord.model

data class DrawProposal(
    val gameId: String,
    val aliveDiscordIds: Set<Long>,
    val acceptedDiscordIds: MutableSet<Long>
)

data class GameProposal(
    val proposalId: String,
    val proposerId: Long,
    val playersInput: String,
    val mapSize: String?,
    val mapType: String?,
    val difficulty: String?,
    val cityStates: String?,
    val strategicBalance: Boolean?,
    val legendaryStart: Boolean?,
    val worldWrap: Boolean?,
    val skipTurnHours: Int?,
    val averageTurnTimeHours: Int?,
    val invitedDiscordIds: Set<Long>,
    val acceptedDiscordIds: MutableSet<Long>
)

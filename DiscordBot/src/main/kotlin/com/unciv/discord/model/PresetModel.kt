package com.unciv.discord.model

import kotlinx.serialization.*

@Serializable
data class GamePreset(
    val name: String,
    val mapSize: String? = null,
    val mapType: String? = null,
    val difficulty: String? = null,
    val cityStates: String? = null,
    val strategicBalance: Boolean? = null,
    val legendaryStart: Boolean? = null,
    val worldWrap: Boolean? = null,
    val skipTurnHours: Int? = null,
    val averageTurnTimeHours: Int? = null
)

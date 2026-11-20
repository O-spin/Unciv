package com.unciv.discord.service

import com.unciv.discord.model.AddIdResult
import com.unciv.discord.model.BotData
import com.unciv.discord.model.PlayerEntry
import com.unciv.discord.model.RegisterResult
import com.unciv.discord.model.TrackedGame
import kotlin.math.max
import kotlin.math.pow

object PlayerManager {
    private fun BotData.hasUncivId(uncivId: String): Boolean {
        return players.any { player -> player.uncivId != null && player.uncivId.equals(uncivId, ignoreCase = true) }
    }

    fun registerUser(data: BotData, userDiscordId: Long, initialUncivId: String? = null, discordName: String? = null): RegisterResult {
        if (data.players.any { it.userDiscordId == userDiscordId }) {
            return RegisterResult.ALREADY_REGISTERED
        }
        if (!initialUncivId.isNullOrBlank() && data.hasUncivId(initialUncivId)) {
            return RegisterResult.UNCIV_ID_TAKEN
        }
        data.players.add(PlayerEntry(userDiscordId, initialUncivId?.takeIf { it.isNotBlank() }, 1000, 0, discordName))
        StorageService.saveData(data)
        return RegisterResult.SUCCESS
    }

    fun unregisterUser(data: BotData, userDiscordId: Long): Boolean {
        val removed = data.players.removeIf { it.userDiscordId == userDiscordId }
        if (removed) {
            StorageService.saveData(data)
        }
        return removed
    }

    fun changeUncivId(data: BotData, userDiscordId: Long, newUncivId: String): AddIdResult {
        val player = data.players.find { it.userDiscordId == userDiscordId } ?: return AddIdResult.NOT_REGISTERED
        if (player.uncivId != null && player.uncivId.equals(newUncivId, ignoreCase = true)) {
            return AddIdResult.ALREADY_HAS_ID
        }
        if (data.hasUncivId(newUncivId)) {
            return AddIdResult.UNCIV_ID_TAKEN
        }
        player.uncivId = newUncivId
        StorageService.saveData(data)
        return AddIdResult.SUCCESS
    }

    fun getPlayer(data: BotData, userDiscordId: Long, discordName: String? = null): PlayerEntry? {
        val player = data.players.find { it.userDiscordId == userDiscordId }
        if (player != null && !discordName.isNullOrBlank() && player.discordName != discordName) {
            player.discordName = discordName
            StorageService.saveData(data)
        }
        return player
    }

    fun setElo(data: BotData, userDiscordId: Long, newElo: Int): Boolean {
        val player = getPlayer(data, userDiscordId) ?: return false
        player.elo = newElo
        StorageService.saveData(data)
        return true
    }

    fun addElo(data: BotData, userDiscordId: Long, delta: Int): Boolean {
        val player = getPlayer(data, userDiscordId) ?: return false
        player.elo += delta
        StorageService.saveData(data)
        return true
    }

    fun addTokens(data: BotData, userDiscordId: Long, amount: Int) {
        val player = getPlayer(data, userDiscordId) ?: return
        player.token = max(0, player.token + amount)
        StorageService.saveData(data)
    }

    fun removeTokens(data: BotData, userDiscordId: Long, amount: Int) {
        val player = getPlayer(data, userDiscordId) ?: return
        player.token = max(0, player.token - amount)
        StorageService.saveData(data)
    }

    fun getTokens(data: BotData, userDiscordId: Long): Int? = getPlayer(data, userDiscordId)?.token

    fun leaderboard(data: BotData): String {
        val sorted = data.players.sortedByDescending { it.elo }
        return sorted.take(10).mapIndexed { i, player -> 
            val name = player.discordName ?: "User ${player.userDiscordId}"
            "${i + 1}. **$name** — ${player.elo} ELO"
        }.joinToString("\n")
    }

    fun findPlayerEntry(data: BotData, gamePlayerId: String): PlayerEntry? {
        data.players.find { it.uncivId != null && it.uncivId.equals(gamePlayerId, ignoreCase = true) }?.let { return it }
        gamePlayerId.toLongOrNull()?.let { discordId ->
            data.players.find { it.userDiscordId == discordId }?.let { return it }
        }
        data.players.find { it.userDiscordId.toString() == gamePlayerId }?.let { return it }
        return null
    }

    fun duel(data: BotData, winnerId: Long, loserId: Long): Pair<Int, Int>? {
        return duel(data, winnerId, loserId, 1.0)
    }

    fun duel(data: BotData, player1Id: Long, player2Id: Long, score1: Double): Pair<Int, Int>? {
        val p1 = getPlayer(data, player1Id)
        val p2 = getPlayer(data, player2Id)
        if (p1 == null || p2 == null) return null

        val k = 32
        val r1 = p1.elo
        val r2 = p2.elo

        val e1 = 1.0 / (1 + 10.0.pow((r2 - r1) / 400.0))
        val e2 = 1.0 / (1 + 10.0.pow((r1 - r2) / 400.0))

        val score2 = 1.0 - score1

        val newR1 = (r1 + k * (score1 - e1)).toInt()
        val newR2 = (r2 + k * (score2 - e2)).toInt()

        val change1 = newR1 - r1
        val change2 = newR2 - r2

        p1.elo = newR1
        p2.elo = newR2
        StorageService.saveData(data)

        return Pair(change1, change2)
    }

    fun awardElo(data: BotData, game: TrackedGame, winnerIds: List<String>) {
        val gamePlayers = game.players
        if (gamePlayers.size < 2) return

        val mappedPlayers = gamePlayers.mapNotNull { gamePlayerId ->
            val playerEntry = findPlayerEntry(data, gamePlayerId)
            if (playerEntry != null) {
                Pair(gamePlayerId, playerEntry.userDiscordId)
            } else {
                null
            }
        }

        if (mappedPlayers.size < 2) return

        for (i in 0 until mappedPlayers.size) {
            for (j in i + 1 until mappedPlayers.size) {
                val (gameId1, discordId1) = mappedPlayers[i]
                val (gameId2, discordId2) = mappedPlayers[j]

                val isWinner1 = winnerIds.any { it.equals(gameId1, ignoreCase = true) }
                val isWinner2 = winnerIds.any { it.equals(gameId2, ignoreCase = true) }

                val score1 = when {
                    winnerIds.isEmpty() -> 0.5
                    isWinner1 && !isWinner2 -> 1.0
                    !isWinner1 && isWinner2 -> 0.0
                    else -> 0.5
                }

                duel(data, discordId1, discordId2, score1)
            }
        }
    }
}

package com.unciv.discord.service

import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.discord.model.TrackedGame
import com.unciv.discord.model.TrackedGamesData
import com.unciv.logic.GameStarter
import com.unciv.logic.civilization.NotificationCategory
import com.unciv.logic.civilization.PlayerType
import com.unciv.logic.map.MapSize
import com.unciv.logic.map.MapType
import com.unciv.logic.multiplayer.storage.AuthStatus
import com.unciv.logic.multiplayer.storage.MultiplayerServer
import com.unciv.logic.multiplayer.storage.UncivServerFileStorage
import com.unciv.models.metadata.BaseRuleset
import com.unciv.models.metadata.GameSetupInfo
import com.unciv.models.metadata.Player
import kotlinx.coroutines.runBlocking
import kotlin.math.max

data class GameTimerInfo(
    val gameId: String,
    val currentPlayerId: String,
    val currentPlayerCiv: String,
    val turnStartTime: Long,
    val minutesActive: Long,
    val minutesUntilSkipTurn: Int,
    val playerMinutesBeforeForceResign: Int,
    val averageTurnTimeForAutoresignHours: Int,
    val currentPlayerAvgTurnTimeSeconds: Int,
    val currentPlayerTurnsPlayedAsHuman: Int,
    val turns: Int
)

object GameManager {
    fun ensureMultiplayerAuthenticated() {
        try {
            val settings = UncivGame.Current.settings.multiplayer
            val serverUrl = settings.getServer()
            val botUserId = settings.getUserId().takeIf { it.isNotEmpty() } ?: "d51bf46e-4d31-4428-8bbd-ddab62a2062c"
            val password = settings.getCurrentServerPassword() ?: ""

            settings.setUserId(botUserId)
            settings.setCurrentServerPassword(password)

            UncivServerFileStorage.serverUrl = serverUrl
            UncivServerFileStorage.timeout = 30000

            val authStatus = UncivServerFileStorage.checkAuthStatus(botUserId, password)
            if (authStatus == AuthStatus.UNREGISTERED) {
                UncivServerFileStorage.authenticate(botUserId, password)
                UncivServerFileStorage.setPassword(password)
                println("🤖 Registered and authenticated bot with multiplayer server as $botUserId")
            } else {
                UncivServerFileStorage.authenticate(botUserId, password)
                println("🤖 Authenticated bot with multiplayer server as $botUserId")
            }
        } catch (e: Exception) {
            println("⚠️ Bot multiplayer authentication warning: ${e.message}")
        }
    }

    fun parsePlayers(playersInput: String?): ArrayList<Player> {
        require(!playersInput.isNullOrBlank()) { "Players list cannot be blank! Please specify players (e.g. player1:Rome, player2)." }

        return playersInput.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { entry ->
                val parts = when {
                    entry.contains(":") -> entry.split(":", limit = 2)
                    entry.contains("(") && entry.endsWith(")") -> listOf(
                        entry.substringBefore("(").trim(),
                        entry.substringAfter("(").removeSuffix(")").trim()
                    )
                    else -> listOf(entry)
                }
                val userId = parts[0].trim()
                val civName = parts.getOrNull(1)?.takeIf { it.isNotBlank() } ?: Constants.random
                Player(civName, PlayerType.Human, userId)
            }.let { 
                require(it.isNotEmpty()) { "At least one valid player must be specified!" }
                ArrayList(it) 
            }
    }

    fun getGameTimerInfo(gameId: String, trackedGamesData: TrackedGamesData): GameTimerInfo? {
        try {
            val multiplayerServer = MultiplayerServer()
            val gameInfo = runBlocking { multiplayerServer.tryDownloadGame(gameId) }
            val trackedGame = trackedGamesData.games.find { it.gameId == gameId }
            val averageTurnTime = trackedGame?.averageTurnTimeForAutoresignHours ?: 4

            val currentPlayerCiv = gameInfo.currentPlayerCiv
            val turnStartTime = gameInfo.currentTurnStartTime
            val minutesActive = (System.currentTimeMillis() - turnStartTime) / (1000 * 60)

            val avgTurnTimeSeconds = if (currentPlayerCiv.turnsPlayedAsHuman > 0) {
                currentPlayerCiv.totalTurnTimeSeconds / currentPlayerCiv.turnsPlayedAsHuman
            } else 0

            return GameTimerInfo(
                gameId = gameId,
                currentPlayerId = currentPlayerCiv.playerId,
                currentPlayerCiv = currentPlayerCiv.civName,
                turnStartTime = turnStartTime,
                minutesActive = minutesActive,
                minutesUntilSkipTurn = gameInfo.gameParameters.minutesUntilSkipTurn,
                playerMinutesBeforeForceResign = currentPlayerCiv.playerMinutesBeforeForceResign,
                averageTurnTimeForAutoresignHours = averageTurnTime,
                currentPlayerAvgTurnTimeSeconds = avgTurnTimeSeconds,
                currentPlayerTurnsPlayedAsHuman = currentPlayerCiv.turnsPlayedAsHuman,
                turns = gameInfo.turns
            )
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    fun skipTurn(gameId: String): String? {
        try {
            val multiplayerServer = MultiplayerServer()
            val gameInfo = runBlocking { multiplayerServer.tryDownloadGame(gameId) }
            val currentCiv = gameInfo.currentPlayerCiv
            val civName = currentCiv.civName

            gameInfo.nextTurn()

            for (civ in gameInfo.civilizations) {
                civ.addNotification("[$civName]'s turn was skipped by admin", NotificationCategory.General, civName)
            }

            runBlocking {
                multiplayerServer.uploadGame(gameInfo, withPreview = true)
            }
            return null
        } catch (e: Exception) {
            e.printStackTrace()
            return e.message ?: "Unknown error"
        }
    }

    fun resignCurrentPlayer(gameId: String): String? {
        try {
            val multiplayerServer = MultiplayerServer()
            val gameInfo = runBlocking { multiplayerServer.tryDownloadGame(gameId) }
            val currentCiv = gameInfo.currentPlayerCiv
            val civName = currentCiv.civName

            // Check if this is the last human player
            val humanPlayers = gameInfo.civilizations.count { it.playerType == PlayerType.Human && it.civName != Constants.spectator }
            if (humanPlayers <= 1) {
                return "Cannot resign the last human player in the game"
            }

            // Set player to AI
            currentCiv.playerType = PlayerType.AI
            currentCiv.playerId = ""
            currentCiv.turnsPlayedAsHuman = 0
            currentCiv.totalTurnTimeSeconds = 0

            gameInfo.nextTurn()

            // Add notification to all players
            for (civ in gameInfo.civilizations) {
                civ.addNotification("[$civName] was forcibly resigned by admin and is now controlled by AI", NotificationCategory.General, civName)
            }

            runBlocking {
                multiplayerServer.uploadGame(gameInfo, withPreview = true)
            }
            return null
        } catch (e: Exception) {
            e.printStackTrace()
            return e.message ?: "Unknown error"
        }
    }

    fun isGameOver(gameId: String): Boolean {
        try {
            val multiplayerServer = MultiplayerServer()
            val gameInfo = runBlocking { multiplayerServer.tryDownloadGame(gameId) }

            // Check if a victory condition has been met
            if (gameInfo.victoryData != null) return true

            // Check if only 1 human player is left
            val humanPlayers = gameInfo.civilizations.count { it.playerType == PlayerType.Human  && it.civName != Constants.spectator }
            if (humanPlayers <= 1) return true

            return false
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    fun startGame(
        creatorDiscordId: Long,
        playersInput: String?,
        mapSizeStr: String?,
        mapTypeStr: String?,
        difficultyStr: String?,
        cityStatesStr: String?,
        strategicBalance: Boolean?,
        legendaryStart: Boolean?,
        worldWrap: Boolean?,
        skipTurnHours: Int?,
        averageTurnTimeForAutoresignHours: Int?,
        trackedGamesData: TrackedGamesData
    ): TrackedGame? {
        try {
            val parsedPlayers = parsePlayers(playersInput)
            val botUncivId = UncivGame.Current.settings.multiplayer.getUserId().takeIf { it.isNotEmpty() } ?: "d51bf46e-4d31-4428-8bbd-ddab62a2062c"
            parsedPlayers.add(Player(Constants.spectator, PlayerType.Human, botUncivId))

            val skipHours = skipTurnHours ?: 12
            val autoResignHours = averageTurnTimeForAutoresignHours ?: 4

            val gameSetupInfo = GameSetupInfo().apply {
                gameParameters.isOnlineMultiplayer = true
                gameParameters.multiplayerServerUrl = UncivGame.Current.settings.multiplayer.getServer()
                gameParameters.players = parsedPlayers
                gameParameters.anyoneCanSpectate = true
                gameParameters.minutesUntilSkipTurn = skipHours * 60
                gameParameters.baseRuleset = BaseRuleset.Civ_V_GnK.fullName
                gameParameters.speed = "Quick"
                gameParameters.espionageEnabled = true

                mapParameters.mapSize = when (mapSizeStr?.lowercase()) {
                    "tiny" -> MapSize.Tiny
                    "small" -> MapSize.Small
                    "medium", "standard" -> MapSize.Medium
                    "large" -> MapSize.Large
                    "huge" -> MapSize.Huge
                    else -> MapSize.Auto
                }
                mapParameters.strategicBalance = strategicBalance ?: true
                mapParameters.worldWrap = worldWrap ?: true
                mapParameters.legendaryStart = legendaryStart ?: false

                mapParameters.type = when (mapTypeStr?.lowercase()) {
                    "archipelago" -> MapType.archipelago
                    "fractal" -> MapType.fractal
                    "continents", "continent and islands" -> MapType.continentAndIslands
                    "two continents", "twocontinents" -> MapType.twoContinents
                    "lakes" -> MapType.lakes
                    "small continents" -> MapType.smallContinents
                    "perlin" -> MapType.perlin
                    else -> MapType.pangaea
                }

                if (!difficultyStr.isNullOrBlank()) {
                    gameParameters.difficulty = difficultyStr
                }

                val playerCount = gameParameters.players.count { it.chosenCiv != Constants.spectator }
                val cityStatesValue = cityStatesStr?.let { str ->
                    if (str.endsWith("x", ignoreCase = true)) {
                        val mult = str.removeSuffix("x").removeSuffix("X").trim().toFloatOrNull() ?: 1.0f
                        (playerCount * mult).toInt()
                    } else {
                        str.toIntOrNull() ?: playerCount
                    }
                } ?: playerCount
                gameParameters.numberOfCityStates = max(0, cityStatesValue)
            }

            val newGame = GameStarter.startNewGame(gameSetupInfo)
            newGame.gameParameters.victoryTypes = ArrayList(newGame.ruleset.victories.keys)
            UncivGame.Current.gameInfo = newGame

            runBlocking<Unit> {
                val multiplayerServer = MultiplayerServer()
                multiplayerServer.uploadGame(newGame, withPreview = true)
            }

            val trackedGame = TrackedGame(
                gameId = newGame.gameId,
                creatorDiscordId = creatorDiscordId,
                players = parsedPlayers.map { it.playerId }.toMutableList(),
                status = "ACTIVE",
                skipTurnHours = skipHours,
                averageTurnTimeForAutoresignHours = autoResignHours
            )
            trackedGamesData.games.add(trackedGame)
            StorageService.saveTrackedGames(trackedGamesData)
            return trackedGame
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    fun winners(gameId: String): List<String> {
        try {
            val multiplayerServer = MultiplayerServer()
            val gameInfo = runBlocking { multiplayerServer.tryDownloadGame(gameId) }

            // If victoryData is set, find the winner's Civilization and return their playerId
            val victoryData = gameInfo.victoryData
            if (victoryData != null) {
                val winner = gameInfo.civilizations.find { it.civName == victoryData.winningCiv }
                if (winner != null) {
                    return listOf(winner.playerId)
                }
            }

            // If only 1 human player is left, return their civ ID
            val humanPlayers = gameInfo.civilizations.filter { it.playerType == PlayerType.Human && it.civName != Constants.spectator }
            if (humanPlayers.size == 1) {
                return listOf(humanPlayers[0].playerId)
            }

            return emptyList()
        } catch (e: Exception) {
            e.printStackTrace()
            return emptyList()
        }
    }

    fun getAliveHumanPlayers(gameId: String): List<String> {
        try {
            val multiplayerServer = MultiplayerServer()
            val gameInfo = runBlocking { multiplayerServer.tryDownloadGame(gameId) }
            return gameInfo.civilizations
                .filter { it.playerType == PlayerType.Human && it.isAlive() && it.civName != Constants.spectator }
                .map { it.playerId }
        } catch (e: Exception) {
            e.printStackTrace()
            return emptyList()
        }
    }
}

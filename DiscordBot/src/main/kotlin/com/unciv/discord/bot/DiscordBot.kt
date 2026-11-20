package com.unciv.discord.bot

import com.unciv.UncivGame
import com.unciv.discord.model.BotData
import com.unciv.discord.model.TrackedGamesData
import com.unciv.discord.service.GameManager
import com.unciv.discord.service.GameManager.ensureMultiplayerAuthenticated
import com.unciv.discord.service.PlayerManager
import com.unciv.discord.service.StorageService
import com.unciv.models.metadata.GameSettings
import com.unciv.models.ruleset.RulesetCache
import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.entities.Activity
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.Commands
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

object DiscordBot {
    private var started = false
    val data = BotData()
    val trackedGamesData = TrackedGamesData()
    private val scheduler = Executors.newSingleThreadScheduledExecutor()

    private fun startTurnTimerChecker() {
        scheduler.scheduleAtFixedRate({
            try {
                for (game in trackedGamesData.games.filter { it.status == "ACTIVE" }) {
                    val timerInfo = GameManager.getGameTimerInfo(game.gameId, trackedGamesData)
                    if (timerInfo != null) {

                        // Auto-skip if player has been on turn longer than the skip limit
                        if (timerInfo.minutesActive >= timerInfo.minutesUntilSkipTurn) {
                            println("   [Game ${timerInfo.gameId}] Skipping turn for ${timerInfo.currentPlayerCiv} (active for ${timerInfo.minutesActive}m)")
                            GameManager.skipTurn(game.gameId)
                        }
                        // Auto-resign if player's average turn time exceeds the auto-resign threshold
                        else {
                            val resignThresholdSeconds = timerInfo.averageTurnTimeForAutoresignHours * 3600
                            if (timerInfo.currentPlayerAvgTurnTimeSeconds >= resignThresholdSeconds && timerInfo.currentPlayerTurnsPlayedAsHuman > 0) {
                                println("   [Game ${timerInfo.gameId}] Resigning ${timerInfo.currentPlayerCiv} (avg turn time ${timerInfo.currentPlayerAvgTurnTimeSeconds}s exceeds threshold ${resignThresholdSeconds}s)")
                                GameManager.resignCurrentPlayer(game.gameId)
                            }
                        }
                    }
                    // Check if game is over (victory condition or only 1 human left)
                    val gameWinners = GameManager.winners(game.gameId)
                    if (gameWinners.isNotEmpty()) {
                        println("   [Game ${game.gameId}] Game is over!")
                        PlayerManager.awardElo(data, game, gameWinners)
                        game.status = "INACTIVE"
                        StorageService.saveTrackedGames(trackedGamesData)
                        continue
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, 1, 60, TimeUnit.MINUTES)
    }

    private fun startAutoUpdateScheduler() {
        // Exit with code 99 every 48 hours to trigger git pull and rebuild in the watchdog bash script
        scheduler.scheduleAtFixedRate({
            try {
                println("🔄 Scheduled 48-hour auto-restart for git update check...")
                StorageService.saveData(data)
                StorageService.saveTrackedGames(trackedGamesData)
                kotlin.system.exitProcess(99)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, 48, 48, TimeUnit.HOURS)
    }

    fun startBot() {
        if (started) return
        started = true

        val token = System.getenv("DISCORD_BOT_TOKEN")
            ?: System.getProperty("DISCORD_BOT_TOKEN")
            ?: File("../../DiscordBot/discord_token.txt").takeIf { it.exists() }?.readText()?.trim()
            ?: return println("❌ No Discord token found!")

        val settingsFile = File("GameSettings.json").takeIf { it.exists() }
        val gameSettings = if (settingsFile != null) {
            com.unciv.logic.files.UncivFiles.getSettingsForPlatformLaunchers(".")
        } else {
            GameSettings()
        }

        UncivGame.Current = UncivGame(true)
        UncivGame.Current.settings = gameSettings
        RulesetCache.loadRulesets(true)
        ensureMultiplayerAuthenticated()

        StorageService.loadData(data)
        StorageService.loadTrackedGames(trackedGamesData)

        startTurnTimerChecker()
        startAutoUpdateScheduler()

        val jda = JDABuilder.createDefault(token)
            .setActivity(Activity.playing("Unciv"))
            .addEventListeners(CommandListener())
            .build()
            .awaitReady()

        val botUserId = jda.selfUser.idLong
        val botTag = jda.selfUser.asTag
        println("🤖 Connected as $botTag (Bot ID: $botUserId)")

        val commandUpdater = jda.updateCommands()

        commandUpdater.addCommands(
            Commands.slash("ping", "Replies with Pong!"),
            Commands.slash("register", "Register yourself as a player")
                .addOption(OptionType.STRING, "unciv_id", "Optional Unciv ID/name", false),
            Commands.slash("unregister", "Unregister your account from the bot"),
            Commands.slash("changeuncivid", "Change your Unciv ID")
                .addOption(OptionType.STRING, "unciv_id", "The new Unciv ID", true),
            Commands.slash("elo", "Show your or another user's ELO")
                .addOption(OptionType.USER, "user", "The user to check", false),
            // We don't want players to have direct access to ELO calculations.
            //Commands.slash("duel", "Calculate ELO change for a duel")
            //    .addOption(OptionType.USER, "winner", "Winner", true)
            //    .addOption(OptionType.USER, "loser", "Loser", true),
            Commands.slash("leaderboard", "Show top 10 players"),
            Commands.slash("tokens", "Check your or another user's tokens")
                .addOption(OptionType.USER, "user", "The user to check", false),
            Commands.slash("startgame", "Generate a new Unciv map and start an online multiplayer game")
                .addOption(OptionType.STRING, "players", "Comma separated list (Discord mentions with optional :civ e.g. @user1, @user2 or @user1:Rome,@user2)", true)
                .addOption(OptionType.STRING, "map_size", "Map size (Auto, Tiny, Small, Medium, Large, Huge) [Default: Auto]", false)
                .addOption(OptionType.STRING, "map_type", "Map type (e.g. Pangaea, Archipelago, Fractal) [Default: Pangaea]", false)
                .addOption(OptionType.STRING, "difficulty", "Game difficulty (e.g. Prince, King, Deity) [Default: King]", false)
                .addOption(OptionType.STRING, "city_states", "Number of city-states (e.g. 5 or 1.5x relative to players) [Default: 1x]", false)
                .addOption(OptionType.BOOLEAN, "strategic_balance", "Enable strategic balance resource placement [Default: true]", false)
                .addOption(OptionType.BOOLEAN, "legendary_start", "Enable legendary start resource placement [Default: false]", false)
                .addOption(OptionType.BOOLEAN, "world_wrap", "Enable world wrap [Default: true]", false)
                .addOption(OptionType.INTEGER, "skip_turn_hours", "Hours until turn can be skipped [Default: 12]", false)
                .addOption(OptionType.INTEGER, "average_time_autoresign_hours", "Average turn time for auto-resign in hours [Default: 4]", false),
            Commands.slash("admingametimer", "Admin: Check turn timer and auto-resign info for a game")
                .addOption(OptionType.STRING, "game_id", "The game ID", true),
            Commands.slash("adminbackup", "Admin: Create timestamped backups of bot data and tracked games"),
            Commands.slash("adminresetdata", "Admin: Create a safety backup and reset/delete all bot data and tracked games"),
            Commands.slash("draw", "Propose a draw for an active game")
                .addOption(OptionType.STRING, "game_id", "The game ID", true),
            Commands.slash("adminskipturn", "Admin: Skip the current player's turn for a game")
                .addOption(OptionType.STRING, "game_id", "The game ID", true),
            Commands.slash("adminresignplayer", "Admin: Resign the current player to AI for a game")
                .addOption(OptionType.STRING, "game_id", "The game ID", true),

            Commands.slash("adminregisterother", "Admin: Register another player")
                .addOption(OptionType.USER, "user", "The user to register", true)
                .addOption(OptionType.STRING, "unciv_id", "Optional Unciv ID/name", false),
            Commands.slash("adminunregisteruser", "Admin: Unregister another user")
                .addOption(OptionType.USER, "user", "The user to unregister", true),
            Commands.slash("adminsetelo", "Admin: Set a player's ELO")
                .addOption(OptionType.USER, "user", "Target user", true)
                .addOption(OptionType.INTEGER, "value", "New ELO value", true),
            Commands.slash("adminaddelo", "Admin: Add or remove ELO to a player")
                .addOption(OptionType.USER, "user", "Target user", true)
                .addOption(OptionType.INTEGER, "value", "ELO change (can be negative)", true),
            Commands.slash("adminaddtokens", "Admin: Add tokens to a player")
                .addOption(OptionType.USER, "user", "Target user", true)
                .addOption(OptionType.INTEGER, "amount", "Number of tokens", true),
            Commands.slash("adminremovetokens", "Admin: Remove tokens from a player")
                .addOption(OptionType.USER, "user", "Target user", true)
                .addOption(OptionType.INTEGER, "amount", "Number of tokens", true)
        ).queue()

        println("✅ Discord bot started successfully.")
    }
}

package com.unciv.discord.bot

import com.unciv.discord.model.AddIdResult
import com.unciv.discord.model.DrawProposal
import com.unciv.discord.model.GameProposal
import com.unciv.discord.model.RegisterResult
import com.unciv.discord.service.GameManager
import com.unciv.discord.service.PlayerManager
import com.unciv.discord.service.StorageService
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.Commands
import net.dv8tion.jda.api.interactions.components.ActionRow
import net.dv8tion.jda.api.interactions.components.buttons.Button

class CommandListener : ListenerAdapter() {
    private val activeDrawProposals = mutableMapOf<String, DrawProposal>()
    private val activeGameProposals = mutableMapOf<String, GameProposal>()

    private fun isAdmin(event: SlashCommandInteractionEvent): Boolean {
        val member = event.member ?: return false
        return member.hasPermission(Permission.ADMINISTRATOR) ||
               member.roles.any { it.name.equals("Administrator", ignoreCase = true) || it.name.equals("Admin", ignoreCase = true) }
    }

    private fun buildGameProposalText(proposal: GameProposal, accepted: Int, total: Int): String {
        val mentions = proposal.invitedDiscordIds.map { "<@$it>" }.joinToString(" ")
        return buildString {
            appendLine("🎮 **New Unciv Game Proposal** by <@${proposal.proposerId}>!")
            appendLine("• **Players:** $mentions")
            appendLine("• **Map Size:** ${proposal.mapSize ?: "Auto"}")
            appendLine("• **Map Type:** ${proposal.mapType ?: "Pangaea"}")
            appendLine("• **Difficulty:** ${proposal.difficulty ?: "King"}")
            appendLine("• **City-States:** ${proposal.cityStates ?: "1x"}")
            appendLine("• **Strategic Balance:** ${if (proposal.strategicBalance != false) "Enabled" else "Disabled"}")
            appendLine("• **Legendary Start:** ${if (proposal.legendaryStart == true) "Enabled" else "Disabled"}")
            appendLine("• **World Wrap:** ${if (proposal.worldWrap != false) "Enabled" else "Disabled"}")
            appendLine("• **Turn Skip Limit:** ${proposal.skipTurnHours ?: 12}h")
            appendLine("• **Auto-Resign Limit:** ${proposal.averageTurnTimeHours ?: 4}h")
            appendLine()
            append("All invited players must accept ($accepted/$total accepted).")
        }
    }

    override fun onSlashCommandInteraction(event: SlashCommandInteractionEvent) {
        if (event.name.startsWith("admin") && !isAdmin(event)) {
            event.reply("❌ You must be an Administrator to use this command!").setEphemeral(true).queue()
            return
        }

        val data = DiscordBot.data
        val trackedGamesData = DiscordBot.trackedGamesData

        when (event.name) {
            "ping" -> event.reply("🏓 Pong!").queue()

            "register" -> {
                val userDiscordId = event.user.idLong
                val uncivId = event.getOption("unciv_id")?.asString
                val replyText = when (PlayerManager.registerUser(data, userDiscordId, uncivId, event.user.effectiveName)) {
                    RegisterResult.SUCCESS -> "✅ You are now registered${if (uncivId != null) " with Unciv ID **$uncivId**" else ""}, ${event.user.asMention}!"
                    RegisterResult.ALREADY_REGISTERED -> "⚠️ You are already registered!"
                    RegisterResult.UNCIV_ID_TAKEN -> "❌ That Unciv ID is already registered by another player!"
                }
                event.reply(replyText).setEphemeral(true).queue()
            }

            "unregister" -> {
                val userDiscordId = event.user.idLong
                val success = PlayerManager.unregisterUser(data, userDiscordId)
                if (success) {
                    event.reply("✅ You have been successfully unregistered from the bot.").setEphemeral(true).queue()
                } else {
                    event.reply("⚠️ You are not registered yet!").setEphemeral(true).queue()
                }
            }

            "changeuncivid" -> {
                val userDiscordId = event.user.idLong
                val uncivId = event.getOption("unciv_id")!!.asString
                val replyText = when (PlayerManager.changeUncivId(data, userDiscordId, uncivId)) {
                    AddIdResult.SUCCESS -> "✅ Successfully changed your Unciv ID to **$uncivId**!"
                    AddIdResult.NOT_REGISTERED -> "❌ You are not registered yet. Use `/register` first!"
                    AddIdResult.ALREADY_HAS_ID -> "⚠️ You already have this Unciv ID linked to your account."
                    AddIdResult.UNCIV_ID_TAKEN -> "❌ That Unciv ID is already registered by another player!"
                }
                event.reply(replyText).setEphemeral(true).queue()
            }

            "elo" -> {
                val user = event.getOption("user")?.asUser ?: event.user
                val player = PlayerManager.getPlayer(data, user.idLong, user.effectiveName)
                val username = player?.discordName ?: user.effectiveName
                if (player != null) {
                    event.reply("⭐ **$username** has **${player.elo} ELO**").queue()
                } else {
                    event.reply("❌ **${user.asMention}** is not registered. Use `/register` first!").queue()
                }
            }
/*
            "duel" -> {
                val winner = event.getOption("winner")!!.asUser
                val loser = event.getOption("loser")!!.asUser
                val result = PlayerManager.duel(data, winner.idLong, loser.idLong)
                if (result != null) {
                    val (wChange, lChange) = result
                    event.reply(
                        "⚔️ Duel result:\n" +
                            "🏆 ${winner.asMention}: **+$wChange ELO**\n" +
                            "💀 ${loser.asMention}: **$lChange ELO**"
                    ).queue()
                } else {
                    event.reply("❌ Both players must be registered before dueling!").queue()
                }
            }
*/
            "leaderboard" -> {
                val board = PlayerManager.leaderboard(data)
                if (board.isNotEmpty()) {
                    event.reply("🏅 **Top 10 Players**\n$board").queue()
                } else {
                    event.reply("❌ No players registered yet!").queue()
                }
            }

            "tokens" -> {
                val user = event.getOption("user")?.asUser ?: event.user
                val tokens = PlayerManager.getTokens(data, user.idLong)
                if (tokens != null)
                    event.reply("🎟️ ${user.asMention} has **$tokens tokens**.").queue()
                else
                    event.reply("❌ ${user.asMention} is not registered. Use `/register` first!").queue()
            }

            "startgame" -> {
                val playersInput = event.getOption("players")!!.asString
                val mapSize = event.getOption("map_size")?.asString
                val mapType = event.getOption("map_type")?.asString
                val difficulty = event.getOption("difficulty")?.asString
                val cityStates = event.getOption("city_states")?.asString
                val strategicBalance = event.getOption("strategic_balance")?.asBoolean
                val legendaryStart = event.getOption("legendary_start")?.asBoolean
                val worldWrap = event.getOption("world_wrap")?.asBoolean
                val skipTurnHours = event.getOption("skip_turn_hours")?.asInt
                val averageTurnTimeHours = event.getOption("average_time_autoresign_hours")?.asInt

                val entries = playersInput.split(",").filter { it.isNotEmpty() }
                val invitedDiscordIds = mutableSetOf<Long>()
                val mappedPlayersList = mutableListOf<String>()

                for (entry in entries) {
                    val discordIdStr = entry.substringBefore(":").removePrefix(" ").removeSuffix(" ").removePrefix("<@").removeSuffix(">")
                    val discordIdLong = discordIdStr.toLongOrNull()
                    val playerEntry = if (discordIdLong != null) PlayerManager.getPlayer(data, discordIdLong) else null
                    if (playerEntry == null || playerEntry.uncivId.isNullOrBlank()) {
                        event.reply("❌ Player `<@$discordIdStr>` (or `$entry`) is not registered or has no linked Unciv ID! All players must use `/register` before starting a game.").setEphemeral(true).queue()
                        return
                    }
                    if (discordIdLong != null) {
                        invitedDiscordIds.add(discordIdLong)
                    }
                    val uncivId = playerEntry.uncivId!!
                    val nation = entry.substringAfter(":").removePrefix(" ").removeSuffix(" ")
                    if (entry.contains(":")) {
                        mappedPlayersList.add("$uncivId:$nation")
                    } else {
                        mappedPlayersList.add(uncivId)
                    }
                }
                invitedDiscordIds.add(event.user.idLong)

                val proposalId = java.util.UUID.randomUUID().toString()
                val proposal = GameProposal(
                    proposalId = proposalId,
                    proposerId = event.user.idLong,
                    playersInput = playersInput,
                    mapSize = mapSize,
                    mapType = mapType,
                    difficulty = difficulty,
                    cityStates = cityStates,
                    strategicBalance = strategicBalance,
                    legendaryStart = legendaryStart,
                    worldWrap = worldWrap,
                    skipTurnHours = skipTurnHours,
                    averageTurnTimeHours = averageTurnTimeHours,
                    invitedDiscordIds = invitedDiscordIds,
                    acceptedDiscordIds = mutableSetOf(event.user.idLong)
                )
                activeGameProposals[proposalId] = proposal

                val accepted = proposal.acceptedDiscordIds.size
                val total = proposal.invitedDiscordIds.size

                if (accepted >= total) {
                    activeGameProposals.remove(proposalId)
                    event.deferReply().queue()
                    Thread {
                        val playersMapped = mappedPlayersList.joinToString(",")
                        val trackedGame = GameManager.startGame(
                            event.user.idLong,
                            playersMapped,
                            mapSize,
                            mapType,
                            difficulty,
                            cityStates,
                            strategicBalance,
                            legendaryStart,
                            worldWrap,
                            skipTurnHours,
                            averageTurnTimeHours,
                            trackedGamesData
                        )
                        if (trackedGame != null) {
                            event.hook.sendMessage("✅ Successfully generated a new Unciv map and uploaded game to server!\n**Game ID:** `${trackedGame.gameId}`").queue()
                        } else {
                            event.hook.sendMessage("❌ Failed to generate map or upload game to server.").queue()
                        }
                    }.start()
                    return
                }

                val buttons = ActionRow.of(
                    Button.success("game_accept:$proposalId", "Accept Game ($accepted/$total)"),
                    Button.danger("game_decline:$proposalId", "Decline Game")
                )
                val proposalText = buildGameProposalText(proposal, accepted, total)
                event.reply(proposalText)
                    .addComponents(buttons)
                    .queue()
            }

            // ==================== ADMIN COMMANDS ====================
            "admingametimer" -> {
                val gameId = event.getOption("game_id")!!.asString
                event.deferReply(true).queue()
                Thread {
                    val timerInfo = GameManager.getGameTimerInfo(gameId, trackedGamesData)
                    if (timerInfo != null) {
                        val avgMinutes = timerInfo.currentPlayerAvgTurnTimeSeconds / 60
                        val avgSeconds = timerInfo.currentPlayerAvgTurnTimeSeconds % 60
                        event.hook.sendMessage(
                            "⏱️ **Game Timer Info for `${timerInfo.gameId}`**:\n" +
                                "• Current Player: **${timerInfo.currentPlayerCiv}** (ID: `${timerInfo.currentPlayerId}`)\n" +
                                "• Turn Active Time: **${timerInfo.minutesActive} minutes**\n" +
                                "• Skip Turn Limit: **${timerInfo.minutesUntilSkipTurn} minutes**\n" +
                                "• Remaining Force Resign Time: **${timerInfo.playerMinutesBeforeForceResign} minutes**\n" +
                                "• Target Auto-Resign Avg: **${timerInfo.averageTurnTimeForAutoresignHours} hours**\n" +
                                "• Current Player Avg Turn Time: **${avgMinutes}m ${avgSeconds}s** (${timerInfo.currentPlayerTurnsPlayedAsHuman} turns played)"
                        ).setEphemeral(true).queue()
                    } else {
                        event.hook.sendMessage("❌ Failed to fetch game timer info for game ID `$gameId`.").setEphemeral(true).queue()
                    }
                }.start()
            }

            "adminbackup" -> {
                val backups = StorageService.createBackup()
                if (backups.isNotEmpty()) {
                    event.reply("📦 Successfully created backups:\n" + backups.joinToString("\n") { "• `$it`" }).setEphemeral(true).queue()
                } else {
                    event.reply("⚠️ No data files found to back up.").setEphemeral(true).queue()
                }
            }

            "adminresetdata" -> {
                val backups = StorageService.resetData(data, trackedGamesData)
                event.reply("🗑️ Bot data and tracked games have been reset!\n📦 Created safety backups before resetting:\n" + backups.joinToString("\n") { "• `$it`" }).setEphemeral(true).queue()
            }

            "adminregisterother" -> {
                val user = event.getOption("user")!!.asUser
                val uncivId = event.getOption("unciv_id")?.asString
                val replyText = when (PlayerManager.registerUser(data, user.idLong, uncivId, user.effectiveName)) {
                    RegisterResult.SUCCESS -> "✅ ${user.asMention} has been registered${if (uncivId != null) " with Unciv ID **$uncivId**" else ""}!"
                    RegisterResult.ALREADY_REGISTERED -> "⚠️ ${user.asMention} is already registered!"
                    RegisterResult.UNCIV_ID_TAKEN -> "❌ That Unciv ID is already registered by another player!"
                }
                event.reply(replyText).setEphemeral(true).queue()
            }

            "adminunregisteruser" -> {
                val user = event.getOption("user")!!.asUser
                val success = PlayerManager.unregisterUser(data, user.idLong)
                if (success) {
                    event.reply("✅ Successfully unregistered ${user.asMention}.").setEphemeral(true).queue()
                } else {
                    event.reply("⚠️ ${user.asMention} is not registered.").setEphemeral(true).queue()
                }
            }

            "adminsetelo" -> {
                val user = event.getOption("user")!!.asUser
                val value = event.getOption("value")!!.asInt
                if (PlayerManager.setElo(data, user.idLong, value))
                    event.reply("✅ Set ${user.asMention}'s ELO to **$value**").queue()
                else
                    event.reply("❌ ${user.asMention} is not registered.").queue()
            }

            "adminaddelo" -> {
                val user = event.getOption("user")!!.asUser
                val delta = event.getOption("value")!!.asInt
                if (PlayerManager.addElo(data, user.idLong, delta))
                    event.reply("✅ Added **$delta** ELO to ${user.asMention} ").queue()
                else
                    event.reply("❌ ${user.asMention} is not registered.").queue()
            }

            "adminaddtokens" -> {
                val user = event.getOption("user")!!.asUser
                val amount = event.getOption("amount")!!.asInt
                PlayerManager.addTokens(data, user.idLong, amount)
                event.reply("✅ Added **$amount tokens** to ${user.asMention}.").queue()
            }

            "adminremovetokens" -> {
                val user = event.getOption("user")!!.asUser
                val amount = event.getOption("amount")!!.asInt
                PlayerManager.removeTokens(data, user.idLong, amount)
                event.reply("✅ Removed **$amount tokens** from ${user.asMention}.").queue()
            }

            "adminskipturn" -> {
                val gameId = event.getOption("game_id")!!.asString
                if (GameManager.skipTurn(gameId) == null) {
                    event.reply("✅ Skipped turn for game `$gameId`.").queue()
                }
                else {
                    event.reply("❌ Failed to skip turn for game `$gameId`.").queue()
                }
            }

            "draw" -> {
                val gameId = event.getOption("game_id")!!.asString
                val trackedGame = trackedGamesData.games.find { it.gameId == gameId && it.status == "ACTIVE" }
                if (trackedGame == null) {
                    event.reply("❌ Game `$gameId` not found or is not active!").setEphemeral(true).queue()
                    return
                }

                val alivePlayerIds = GameManager.getAliveHumanPlayers(gameId)
                val aliveDiscordIds = alivePlayerIds.mapNotNull { gamePlayerId ->
                    PlayerManager.findPlayerEntry(data, gamePlayerId)?.userDiscordId
                }.toSet()

                if (aliveDiscordIds.isEmpty()) {
                    event.reply("❌ No registered alive players found for game `$gameId`!").setEphemeral(true).queue()
                    return
                }

                if (!aliveDiscordIds.contains(event.user.idLong)) {
                    event.reply("❌ You are not an alive player in this game!").setEphemeral(true).queue()
                    return
                }

                if (activeDrawProposals.containsKey(gameId)) {
                    event.reply("⚠️ A draw proposal is already active for game `$gameId`!").setEphemeral(true).queue()
                    return
                }

                val proposal = DrawProposal(
                    gameId = gameId,
                    aliveDiscordIds = aliveDiscordIds,
                    acceptedDiscordIds = mutableSetOf(event.user.idLong)
                )
                activeDrawProposals[gameId] = proposal

                val accepted = proposal.acceptedDiscordIds.size
                val total = proposal.aliveDiscordIds.size

                if (accepted >= total) {
                    activeDrawProposals.remove(gameId)
                    val alivePlayerIds = GameManager.getAliveHumanPlayers(gameId)
                    PlayerManager.awardElo(data, trackedGame, alivePlayerIds)
                    trackedGame.status = "INACTIVE"
                    StorageService.saveTrackedGames(trackedGamesData)
                    StorageService.saveData(data)
                    event.reply("🏆 **All alive players agreed to a draw!** ELO scores have been updated (players who lost earlier are treated as losers) and game `$gameId` is now inactive.").queue()
                    return
                }

                val buttons = ActionRow.of(
                    Button.success("draw_accept:$gameId", "Accept Draw ($accepted/$total)"),
                    Button.danger("draw_decline:$gameId", "Decline Draw")
                )
                event.reply("🤝 **Draw Proposed** by ${event.user.asMention} for game `$gameId`.\nAll alive players must agree ($accepted/$total accepted).")
                    .addComponents(buttons)
                    .queue()
            }
            
            "adminresignplayer" -> {
                val gameId = event.getOption("game_id")!!.asString
                if (GameManager.resignCurrentPlayer(gameId) == null) {
                    event.reply("✅ Resigned player for game `$gameId`.").queue()
                }
                else {
                    event.reply("❌ Failed to resign player for game `$gameId`.").queue()
                }
                
            }
        }
    }

    override fun onButtonInteraction(event: ButtonInteractionEvent) {
        val componentId = event.button.id ?: return
        val data = DiscordBot.data
        val trackedGamesData = DiscordBot.trackedGamesData

        when {
            componentId.startsWith("draw_accept:") -> {
                val gameId = componentId.substringAfter(":")
                val proposal = activeDrawProposals[gameId]
                if (proposal == null) {
                    event.reply("❌ This draw proposal has expired or already been resolved.").setEphemeral(true).queue()
                    return
                }
                if (!proposal.aliveDiscordIds.contains(event.user.idLong)) {
                    event.reply("❌ You are not an alive player in this game!").setEphemeral(true).queue()
                    return
                }
                proposal.acceptedDiscordIds.add(event.user.idLong)

                val trackedGame = trackedGamesData.games.find { it.gameId == gameId }
                val accepted = proposal.acceptedDiscordIds.size
                val total = proposal.aliveDiscordIds.size

                if (accepted >= total) {
                    activeDrawProposals.remove(gameId)
                    if (trackedGame != null) {
                        val alivePlayerIds = GameManager.getAliveHumanPlayers(gameId)
                        PlayerManager.awardElo(data, trackedGame, alivePlayerIds)
                        trackedGame.status = "INACTIVE"
                        StorageService.saveTrackedGames(trackedGamesData)
                        StorageService.saveData(data)
                    }
                    event.editMessage("🏆 **All alive players agreed to a draw!** ELO scores have been updated (players who lost earlier are treated as losers) and game `$gameId` is now inactive.")
                        .setComponents()
                        .queue()
                } else {
                    val buttons = ActionRow.of(
                        Button.success("draw_accept:$gameId", "Accept Draw ($accepted/$total)"),
                        Button.danger("draw_decline:$gameId", "Decline Draw")
                    )
                    event.editMessage("🤝 **Draw Proposed** for game `$gameId`.\nAll alive players must agree ($accepted/$total accepted).")
                        .setComponents(buttons)
                        .queue()
                    event.reply("✅ You accepted the draw.").setEphemeral(true).queue()
                }
            }
            componentId.startsWith("draw_decline:") -> {
                val gameId = componentId.substringAfter(":")
                val proposal = activeDrawProposals.remove(gameId)
                if (proposal == null) {
                    event.reply("❌ This draw proposal has expired or already been resolved.").setEphemeral(true).queue()
                    return
                }
                if (!proposal.aliveDiscordIds.contains(event.user.idLong)) {
                    event.reply("❌ You are not an alive player in this game!").setEphemeral(true).queue()
                    return
                }
                event.editMessage("❌ Draw proposal for game `$gameId` was declined by ${event.user.asMention}.")
                    .setComponents()
                    .queue()
            }
            componentId.startsWith("game_accept:") -> {
                val proposalId = componentId.substringAfter(":")
                val proposal = activeGameProposals[proposalId]
                if (proposal == null) {
                    event.reply("❌ This game proposal has expired or already been resolved.").setEphemeral(true).queue()
                    return
                }
                if (!proposal.invitedDiscordIds.contains(event.user.idLong)) {
                    event.reply("❌ You are not invited to this game!").setEphemeral(true).queue()
                    return
                }
                proposal.acceptedDiscordIds.add(event.user.idLong)

                val accepted = proposal.acceptedDiscordIds.size
                val total = proposal.invitedDiscordIds.size

                if (accepted >= total) {
                    activeGameProposals.remove(proposalId)
                    event.deferEdit().queue()
                    Thread {
                        val entries = proposal.playersInput.split(",").filter { it.isNotEmpty() }
                        val mappedPlayersList = mutableListOf<String>()
                        for (entry in entries) {
                            val discordIdStr = entry.substringBefore(":").removePrefix(" ").removeSuffix(" ").removePrefix("<@").removeSuffix(">")
                            val discordIdLong = discordIdStr.toLongOrNull()
                            val playerEntry = if (discordIdLong != null) PlayerManager.getPlayer(data, discordIdLong) else null
                            val uncivId = playerEntry?.uncivId ?: entry
                            val nation = entry.substringAfter(":").removePrefix(" ").removeSuffix(" ")
                            if (entry.contains(":")) {
                                mappedPlayersList.add("$uncivId:$nation")
                            } else {
                                mappedPlayersList.add(uncivId)
                            }
                        }
                        val playersMapped = mappedPlayersList.joinToString(",")

                        val trackedGame = GameManager.startGame(
                            proposal.proposerId,
                            playersMapped,
                            proposal.mapSize,
                            proposal.mapType,
                            proposal.difficulty,
                            proposal.cityStates,
                            proposal.strategicBalance,
                            proposal.legendaryStart,
                            proposal.worldWrap,
                            proposal.skipTurnHours,
                            proposal.averageTurnTimeHours,
                            trackedGamesData
                        )
                        if (trackedGame != null) {
                            event.hook.editOriginal("🏆 **All players accepted!** Successfully generated a new Unciv map and uploaded game to server!\n**Game ID:** `${trackedGame.gameId}`")
                                .setComponents()
                                .queue()
                        } else {
                            event.hook.editOriginal("❌ Failed to generate map or upload game to server.")
                                .setComponents()
                                .queue()
                        }
                    }.start()
                } else {
                    val buttons = ActionRow.of(
                        Button.success("game_accept:$proposalId", "Accept Game ($accepted/$total)"),
                        Button.danger("game_decline:$proposalId", "Decline Game")
                    )
                    val proposalText = buildGameProposalText(proposal, accepted, total)
                    event.editMessage(proposalText)
                        .setComponents(buttons)
                        .queue()
                    event.reply("✅ You accepted the game proposal.").setEphemeral(true).queue()
                }
            }
            componentId.startsWith("game_decline:") -> {
                val proposalId = componentId.substringAfter(":")
                val proposal = activeGameProposals.remove(proposalId)
                if (proposal == null) {
                    event.reply("❌ This game proposal has expired or already been resolved.").setEphemeral(true).queue()
                    return
                }
                if (!proposal.invitedDiscordIds.contains(event.user.idLong)) {
                    event.reply("❌ You are not invited to this game!").setEphemeral(true).queue()
                    return
                }
                event.editMessage("❌ Game proposal was declined by ${event.user.asMention}.")
                    .setComponents()
                    .queue()
            }
        }
    }
}

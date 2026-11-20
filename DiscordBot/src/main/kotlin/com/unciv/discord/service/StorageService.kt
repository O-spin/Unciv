package com.unciv.discord.service

import com.unciv.discord.model.BotData
import com.unciv.discord.model.TrackedGamesData
import kotlinx.serialization.json.Json
import java.io.File

object StorageService {
    val dataFile = File("../../DiscordBot/bot_data.json")
    val trackedGamesFile = File("../../DiscordBot/tracked_games.json")

    fun loadData(data: BotData) {
        if (dataFile.exists()) {
            try {
                val loaded = Json.decodeFromString<BotData>(dataFile.readText())
                data.players.clear()
                data.players.addAll(loaded.players)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun saveData(data: BotData) {
        dataFile.writeText(Json.encodeToString(data))
        println("📂 Bot data file path: ${dataFile.absolutePath}")
    }

    fun loadTrackedGames(trackedGamesData: TrackedGamesData) {
        if (trackedGamesFile.exists()) {
            try {
                val loaded = Json.decodeFromString<TrackedGamesData>(trackedGamesFile.readText())
                trackedGamesData.games.clear()
                trackedGamesData.games.addAll(loaded.games)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun saveTrackedGames(trackedGamesData: TrackedGamesData) {
        trackedGamesFile.writeText(Json.encodeToString(trackedGamesData))
        println("📂 Tracked games file path: ${trackedGamesFile.absolutePath}")
    }

    fun createBackup(): List<String> {
        val timestamp = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss").format(java.time.LocalDateTime.now())
        val backupsDir = File(dataFile.parentFile, "backups").apply { mkdirs() }
        val backupFiles = mutableListOf<String>()

        if (dataFile.exists()) {
            val backupFile = File(backupsDir, "bot_data_backup_$timestamp.json")
            dataFile.copyTo(backupFile, overwrite = true)
            backupFiles.add("backups/${backupFile.name}")
        }
        if (trackedGamesFile.exists()) {
            val backupFile = File(backupsDir, "tracked_games_backup_$timestamp.json")
            trackedGamesFile.copyTo(backupFile, overwrite = true)
            backupFiles.add("backups/${backupFile.name}")
        }
        println("📦 Created backups in backups folder: $backupFiles")
        return backupFiles
    }

    fun resetData(data: BotData, trackedGamesData: TrackedGamesData): List<String> {
        val backups = createBackup()
        data.players.clear()
        saveData(data)
        trackedGamesData.games.clear()
        saveTrackedGames(trackedGamesData)
        if (dataFile.exists()) dataFile.delete()
        if (trackedGamesFile.exists()) trackedGamesFile.delete()
        println("🗑️ Reset bot data and tracked games after backing up.")
        return backups
    }
}

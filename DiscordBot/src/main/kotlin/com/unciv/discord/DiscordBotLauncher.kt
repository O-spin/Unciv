package com.unciv.discord

import com.unciv.discord.bot.DiscordBot
import java.io.File

fun main(args: Array<String>) {
    val assetsDir = listOf(
        File("android/assets"),
        File("../android/assets"),
        File("../../android/assets")
    ).firstOrNull { it.exists() }

    if (assetsDir != null) {
        System.setProperty("user.dir", assetsDir.absolutePath)
        println("📂 Set working directory to assets: ${assetsDir.absolutePath}")
    }

    println("🚀 Starting standalone Unciv Discord Bot...")
    DiscordBot.startBot()

    // Keep the main thread alive so the bot process doesn't exit immediately
    Thread.currentThread().join()
}

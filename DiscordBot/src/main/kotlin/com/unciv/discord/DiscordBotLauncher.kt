package com.unciv.discord

import com.unciv.discord.bot.DiscordBot

fun main(args: Array<String>) {
    println("🚀 Starting standalone Unciv Discord Bot...")
    DiscordBot.startBot()

    // Keep the main thread alive so the bot process doesn't exit immediately
    Thread.currentThread().join()
}

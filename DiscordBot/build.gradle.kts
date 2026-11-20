plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    application
}

application {
    mainClass.set("com.unciv.discord.DiscordBotLauncherKt")
}

dependencies {
    implementation(project(":core"))
    implementation(rootProject.libs.coroutines.core)
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0")
    implementation("net.dv8tion:JDA:5.0.0")
    implementation(rootProject.libs.gdx)
    implementation(rootProject.libs.gdx.backend.headless)
}

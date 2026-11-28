#!/bin/bash

# ==============================================================================
# ENVIRONMENT ASSUMPTIONS & PREREQUISITES:
# 1. Operating System: Linux (Ubuntu, Debian, CentOS, RHEL, Arch, etc.)
# 2. Installed Software:
#    - Java Development Kit (JDK 17 or newer) available in PATH (`java -version`).
#    - Git installed and configured with repository access (SSH key or credentials cached).
#    - Bash shell.
# 3. Project Structure & Permissions:
#    - Located at the root directory of the UncivRankedBot Git repository.
#    - Gradle wrapper (`./gradlew`) must have execution permissions (`chmod +x gradlew`).
#    - This script (`run_bot.sh`) must have execution permissions (`chmod +x run_bot.sh`).
# ==============================================================================

PROJECT_DIR="$(dirname "$(realpath "$0")")"
cd "$PROJECT_DIR"

echo "UncivRankedBot Linux Watchdog Script Started."

while true; do
    echo "=================================================="
    echo "Building project distribution (:DiscordBot:installDist)..."
    echo "=================================================="

    ./gradlew :DiscordBot:installDist

    if [ $? -ne 0 ]; then
        echo "Gradle build failed! Retrying in 30 seconds..."
        sleep 30
        continue
    fi

    echo "=================================================="
    echo "Starting Discord Bot..."
    echo "=================================================="

    # Change into DiscordBot/build so that relative paths (../../DiscordBot/...) resolve correctly
    cd "$PROJECT_DIR/android/assets"
    "$PROJECT_DIR/DiscordBot/build/install/DiscordBot/bin/DiscordBot"
    EXIT_CODE=$?
    cd "$PROJECT_DIR"

    # Return to project root for git pull / gradlew in the next loop iteration
    cd "$PROJECT_DIR"

    echo "Bot process exited with exit code: $EXIT_CODE"

    if [ $EXIT_CODE -eq 99 ]; then
        echo "Update & restart requested by bot (Exit code 99). Pulling latest changes from origin DiscordBot..."
        
        git pull origin DiscordBot
        if [ $? -ne 0 ]; then
            echo "Git pull failed (possible merge conflicts or network issue). Waiting 60 seconds before retry..."
            sleep 60
        else
            echo "Git pull successful."
        fi
    else
        echo "Bot stopped unexpectedly or crashed. Restarting in 15 seconds..."
        sleep 15
    fi

    sleep 2
done

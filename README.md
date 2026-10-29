# Board Score Tracker

A compact Android score tracker designed for board-game tables with 2–12 players.

## Features
- Enter player names at the start (2–12 players).
- Compact score-entry rows designed to keep many players visible on one screen.
- Enter each player's score for a round, then add the round to everyone's totals.
- Round history, undo last round, and reset the game.
- Automatically saves the current game on the device.

## Build an APK using GitHub Actions (no PC required)
1. Create a new GitHub repository and upload the **contents** of this ZIP (not the ZIP's containing folder).
2. Open the repository's **Actions** tab.
3. Enable Actions if prompted, then run **Build Android APK** using **Run workflow**.
4. When it finishes, open the workflow run and download the `board-score-tracker-debug-apk` artifact.
5. Extract the artifact ZIP and install `app-debug.apk` on your Android phone. You may need to allow installing apps from your browser or file manager.

The workflow downloads Gradle on the GitHub runner, so the Gradle wrapper binary is not required in this project.
